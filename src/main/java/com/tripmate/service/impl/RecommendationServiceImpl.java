package com.tripmate.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tripmate.entity.Attraction;
import com.tripmate.entity.UserBehavior;
import com.tripmate.mapper.AttractionMapper;
import com.tripmate.mapper.UserBehaviorMapper;
import com.tripmate.service.BaiduMapsService;
import com.tripmate.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 混合评分推荐引擎。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationServiceImpl implements RecommendationService {

    // ── 行为权重 ──
    private static final Map<String, Integer> ACTION_WEIGHTS = Map.of(
        "BOOKMARK", 5,
        "LIKE", 3,
        "VIEW", 1
    );

    private static final List<String> ENGAGEMENT_ACTIONS = List.of("VIEW", "LIKE", "BOOKMARK");
    private static final double HALF_LIFE_DAYS = 30.0;
    private static final int SEED_CANDIDATE_POOL = 200;

    // ── 缓存 ──
    private static final String CACHE_KEY_USER = "recommend:v2:user:%d:%d";
    private static final String CACHE_KEY_GLOBAL = "recommend:v2:global:%d";
    private static final long CACHE_TTL_SECONDS = 60;

    private final UserBehaviorMapper userBehaviorMapper;
    private final AttractionMapper attractionMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final BaiduMapsService baiduMapsService;

    @Override
    public List<Attraction> recommend(int limit) {
        long start = System.currentTimeMillis();
        Long userId = getCurrentUserId();

        if (userId == null) {
            log.debug("推荐请求：未登录用户 limit={}", limit);
            List<Attraction> result = recommendWithLocation(limit);
            log.info("推荐完成（游客） resultSize={} elapsed={}ms", result.size(),
                    System.currentTimeMillis() - start);
            return result;
        }

        String key = String.format(CACHE_KEY_USER, userId, limit);
        @SuppressWarnings("unchecked")
        List<Attraction> cached = (List<Attraction>) redisTemplate.opsForValue().get(key);
        if (cached != null) {
            log.debug("推荐缓存命中 userId={} limit={}", userId, limit);
            return cached;
        }

        List<UserBehavior> allBehaviors = getAllBehaviors(userId);

        if (allBehaviors.isEmpty()) {
            log.debug("推荐请求：冷启动 userId={} limit={}", userId, limit);
            List<Attraction> result = recommendWithLocation(limit);
            redisTemplate.opsForValue().set(key, result, Duration.ofSeconds(CACHE_TTL_SECONDS));
            log.info("推荐完成（冷启动） userId={} resultSize={} elapsed={}ms",
                    userId, result.size(), System.currentTimeMillis() - start);
            return result;
        }

        log.debug("推荐请求：混合评分 userId={} behaviors={} limit={}",
                userId, allBehaviors.size(), limit);
        List<Attraction> result = hybridScore(allBehaviors, limit);
        redisTemplate.opsForValue().set(key, result, Duration.ofSeconds(CACHE_TTL_SECONDS));
        log.info("推荐完成（混合评分） userId={} resultSize={} elapsed={}ms",
                userId, result.size(), System.currentTimeMillis() - start);
        return result;
    }

    // ═══════════════════════════════════════════════════════════════
    // 混合评分引擎
    // ═══════════════════════════════════════════════════════════════

    private List<Attraction> hybridScore(List<UserBehavior> behaviors, int limit) {
        LocalDateTime now = LocalDateTime.now();

        // 1. 计算用户偏好向量 {category → score, city → score}
        Map<String, Double> categoryScore = new HashMap<>();
        Map<String, Double> cityScore = new HashMap<>();
        Map<Long, Double> attractionScore = new HashMap<>();
        Set<Long> excludeIds = new HashSet<>();

        for (UserBehavior b : behaviors) {
            excludeIds.add(b.getAttractionId());
            double w = actionWeight(b.getActionType()) * timeDecay(b.getCreatedAt(), now);
            attractionScore.merge(b.getAttractionId(), w, Double::sum);
        }

        // 加载已交互景点的类别/城市
        if (!attractionScore.isEmpty()) {
            List<Attraction> interacted = attractionMapper.selectBatchIds(attractionScore.keySet());
            for (Attraction a : interacted) {
                double w = attractionScore.getOrDefault(a.getId(), 1.0);
                if (a.getCategory() != null) {
                    categoryScore.merge(a.getCategory(), w, Double::sum);
                }
                if (a.getCity() != null) {
                    cityScore.merge(a.getCity(), w, Double::sum);
                }
            }
        }

        // 2. 拉取候选池
        List<Attraction> candidates = fetchCandidatePool(excludeIds);

        // 3. 获取 IP 位置
        String userCity = getUserCity();

        // 4. 计算 maxPopularity 用于归一化
        int maxView = candidates.stream()
                .mapToInt(a -> a.getViewCount() != null ? a.getViewCount() : 0)
                .max().orElse(1);

        // 5. 对候选池逐项打分
        List<ScoredAttraction> scored = new ArrayList<>();

        for (Attraction a : candidates) {
            if (a.getStatus() == null || a.getStatus() != 1) {
                continue;
            }

            // ── 偏好分 (0-100) ──
            double prefCat = categoryScore.getOrDefault(a.getCategory(), 0.0);
            double prefCity = cityScore.getOrDefault(a.getCity(), 0.0);
            // 归一化：用 top category score 作为基准
            double maxCat = categoryScore.values().stream().max(Double::compare).orElse(1.0);
            double prefScore = Math.min(100, (prefCat / maxCat) * 70 + (prefCity / maxCat) * 30);

            // ── 热度分 (0-100) ──
            int vc = a.getViewCount() != null ? a.getViewCount() : 0;
            double popScore = maxView > 0 ? (vc / (double) maxView) * 100 : 0;

            // ── 位置分 (0-100) ──
            double locScore = 0;
            if (userCity != null && userCity.equals(a.getCity())) {
                locScore = 100;
            }

            // ── 数据质量加分 ──
            double qualityBonus = (a.getLatitude() != null && a.getLongitude() != null) ? 10 : 0;

            // ── 用户评分因子 (0-100) ──
            int rating = a.getRating() != null ? a.getRating() : 0;
            double ratingScore = (rating / 5.0) * 100;

            // ── 多样性扰动 (±10) ──
            double diversity = (Math.random() - 0.5) * 20;

            // ── 综合得分 ──
            double total = 0.35 * prefScore + 0.25 * popScore + 0.15 * locScore
                         + 0.15 * ratingScore + qualityBonus + diversity;

            scored.add(new ScoredAttraction(a, total, prefScore, popScore));
        }

        // 6. 排序 + 多样性保障
        scored.sort((a, b) -> Double.compare(b.totalScore, a.totalScore));

        List<Attraction> result = new ArrayList<>();
        Set<String> usedCategories = new HashSet<>();
        int diversitySlots = Math.max(1, (int)(limit * 0.2)); // 20% 多样性保留

        // 逐项选取
        for (int i = 0; i < scored.size() && result.size() < limit; i++) {
            ScoredAttraction sa = scored.get(i);
            boolean fromTopCat = isTopCategory(sa.attraction, categoryScore, 3);

            if (result.size() < limit - diversitySlots) {
                // 主流槽位：偏好 + 热门
                result.add(sa.attraction);
                usedCategories.add(sa.attraction.getCategory());
            } else if (fromTopCat && usedCategories.size() < 3) {
                // 多样性槽位未满，先填主流
                result.add(sa.attraction);
                usedCategories.add(sa.attraction.getCategory());
            } else if (!fromTopCat) {
                // 多样性槽位：优先非 top 类别
                result.add(sa.attraction);
                usedCategories.add(sa.attraction.getCategory());
            }
        }

        // 7. 如果不够 limit，用全局热门补齐
        if (result.size() < limit) {
            Set<Long> existingIds = result.stream().map(Attraction::getId).collect(Collectors.toSet());
            List<Attraction> fillers = fetchGlobalHot(limit * 2);
            for (Attraction a : fillers) {
                if (result.size() >= limit) {
                    break;
                }
                if (!existingIds.contains(a.getId())) {
                    result.add(a);
                }
            }
        }

        return result;
    }

    // ═══════════════════════════════════════════════════════════════
    // 辅助方法
    // ═══════════════════════════════════════════════════════════════

    /** 时间衰减：e^(-t / halfLife)，半衰期后权重降为 50% */
    private double timeDecay(LocalDateTime eventTime, LocalDateTime now) {
        if (eventTime == null) {
            return 1.0;
        }
        double days = Duration.between(eventTime, now).toDays();
        return Math.exp(-days * Math.log(2) / HALF_LIFE_DAYS);
    }

    private double actionWeight(String actionType) {
        return ACTION_WEIGHTS.getOrDefault(actionType, 1);
    }

    /** 判断某景点类别是否属于用户 top N 偏好类别 */
    private boolean isTopCategory(Attraction a, Map<String, Double> categoryScore, int n) {
        if (categoryScore.isEmpty()) {
            return true;
        }
        List<String> topCats = categoryScore.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(n)
                .map(Map.Entry::getKey)
                .toList();
        return topCats.contains(a.getCategory());
    }

    /** 获取用户当前城市（通过 IP），失败返回 null */
    private String getUserCity() {
        try {
            BaiduMapsService.IpLocation loc = baiduMapsService.ipLocation();
            return loc != null ? loc.city() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** 获取当前用户 ID（未登录返回 null） */
    private Long getCurrentUserId() {
        try {
            return com.tripmate.common.UserContext.getUserId();
        } catch (Exception e) {
            return null;
        }
    }

    /** 拉取候选池（活跃景点，排除已交互） */
    private List<Attraction> fetchCandidatePool(Set<Long> excludeIds) {
        LambdaQueryWrapper<Attraction> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Attraction::getStatus, 1)
               .notIn(!excludeIds.isEmpty(), Attraction::getId, excludeIds)
               .orderByDesc(Attraction::getViewCount)
               .last("LIMIT " + SEED_CANDIDATE_POOL);
        return attractionMapper.selectList(wrapper);
    }

    /** 获取用户全部交互记录 */
    private List<UserBehavior> getAllBehaviors(Long userId) {
        LambdaQueryWrapper<UserBehavior> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserBehavior::getUserId, userId)
               .in(UserBehavior::getActionType, ENGAGEMENT_ACTIONS)
               .orderByDesc(UserBehavior::getCreatedAt);
        return userBehaviorMapper.selectList(wrapper);
    }

    /** 全局热门 */
    private List<Attraction> fetchGlobalHot(int limit) {
        LambdaQueryWrapper<Attraction> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Attraction::getStatus, 1)
               .orderByDesc(Attraction::getViewCount)
               .last("LIMIT " + limit);
        return attractionMapper.selectList(wrapper);
    }

    /** 冷启动：位置感知热门推荐 */
    private List<Attraction> recommendWithLocation(int limit) {
        String key = String.format(CACHE_KEY_GLOBAL, limit);
        @SuppressWarnings("unchecked")
        List<Attraction> cached = (List<Attraction>) redisTemplate.opsForValue().get(key);
        if (cached != null) {
            return cached;
        }

        // 优先推荐同城热门，不足时用异地热门补齐
        String userCity = getUserCity();
        List<Attraction> result = new ArrayList<>();
        Set<Long> seen = new HashSet<>();

        if (userCity != null && !userCity.isEmpty()) {
            LambdaQueryWrapper<Attraction> cityWrapper = new LambdaQueryWrapper<>();
            cityWrapper.eq(Attraction::getStatus, 1)
                       .eq(Attraction::getCity, userCity)
                       .orderByDesc(Attraction::getViewCount)
                       .last("LIMIT " + limit * 2);
            for (Attraction a : attractionMapper.selectList(cityWrapper)) {
                if (result.size() >= limit) {
                    break;
                }
                if (seen.add(a.getId())) {
                    result.add(a);
                }
            }
        }

        // 不够补齐全国热门
        if (result.size() < limit) {
            LambdaQueryWrapper<Attraction> hotWrapper = new LambdaQueryWrapper<>();
            hotWrapper.eq(Attraction::getStatus, 1)
                       .notIn(!seen.isEmpty(), Attraction::getId, seen)
                       .orderByDesc(Attraction::getViewCount)
                       .last("LIMIT " + (limit - result.size()));
            result.addAll(attractionMapper.selectList(hotWrapper));
        }

        redisTemplate.opsForValue().set(key, result, Duration.ofSeconds(CACHE_TTL_SECONDS));
        return result;
    }

    // ═══════════════════════════════════════════════════════════════
    // 内部类
    // ═══════════════════════════════════════════════════════════════

    private record ScoredAttraction(
            Attraction attraction,
            double totalScore,
            double prefScore,
            double popScore) {}
}
