package com.tripmate.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 百度地图 API 服务封装。
 * 使用驾车路线规划矩阵接口获取点对点之间的距离和时长。
 */
@Service
public class BaiduMapsService {

    private final RestClient restClient;
    private final String ak;
    private final ObjectMapper objectMapper;

    public BaiduMapsService(RestClient.Builder builder,
                            @Value("${baidu.maps.ak}") String ak) {
        this.restClient = builder.baseUrl("https://api.map.baidu.com").build();
        this.ak = ak;
        this.objectMapper = new ObjectMapper();
    }



    /**
     * 批量获取驾车距离和时长矩阵。
     * @param coords 坐标列表 [ (lat,lng), ... ]
     * @return distance[i][j] = 从 coords[i] 到 coords[j] 的距离(米)，
     *         duration[i][j] = 从 coords[i] 到 coords[j] 的时长(秒)
     */
    /*public RouteMatrix getDrivingMatrix(List<Coord> coords) {
        int n = coords.size();
        String origins = coordsToParam(coords);
        String destinations = origins; // 方阵

        String json = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/routematrix/v2/driving")
                        .queryParam("origins", origins)
                        .queryParam("destinations", destinations)
                        .queryParam("tactics", "12")  // 最短时间
                        .queryParam("ak", ak)
                        .build())
                .retrieve()
                .body(String.class);

        int[][] distance = new int[n][n];
        int[][] duration = new int[n][n];

        try {
            JsonNode root = objectMapper.readTree(json);
            if (root.get("status").asInt() != 0) {
                throw new RuntimeException("百度地图API调用失败: " + root.path("message").asText());
            }
            JsonNode results = root.get("result");
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    JsonNode cell = results.get(i * n + j);
                    distance[i][j] = cell.get("distance").get("value").asInt();
                    duration[i][j] = cell.get("duration").get("value").asInt();
                }
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("百度地图API响应解析失败", e);
        }

        return new RouteMatrix(distance, duration);
    }
*/
    /**
     * 公交路线规划。
     * @param origin 起点坐标
     * @param destination 终点坐标
     * @return 路线详情
     */
    public RoutePlan getTransitRoute(Coord origin, Coord destination) {
        String originParam = origin.lat() + "," + origin.lng();
        String destParam = destination.lat() + "," + destination.lng();

        String json = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/direction/v2/transit")
                        .queryParam("origin", originParam)
                        .queryParam("destination", destParam)
                        .queryParam("ak", ak)
                        .build())
                .retrieve()
                .body(String.class);

