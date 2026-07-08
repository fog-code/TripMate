package com.tripmate.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 行程计划实体，映射 trip_plans 表。
 *
 * <p>每个用户可创建多个行程计划，每个行程包含若干天、若干景点。
 * status 表示行程生命周期：0=规划中，1=出行中，2=已完成。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("trip_plans")
public class TripPlan {

    /** 行程唯一 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属用户 ID */
    private Long userId;

    /** 行程标题，如"北京三日游" */
    private String title;

    /** 行程描述 */
    private String description;

    /** 行程开始日期 */
    private LocalDate startDate;

    /** 行程结束日期 */
    private LocalDate endDate;

    /** 总天数（由日期区间自动计算） */
    private Integer totalDays;

    /** 行程状态：0=规划中，1=出行中，2=已完成 */
    private Integer status;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 最近更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
