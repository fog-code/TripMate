package com.tripmate.service;

import com.tripmate.dto.AttractionCreateRequest;
import com.tripmate.dto.AttractionUpdateRequest;
import com.tripmate.entity.Attraction;

import java.util.List;
import java.util.Map;

/**
 * 景点服务接口。
 *
 * <p>提供景点的 CRUD 操作，支持分页筛选、Redis 缓存（1 小时 TTL）和周边搜索。</p>
 */
public interface AttractionService {

    /** 分页查询景点列表，支持分类、城市、关键词筛选。仅返回已上架的景点 */
    Map<String, Object> list(Integer page, Integer size, String category, String city, String keyword);

    /** 查询景点详情（优先从 Redis 缓存读取）。认证用户会触发 VIEW 行为记录和浏览计数 +1 */
    Attraction getById(Long id);

    /** 新增景点，默认状态为已上架 */
    Attraction create(AttractionCreateRequest request);

    /** 部分更新景点信息，更新后清除 Redis 缓存 */
    Attraction update(Long id, AttractionUpdateRequest request);

    /** 删除景点，同时清除 Redis 缓存 */
    void delete(Long id);

    /** 获取景点周边的其他同城景点，按 Haversine 公式计算直线距离排序 */
    List<Attraction> getNearby(Long attractionId, int radius, int limit);
}
