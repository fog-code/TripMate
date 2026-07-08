package com.tripmate.controller;

import com.tripmate.common.Result;
import com.tripmate.dto.AttractionCreateRequest;
import com.tripmate.dto.AttractionUpdateRequest;
import com.tripmate.service.AttractionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 景点管理控制器。
 *
 * <p>提供景点列表查询（支持分类/城市/关键词筛选）、详情查看、CRUD 操作。
 * GET 接口无需登录，POST/PUT/DELETE 需管理员权限。</p>
 */
@RestController
@RequestMapping("/api/attractions")
@RequiredArgsConstructor
public class AttractionController {

    private final AttractionService attractionService;

    /** 分页查询景点列表，支持分类、城市、关键词筛选 */
    @GetMapping
    public Result<?> list(@RequestParam(defaultValue = "1") Integer page,
                          @RequestParam(defaultValue = "10") Integer size,
                          @RequestParam(required = false) String category,
                          @RequestParam(required = false) String city,
                          @RequestParam(required = false) String keyword) {
        return Result.success(attractionService.list(page, size, category, city, keyword));
    }

    /** 景点详情，仅返回已上架景点。认证用户会自动记录 VIEW 行为并递增浏览计数 */
    @GetMapping("/{id}")
    public Result<?> getById(@PathVariable Long id) {
        return Result.success(attractionService.getById(id));
    }

    /** 新增景点 */
    @PostMapping
    public Result<?> create(@Valid @RequestBody AttractionCreateRequest request) {
        return Result.success(attractionService.create(request));
    }

    /** 更新景点（部分更新，仅传需要修改的字段） */
    @PutMapping("/{id}")
    public Result<?> update(@PathVariable Long id,
                            @RequestBody AttractionUpdateRequest request) {
        return Result.success(attractionService.update(id, request));
    }

    /** 删除景点，同时清除 Redis 缓存 */
    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        attractionService.delete(id);
        return Result.success();
    }

    /**
     * 获取景点周边的其他景点。
     *
     * @param id     中心景点 ID
     * @param radius 搜索半径（米），默认 5000
     * @param limit  返回数量上限，默认 5
     */
    @GetMapping("/{id}/nearby")
    public Result<?> nearby(@PathVariable Long id,
                            @RequestParam(defaultValue = "5000") int radius,
                            @RequestParam(defaultValue = "5") int limit) {
        return Result.success(attractionService.getNearby(id, radius, limit));
    }
}
