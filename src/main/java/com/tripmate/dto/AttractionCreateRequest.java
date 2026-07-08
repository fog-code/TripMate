package com.tripmate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 景点创建请求 DTO。
 */
@Data
public class AttractionCreateRequest {

    @NotBlank(message = "景点名称不能为空")
    private String name;

    @NotBlank(message = "景点描述不能为空")
    private String description;

    @NotBlank(message = "景点分类不能为空")
    private String category;

    @NotBlank(message = "所在城市不能为空")
    private String city;

    @NotBlank(message = "详细地址不能为空")
    private String address;

    @NotNull(message = "纬度不能为空")
    private Double latitude;

    @NotNull(message = "经度不能为空")
    private Double longitude;

    /** 可选，封面图片 URL */
    private String imageUrl;
}
