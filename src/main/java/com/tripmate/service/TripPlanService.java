package com.tripmate.service;

import com.tripmate.entity.Attraction;
import com.tripmate.entity.TripPlan;
import com.tripmate.entity.TripPlanItem;

import java.util.List;
import java.util.Map;

/**
 * 行程规划服务接口。
 *
 * <p>提供行程的 CRUD、景点管理（增删改）以及智能路线编排功能。
 * 编排使用百度地图路线矩阵 + 贪心最近邻算法对每天景点重新排序。</p>
 */
public interface TripPlanService {

    /** 创建行程，自动计算总天数 */
    TripPlan createPlan(TripPlan plan);

    /** 分页查询用户的行程列表 */
    Map<String, Object> listMyPlans(Integer page, Integer size, Long userId);

    /** 查询行程详情 */
    TripPlan getPlanById(Long id);

    /** 部分更新行程信息 */
    TripPlan updatePlan(Long id, TripPlan plan);

    /** 删除行程，同时删除关联的所有景点明细 */
    void deletePlan(Long id);

    /** 向行程添加景点 */
    TripPlanItem addItem(Long planId, TripPlanItem item);

    /** 更新行程中的景点信息（天数、顺序等） */
    TripPlanItem updateItem(Long planId, Long itemId, TripPlanItem item);

    /** 从行程移除景点 */
    void removeItem(Long planId, Long itemId);

    /** 获取行程景点列表（按 dayNumber + sortOrder 排序） */
    List<Attraction> getPlanAttractions(Long planId);

    /** 智能编排：调用百度路线矩阵 + 贪心最近邻算法，优化每天的景点游览顺序 */
    TripPlan optimize(Long planId);
}
