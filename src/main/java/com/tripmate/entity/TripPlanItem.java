package com.tripmate.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * 行程景点明细实体，映射 trip_plan_items 表。
 *
 * <p>一个行程的每一天包含多个景点，每个景点有：
 * dayNumber（第几天）+ sortOrder（当天游览顺序）。
 * 智能编排（optimize）通过百度地图路线矩阵重算 sortOrder 实现最优游览路径。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("trip_plan_items")
public class TripPlanItem {

    /** 明细唯一 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属行程 ID */
    private Long planId;

    /** 景点 ID */
    private Long attractionId;

    /** 第几天（从 1 开始） */
    private Integer dayNumber;

    /** 当天游览顺序（从 0 开始，数字越小越早） */
    private Integer sortOrder;

    /** 预计游玩时长（分钟） */
    private Integer duration;

    /** 出行方式：driving（驾车）/ transit（公交）/ walking（步行） */
    private String transportMode;

    /** 备注信息 */
    private String notes;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 最近更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
