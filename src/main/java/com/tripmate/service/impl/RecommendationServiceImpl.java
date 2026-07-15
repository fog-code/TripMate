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
 * 「规则 + 协同过滤」混合推荐引擎。
 *
 * <p>规则引擎：偏好分（类别+城市）+ 热度分 + 位置分 + 评分分 + 多样性扰动
 * 协同过滤：Item-based CF（Jaccard 相似度），挖掘"看了A的人也看了B"的隐藏关联
 * 最终得分 = 0.5 × 规则分 + 0.5 × CF 分</p>
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
    private static final int TOP_K_SIMILAR = 20;

    // ── 缓存 Key ──
    private static final String CACHE_KEY_USER = "recommend:v2:user:%d:%d";
    private static final String CACHE_KEY_GLOBAL = "recommend:v2:global:%d";
    private static final String CACHE_KEY_ITEM_SIM = "recommend:v2:itemSim";
    private static final long CACHE_TTL_SECONDS = 60;
    private static final long ITEM_SIM_CACHE_TTL = 1800; // 30 分钟

    // ── 融合权重 ──
    private static final double RULE_WEIGHT = 0.5;
    private static final double CF_WEIGHT = 0.5;

    private final UserBehaviorMapper userBehaviorMapper;
    private final AttractionMapper attractionMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final BaiduMapsService baiduMapsService;

    @Override
    public List<Attraction> recommend(int limit) {
        long start = System.currentTimeMillis();
        Long userId = getCurrentUserId();

        if (userId == null) {
            List<Attraction> result = recommendWithLocation(limit);
            log.info("推荐完成（游客） resultSize={} elapsed={}ms",
                    result.size(), System.currentTimeMillis() - start);
            return result;
        }

        String key = String.format(CACHE_KEY_USER, userId, limit);
        @SuppressWarnings("unchecked")
        List<Attraction> cached = (List<Attraction>) redisTemplate.opsForValue().get(key);
        if (cached != null) {
            log.debug("推荐缓存命中 userId={}", userId);
            return cached;
        }

        List<UserBehavior> allBehaviors = getAllBehaviors(userId);
        if (allBehaviors.isEmpty()) {
            List<Attraction> result = recommendWithLocation(limit);
            redisTemplate.opsForValue().set(key, result, Duration.ofSeconds(CACHE_TTL_SECONDS));
            log.info("推荐完成（冷启动） userId={} resultSize={} elapsed={}ms",
                    userId, result.size(), System.currentTimeMillis() - start);
            return result;
        }

        // ── 协同过滤：计算 CF 得分 ──
        Map<Long, Double> cfScores = getCFScores(userId, allBehaviors);
        log.debug("CF得分计算完成 userId={} scoredItems={}", userId, cfScores.size());

        // ── 规则 + CF 混合评分 ──
        List<Attraction> result = hybridScore(allBehaviors, cfScores, limit);
        redisTemplate.opsForValue().set(key, result, Duration.ofSeconds(CACHE_TTL_SECONDS));
        log.info("推荐完成（规则+CF） userId={} resultSize={} elapsed={}ms",
                userId, result.size(), System.currentTimeMillis() - start);
        return result;
    }

    // ═══════════════════════════════════════════════════════════════
    // Item-based 协同过滤
    // ═══════════════════════════════════════════════════════════════

    /**
     * 构建景点相似度矩阵（Jaccard），缓存到 Redis。
     *
     * <p>数据结构：Map<Long, List<SimilarItem>>
     *   key = 景点ID
     *   value = Top 20 最相似景点列表（按 Jaccard 降序）</p>
     */
    @SuppressWarnings("unchecked")
    private Map<Long, List<SimilarItem>> getItemSimilarityMatrix() {
        Map<Long, List<SimilarItem>> cached =
                (Map<Long, List<SimilarItem>>) redisTemplate.opsForValue().get(CACHE_KEY_ITEM_SIM);
        if (cached != null) {
            return cached;
        }
        Map<Long, List<SimilarItem>> matrix = computeItemSimilarities();
        redisTemplate.opsForValue().set(CACHE_KEY_ITEM_SIM, matrix, Duration.ofSeconds(ITEM_SIM_CACHE_TTL));
        log.info("景点相似度矩阵已构建 items={} cached=30min", matrix.size());
        return matrix;
    }

    /**
     * 从 user_behaviors 表计算景点之间的 Jaccard 相似度。
     *
     * <p>公式：Jaccard(A,B) = |同时交互A和B的用户数| / |交互A的用户数 ∪ 交互B的用户数|
     * 时间复杂度：O(U × K²)（U=用户数，K=每用户平均景点数，实际数据中 K 很小）</p>
     */
    private Map<Long, List<SimilarItem>> computeItemSimilarities() {
        // 1. 加载全量有效行为 → {userId → [attractionId, ...]}
        LambdaQueryWrapper<UserBehavior> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(UserBehavior::getActionType, ENGAGEMENT_ACTIONS)
               .select(UserBehavior::getUserId, UserBehavior::getAttractionId);
        List<UserBehavior> all = userBehaviorMapper.selectList(wrapper);

        Map<Long, Set<Long>> userItems = new LinkedHashMap<>();
        for (UserBehavior b : all) {
            userItems.computeIfAbsent(b.getUserId(), k -> new LinkedHashSet<>())
                     .add(b.getAttractionId());
        }

        // 2. 统计：coOccur[A][B] + userCount[A]
        Map<Long, Map<Long, Integer>> coOccur = new HashMap<>();
        Map<Long, Integer> userCount = new HashMap<>();

        for (Set<Long> items : userItems.values()) {
            List<Long> itemList = new ArrayList<>(items);
            for (Long id : itemList) {
                userCount.merge(id, 1, Integer::sum);
            }
            // 对每对景点累加共现次数
            for (int i = 0; i < itemList.size(); i++) {
                for (int j = i + 1; j < itemList.size(); j++) {
                    Long a = itemList.get(i);
                    Long b = itemList.get(j);
                    coOccur.computeIfAbsent(a, k -> new HashMap<>()).merge(b, 1, Integer::sum);
                    coOccur.computeIfAbsent(b, k -> new HashMap<>()).merge(a, 1, Integer::sum);
                }
            }
        }

        // 3. 计算 Jaccard，每个景点保留 Top K
        Map<Long, List<SimilarItem>> matrix = new HashMap<>();
        for (Map.Entry<Long, Map<Long, Integer>> entry : coOccur.entrySet()) {
            Long itemA = entry.getKey();
            int countA = userCount.getOrDefault(itemA, 0);
            List<SimilarItem> similars = new ArrayList<>();

            for (Map.Entry<Long, Integer> co : entry.getValue().entrySet()) {
                Long itemB = co.getKey();
                int countB = userCount.getOrDefault(itemB, 0);
                int coCount = co.getValue();
                double jaccard = (double) coCount / (countA + countB - coCount);
                similars.add(new SimilarItem(itemB, jaccard));
            }

            similars.sort((a, b) -> Double.compare(b.similarity, a.similarity));
            matrix.put(itemA, similars.stream().limit(TOP_K_SIMILAR).collect(Collectors.toList()));
        }
        return matrix;
    }

    /**
     * 为用户计算 Item-based CF 得分。
     *
     * <p>算法：
     * <ol>
     *   <li>获取用户交互景点的相似景点列表</li>
     *   <li>累计：cfScore[S] += 用户对 I 的权重 × Jaccard(I, S)</li>
     *   <li>归一化到 0-100</li>
     * </ol></p>
     *
     * @param userId   当前用户 ID
     * @param behaviors 用户互动行为列表
     * @return attractionId → CF 得分（0-100）
     */
    private Map<Long, Double> getCFScores(Long userId, List<UserBehavior> behaviors) {
        // 用户已交互的景点 ID 集合（用于排除）
        Set<Long> interacted = behaviors.stream()
                .map(UserBehavior::getAttractionId)
                .collect(Collectors.toSet());

        // 用户对每个交互景点的行为权重
        LocalDateTime now = LocalDateTime.now();
        Map<Long, Double> userWeights = new HashMap<>();
        for (UserBehavior b : behaviors) {
            double w = actionWeight(b.getActionType()) * timeDecay(b.getCreatedAt(), now);
            userWeights.merge(b.getAttractionId(), w, Double::sum);
        }

        // 加载相似度矩阵
        Map<Long, List<SimilarItem>> simMatrix = getItemSimilarityMatrix();

        // 累计 CF 得分
        Map<Long, Double> cfScores = new HashMap<>();
        for (Map.Entry<Long, Double> uw : userWeights.entrySet()) {
            Long itemI = uw.getKey();
            double userWeight = uw.getValue();
            List<SimilarItem> similars = simMatrix.get(itemI);
            if (similars == null) continue;

            for (SimilarItem sim : similars) {
                if (interacted.contains(sim.attractionId)) continue;
                cfScores.merge(sim.attractionId, userWeight * sim.similarity, Double::sum);
            }
        }

        // 归一化到 0-100
        if (!cfScores.isEmpty()) {
            double maxScore = cfScores.values().stream().max(Double::compare).orElse(1.0);
            cfScores.replaceAll((id, score) -> (score / maxScore) * 100.0);
        }
        return cfScores;
    }

    // ═══════════════════════════════════════════════════════════════
    // 规则 + CF 混合评分
    // ═══════════════════════════════════════════════════════════════

    private List<Attraction> hybridScore(List<UserBehavior> behaviors,
                                         Map<Long, Double> cfScores,
                                         int limit) {
        LocalDateTime now = LocalDateTime.now();

        // 1. 计算用户偏好向量
        Map<String, Double> categoryScore = new HashMap<>();
        Map<String, Double> cityScore = new HashMap<>();
        Map<Long, Double> attractionScore = new HashMap<>();
        Set<Long> excludeIds = new HashSet<>();

        for (UserBehavior b : behaviors) {
            excludeIds.add(b.getAttractionId());
            double w = actionWeight(b.getActionType()) * timeDecay(b.getCreatedAt(), now);
            attractionScore.merge(b.getAttractionId(), w, Double::sum);
        }

        if (!attractionScore.isEmpty()) {
            List<Attraction> interacted = attractionMapper.selectBatchIds(attractionScore.keySet());
            for (Attraction a : interacted) {
                double w = attractionScore.getOrDefault(a.getId(), 1.0);
                if (a.getCategory() != null) categoryScore.merge(a.getCategory(), w, Double::sum);
                if (a.getCity() != null) cityScore.merge(a.getCity(), w, Double::sum);
            }
        }

        // 2. 拉取候选池（含 CF 高分的景点）
        Set<Long> candidateIds = new HashSet<>(excludeIds);
        candidateIds.addAll(cfScores.keySet());
        List<Attraction> candidates = fetchCandidatePool(excludeIds);

        // CF 得分高的景点如果不在候选池（viewCount 不够高），也加进来
        if (!cfScores.isEmpty()) {
            Set<Long> existingIds = candidates.stream().map(Attraction::getId).collect(Collectors.toSet());
            List<Long> missingCfIds = cfScores.keySet().stream()
                    .filter(id -> !existingIds.contains(id) && !excludeIds.contains(id))
                    .limit(50).toList();
            if (!missingCfIds.isEmpty()) {
                List<Attraction> extra = attractionMapper.selectBatchIds(missingCfIds);
                extra = extra.stream().filter(a -> a.getStatus() != null && a.getStatus() == 1).toList();
                candidates.addAll(extra);
            }
        }

        // 3. 获取 IP 位置
        String userCity = getUserCity();

        // 4. 对候选池逐项打分（规则分 + CF 分）
        int maxView = candidates.stream()
                .mapToInt(a -> a.getViewCount() != null ? a.getViewCount() : 0)
                .max().orElse(1);
        double maxCat = categoryScore.values().stream().max(Double::compare).orElse(1.0);

        List<ScoredAttraction> scored = new ArrayList<>();
        for (Attraction a : candidates) {
            if (a.getStatus() == null || a.getStatus() != 1) continue;

            // ── 规则分 (0-100) ──
            double prefCat = categoryScore.getOrDefault(a.getCategory(), 0.0);
            double prefCity = cityScore.getOrDefault(a.getCity(), 0.0);
            double prefScore = Math.min(100, (prefCat / maxCat) * 70 + (prefCity / maxCat) * 30);

            int vc = a.getViewCount() != null ? a.getViewCount() : 0;
            double popScore = maxView > 0 ? (vc / (double) maxView) * 100 : 0;

            double locScore = (userCity != null && userCity.equals(a.getCity())) ? 100 : 0;
            double qualityBonus = (a.getLatitude() != null && a.getLongitude() != null) ? 10 : 0;
            int rating = a.getRating() != null ? a.getRating() : 0;
            double ratingScore = (rating / 5.0) * 100;
            double diversity = (Math.random() - 0.5) * 20;

            double ruleScore = 0.35 * prefScore + 0.25 * popScore + 0.15 * locScore
                             + 0.15 * ratingScore + qualityBonus + diversity;

            // ── CF 分 (0-100) ──
            double cfScore = cfScores.getOrDefault(a.getId(), 0.0);

            // ── 融合 ──
            double total = RULE_WEIGHT * ruleScore + CF_WEIGHT * cfScore;
            scored.add(new ScoredAttraction(a, total, prefScore, popScore));
        }

        // 5. 排序 + 多样性保障
        scored.sort((a, b) -> Double.compare(b.totalScore, a.totalScore));

        List<Attraction> result = new ArrayList<>();
        Set<String> usedCategories = new HashSet<>();
        int diversitySlots = Math.max(1, (int)(limit * 0.2));

        for (int i = 0; i < scored.size() && result.size() < limit; i++) {
            ScoredAttraction sa = scored.get(i);
            boolean fromTopCat = isTopCategory(sa.attraction, categoryScore, 3);

            if (result.size() < limit - diversitySlots) {
                result.add(sa.attraction);
                usedCategories.add(sa.attraction.getCategory());
            } else if (!fromTopCat) {
                result.add(sa.attraction);
                usedCategories.add(sa.attraction.getCategory());
            }
        }

        // 6. 补齐
        if (result.size() < limit) {
            Set<Long> existingIds = result.stream().map(Attraction::getId).collect(Collectors.toSet());
            for (Attraction a : fetchGlobalHot(limit * 2)) {
                if (result.size() >= limit) break;
                if (!existingIds.contains(a.getId())) result.add(a);
            }
        }
        return result;
    }

    // ═══════════════════════════════════════════════════════════════
    // 辅助方法（不变）
    // ═══════════════════════════════════════════════════════════════

    private double timeDecay(LocalDateTime eventTime, LocalDateTime now) {
        if (eventTime == null) return 1.0;
        double days = Duration.between(eventTime, now).toDays();
        return Math.exp(-days * Math.log(2) / HALF_LIFE_DAYS);
    }

    private double actionWeight(String actionType) {
        return ACTION_WEIGHTS.getOrDefault(actionType, 1);
    }

    private boolean isTopCategory(Attraction a, Map<String, Double> categoryScore, int n) {
        if (categoryScore.isEmpty()) return true;
        List<String> topCats = categoryScore.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(n).map(Map.Entry::getKey).toList();
        return topCats.contains(a.getCategory());
    }

    private String getUserCity() {
        try {
            BaiduMapsService.IpLocation loc = baiduMapsService.ipLocation();
            return loc != null ? loc.city() : null;
        } catch (Exception e) { return null; }
    }

    private Long getCurrentUserId() {
        try { return com.tripmate.common.UserContext.getUserId(); }
        catch (Exception e) { return null; }
    }

    private List<Attraction> fetchCandidatePool(Set<Long> excludeIds) {
        LambdaQueryWrapper<Attraction> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Attraction::getStatus, 1)
               .notIn(!excludeIds.isEmpty(), Attraction::getId, excludeIds)
               .orderByDesc(Attraction::getViewCount)
               .last("LIMIT " + SEED_CANDIDATE_POOL);
        return attractionMapper.selectList(wrapper);
    }

    private List<UserBehavior> getAllBehaviors(Long userId) {
        LambdaQueryWrapper<UserBehavior> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserBehavior::getUserId, userId)
               .in(UserBehavior::getActionType, ENGAGEMENT_ACTIONS)
               .orderByDesc(UserBehavior::getCreatedAt);
        return userBehaviorMapper.selectList(wrapper);
    }

    private List<Attraction> fetchGlobalHot(int limit) {
        LambdaQueryWrapper<Attraction> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Attraction::getStatus, 1)
               .orderByDesc(Attraction::getViewCount)
               .last("LIMIT " + limit);
        return attractionMapper.selectList(wrapper);
    }

    private List<Attraction> recommendWithLocation(int limit) {
        String key = String.format(CACHE_KEY_GLOBAL, limit);
        @SuppressWarnings("unchecked")
        List<Attraction> cached = (List<Attraction>) redisTemplate.opsForValue().get(key);
        if (cached != null) return cached;

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
                if (result.size() >= limit) break;
                if (seen.add(a.getId())) result.add(a);
            }
        }
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
            Attraction attraction, double totalScore,
            double prefScore, double popScore) {}

    private record SimilarItem(long attractionId, double similarity) {}
}
