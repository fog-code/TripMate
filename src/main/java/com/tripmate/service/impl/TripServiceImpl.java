package com.tripmate.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tripmate.entity.Attraction;
import com.tripmate.entity.Trip;
import com.tripmate.entity.TripItem;
import com.tripmate.mapper.AttractionMapper;
import com.tripmate.mapper.TripItemMapper;
import com.tripmate.mapper.TripMapper;
import com.tripmate.service.BaiduMapsService;
import com.tripmate.service.BaiduMapsService.Coord;
import com.tripmate.service.BaiduMapsService.RoutePlan;
import com.tripmate.service.BaiduMapsService.RouteStep;
import com.tripmate.service.TripService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 旅行服务实现。
 *
 * <p>所有写操作均校验用户权限（仅允许操作自己的旅行）。
 * planRoute 调用百度地图驾车路线规划接口计算景点间最优路径。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TripServiceImpl implements TripService {

    private final TripMapper tripMapper;
    private final TripItemMapper tripItemMapper;
    private final AttractionMapper attractionMapper;
    private final BaiduMapsService baiduMapsService;

    @Override
    public List<Trip> listByUser(Long userId) {
        LambdaQueryWrapper<Trip> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Trip::getUserId, userId)
               .orderByDesc(Trip::getUpdatedAt);
        return tripMapper.selectList(wrapper);
    }

    @Override
    public Trip getDetail(Long tripId, Long userId) {
        Trip trip = tripMapper.selectById(tripId);
        if (trip == null || !trip.getUserId().equals(userId)) {
            throw new RuntimeException("旅行不存在或无权访问");
        }

        // 查询该旅行下的所有景点明细，按 dayNumber + orderIndex 排序
        LambdaQueryWrapper<TripItem> itemWrapper = new LambdaQueryWrapper<>();
        itemWrapper.eq(TripItem::getTripId, tripId)
                   .orderByAsc(TripItem::getDayNumber, TripItem::getOrderIndex);
        List<TripItem> items = tripItemMapper.selectList(itemWrapper);

        // 为每个明细填充景点详细信息
        List<Map<String, Object>> enrichedItems = new ArrayList<>();
        for (TripItem item : items) {
            Map<String, Object> enriched = new LinkedHashMap<>();
            enriched.put("id", item.getId());
            enriched.put("tripId", item.getTripId());
            enriched.put("attractionId", item.getAttractionId());
            enriched.put("dayNumber", item.getDayNumber());
            enriched.put("orderIndex", item.getOrderIndex());
            enriched.put("notes", item.getNotes());
            enriched.put("createdAt", item.getCreatedAt());

            // 关联景点数据
            Attraction attraction = attractionMapper.selectById(item.getAttractionId());
            if (attraction != null) {
                enriched.put("attraction", attraction);
            }
            enrichedItems.add(enriched);
        }
        trip.setItems(enrichedItems);
        return trip;
    }

    @Override
    @Transactional
    public Trip create(Long userId, String name, String city) {
        Trip trip = new Trip();
        trip.setUserId(userId);
        trip.setName(name);
        trip.setCity(city);
        trip.setStatus(1); // 规划中
        tripMapper.insert(trip);
        log.info("用户 {} 创建了旅行: {} (ID={})", userId, name, trip.getId());
        return trip;
    }

    @Override
    @Transactional
    public void update(Long tripId, Long userId, String name) {
        Trip trip = tripMapper.selectById(tripId);
        if (trip == null || !trip.getUserId().equals(userId)) {
            throw new RuntimeException("旅行不存在或无权操作");
        }
        trip.setName(name);
        tripMapper.updateById(trip);
        log.info("用户 {} 更新了旅行 {} 的名称为: {}", userId, tripId, name);
    }

    @Override
    @Transactional
    public void delete(Long tripId, Long userId) {
        Trip trip = tripMapper.selectById(tripId);
        if (trip == null || !trip.getUserId().equals(userId)) {
            throw new RuntimeException("旅行不存在或无权操作");
        }

        // 删除关联的所有景点明细
        LambdaQueryWrapper<TripItem> itemWrapper = new LambdaQueryWrapper<>();
        itemWrapper.eq(TripItem::getTripId, tripId);
        tripItemMapper.delete(itemWrapper);

        // 删除旅行本身
        tripMapper.deleteById(tripId);
        log.info("用户 {} 删除了旅行 {} (ID={})", userId, trip.getName(), tripId);
    }

    @Override
    @Transactional
    public TripItem addItem(Long tripId, Long userId, Long attractionId, Integer dayNumber) {
        // 校验旅行归属
        Trip trip = tripMapper.selectById(tripId);
        if (trip == null || !trip.getUserId().equals(userId)) {
            throw new RuntimeException("旅行不存在或无权操作");
        }

        // 校验景点存在
        Attraction attraction = attractionMapper.selectById(attractionId);
        if (attraction == null) {
            throw new RuntimeException("景点不存在");
        }

        // 计算当前天已有的最大 orderIndex，新景点追加到末尾
        LambdaQueryWrapper<TripItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TripItem::getTripId, tripId)
               .eq(TripItem::getDayNumber, dayNumber)
               .orderByDesc(TripItem::getOrderIndex)
               .last("LIMIT 1");
        TripItem lastItem = tripItemMapper.selectOne(wrapper);
        int nextOrder = lastItem != null ? lastItem.getOrderIndex() + 1 : 0;

        TripItem item = new TripItem();
        item.setTripId(tripId);
        item.setAttractionId(attractionId);
        item.setDayNumber(dayNumber != null ? dayNumber : 1);
        item.setOrderIndex(nextOrder);
        tripItemMapper.insert(item);

        log.info("用户 {} 向旅行 {} 添加了景点 {} (day={}, order={})",
                 userId, tripId, attractionId, dayNumber, nextOrder);
        return item;
    }

    @Override
    @Transactional
    public void removeItem(Long tripId, Long userId, Long itemId) {
        // 校验旅行归属
        Trip trip = tripMapper.selectById(tripId);
        if (trip == null || !trip.getUserId().equals(userId)) {
            throw new RuntimeException("旅行不存在或无权操作");
        }

        // 校验明细属于该旅行
        TripItem item = tripItemMapper.selectById(itemId);
        if (item == null || !item.getTripId().equals(tripId)) {
            throw new RuntimeException("景点明细不存在或不属于该旅行");
        }

        tripItemMapper.deleteById(itemId);
        log.info("用户 {} 从旅行 {} 移除了景点明细 {}", userId, tripId, itemId);
    }

    @Override
    @Transactional
    public void reorderItems(Long tripId, Long userId, List<Map<String, Object>> items) {
        // 校验旅行归属
        Trip trip = tripMapper.selectById(tripId);
        if (trip == null || !trip.getUserId().equals(userId)) {
            throw new RuntimeException("旅行不存在或无权操作");
        }

        for (Map<String, Object> itemMap : items) {
            Long itemId = itemMap.get("id") != null
                    ? ((Number) itemMap.get("id")).longValue() : null;
            Integer orderIndex = itemMap.get("orderIndex") != null
                    ? ((Number) itemMap.get("orderIndex")).intValue() : null;

            if (itemId == null || orderIndex == null) {
                continue;
            }

            TripItem item = tripItemMapper.selectById(itemId);
            if (item != null && item.getTripId().equals(tripId)) {
                item.setOrderIndex(orderIndex);
                tripItemMapper.updateById(item);
            }
        }
        log.info("用户 {} 重新排序了旅行 {} 的景点", userId, tripId);
    }

    @Override
    public Map<String, Object> planRoute(Long tripId, Long userId) {
        // 校验旅行归属
        Trip trip = tripMapper.selectById(tripId);
        if (trip == null || !trip.getUserId().equals(userId)) {
            throw new RuntimeException("旅行不存在或无权操作");
        }

        // 获取所有景点明细，按 dayNumber + orderIndex 排序
        LambdaQueryWrapper<TripItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TripItem::getTripId, tripId)
               .orderByAsc(TripItem::getDayNumber, TripItem::getOrderIndex);
        List<TripItem> items = tripItemMapper.selectList(wrapper);

        if (items.size() < 2) {
            return Map.of(
                "distance", 0,
                "duration", 0,
                "steps", Collections.emptyList(),
                "message", "至少需要两个景点才能规划路线"
            );
        }

        // 获取每个景点对应的坐标
        List<Coord> coords = new ArrayList<>();
        List<String> attractionNames = new ArrayList<>();
        for (TripItem item : items) {
            Attraction attraction = attractionMapper.selectById(item.getAttractionId());
            if (attraction != null && attraction.getLatitude() != null && attraction.getLongitude() != null) {
                coords.add(new Coord(attraction.getLatitude(), attraction.getLongitude()));
                attractionNames.add(attraction.getName());
            } else {
                log.warn("景点 ID={} 缺少坐标信息，跳过路线规划", item.getAttractionId());
            }
        }

        if (coords.size() < 2) {
            return Map.of(
                "distance", 0,
                "duration", 0,
                "steps", Collections.emptyList(),
                "message", "有效坐标不足，无法规划路线"
            );
        }

        // 调用百度地图驾车路线规划
        Coord origin = coords.get(0);
        Coord destination = coords.get(coords.size() - 1);
        List<Coord> waypoints = coords.size() > 2
                ? coords.subList(1, coords.size() - 1)
                : null;

        RoutePlan routePlan = baiduMapsService.getDrivingRoute(origin, destination, waypoints);

        // 构建步骤列表
        List<Map<String, Object>> steps = routePlan.steps().stream()
                .map(step -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("instruction", step.instruction());
                    m.put("roadName", step.roadName());
                    m.put("distance", step.distance());
                    m.put("duration", step.duration());
                    return m;
                })
                .collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("distance", routePlan.totalDistance());
        result.put("duration", routePlan.totalDuration());
        result.put("steps", steps);
        result.put("attractions", attractionNames);

        log.info("用户 {} 为旅行 {} (ID={}) 规划路线: 总距离={}m, 总时长={}s",
                 userId, trip.getName(), tripId, routePlan.totalDistance(), routePlan.totalDuration());
        return result;
    }
}
