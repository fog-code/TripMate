package com.tripmate.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 单目的地路线推荐请求。
 * 用户选择一个目的地景点，系统推荐驾车到达路线及周边路线。
 */
@Data
public class DestinationRouteRequest {

    /** 目的地景点ID（必填） */
    @NotNull(message = "目的地景点ID不能为空")
    private Long attractionId;

    /** 起点纬度（可选，默认使用目的地所在城市中心） */
    private Double originLat;

    /** 起点经度（可选） */
    private Double originLng;

    /** 周边搜索半径（米），默认 5000 */
    private Integer radius = 5000;

    /** 返回周边景点数量上限，默认 5 */
    private Integer nearbyLimit = 5;

    /** 出行方式：driving（驾车）/ transit（公交），默认 driving */
    private String travelMode = "driving";
}
