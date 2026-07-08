package com.tripmate.controller;

import com.tripmate.common.Result;
import com.tripmate.dto.DestinationRouteRequest;
import com.tripmate.service.BaiduMapsService;
import com.tripmate.service.NavigationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class NavigationController {

    private final NavigationService navigationService;
    private final BaiduMapsService baiduMapsService;

    /** 目的地路线推荐 */
    @PostMapping("/destination/route")
    public Result<?> recommendDestinationRoute(@Valid @RequestBody DestinationRouteRequest request) {
        return Result.success(navigationService.recommendDestinationRoute(request));
    }

    /** IP 定位 */
    @GetMapping("/location/ip")
    public Result<?> ipLocation() {
        BaiduMapsService.IpLocation location = baiduMapsService.ipLocation();
        if (location == null) {
            return Result.error(500, "IP定位失败");
        }
        return Result.success(location);
    }

    /** 地点搜索 */
    @GetMapping("/place/suggest")
    public Result<?> placeSearch(@RequestParam String query,
                                 @RequestParam(required = false) String region) {
        return Result.success(baiduMapsService.placeSearch(query, region));
    }
}
