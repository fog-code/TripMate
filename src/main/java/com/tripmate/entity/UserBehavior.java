package com.tripmate.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * 用户行为实体类，映射 user_behaviors 表。
 *
 * <p>记录用户对景点的互动行为：浏览、点赞、收藏。
 * 行为数据是推荐引擎的核心输入，用于计算用户偏好向量。
 * 本表为追加型日志（不可变），无 updatedAt 字段。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("user_behaviors")
public class UserBehavior {

    /** 行为记录唯一 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 操作用户 ID */
    private Long userId;

    /** 目标景点 ID */
    private Long attractionId;

    /**
     * 行为类型：
     * <ul>
     *   <li>VIEW —— 浏览景点详情</li>
     *   <li>LIKE —— 点赞</li>
     *   <li>BOOKMARK —— 收藏</li>
     * </ul>
     * 推荐引擎中权重：BOOKMARK(5) &gt; LIKE(3) &gt; VIEW(1)
     */
    private String actionType;

    /** 行为数值（预留字段，如评分分数、浏览时长等） */
    private Integer actionValue;

    /** 行为发生时间，插入时自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
