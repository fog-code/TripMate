package com.tripmate.controller;

import com.tripmate.common.Result;
import com.tripmate.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 管理后台控制器。
 *
 * <p>所有接口需要 ROLE_ADMIN 权限（通过 @PreAuthorize 控制）。</p>
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;

    /** 仪表盘统计：用户数、景点数、今日活跃用户、今日新增用户 */
    @GetMapping("/dashboard")
    public Result<?> dashboard() { return Result.success(adminService.dashboard()); }

    /** 分页查询用户列表（密码字段已过滤），支持用户名/邮箱搜索 */
    @GetMapping("/users")
    public Result<?> listUsers(@RequestParam(defaultValue = "1") Integer page,
                               @RequestParam(defaultValue = "10") Integer size,
                               @RequestParam(required = false) String keyword) {
        return Result.success(adminService.listUsers(page, size, keyword));
    }

    /** 启用/禁用用户：{ "status": 0 } 禁用，{ "status": 1 } 启用 */
    @PutMapping("/users/{id}/status")
    public Result<?> toggleUserStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        adminService.toggleUserStatus(id, body.get("status"));
        return Result.success();
    }

    /** 分页查询所有景点（含已下架），支持名称搜索 */
    @GetMapping("/attractions")
    public Result<?> listAttractions(@RequestParam(defaultValue = "1") Integer page,
                                     @RequestParam(defaultValue = "10") Integer size,
                                     @RequestParam(required = false) String keyword) {
        return Result.success(adminService.listAttractions(page, size, keyword));
    }

    /** 审核景点上下架：{ "status": 0 } 下架，{ "status": 1 } 上架 */
    @PutMapping("/attractions/{id}/status")
    public Result<?> toggleAttractionStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        adminService.toggleAttractionStatus(id, body.get("status"));
        return Result.success();
    }

    /** 分页查询所有评论，支持内容搜索 */
    @GetMapping("/reviews")
    public Result<?> listReviews(@RequestParam(defaultValue = "1") Integer page,
                                 @RequestParam(defaultValue = "10") Integer size,
                                 @RequestParam(required = false) String keyword) {
        return Result.success(adminService.listReviews(page, size, keyword));
    }

    /** 删除评论 */
    @DeleteMapping("/reviews/{id}")
    public Result<?> deleteReview(@PathVariable Long id) {
        adminService.deleteReview(id);
        return Result.success();
    }
}
