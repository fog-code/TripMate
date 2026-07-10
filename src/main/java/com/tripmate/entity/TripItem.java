package com.tripmate.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * 旅行景点明细实体，映射 trip_items 表。
 *
 * <p>一个旅行的每一天包含多个景点，每个景点有：
 * dayNumber（第几天）+ orderIndex（当天游览顺序）。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("trip_items")
public class TripItem {

    /** 明细唯一 ID，数据库自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属旅行 ID */
    private Long tripId;

    /** 景点 ID */
    private Long attractionId;

    /** 第几天（从 1 开始） */
    private Integer dayNumber;

    /** 当天游览顺序（从 0 开始，数字越小越早） */
    private Integer orderIndex;

    /** 备注信息 */
    private String notes;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
