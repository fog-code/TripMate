package com.tripmate.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tripmate.dto.DestinationRouteRequest;
import com.tripmate.dto.DestinationRouteResponse;
import com.tripmate.entity.Attraction;
import com.tripmate.mapper.AttractionMapper;
import com.tripmate.service.BaiduMapsService;
import com.tripmate.service.BaiduMapsService.Coord;
import com.tripmate.service.BaiduMapsService.RoutePlan;
import com.tripmate.service.BaiduMapsService.RouteStep;
import com.tripmate.service.NavigationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NavigationServiceImpl implements NavigationService {

    private final BaiduMapsService baiduMapsService;
    private final AttractionMapper attractionMapper;

    @Override
    public DestinationRouteResponse recommendDestinationRoute(DestinationRouteRequest request) {
        log.info("目的地路线请求 attractionId={}", request.getAttractionId());

        Attraction destination = attractionMapper.selectById(request.getAttractionId());
        if (destination == null) {
            log.warn("目的地景点不存在 attractionId={}", request.getAttractionId());
            throw new RuntimeException("目的地景点不存在");
        }
        // 检查是否有坐标
        if (destination.getLatitude() == null || destination.getLongitude() == null) {
            throw new RuntimeException("该景点缺少经纬度信息，无法规划路线");
        }

        Coord destCoord = new Coord(destination.getLatitude(), destination.getLongitude());

        // 2. 确定起点（用户必须指定）
        Attraction suggestedOrigin = null;
        Coord originCoord;

        if (request.getOriginLat() != null && request.getOriginLng() != null) {
            originCoord = new Coord(request.getOriginLat(), request.getOriginLng());
        } else {
            throw new RuntimeException("请指定起点坐标（使用 IP 定位或搜索地点）");
        }

        // 3. 规划到达路线（根据出行方式选择 API）
        RoutePlan arrivalPlan;
        try {
            boolean isTransit = "transit".equalsIgnoreCase(request.getTravelMode());
            if (isTransit) {
                try {
                    arrivalPlan = baiduMapsService.getTransitRoute(originCoord, destCoord);
                } catch (Exception te) {
                    // 公交规划失败时降级为驾车
                    log.warn("公交路线规划失败，降级为驾车: {}", te.getMessage());
                    arrivalPlan = baiduMapsService.getDrivingRoute(originCoord, destCoord, null);
                }
            } else {
                arrivalPlan = baiduMapsService.getDrivingRoute(originCoord, destCoord, null);
            }
        } catch (Exception e) {
            throw new RuntimeException("百度地图路线规划失败: " + e.getMessage(), e);
        }

        // 4. 搜索周边景点（同城市，排除目的地自身）
        List<Attraction> nearbyAttractions = findNearbyAttractions(
                destination, request.getRadius(), request.getNearbyLimit());

        // 5. 规划周边游览路线（目的地 → 周边景点1 → 周边景点2 → ...）
        Integer tourDistance = null;
        Integer tourDuration = null;
        List<RouteStep> tourSteps = null;

        if (!nearbyAttractions.isEmpty()) {
            try {
                List<Coord> tourCoords = new ArrayList<>();
                tourCoords.add(destCoord); // 起点：目的地
                for (Attraction a : nearbyAttractions) {
                    if (a.getLatitude() != null && a.getLongitude() != null) {
                        tourCoords.add(new Coord(a.getLatitude(), a.getLongitude()));
                    }
                }
                if (tourCoords.size() >= 2) {
                    Coord tourOrigin = tourCoords.get(0);
                    Coord tourDest = tourCoords.get(tourCoords.size() - 1);
                    List<Coord> tourWaypoints = tourCoords.size() > 2
                            ? tourCoords.subList(1, tourCoords.size() - 1) : null;
                    RoutePlan tourPlan = baiduMapsService.getDrivingRoute(tourOrigin, tourDest, tourWaypoints);
                    tourDistance = tourPlan.totalDistance();
                    tourDuration = tourPlan.totalDuration();
                    tourSteps = tourPlan.steps();
                }
            } catch (Exception e) {
                // 周边路线规划失败不影响主流程
            }
        }

        // 6. 构建响应
        return DestinationRouteResponse.builder()
                .destination(destination)
                .suggestedOrigin(suggestedOrigin)
                .arrivalDistance(arrivalPlan.totalDistance())
                .arrivalDuration(arrivalPlan.totalDuration())
                .arrivalSteps(arrivalPlan.steps())
                .nearbyAttractions(nearbyAttractions)
                .tourDistance(tourDistance)
                .tourDuration(tourDuration)
                .tourSteps(tourSteps)
                .build();
    }

    /**
     * 搜索目的地周边的热门景点。
     */
    private List<Attraction> findNearbyAttractions(Attraction destination, int radius, int limit) {
        LambdaQueryWrapper<Attraction> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Attraction::getCity, destination.getCity())
               .eq(Attraction::getStatus, 1)
               .ne(Attraction::getId, destination.getId())
               .isNotNull(Attraction::getLatitude)
               .isNotNull(Attraction::getLongitude)
               .orderByDesc(Attraction::getViewCount);

        List<Attraction> allCity = attractionMapper.selectList(wrapper);

        // 按直线距离排序，取最近的 limit 个
        final double destLat = destination.getLatitude();
        final double destLng = destination.getLongitude();

        return allCity.stream()
                .sorted((a, b) -> {
                    double dA = haversineDistance(destLat, destLng, a.getLatitude(), a.getLongitude());
                    double dB = haversineDistance(destLat, destLng, b.getLatitude(), b.getLongitude());
                    return Double.compare(dA, dB);
                })
                .filter(a -> haversineDistance(destLat, destLng, a.getLatitude(), a.getLongitude()) <= radius)
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Haversine 公式计算两点间直线距离（米）。
     */
    private double haversineDistance(double lat1, double lng1, double lat2, double lng2) {
        final double R = 6371000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                   * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}
