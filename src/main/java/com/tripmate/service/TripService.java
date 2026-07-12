package com.tripmate.service;

import com.tripmate.entity.Trip;
import com.tripmate.entity.TripItem;

import java.util.List;
import java.util.Map;

/**
 * 旅行服务接口。
 */
public interface TripService {

    List<Trip> listByUser(Long userId);
    Trip getDetail(Long tripId, Long userId);
    Trip create(Long userId, String name, String city);
    void update(Long tripId, Long userId, String name);
    void delete(Long tripId, Long userId);
    TripItem addItem(Long tripId, Long userId, Long attractionId, Integer dayNumber);
    void removeItem(Long tripId, Long userId, Long itemId);
    /** 更新单个行程项（跨天移动 / 排序） */
    void updateItem(Long tripId, Long userId, Long itemId, Integer dayNumber, Integer orderIndex);
    void reorderItems(Long tripId, Long userId, List<Map<String, Object>> items);
    /** 按天分组规划驾车路线，结果持久化到 trips.route_data */
    Map<String, Object> planRoute(Long tripId, Long userId);
}
