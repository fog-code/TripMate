package com.tripmate.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * 景点实体类，映射 attractions 表。
 *
 * <p>每个景点包含名称、描述、分类、城市、坐标等信息。
 * status 控制上下架：0=已下架（前端不可见），1=上架（正常展示）。
 * viewCount 记录累计浏览次数，通过 SQL 原子递增保证并发安全。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("attractions")
public class Attraction {

    /** 景点唯一 ID，数据库自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 景点名称 */
    private String name;

    /** 景点详细介绍 */
    private String description;

    /** 分类标签：自然风光 / 历史古迹 / 主题公园 / 博物馆 / 美食街区 等 */
    private String category;

    /** 所在城市 */
    private String city;

    /** 详细地址 */
    private String address;

    /** 纬度（百度坐标系） */
    private Double latitude;

    /** 经度（百度坐标系） */
    private Double longitude;

    /** 封面图片 URL */
    private String imageUrl;

    /** 用户评分（1-5），默认 0 表示暂未评分 */
    private Integer rating;

    /** 上架状态：0=已下架，1=已上架（正常展示） */
    private Integer status;

    /** 累计浏览次数，用于热门排序和推荐算法 */
    private Integer viewCount;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 最近更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
