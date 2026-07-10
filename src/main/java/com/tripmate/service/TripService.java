package com.tripmate.service;

import com.tripmate.entity.Trip;
import com.tripmate.entity.TripItem;

import java.util.List;
import java.util.Map;

/**
 * 旅行服务接口。
 *
 * <p>提供旅行的 CRUD、景点管理以及百度地图路线规划功能。
 * 所有写操作都会校验用户权限（仅允许操作自己的旅行）。</p>
 */
public interface TripService {

    /** 查询当前用户的所有旅行列表 */
    List<Trip> listByUser(Long userId);

    /** 查询旅行详情，返回 Trip 及其景点列表（含景点详细信息） */
    Trip getDetail(Long tripId, Long userId);

    /** 创建旅行，默认状态为规划中（1） */
    Trip create(Long userId, String name, String city);

    /** 更新旅行名称 */
    void update(Long tripId, Long userId, String name);

    /** 删除旅行，同时删除关联的所有景点 */
    void delete(Long tripId, Long userId);

    /** 向旅行添加景点 */
    TripItem addItem(Long tripId, Long userId, Long attractionId, Integer dayNumber);

    /** 从旅行移除景点 */
    void removeItem(Long tripId, Long userId, Long itemId);

    /** 重新排序行程中的景点 */
    void reorderItems(Long tripId, Long userId, List<Map<String, Object>> items);

    /** 路线规划：使用百度地图计算景点间的驾车路线 */
    Map<String, Object> planRoute(Long tripId, Long userId);
}
