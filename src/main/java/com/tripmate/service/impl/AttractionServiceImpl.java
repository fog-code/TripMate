package com.tripmate.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.tripmate.common.UserContext;
import com.tripmate.dto.AttractionCreateRequest;
import com.tripmate.dto.AttractionUpdateRequest;
import com.tripmate.entity.Attraction;
import com.tripmate.entity.UserBehavior;
import com.tripmate.mapper.AttractionMapper;
import com.tripmate.mapper.UserBehaviorMapper;
import com.tripmate.service.AttractionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AttractionServiceImpl implements AttractionService {

    private static final String CACHE_KEY = "attraction:%d";
    private static final long CACHE_TTL_SECONDS = 3600;

    private final AttractionMapper attractionMapper;
    private final UserBehaviorMapper userBehaviorMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public Map<String, Object> list(Integer pageNum, Integer pageSize,
                                     String category, String city, String keyword) {
        LambdaQueryWrapper<Attraction> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Attraction::getStatus, 1);

        if (category != null && !category.isBlank()) {
            wrapper.eq(Attraction::getCategory, category);
        }
        if (city != null && !city.isBlank()) {
            wrapper.eq(Attraction::getCity, city);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(Attraction::getName, keyword)
                              .or().like(Attraction::getDescription, keyword));
        }

        long total = attractionMapper.selectCount(wrapper);

        wrapper.orderByDesc(Attraction::getCreatedAt);
        wrapper.last("LIMIT " + pageSize + " OFFSET " + (pageNum - 1) * pageSize);
        List<Attraction> records = attractionMapper.selectList(wrapper);

        return Map.of(
            "records", records,
            "total",   total,
            "page",    pageNum,
            "size",    pageSize
        );
    }

    @Override
    public Attraction getById(Long id) {
        String key = String.format(CACHE_KEY, id);
        Attraction attraction = (Attraction) redisTemplate.opsForValue().get(key);
        if (attraction == null) {
            attraction = attractionMapper.selectById(id);
            if (attraction == null || attraction.getStatus() == 0) {
                throw new RuntimeException("景点不存在或已下架");
            }
            redisTemplate.opsForValue().set(key, attraction, Duration.ofSeconds(CACHE_TTL_SECONDS));
        }
        recordViewAndCount(id);
        return attraction;
    }

    @Override
    public Attraction create(AttractionCreateRequest request) {
        Attraction attraction = new Attraction();
        attraction.setName(request.getName());
        attraction.setDescription(request.getDescription());
        attraction.setCategory(request.getCategory());
        attraction.setCity(request.getCity());
        attraction.setAddress(request.getAddress());
        attraction.setLatitude(request.getLatitude());
        attraction.setLongitude(request.getLongitude());
        attraction.setImageUrl(request.getImageUrl());
        attraction.setRating(0);
        attraction.setStatus(1);
        attractionMapper.insert(attraction);
        return attraction;
    }

    @Override
    public Attraction update(Long id, AttractionUpdateRequest request) {
        Attraction attraction = attractionMapper.selectById(id);
        if (attraction == null) {
            throw new RuntimeException("景点不存在");
        }

        if (request.getName() != null)        { attraction.setName(request.getName()); }
        if (request.getDescription() != null) { attraction.setDescription(request.getDescription()); }
        if (request.getCategory() != null)    { attraction.setCategory(request.getCategory()); }
        if (request.getCity() != null)        { attraction.setCity(request.getCity()); }
        if (request.getAddress() != null)     { attraction.setAddress(request.getAddress()); }
        if (request.getLatitude() != null)    { attraction.setLatitude(request.getLatitude()); }
        if (request.getLongitude() != null)   { attraction.setLongitude(request.getLongitude()); }
        if (request.getImageUrl() != null)    { attraction.setImageUrl(request.getImageUrl()); }
        if (request.getRating() != null)      { attraction.setRating(request.getRating()); }
        if (request.getStatus() != null)      { attraction.setStatus(request.getStatus()); }

        attractionMapper.updateById(attraction);

        String key = String.format(CACHE_KEY, id);
        redisTemplate.delete(key);

        return attraction;
    }

    @Override
    public void delete(Long id) {
        Attraction attraction = attractionMapper.selectById(id);
        if (attraction == null) {
            throw new RuntimeException("景点不存在");
        }
        attractionMapper.deleteById(id);

        String key = String.format(CACHE_KEY, id);
        redisTemplate.delete(key);
    }

    private void recordViewAndCount(Long id) {
        Long userId = UserContext.getUserId();
        if (userId != null) {
            UserBehavior behavior = new UserBehavior();
            behavior.setUserId(userId);
            behavior.setAttractionId(id);
            behavior.setActionType("VIEW");
            userBehaviorMapper.insert(behavior);
        }

        LambdaUpdateWrapper<Attraction> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(Attraction::getId, id)
                     .setSql("view_count = view_count + 1");
        attractionMapper.update(null, updateWrapper);
    }

    @Override
    public List<Attraction> getNearby(Long attractionId, int radius, int limit) {
        Attraction dest = attractionMapper.selectById(attractionId);
        if (dest == null || dest.getLatitude() == null) {
            return Collections.emptyList();
        }

        LambdaQueryWrapper<Attraction> w = new LambdaQueryWrapper<>();
        w.eq(Attraction::getCity, dest.getCity())
         .eq(Attraction::getStatus, 1)
         .ne(Attraction::getId, dest.getId())
         .isNotNull(Attraction::getLatitude)
         .orderByDesc(Attraction::getViewCount);
        List<Attraction> all = attractionMapper.selectList(w);

        final double dLat = dest.getLatitude(), dLng = dest.getLongitude();
        return all.stream()
                .filter(a -> haversine(dLat, dLng, a.getLatitude(), a.getLongitude()) <= radius)
                .limit(limit)
                .collect(Collectors.toList());
    }

    private double haversine(double lat1, double lng1, double lat2, double lng2) {
        final double R = 6371000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat/2)*Math.sin(dLat/2) +
                   Math.cos(Math.toRadians(lat1))*Math.cos(Math.toRadians(lat2))*
                   Math.sin(dLng/2)*Math.sin(dLng/2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
    }
}
