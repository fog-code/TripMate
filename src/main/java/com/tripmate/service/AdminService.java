package com.tripmate.service;

import java.util.Map;

/**
 * 管理后台服务接口。
 *
 * <p>提供仪表盘统计、用户管理（启停）、景点审核（上下架）、评论管理功能。
 * 所有操作需要 ROLE_ADMIN 权限。</p>
 */
public interface AdminService {

    /** 仪表盘数据：用户数、景点数、今日活跃用户、今日新增用户 */
    Map<String, Object> dashboard();

    /** 分页查询用户列表（密码已过滤），支持用户名/邮箱搜索 */
    Map<String, Object> listUsers(Integer page, Integer size, String keyword);

    /** 启用/禁用用户账号 */
    void toggleUserStatus(Long userId, Integer status);

    /** 分页查询景点列表（含已下架），支持名称搜索 */
    Map<String, Object> listAttractions(Integer page, Integer size, String keyword);

    /** 审核景点上下架 */
    void toggleAttractionStatus(Long attractionId, Integer status);

    /** 分页查询所有评论，支持内容搜索 */
    Map<String, Object> listReviews(Integer page, Integer size, String keyword);

    /** 删除评论 */
    void deleteReview(Long reviewId);
}
