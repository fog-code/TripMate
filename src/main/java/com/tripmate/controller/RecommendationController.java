package com.tripmate.controller;

import com.tripmate.common.Result;
import com.tripmate.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 智能推荐控制器。
 *
 * <p>根据用户行为（浏览/点赞/收藏）和 IP 位置，返回个性化景点推荐。
 * 未登录返回热门景点，登录后返回混合评分推荐。结果有 Redis 缓存（1 分钟 TTL）。</p>
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;

    /** 获取推荐景点列表，limit 控制返回数量（默认 10） */
    @GetMapping("/recommendations")
    public Result<?> recommend(@RequestParam(defaultValue = "10") int limit) {
        return Result.success(recommendationService.recommend(limit));
    }
}
