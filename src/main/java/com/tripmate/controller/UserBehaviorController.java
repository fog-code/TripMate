package com.tripmate.controller;

import com.tripmate.common.Result;
import com.tripmate.common.UserContext;
import com.tripmate.dto.BehaviorRecordRequest;
import com.tripmate.service.UserBehaviorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 用户行为控制器。
 *
 * <p>提供行为记录（浏览/点赞/收藏）、查询、取消等功能。
 * 所有接口需登录，userId 从当前认证上下文自动获取。
 * 点赞和收藏有去重逻辑（同一用户对同一景点只能有一条 LIKE/BOOKMARK 记录）。</p>
 */
@RestController
@RequestMapping("/api/behaviors")
@RequiredArgsConstructor
public class UserBehaviorController {

    private final UserBehaviorService userBehaviorService;

    /** 记录用户行为（浏览由景点详情接口自动记录，此接口主要用于点赞/收藏） */
    @PostMapping
    public Result<?> record(@Valid @RequestBody BehaviorRecordRequest request) {
        Long userId = UserContext.getUserId();
        return Result.success(userBehaviorService.record(request, userId));
    }

    /** 分页查询当前用户的行为记录 */
    @GetMapping
    public Result<?> listMyBehaviors(@RequestParam(defaultValue = "1") Integer page,
                                     @RequestParam(defaultValue = "10") Integer size) {
        Long userId = UserContext.getUserId();
        return Result.success(userBehaviorService.listMyBehaviors(page, size, userId));
    }

    /** 查询当前用户对某景点的点赞/收藏状态：{ liked: bool, bookmarked: bool } */
    @GetMapping("/status")
    public Result<?> getStatus(@RequestParam Long attractionId) {
        Long userId = UserContext.getUserId();
        return Result.success(userBehaviorService.getStatus(userId, attractionId));
    }

    /** 取消行为记录（取消点赞/取消收藏） */
    @DeleteMapping
    public Result<?> delete(@RequestParam Long attractionId,
                            @RequestParam String actionType) {
        Long userId = UserContext.getUserId();
        userBehaviorService.delete(userId, attractionId, actionType);
        return Result.success();
    }

    /** 分页查询当前用户的收藏/点赞景点列表（返回景点详情而非行为记录） */
    @GetMapping("/favorites")
    public Result<?> favorites(@RequestParam String actionType,
                               @RequestParam(defaultValue = "1") Integer page,
                               @RequestParam(defaultValue = "12") Integer size) {
        Long userId = UserContext.getUserId();
        return Result.success(userBehaviorService.getFavorites(userId, actionType, page, size));
    }
}
