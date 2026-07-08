package com.tripmate.service;

import com.tripmate.entity.Attraction;

import java.util.List;

/**
 * 智能推荐服务接口。
 *
 * <p>基于用户行为数据（浏览/点赞/收藏权重 + 时间衰减）、景点热度、
 * IP 位置进行混合评分推荐。结果缓存 1 分钟。</p>
 */
public interface RecommendationService {

    /**
     * 获取个性化推荐景点列表。
     *
     * @param limit 返回数量上限
     * @return 按推荐度排序的景点列表
     */
    List<Attraction> recommend(int limit);
}
