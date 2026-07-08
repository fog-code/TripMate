package com.tripmate.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tripmate.dto.BehaviorRecordRequest;
import com.tripmate.entity.Attraction;
import com.tripmate.entity.UserBehavior;
import com.tripmate.mapper.AttractionMapper;
import com.tripmate.mapper.UserBehaviorMapper;
import com.tripmate.service.UserBehaviorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserBehaviorServiceImpl implements UserBehaviorService {

    private final UserBehaviorMapper userBehaviorMapper;
    private final AttractionMapper attractionMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public UserBehavior record(BehaviorRecordRequest request, Long userId) {
        // 防止重复：LIKE/BOOKMARK 同一用户对同一景点只保留一条；VIEW 允许多次
        if ("LIKE".equals(request.getActionType())
                || "BOOKMARK".equals(request.getActionType())) {
            LambdaQueryWrapper<UserBehavior> existCheck = new LambdaQueryWrapper<>();
            existCheck.eq(UserBehavior::getUserId, userId)
                      .eq(UserBehavior::getAttractionId, request.getAttractionId())
                      .eq(UserBehavior::getActionType, request.getActionType());
            if (userBehaviorMapper.selectCount(existCheck) > 0) {
                log.debug("行为已存在跳过 userId={} attractionId={} actionType={}",
                        userId, request.getAttractionId(), request.getActionType());
                return userBehaviorMapper.selectOne(existCheck);
            }
        }

        UserBehavior behavior = new UserBehavior();
        behavior.setUserId(userId);
        behavior.setAttractionId(request.getAttractionId());
        behavior.setActionType(request.getActionType());
        behavior.setActionValue(request.getActionValue());
        userBehaviorMapper.insert(behavior);

        // 清除该用户的推荐缓存
        evictRecommendCache(userId);

        log.info("行为记录成功 userId={} attractionId={} actionType={}",
                userId, request.getAttractionId(), request.getActionType());
        return behavior;
    }

    @Override
    public Map<String, Object> listMyBehaviors(Integer pageNum, Integer pageSize, Long userId) {
        LambdaQueryWrapper<UserBehavior> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserBehavior::getUserId, userId);

        long total = userBehaviorMapper.selectCount(wrapper);

        wrapper.orderByDesc(UserBehavior::getCreatedAt);
        wrapper.last("LIMIT " + pageSize + " OFFSET " + (pageNum - 1) * pageSize);
        List<UserBehavior> records = userBehaviorMapper.selectList(wrapper);

        return Map.of(
            "records", records,
            "total",   total,
            "page",    pageNum,
            "size",    pageSize
        );
    }

    @Override
    public Map<String, Boolean> getStatus(Long userId, Long attractionId) {
        LambdaQueryWrapper<UserBehavior> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserBehavior::getUserId, userId)
               .eq(UserBehavior::getAttractionId, attractionId);

        boolean liked = userBehaviorMapper.selectCount(
                wrapper.clone().eq(UserBehavior::getActionType, "LIKE")) > 0;
        boolean bookmarked = userBehaviorMapper.selectCount(
                wrapper.clone().eq(UserBehavior::getActionType, "BOOKMARK")) > 0;

        return Map.of("liked", liked, "bookmarked", bookmarked);
    }

    @Override
    public void delete(Long userId, Long attractionId, String actionType) {
        LambdaQueryWrapper<UserBehavior> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserBehavior::getUserId, userId)
               .eq(UserBehavior::getAttractionId, attractionId)
               .eq(UserBehavior::getActionType, actionType);
        userBehaviorMapper.delete(wrapper);
        evictRecommendCache(userId);
        log.info("行为删除成功 userId={} attractionId={} actionType={}",
                userId, attractionId, actionType);
    }

    @Override
    public Map<String, Object> getFavorites(Long userId, String actionType, Integer page, Integer size) {
        LambdaQueryWrapper<UserBehavior> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserBehavior::getUserId, userId)
               .eq(UserBehavior::getActionType, actionType)
               .orderByDesc(UserBehavior::getCreatedAt);

        long total = userBehaviorMapper.selectCount(wrapper);
        wrapper.last("LIMIT " + size + " OFFSET " + (page - 1) * size);
        List<UserBehavior> behaviors = userBehaviorMapper.selectList(wrapper);

        if (behaviors.isEmpty()) {
            return Map.of("records", List.of(), "total", 0, "page", page, "size", size);
        }

        List<Long> ids = behaviors.stream().map(UserBehavior::getAttractionId).distinct().toList();
        List<Attraction> attractions = attractionMapper.selectBatchIds(ids);

        // 去重 + 保持原有顺序
        Set<Long> seen = new LinkedHashSet<>();
        List<Attraction> ordered = new ArrayList<>();
        for (UserBehavior b : behaviors) {
            if (seen.add(b.getAttractionId())) {
                for (Attraction a : attractions) {
                    if (a.getId().equals(b.getAttractionId())) {
                        ordered.add(a);
                        break;
                    }
                }
            }
        }

        return Map.of("records", ordered, "total", total, "page", page, "size", size);
    }

    /**
     * 清除用户推荐缓存（使用 SCAN 避免阻塞 KEYS 命令）。
     * 匹配模式：recommend:v2:user:{userId}:*
     */
    private void evictRecommendCache(Long userId) {
        String pattern = "recommend:v2:user:" + userId + ":*";
        var keys = scanKeys(pattern);
        if (!keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    /**
     * 使用 SCAN 命令安全地获取匹配模式的所有键。
     * KEYS 命令会阻塞 Redis（O(N)），SCAN 是非阻塞的游标迭代。
     */
    private Set<String> scanKeys(String pattern) {
        return redisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Set<String>>) connection -> {
            Set<String> result = new LinkedHashSet<>();
            var cursor = connection.keyCommands().scan(
                    ScanOptions.scanOptions().match(pattern).count(100).build());
            cursor.forEachRemaining(key -> result.add(new String(key)));
            return result;
        });
    }
}
