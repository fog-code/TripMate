package com.tripmate.service;

import com.tripmate.dto.BehaviorRecordRequest;
import com.tripmate.entity.UserBehavior;

import java.util.Map;

/**
 * 用户行为服务接口。
 *
 * <p>提供行为记录（浏览/点赞/收藏）、分页查询、状态查询、取消和收藏列表功能。
 * 记录新行为时会自动清除该用户的推荐缓存以保证推荐结果实时性。</p>
 */
public interface UserBehaviorService {

    /** 记录行为：LIKE/BOOKMARK 对同一用户+景点去重，VIEW 允许多次 */
    UserBehavior record(BehaviorRecordRequest request, Long userId);

    /** 分页查询用户行为记录列表 */
    Map<String, Object> listMyBehaviors(Integer page, Integer size, Long userId);

    /** 查询用户对景点的交互状态：{ liked: bool, bookmarked: bool } */
    Map<String, Boolean> getStatus(Long userId, Long attractionId);

    /** 删除行为记录（取消点赞/收藏），同时清除推荐缓存 */
    void delete(Long userId, Long attractionId, String actionType);

    /** 查询用户点赞/收藏的景点列表（返回景点详情而非行为记录） */
    Map<String, Object> getFavorites(Long userId, String actionType, Integer page, Integer size);
}
