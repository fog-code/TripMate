package com.tripmate.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripmate.entity.Attraction;
import com.tripmate.entity.Trip;
import com.tripmate.entity.TripItem;
import com.tripmate.mapper.AttractionMapper;
import com.tripmate.mapper.TripItemMapper;
import com.tripmate.mapper.TripMapper;
import com.tripmate.service.BaiduMapsService;
import com.tripmate.service.BaiduMapsService.Coord;
import com.tripmate.service.BaiduMapsService.RoutePlan;
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
 * <p>修复清单：
 * <ul>
 *   <li>getDetail: 批量 IN 查询景点，消除 N+1</li>
 *   <li>新增 updateItem: 支持跨天移动 + 排序</li>
 *   <li>planRoute: 按天分组规划，百度 waypoints 上限 10 自动分批</li>
 *   <li>planRoute: 结果持久化到 trips.route_data</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TripServiceImpl implements TripService {

    private static final int BAIDU_MAX_WAYPOINTS = 10;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final TripMapper tripMapper;
    private final TripItemMapper tripItemMapper;
    private final AttractionMapper attractionMapper;
    private final BaiduMapsService baiduMapsService;

    // ══════════════════════════════════════════
    // CRUD
    // ══════════════════════════════════════════

    @Override
    public List<Trip> listByUser(Long userId) {
        List<Trip> trips = tripMapper.selectList(
                new LambdaQueryWrapper<Trip>()
                        .eq(Trip::getUserId, userId)
                        .orderByDesc(Trip::getUpdatedAt));
        // 批量统计每个行程的景点数量，避免列表页误显示 0
        if (!trips.isEmpty()) {
            Set<Long> tripIds = trips.stream().map(Trip::getId).collect(Collectors.toSet());
            List<TripItem> allItems = tripItemMapper.selectList(
                    new LambdaQueryWrapper<TripItem>().in(TripItem::getTripId, tripIds));
            Map<Long, Long> countMap = allItems.stream()
                    .collect(Collectors.groupingBy(TripItem::getTripId, Collectors.counting()));
            for (Trip t : trips) {
                t.setItemCount(countMap.getOrDefault(t.getId(), 0L).intValue());
            }
        }
        return trips;
    }

    @Override
    public Trip getDetail(Long tripId, Long userId) {
        Trip trip = tripMapper.selectById(tripId);
        if (trip == null || !trip.getUserId().equals(userId)) {
            throw new RuntimeException("旅行不存在或无权访问");
        }

        List<TripItem> items = tripItemMapper.selectList(
                new LambdaQueryWrapper<TripItem>()
                        .eq(TripItem::getTripId, tripId)
                        .orderByAsc(TripItem::getDayNumber, TripItem::getOrderIndex));

        // ── 批量 IN 查询景点，消除 N+1 ──
        Set<Long> attractionIds = items.stream().map(TripItem::getAttractionId).collect(Collectors.toSet());
        Map<Long, Attraction> attrMap = Collections.emptyMap();
        if (!attractionIds.isEmpty()) {
            List<Attraction> attrs = attractionMapper.selectBatchIds(attractionIds);
            attrMap = attrs.stream().collect(Collectors.toMap(Attraction::getId, a -> a));
        }

        List<Map<String, Object>> enriched = new ArrayList<>();
        for (TripItem item : items) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", item.getId());
            m.put("tripId", item.getTripId());
            m.put("attractionId", item.getAttractionId());
            m.put("dayNumber", item.getDayNumber());
            m.put("orderIndex", item.getOrderIndex());
            m.put("notes", item.getNotes());
            m.put("createdAt", item.getCreatedAt());
            Attraction a = attrMap.get(item.getAttractionId());
            if (a != null) {
                m.put("attraction", a);
            }
            enriched.add(m);
        }
        trip.setItems(enriched);
        return trip;
    }

    @Override
    @Transactional
    public Trip create(Long userId, String name, String city) {
        Trip trip = new Trip();
        trip.setUserId(userId);
        trip.setName(name);
        trip.setCity(city);
        trip.setStatus(1);
        tripMapper.insert(trip);
        return trip;
    }

    @Override
    @Transactional
    public void update(Long tripId, Long userId, String name) {
        Trip trip = requireOwnTrip(tripId, userId);
        trip.setName(name);
        tripMapper.updateById(trip);
    }

    @Override
    @Transactional
    public void delete(Long tripId, Long userId) {
        requireOwnTrip(tripId, userId);
        tripItemMapper.delete(new LambdaQueryWrapper<TripItem>().eq(TripItem::getTripId, tripId));
        tripMapper.deleteById(tripId);
    }

    // ══════════════════════════════════════════
    // Items
    // ══════════════════════════════════════════

    @Override
    @Transactional
    public TripItem addItem(Long tripId, Long userId, Long attractionId, Integer dayNumber) {
        Trip trip = requireOwnTrip(tripId, userId);
        Attraction attraction = attractionMapper.selectById(attractionId);
        if (attraction == null) {
            throw new RuntimeException("景点不存在");
        }
        if (trip.getCity() != null && !trip.getCity().isBlank()
                && !trip.getCity().equals(attraction.getCity())) {
            throw new RuntimeException("该景点不属于行程城市 " + trip.getCity());
        }
        int day = dayNumber != null ? dayNumber : 1;
        TripItem last = tripItemMapper.selectOne(
                new LambdaQueryWrapper<TripItem>()
                        .eq(TripItem::getTripId, tripId)
                        .eq(TripItem::getDayNumber, day)
                        .orderByDesc(TripItem::getOrderIndex)
                        .last("LIMIT 1"));
        int nextOrder = last != null ? last.getOrderIndex() + 1 : 0;

        TripItem item = new TripItem();
        item.setTripId(tripId);
        item.setAttractionId(attractionId);
        item.setDayNumber(day);
        item.setOrderIndex(nextOrder);
        tripItemMapper.insert(item);
        // 景点构成变化，使已持久化的路线规划失效
        trip.setRouteData(null);
        tripMapper.updateById(trip);
        return item;
    }

    @Override
    @Transactional
    public void removeItem(Long tripId, Long userId, Long itemId) {
        Trip trip = requireOwnTrip(tripId, userId);
        TripItem item = tripItemMapper.selectById(itemId);
        if (item == null || !item.getTripId().equals(tripId)) {
            throw new RuntimeException("景点明细不存在或不属于该旅行");
        }
        tripItemMapper.deleteById(itemId);
        // 景点构成变化，使已持久化的路线规划失效
        trip.setRouteData(null);
        tripMapper.updateById(trip);
    }

    @Override
    @Transactional
    public void updateItem(Long tripId, Long userId, Long itemId, Integer dayNumber, Integer orderIndex) {
        Trip trip = requireOwnTrip(tripId, userId);
        TripItem item = tripItemMapper.selectById(itemId);
        if (item == null || !item.getTripId().equals(tripId)) {
            throw new RuntimeException("景点明细不存在或不属于该旅行");
        }
        Integer oldDay = item.getDayNumber();
        if (dayNumber != null) {
            item.setDayNumber(dayNumber);
        }
        if (orderIndex != null) {
            item.setOrderIndex(orderIndex);
        }
        tripItemMapper.updateById(item);

        // 跨天移动后，紧凑化源天的 orderIndex
        if (dayNumber != null && !dayNumber.equals(oldDay)) {
            compactOrderIndexes(tripId, oldDay);
        }
        // 景点构成/顺序变化，使已持久化的路线规划失效
        trip.setRouteData(null);
        tripMapper.updateById(trip);
    }

    /** 紧凑化某天的 orderIndex，消除空洞 */
    private void compactOrderIndexes(Long tripId, int dayNumber) {
        List<TripItem> dayItems = tripItemMapper.selectList(
                new LambdaQueryWrapper<TripItem>()
                        .eq(TripItem::getTripId, tripId)
                        .eq(TripItem::getDayNumber, dayNumber)
                        .orderByAsc(TripItem::getOrderIndex));
        for (int i = 0; i < dayItems.size(); i++) {
            TripItem di = dayItems.get(i);
            if (di.getOrderIndex() != i) {
                di.setOrderIndex(i);
                tripItemMapper.updateById(di);
            }
        }
    }

    @Override
    @Transactional
    public void reorderItems(Long tripId, Long userId, List<Map<String, Object>> items) {
        Trip trip = requireOwnTrip(tripId, userId);
        for (Map<String, Object> im : items) {
            Long itemId = toLong(im.get("id"));
            Integer orderIndex = toInt(im.get("orderIndex"));
            if (itemId == null || orderIndex == null) continue;
            TripItem item = tripItemMapper.selectById(itemId);
            if (item != null && item.getTripId().equals(tripId)) {
                item.setOrderIndex(orderIndex);
                tripItemMapper.updateById(item);
            }
        }
        // 顺序变化，使已持久化的路线规划失效
        trip.setRouteData(null);
        tripMapper.updateById(trip);
    }

    // ══════════════════════════════════════════
    // Route Planning (per day, with waypoint batching)
    // ══════════════════════════════════════════

    @Override
    public Map<String, Object> planRoute(Long tripId, Long userId) {
        Trip trip = requireOwnTrip(tripId, userId);

        List<TripItem> items = tripItemMapper.selectList(
                new LambdaQueryWrapper<TripItem>()
                        .eq(TripItem::getTripId, tripId)
                        .orderByAsc(TripItem::getDayNumber, TripItem::getOrderIndex));

        if (items.size() < 2) {
            return Map.of("message", "至少需要两个景点才能规划路线", "days", Collections.emptyList());
        }

        // ── 批量 IN 查询景点坐标 ──
        Set<Long> ids = items.stream().map(TripItem::getAttractionId).collect(Collectors.toSet());
        Map<Long, Attraction> attrMap = attractionMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Attraction::getId, a -> a));

        // ── 按天分组 ──
        Map<Integer, List<TripItem>> byDay = items.stream()
                .collect(Collectors.groupingBy(TripItem::getDayNumber, LinkedHashMap::new, Collectors.toList()));

        List<Map<String, Object>> dayResults = new ArrayList<>();
        int totalDistance = 0;
        int totalDuration = 0;

        for (Map.Entry<Integer, List<TripItem>> entry : byDay.entrySet()) {
            Integer day = entry.getKey();
            List<TripItem> dayItems = entry.getValue();
            Map<String, Object> dayResult = planDayRoute(day, dayItems, attrMap);
            dayResults.add(dayResult);
            totalDistance += (int) dayResult.getOrDefault("distance", 0);
            totalDuration += (int) dayResult.getOrDefault("duration", 0);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalDistance", totalDistance);
        result.put("totalDuration", totalDuration);
        result.put("days", dayResults);

        // ── 持久化到 trips.route_data ──
        try {
            trip.setRouteData(objectMapper.writeValueAsString(result));
            tripMapper.updateById(trip);
        } catch (Exception e) {
            log.warn("路线结果持久化失败: {}", e.getMessage());
        }

        return result;
    }

    /**
     * 规划单天路线。百度 waypoints 上限 10，超出时分段规划。
     */
    private Map<String, Object> planDayRoute(Integer day, List<TripItem> dayItems,
                                              Map<Long, Attraction> attrMap) {
        Map<String, Object> dayResult = new LinkedHashMap<>();
        dayResult.put("day", day);

        // 收集有效坐标
        List<Coord> coords = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (TripItem item : dayItems) {
            Attraction a = attrMap.get(item.getAttractionId());
            if (a != null && a.getLatitude() != null && a.getLongitude() != null) {
                coords.add(new Coord(a.getLatitude(), a.getLongitude()));
                names.add(a.getName());
            }
        }

        if (coords.size() < 2) {
            dayResult.put("distance", 0);
            dayResult.put("duration", 0);
            dayResult.put("attractions", names);
            dayResult.put("segments", Collections.emptyList());
            dayResult.put("message", coords.size() < 2 ? "当天有效坐标不足，无法规划" : null);
            return dayResult;
        }

        // 分片：百度 waypoints 最多 10 个
        List<Map<String, Object>> segments = new ArrayList<>();
        int dayDistance = 0;
        int dayDuration = 0;
        boolean hasSegmentError = false;

        int i = 0;
        while (i < coords.size() - 1) {
            int end = Math.min(i + BAIDU_MAX_WAYPOINTS + 1, coords.size());
            List<Coord> batchCoords = coords.subList(i, end);
            Coord origin = batchCoords.get(0);
            Coord dest = batchCoords.get(batchCoords.size() - 1);
            List<Coord> waypoints = batchCoords.size() > 2
                    ? batchCoords.subList(1, batchCoords.size() - 1) : null;

            try {
                RoutePlan plan = baiduMapsService.getDrivingRoute(origin, dest, waypoints);
                Map<String, Object> seg = new LinkedHashMap<>();
                seg.put("distance", plan.totalDistance());
                seg.put("duration", plan.totalDuration());
                seg.put("from", names.get(i));
                seg.put("to", names.get(end - 1));
                seg.put("steps", plan.steps().stream().map(s -> {
                    Map<String, Object> sm = new LinkedHashMap<>();
                    sm.put("instruction", s.instruction());
                    sm.put("roadName", s.roadName());
                    sm.put("distance", s.distance());
                    sm.put("duration", s.duration());
                    return sm;
                }).collect(Collectors.toList()));
                segments.add(seg);
                dayDistance += plan.totalDistance();
                dayDuration += plan.totalDuration();
            } catch (Exception e) {
                log.warn("Day {} segment {}-{} 路线规划失败: {}", day, i, end - 1, e.getMessage());
                hasSegmentError = true;
            }
            i = end - 1; // 上一段终点是下一段起点
        }

        dayResult.put("distance", dayDistance);
        dayResult.put("duration", dayDuration);
        dayResult.put("attractions", names);
        dayResult.put("segments", segments);
        if (hasSegmentError) {
            dayResult.put("warning", "部分路段规划失败，距离或时长可能不完整");
        }
        return dayResult;
    }

    // ══════════════════════════════════════════
    // Helpers
    // ══════════════════════════════════════════

    private Trip requireOwnTrip(Long tripId, Long userId) {
        Trip trip = tripMapper.selectById(tripId);
        if (trip == null || !trip.getUserId().equals(userId)) {
            throw new RuntimeException("旅行不存在或无权操作");
        }
        return trip;
    }

    private Long toLong(Object v) {
        return v instanceof Number ? ((Number) v).longValue() : null;
    }

    private Integer toInt(Object v) {
        return v instanceof Number ? ((Number) v).intValue() : null;
    }
}
