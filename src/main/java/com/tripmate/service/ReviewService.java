package com.tripmate.service;

import com.tripmate.entity.Review;
import java.util.Map;

/**
 * 评论服务接口。
 */
public interface ReviewService {
    /** 分页查询景点评论（含回复） */
    Map<String, Object> listByAttraction(Long attractionId, Integer page, Integer size);
    /** 发表评论 */
    Review create(Long userId, Long attractionId, String content, Integer rating);
    /** 回复评论 */
    Review reply(Long userId, Long parentReviewId, String content);
    /** 删除评论（用户删自己，管理员删任意） */
    void delete(Long reviewId, Long userId, boolean isAdmin);
}
