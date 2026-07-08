package com.tripmate.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * 评论实体，映射 reviews 表。
 * 支持嵌套回复：parentId 指向父评论，replyToUserId 指向被回复用户。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("reviews")
public class Review {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long attractionId;
    /** 评论内容 */
    private String content;
    /** 评分 1-5（回复时可为 null） */
    private Integer rating;
    /** 父评论 ID，null=顶级评论 */
    private Long parentId;
    /** 被回复的用户 ID */
    private Long replyToUserId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
