package com.tripmate.service;

import com.tripmate.dto.DestinationRouteRequest;
import com.tripmate.dto.DestinationRouteResponse;

/**
 * 路线导航服务接口。
 *
 * <p>基于百度地图 Direction API 提供驾车/公交路线规划。
 * 支持自由路线规划、单目的地路线推荐（含周边景点游览路线）。</p>
 */
public interface NavigationService {

    /**
     * 单目的地路线推荐。
     * 用户选择一个景点作为目的地，系统推荐到达路线 + 周边景点 + 周边游览路线。
     */
    DestinationRouteResponse recommendDestinationRoute(DestinationRouteRequest request);
}
