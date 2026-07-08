package com.tripmate.dto;

import lombok.Data;

/**
 * 景点更新请求 DTO。
 *
 * <p>所有字段可选——仅传入需要更新的字段，未传字段保持不变（部分更新模式）。
 * status: 0=下架，1=上架。</p>
 */
@Data
public class AttractionUpdateRequest {

    private String name;
    private String description;
    private String category;
    private String city;
    private String address;
    private Double latitude;
    private Double longitude;
    private String imageUrl;
    private Integer rating;
    private Integer status;
}