        return parseRoutePlan(json);
    }

    /**
     * 驾车路线规划（含途经点）。
     * @param origin 起点坐标
     * @param destination 终点坐标
     * @param waypoints 途经点列表（可选）
     * @return 路线详情：总距离、总时长、步骤列表
     */
    public RoutePlan getDrivingRoute(Coord origin, Coord destination, List<Coord> waypoints) {
        String originParam = origin.lat() + "," + origin.lng();
        String destParam = destination.lat() + "," + destination.lng();

        var uriBuilder = restClient.get()
                .uri(uri -> {
                    var u = uri.path("/direction/v2/driving")
                            .queryParam("origin", originParam)
                            .queryParam("destination", destParam)
                            .queryParam("ak", ak);
                    if (waypoints != null && !waypoints.isEmpty()) {
                        u.queryParam("waypoints", coordsToParam(waypoints));
                    }
                    return u.build();
                });

        String json = uriBuilder.retrieve().body(String.class);
        return parseRoutePlan(json);
    }

    /**
     * 解析路线规划 JSON 响应（驾车/公交通用）。
     */
    private RoutePlan parseRoutePlan(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root.get("status").asInt() != 0) {
                throw new RuntimeException("百度地图API调用失败: " + root.path("message").asText());
            }
            JsonNode route = root.get("result").get("routes").get(0);

            int totalDistance = route.get("distance").asInt();
            int totalDuration = route.get("duration").asInt();

            List<RouteStep> steps = new ArrayList<>();
            JsonNode stepsNode = route.get("steps");
            if (stepsNode != null) {
                for (JsonNode s : stepsNode) {
                    // 公交 API 返回的 steps 是二维数组，需要展开
                    if (s.isArray()) {
                        for (JsonNode sub : s) {
                            steps.add(parseStep(sub));
                        }
                    } else {
                        steps.add(parseStep(s));
                    }
                }
            }

            return new RoutePlan(totalDistance, totalDuration, steps);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("百度地图API响应解析失败", e);
        }
    }

    private RouteStep parseStep(JsonNode s) {
        String instruction = s.has("instructions") ? s.get("instructions").asText() : "";
        String roadName = s.has("road_name") ? s.get("road_name").asText() : "";
        int stepDistance = s.has("distance") ? s.get("distance").asInt() : 0;
        int stepDuration = s.has("duration") ? s.get("duration").asInt() : 0;
        String path = s.has("path") ? s.get("path").asText() : "";
        return new RouteStep(instruction, roadName, stepDistance, stepDuration, path);
    }

    private String coordsToParam(List<Coord> coords) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < coords.size(); i++) {
            if (i > 0){ sb.append("|");}
            sb.append(coords.get(i).lat()).append(",").append(coords.get(i).lng());
        }
        return sb.toString();
    }

    // ═══════════════════════════════════════════════════════════════
    // IP 定位 + 地点搜索
    // ═══════════════════════════════════════════════════════════════

    /**
     * IP 定位：获取当前 IP 所在城市和大致坐标。
     * 返回 {city, lat, lng} 或 null
     */
    public IpLocation ipLocation() {
        String json = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/location/ip")
                        .queryParam("ak", ak)
                        .queryParam("coor", "bd09ll")  // 百度坐标系
                        .build())
                .retrieve()
                .body(String.class);

        try {
            JsonNode root = objectMapper.readTree(json);
            if (root.get("status").asInt() != 0) {
                return null;
            }
            JsonNode content = root.get("content");
            String address = content.has("address") ? content.get("address").asText() : "";
            JsonNode point = content.get("point");
            double lat = point.get("y").asDouble();
            double lng = point.get("x").asDouble();
            // 提取城市名
            JsonNode addrDetail = content.get("address_detail");
            String city = "";
            if (addrDetail != null) {
                city = addrDetail.has("city") ? addrDetail.get("city").asText() : "";
            }
            return new IpLocation(city, address, lat, lng);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 地点搜索建议：根据关键词搜索地点。
     * @param query 搜索关键词
     * @param region 限定城市（可选）
     * @return 地点建议列表
     */
    public List<PlaceSuggestion> placeSearch(String query, String region) {
        String json = restClient.get()
                .uri(uriBuilder -> {
                    var u = uriBuilder.path("/place/v2/suggestion")
                            .queryParam("query", query)
                            .queryParam("region", region != null ? region : "全国")
                            .queryParam("ak", ak)
                            .queryParam("output", "json");
                    return u.build();
                })
                .retrieve()
                .body(String.class);

        List<PlaceSuggestion> results = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root.get("status").asInt() != 0) {
                return results;
            }
            JsonNode list = root.get("result");
            if (list == null || !list.isArray()) {
                return results;
            }

            for (JsonNode item : list) {
                String name = item.has("name") ? item.get("name").asText() : "";
                String city = item.has("city") ? item.get("city").asText() : "";
                String district = item.has("district") ? item.get("district").asText() : "";
                double lat = 0, lng = 0;
                if (item.has("location")) {
                    lat = item.get("location").get("lat").asDouble();
                    lng = item.get("location").get("lng").asDouble();
                }
                String address = item.has("address") ? item.get("address").asText() : "";
                String uid = item.has("uid") ? item.get("uid").asText() : "";
                results.add(new PlaceSuggestion(name, city, district, lat, lng, address, uid));
            }
        } catch (Exception e) {
            // 解析失败返回空
        }
        return results;
    }

    public record Coord(double lat, double lng) {}

    public record RouteMatrix(int[][] distance, int[][] duration) {}

    public record RoutePlan(int totalDistance, int totalDuration, List<RouteStep> steps) {}

    public record RouteStep(String instruction, String roadName, int distance, int duration, String path) {}

    public record IpLocation(String city, String address, double lat, double lng) {}

    public record PlaceSuggestion(String name, String city, String district,
                                   double lat, double lng, String address, String uid) {}
}
