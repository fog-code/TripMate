package com.tripmate.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.tripmate.entity.*;
import com.tripmate.mapper.*;
import com.tripmate.service.AdminService;
import com.tripmate.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final UserMapper userMapper;
    private final AttractionMapper attractionMapper;
    private final UserBehaviorMapper userBehaviorMapper;
    private final ReviewMapper reviewMapper;
    private final ReviewService reviewService;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public Map<String, Object> dashboard() {
        long userCount = userMapper.selectCount(null);
        long attractionCount = attractionMapper.selectCount(
                new LambdaQueryWrapper<Attraction>().eq(Attraction::getStatus, 1));

        LocalDateTime todayStart = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        long todayNewUsers = userMapper.selectCount(
                new LambdaQueryWrapper<User>().ge(User::getCreatedAt, todayStart));

        LambdaQueryWrapper<UserBehavior> todayWrapper = new LambdaQueryWrapper<>();
        todayWrapper.ge(UserBehavior::getCreatedAt, todayStart)
                    .select(UserBehavior::getUserId);
        long todayActiveUsers = userBehaviorMapper.selectList(todayWrapper)
                .stream().map(UserBehavior::getUserId).distinct().count();

        return Map.of(
            "userCount", userCount,
            "attractionCount", attractionCount,
            "todayActiveUsers", todayActiveUsers,
            "todayNewUsers", todayNewUsers
        );
    }

    @Override
    public Map<String, Object> listUsers(Integer page, Integer size, String keyword) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(User::getUsername, keyword).or().like(User::getEmail, keyword);
        }
        wrapper.orderByDesc(User::getCreatedAt);
        long total = userMapper.selectCount(wrapper);
        wrapper.last("LIMIT " + size + " OFFSET " + (page - 1) * size);
        wrapper.select(User.class, info -> !"password".equals(info.getColumn()));
        List<User> records = userMapper.selectList(wrapper);
        return Map.of("records", records, "total", total, "page", page, "size", size);
    }

    @Override
    public void toggleUserStatus(Long userId, Integer status) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        LambdaUpdateWrapper<User> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(User::getId, userId).set(User::getStatus, status);
        userMapper.update(null, wrapper);
        // 删除用户缓存，强制下次请求从 MySQL 重新加载（禁用后 JWT 过滤器会拒绝访问）
        redisTemplate.delete("user:" + userId);
    }

    @Override
    public Map<String, Object> listAttractions(Integer page, Integer size, String keyword) {
        LambdaQueryWrapper<Attraction> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(Attraction::getName, keyword);
        }
        wrapper.orderByDesc(Attraction::getCreatedAt);
        long total = attractionMapper.selectCount(wrapper);
        wrapper.last("LIMIT " + size + " OFFSET " + (page - 1) * size);
        List<Attraction> records = attractionMapper.selectList(wrapper);
        return Map.of("records", records, "total", total, "page", page, "size", size);
    }

    @Override
    public void toggleAttractionStatus(Long attractionId, Integer status) {
        Attraction attraction = attractionMapper.selectById(attractionId);
        if (attraction == null) {
            throw new RuntimeException("景点不存在");
        }
        LambdaUpdateWrapper<Attraction> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Attraction::getId, attractionId).set(Attraction::getStatus, status);
        attractionMapper.update(null, wrapper);
    }

    @Override
    public Map<String, Object> listReviews(Integer page, Integer size, String keyword) {
        LambdaQueryWrapper<Review> w = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            w.like(Review::getContent, keyword);
        }
        w.orderByDesc(Review::getCreatedAt);
        long total = reviewMapper.selectCount(w);
        w.last("LIMIT " + size + " OFFSET " + (page - 1) * size);
        return Map.of("records", reviewMapper.selectList(w), "total", total, "page", page, "size", size);
    }

    @Override
    public void deleteReview(Long reviewId) {
        // 委托给 ReviewService，统一触发评分更新
        reviewService.delete(reviewId, null, true);
    }
}
