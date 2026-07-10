package com.tripmate.controller;

import com.tripmate.common.Result;
import com.tripmate.common.UserContext;
import com.tripmate.service.TripService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 旅行控制器。
 *
 * <p>提供旅行的 CRUD、景点管理及路线规划接口。
 * 所有接口均需登录，通过 UserContext 获取当前用户 ID。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/trips")
@RequiredArgsConstructor
public class TripController {

    private final TripService tripService;

    /** 获取当前用户的旅行列表 */
    @GetMapping
    public Result<?> list() {
        Long userId = UserContext.getUserId();
        log.info("用户 {} 查询旅行列表", userId);
        return Result.success(tripService.listByUser(userId));
    }

    /** 获取旅行详情（含景点明细及景点信息） */
    @GetMapping("/{id}")
    public Result<?> getDetail(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        log.info("用户 {} 查询旅行 {} 详情", userId, id);
        return Result.success(tripService.getDetail(id, userId));
    }

    /** 创建旅行 */
    @PostMapping
    public Result<?> create(@RequestBody Map<String, String> body) {
        Long userId = UserContext.getUserId();
        String name = body.get("name");
        String city = body.get("city");
        if (name == null || name.isBlank()) {
            return Result.error(400, "旅行名称不能为空");
        }
        log.info("用户 {} 创建旅行: name={}, city={}", userId, name, city);
        return Result.success(tripService.create(userId, name, city));
    }

    /** 更新旅行名称 */
    @PutMapping("/{id}")
    public Result<?> update(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Long userId = UserContext.getUserId();
        String name = body.get("name");
        if (name == null || name.isBlank()) {
            return Result.error(400, "旅行名称不能为空");
        }
        log.info("用户 {} 更新旅行 {} 名称为: {}", userId, id, name);
        tripService.update(id, userId, name);
        return Result.success();
    }

    /** 删除旅行（同时删除关联景点） */
    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        log.info("用户 {} 删除旅行 {}", userId, id);
        tripService.delete(id, userId);
        return Result.success();
    }

    /** 向旅行添加景点 */
    @PostMapping("/{id}/items")
    public Result<?> addItem(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long userId = UserContext.getUserId();
        Long attractionId = body.get("attractionId") != null
                ? ((Number) body.get("attractionId")).longValue() : null;
        Integer dayNumber = body.get("dayNumber") != null
                ? ((Number) body.get("dayNumber")).intValue() : 1;

        if (attractionId == null) {
            return Result.error(400, "景点ID不能为空");
        }
        log.info("用户 {} 向旅行 {} 添加景点 {}, day={}", userId, id, attractionId, dayNumber);
        return Result.success(tripService.addItem(id, userId, attractionId, dayNumber));
    }

    /** 从旅行移除景点 */
    @DeleteMapping("/{id}/items/{itemId}")
    public Result<?> removeItem(@PathVariable Long id, @PathVariable Long itemId) {
        Long userId = UserContext.getUserId();
        log.info("用户 {} 从旅行 {} 移除景点明细 {}", userId, id, itemId);
        tripService.removeItem(id, userId, itemId);
        return Result.success();
    }

    /** 重新排序旅行景点 */
    @PutMapping("/{id}/items/reorder")
    public Result<?> reorderItems(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long userId = UserContext.getUserId();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        if (items == null || items.isEmpty()) {
            return Result.error(400, "排序列表不能为空");
        }
        log.info("用户 {} 重新排序旅行 {} 的景点", userId, id);
        tripService.reorderItems(id, userId, items);
        return Result.success();
    }

    /** 路线规划：使用百度地图计算景点间的驾车路线 */
    @PostMapping("/{id}/route")
    public Result<?> planRoute(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        log.info("用户 {} 为旅行 {} 规划路线", userId, id);
        return Result.success(tripService.planRoute(id, userId));
    }
}
