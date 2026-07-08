package com.tripmate.controller;

import com.tripmate.common.Result;
import com.tripmate.common.UserContext;
import com.tripmate.dto.CachedUser;
import com.tripmate.service.ReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/**
 * 评论控制器。
 * GET 公开查看，POST 发表/回复需登录，DELETE 本人或管理员。
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ReviewController {
    private final ReviewService reviewService;

    /** 景点评论列表（含回复） */
    @GetMapping("/attractions/{id}/reviews")
    public Result<?> list(@PathVariable Long id,
                          @RequestParam(defaultValue = "1") Integer page,
                          @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(reviewService.listByAttraction(id, page, size));
    }

    /** 发表评论 */
    @PostMapping("/attractions/{id}/reviews")
    public Result<?> create(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long userId = UserContext.getUserId();
        String content = (String) body.get("content");
        Integer rating = body.get("rating") != null ? ((Number) body.get("rating")).intValue() : 5;
        if (content == null || content.isBlank()) {
            return Result.error(400, "评论内容不能为空");
        }
        return Result.success(reviewService.create(userId, id, content, rating));
    }

    /** 回复评论 */
    @PostMapping("/reviews/{reviewId}/reply")
    public Result<?> reply(@PathVariable Long reviewId, @RequestBody Map<String, Object> body) {
        Long userId = UserContext.getUserId();
        String content = (String) body.get("content");
        if (content == null || content.isBlank()) {
            return Result.error(400, "回复内容不能为空");
        }
        return Result.success(reviewService.reply(userId, reviewId, content));
    }

    /** 删除评论 */
    @DeleteMapping("/reviews/{reviewId}")
    public Result<?> delete(@PathVariable Long reviewId) {
        Long userId = UserContext.getUserId();
        CachedUser user = UserContext.get();
        boolean isAdmin = user != null && user.getAuthorities().contains("ROLE_ADMIN");
        reviewService.delete(reviewId, userId, isAdmin);
        return Result.success();
    }
}
