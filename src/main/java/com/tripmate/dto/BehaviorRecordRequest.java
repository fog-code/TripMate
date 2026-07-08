package com.tripmate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 行为记录请求 DTO。
 *
 * <p>actionType 支持 VIEW（浏览）/ LIKE（点赞）/ BOOKMARK（收藏）。
 * actionValue 为可选数值（如评分分数）。</p>
 */
@Data
public class BehaviorRecordRequest {

    /** 目标景点 ID */
    @NotNull(message = "景点ID不能为空")
    private Long attractionId;

    /** 行为类型：VIEW / LIKE / BOOKMARK */
    @NotBlank(message = "行为类型不能为空")
    private String actionType;

    /** 行为数值（可选，如评分分数） */
    private Integer actionValue;
}
