package com.tripmate.dto;

import com.tripmate.entity.Attraction;
import com.tripmate.service.BaiduMapsService.RouteStep;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 单目的地路线推荐结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DestinationRouteResponse {

    /** 目的地景点信息 */
    private Attraction destination;

    /** 推荐起点信息（城市中心/交通枢纽） */
    private Attraction suggestedOrigin;

    // ─── 到达路线（起点 → 目的地） ───

    /** 到达路线总距离(米) */
    private Integer arrivalDistance;

    /** 到达路线总时长(秒) */
    private Integer arrivalDuration;

    /** 到达路线导航步骤 */
    private List<RouteStep> arrivalSteps;

    // ─── 周边景点 ───

    /** 周边推荐景点 */
    private List<Attraction> nearbyAttractions;

    // ─── 周边游览路线（目的地 → 周边景点循环） ───

    /** 周边游览路线总距离 */
    private Integer tourDistance;

    /** 周边游览路线总时长 */
    private Integer tourDuration;

    /** 周边游览路线导航步骤 */
    private List<RouteStep> tourSteps;
}
