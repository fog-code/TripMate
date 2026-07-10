package com.tripmate.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 旅行实体，映射 trips 表。
 *
 * <p>每个用户可创建多个旅行，status 表示旅行状态：
 * 1=规划中，2=已完成。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("trips")
public class Trip {

    /** 旅行唯一 ID，数据库自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属用户 ID */
    private Long userId;

    /** 旅行名称，如"周末出游" */
    private String name;

    /** 目标城市 */
    private String city;

    /** 旅行状态：1=规划中，2=已完成 */
    private Integer status;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 最近更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 景点明细列表（非数据库字段，getDetail 时填充，含景点详细信息） */
    @TableField(exist = false)
    private List<Map<String, Object>> items;
}
