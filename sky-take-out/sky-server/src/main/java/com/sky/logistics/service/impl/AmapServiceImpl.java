package com.sky.logistics.service.impl;

import com.sky.logistics.config.AmapProperties;
import com.sky.logistics.dto.GeoPointDTO;
import com.sky.logistics.dto.GpsPointDTO;
import com.sky.logistics.dto.RoutePlanRequestDTO;
import com.sky.logistics.dto.TruckInfoDTO;
import com.sky.logistics.service.AmapService;
import com.sky.logistics.vo.AmapAddressVO;
import com.sky.logistics.vo.AmapInputTipVO;
import com.sky.logistics.vo.RoutePlanVO;
import com.sky.logistics.vo.RouteStepVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class AmapServiceImpl implements AmapService {

    private static final double DEFAULT_SPEED_KMH = 60.0;
    private static final double ROAD_FACTOR = 1.22;

    private final AmapProperties properties;
    private final RestTemplate restTemplate;

    public AmapServiceImpl(AmapProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeoutMs());
        factory.setReadTimeout(properties.getReadTimeoutMs());
        this.restTemplate = new RestTemplate(factory);
    }

    @Override
    public List<AmapInputTipVO> inputTips(String keywords, String city) {
        String safeKeywords = trimToNull(keywords);
        if (!StringUtils.hasText(safeKeywords)) {
            throw new IllegalArgumentException("搜索关键词不能为空");
        }
        if (!hasWebKey()) {
            return fallbackTips(safeKeywords, city);
        }

        Map<String, Object> body = get("/v3/assistant/inputtips",
                "keywords", safeKeywords,
                "city", trimToNull(city),
                "datatype", "all");
        if (!isOk(body)) {
            log.warn("高德输入提示失败: {}", amapFailureMessage(body));
            return fallbackTips(safeKeywords, city);
        }

        List<Map<String, Object>> tips = list(body.get("tips"));
        List<AmapInputTipVO> result = new ArrayList<>();
        for (Map<String, Object> tip : tips) {
            String location = stringValue(tip.get("location"));
            GeoPointDTO point = parseFirstPoint(location);
            result.add(AmapInputTipVO.builder()
                    .name(stringValue(tip.get("name")))
                    .district(stringValue(tip.get("district")))
                    .address(stringValue(tip.get("address")))
                    .lng(point == null ? null : point.getLng())
                    .lat(point == null ? null : point.getLat())
                    .adcode(stringValue(tip.get("adcode")))
                    .build());
        }
        return result;
    }

    @Override
    public AmapAddressVO geocode(String address, String city) {
        String safeAddress = trimToNull(address);
        if (!StringUtils.hasText(safeAddress)) {
            throw new IllegalArgumentException("地址不能为空");
        }
        if (!hasWebKey()) {
            return fallbackAddress(safeAddress, city, null, null, "LOCAL_FALLBACK");
        }

        Map<String, Object> body = get("/v3/geocode/geo",
                "address", safeAddress,
                "city", trimToNull(city));
        if (!isOk(body)) {
            log.warn("高德地理编码失败: {}", amapFailureMessage(body));
            return fallbackAddress(safeAddress, city, null, null, "LOCAL_FALLBACK");
        }

        List<Map<String, Object>> geocodes = list(body.get("geocodes"));
        if (geocodes.isEmpty()) {
            return fallbackAddress(safeAddress, city, null, null, "LOCAL_FALLBACK");
        }
        Map<String, Object> first = geocodes.get(0);
        GeoPointDTO point = parseFirstPoint(stringValue(first.get("location")));
        return AmapAddressVO.builder()
                .formattedAddress(stringValue(first.get("formatted_address")))
                .province(stringValue(first.get("province")))
                .city(stringValue(first.get("city")))
                .district(stringValue(first.get("district")))
                .lng(point == null ? null : point.getLng())
                .lat(point == null ? null : point.getLat())
                .level(stringValue(first.get("level")))
                .adcode(stringValue(first.get("adcode")))
                .source("AMAP")
                .build();
    }

    @Override
    public AmapAddressVO regeo(Double lng, Double lat) {
        validatePoint(lng, lat, "经纬度不能为空");
        if (!hasWebKey()) {
            return fallbackAddress(formatPoint(lng, lat), null, lng, lat, "LOCAL_FALLBACK");
        }

        Map<String, Object> body = get("/v3/geocode/regeo",
                "location", formatPoint(lng, lat),
                "extensions", "base",
                "radius", "1000");
        if (!isOk(body)) {
            log.warn("高德逆地理编码失败: {}", amapFailureMessage(body));
            return fallbackAddress(formatPoint(lng, lat), null, lng, lat, "LOCAL_FALLBACK");
        }

        Map<String, Object> regeocode = map(body.get("regeocode"));
        Map<String, Object> component = map(regeocode.get("addressComponent"));
        Map<String, Object> street = map(component.get("streetNumber"));
        return AmapAddressVO.builder()
                .formattedAddress(stringValue(regeocode.get("formatted_address")))
                .province(stringValue(component.get("province")))
                .city(firstText(component.get("city"), component.get("province")))
                .district(stringValue(component.get("district")))
                .road(stringValue(street.get("street")))
                .poiName(stringValue(street.get("number")))
                .lng(lng)
                .lat(lat)
                .adcode(stringValue(component.get("adcode")))
                .source("AMAP")
                .build();
    }

    @Override
    public RoutePlanVO planDrivingRoute(RoutePlanRequestDTO request) {
        validateRouteRequest(request);
        if (!hasWebKey()) {
            return fallbackRoute(request, "DRIVING", "LOCAL_FALLBACK");
        }

        Map<String, Object> body = get("/v3/direction/driving",
                "origin", formatPoint(request.getOrigin()),
                "destination", formatPoint(request.getDestination()),
                "waypoints", formatWaypoints(request.getWaypoints()),
                "strategy", strategyCode(request.getStrategy()),
                "extensions", "all");
        RoutePlanVO route = parseDrivingRoute(body, "DRIVING");
        if (route == null) {
            log.warn("高德驾车规划失败: {}", amapFailureMessage(body));
            return fallbackRoute(request, "DRIVING", "LOCAL_FALLBACK");
        }
        return route;
    }

    @Override
    public RoutePlanVO planTruckRoute(RoutePlanRequestDTO request) {
        validateRouteRequest(request);
        TruckInfoDTO truck = request.getTruck() != null ? request.getTruck() : request.getVehicle();
        if (!hasWebKey()) {
            return fallbackRoute(request, "TRUCK", "LOCAL_FALLBACK");
        }

        try {
            Map<String, Object> body = get("/v4/direction/truck",
                    "origin", formatPoint(request.getOrigin()),
                    "destination", formatPoint(request.getDestination()),
                    "waypoints", formatWaypoints(request.getWaypoints()),
                    "strategy", "5",
                    "size", truckSizeCode(truck),
                    "height", decimalText(truck == null ? null : truck.getHeightMeters()),
                    "width", decimalText(truck == null ? null : truck.getWidthMeters()),
                    "load", decimalText(truck == null ? null : truck.getLoadWeightTons()),
                    "weight", decimalText(truck == null ? null : truck.getTotalWeightTons()),
                    "axis", truck == null || truck.getAxis() == null ? null : String.valueOf(truck.getAxis()));
            RoutePlanVO route = parseDrivingRoute(body, "TRUCK");
            if (route != null) {
                route.setRestrictionWarnings(Arrays.asList("已按货车参数请求高德路线", "如高德未返回限行信息，则按普通路网兜底"));
                return route;
            }
            log.warn("高德货车规划失败，降级为驾车规划: {}", amapFailureMessage(body));
        } catch (Exception e) {
            log.warn("高德货车规划异常，降级为驾车规划: {}", e.getMessage());
        }
        RoutePlanVO fallback = planDrivingRoute(request);
        fallback.setRouteType("TRUCK");
        fallback.setRestrictionWarnings(Arrays.asList("货车规划暂不可用，已使用驾车路线兜底"));
        return fallback;
    }

    @Override
    public List<GeoPointDTO> correctTrajectory(List<GpsPointDTO> points) {
        if (points == null || points.isEmpty()) {
            return Collections.emptyList();
        }
        // 高德轨迹纠偏接口对点格式、配额和服务权限要求更高；先保持统一出口，
        // API 未配置或调用失败时返回过滤后的原始点，前端和业务逻辑不受影响。
        List<GeoPointDTO> corrected = new ArrayList<>();
        for (GpsPointDTO point : points) {
            if (point != null && point.getLng() != null && point.getLat() != null) {
                corrected.add(GeoPointDTO.builder().lng(point.getLng()).lat(point.getLat()).build());
            }
        }
        return corrected;
    }

    private RoutePlanVO parseDrivingRoute(Map<String, Object> body, String routeType) {
        if (!isOk(body)) {
            return null;
        }
        Map<String, Object> route = map(body.get("route"));
        List<Map<String, Object>> paths = list(route.get("paths"));
        if (paths.isEmpty()) {
            return null;
        }
        Map<String, Object> path = paths.get(0);
        long distanceMeters = longValue(path.get("distance"));
        long durationSeconds = longValue(path.get("duration"));
        BigDecimal tolls = decimalValue(firstText(path.get("tolls"), path.get("toll_cost")));
        Integer trafficLights = intValue(firstText(path.get("traffic_lights"), path.get("trafficLights")));

        List<RouteStepVO> steps = new ArrayList<>();
        List<GeoPointDTO> polyline = new ArrayList<>();
        for (Map<String, Object> step : list(path.get("steps"))) {
            long stepDistance = longValue(step.get("distance"));
            long stepDuration = longValue(step.get("duration"));
            String stepPolyline = stringValue(step.get("polyline"));
            polyline.addAll(parsePolyline(stepPolyline));
            steps.add(RouteStepVO.builder()
                    .instruction(stringValue(step.get("instruction")))
                    .roadName(stringValue(step.get("road")))
                    .distanceMeters(stepDistance)
                    .durationSeconds(stepDuration)
                    .distanceKm(km(stepDistance))
                    .durationMinutes(minutes(stepDuration))
                    .build());
        }

        return RoutePlanVO.builder()
                .routeId("ROUTE-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase())
                .routeType(routeType)
                .distanceMeters(distanceMeters)
                .durationSeconds(durationSeconds)
                .distanceKm(km(distanceMeters))
                .durationMinutes(minutes(durationSeconds))
                .tolls(tolls)
                .tollCost(tolls)
                .trafficLights(trafficLights)
                .restriction("NO_RESTRICTION")
                .restrictionWarnings(Collections.<String>emptyList())
                .polyline(deduplicatePolyline(polyline))
                .steps(steps)
                .source("AMAP")
                .build();
    }

    private RoutePlanVO fallbackRoute(RoutePlanRequestDTO request, String routeType, String source) {
        List<GeoPointDTO> line = new ArrayList<>();
        line.add(request.getOrigin());
        if (request.getWaypoints() != null) {
            for (GeoPointDTO waypoint : request.getWaypoints()) {
                if (isValidPoint(waypoint)) {
                    line.add(waypoint);
                }
            }
        }
        line.add(request.getDestination());

        double distanceKm = 0.0;
        for (int i = 1; i < line.size(); i++) {
            distanceKm += haversineKm(line.get(i - 1), line.get(i)) * ROAD_FACTOR;
        }
        long distanceMeters = Math.round(distanceKm * 1000);
        long durationSeconds = Math.round(distanceKm / DEFAULT_SPEED_KMH * 3600);

        RouteStepVO step = RouteStepVO.builder()
                .instruction("本地兜底路线，等待高德 Web 服务 Key 配置后返回真实路网")
                .roadName("LOCAL")
                .distanceMeters(distanceMeters)
                .durationSeconds(durationSeconds)
                .distanceKm(km(distanceMeters))
                .durationMinutes(minutes(durationSeconds))
                .build();

        return RoutePlanVO.builder()
                .routeId("ROUTE-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase())
                .routeType(routeType)
                .distanceMeters(distanceMeters)
                .durationSeconds(durationSeconds)
                .distanceKm(km(distanceMeters))
                .durationMinutes(minutes(durationSeconds))
                .tolls(BigDecimal.ZERO)
                .tollCost(BigDecimal.ZERO)
                .trafficLights(0)
                .restriction("UNKNOWN")
                .restrictionWarnings("TRUCK".equals(routeType) ? Arrays.asList("货车限行信息需要高德货车规划服务") : Collections.<String>emptyList())
                .polyline(line)
                .steps(Collections.singletonList(step))
                .source(source)
                .build();
    }

    private Map<String, Object> get(String path, String... kv) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(properties.getBaseUrl() + path)
                .queryParam("key", properties.getWebKey())
                .queryParam("output", "JSON");
        for (int i = 0; i + 1 < kv.length; i += 2) {
            if (StringUtils.hasText(kv[i + 1])) {
                builder.queryParam(kv[i], kv[i + 1]);
            }
        }
        ResponseEntity<Map> response = restTemplate.getForEntity(builder.build().encode().toUri(), Map.class);
        return response.getBody() == null ? new LinkedHashMap<String, Object>() : response.getBody();
    }

    private boolean hasWebKey() {
        return StringUtils.hasText(properties.getWebKey());
    }

    private boolean isOk(Map<String, Object> body) {
        return body != null && "1".equals(String.valueOf(body.get("status")));
    }

    private String amapFailureMessage(Map<String, Object> body) {
        if (body == null || body.isEmpty()) {
            return "empty response";
        }
        String status = stringValue(body.get("status"));
        String info = stringValue(body.get("info"));
        String infocode = stringValue(body.get("infocode"));
        return "status=" + (status == null ? "unknown" : status)
                + ", infocode=" + (infocode == null ? "unknown" : infocode)
                + ", info=" + (info == null ? "unknown" : info);
    }

    private void validateRouteRequest(RoutePlanRequestDTO request) {
        if (request == null) {
            throw new IllegalArgumentException("路线规划参数不能为空");
        }
        if (!isValidPoint(request.getOrigin()) || !isValidPoint(request.getDestination())) {
            throw new IllegalArgumentException("路线起点和终点经纬度不能为空");
        }
    }

    private boolean isValidPoint(GeoPointDTO point) {
        return point != null && point.getLng() != null && point.getLat() != null;
    }

    private void validatePoint(Double lng, Double lat, String message) {
        if (lng == null || lat == null) {
            throw new IllegalArgumentException(message);
        }
    }

    private String formatPoint(GeoPointDTO point) {
        return formatPoint(point.getLng(), point.getLat());
    }

    private String formatPoint(Double lng, Double lat) {
        return trimNumber(lng) + "," + trimNumber(lat);
    }

    private String formatWaypoints(List<GeoPointDTO> points) {
        if (points == null || points.isEmpty()) {
            return null;
        }
        List<String> values = new ArrayList<>();
        for (GeoPointDTO point : points) {
            if (isValidPoint(point)) {
                values.add(formatPoint(point));
            }
        }
        return values.isEmpty() ? null : StringUtils.collectionToDelimitedString(values, ";");
    }

    private String trimNumber(Double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private String decimalText(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    private String strategyCode(String strategy) {
        if ("SHORTEST".equalsIgnoreCase(strategy)) return "2";
        if ("AVOID_CONGESTION".equalsIgnoreCase(strategy)) return "4";
        if ("FASTEST".equalsIgnoreCase(strategy)) return "0";
        return "10";
    }

    private String truckSizeCode(TruckInfoDTO truck) {
        if (truck == null || !StringUtils.hasText(truck.getSize())) {
            return "2";
        }
        String size = truck.getSize().toUpperCase();
        if ("MINI".equals(size) || "SMALL".equals(size)) return "1";
        if ("LARGE".equals(size)) return "3";
        return "2";
    }

    private GeoPointDTO parseFirstPoint(String location) {
        if (!StringUtils.hasText(location) || location.contains("," + "[]")) {
            return null;
        }
        String first = location.split(";")[0];
        String[] pair = first.split(",");
        if (pair.length != 2) {
            return null;
        }
        try {
            return GeoPointDTO.builder()
                    .lng(Double.parseDouble(pair[0]))
                    .lat(Double.parseDouble(pair[1]))
                    .build();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private List<GeoPointDTO> parsePolyline(String polylineText) {
        if (!StringUtils.hasText(polylineText)) {
            return Collections.emptyList();
        }
        List<GeoPointDTO> result = new ArrayList<>();
        String[] points = polylineText.split(";");
        for (String point : points) {
            GeoPointDTO parsed = parseFirstPoint(point);
            if (parsed != null) {
                result.add(parsed);
            }
        }
        return result;
    }

    private List<GeoPointDTO> deduplicatePolyline(List<GeoPointDTO> points) {
        if (points == null || points.size() < 2) {
            return points == null ? Collections.<GeoPointDTO>emptyList() : points;
        }
        List<GeoPointDTO> result = new ArrayList<>();
        String last = null;
        for (GeoPointDTO point : points) {
            String current = trimNumber(point.getLng()) + "," + trimNumber(point.getLat());
            if (!current.equals(last)) {
                result.add(point);
            }
            last = current;
        }
        return result;
    }

    private List<AmapInputTipVO> fallbackTips(String keywords, String city) {
        AmapInputTipVO tip = AmapInputTipVO.builder()
                .name(keywords)
                .district(StringUtils.hasText(city) ? city : "待高德解析")
                .address(keywords)
                .lng(null)
                .lat(null)
                .build();
        return Collections.singletonList(tip);
    }

    private AmapAddressVO fallbackAddress(String address, String city, Double lng, Double lat, String source) {
        return AmapAddressVO.builder()
                .formattedAddress(address)
                .city(city)
                .lng(lng)
                .lat(lat)
                .level("LOCAL_FALLBACK")
                .source(source)
                .build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : new LinkedHashMap<String, Object>();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> list(Object value) {
        return value instanceof List ? (List<Map<String, Object>>) value : Collections.<Map<String, Object>>emptyList();
    }

    private String stringValue(Object value) {
        if (value == null) return null;
        if (value instanceof List || value instanceof Map) return null;
        String text = String.valueOf(value);
        return StringUtils.hasText(text) ? text : null;
    }

    private String firstText(Object first, Object second) {
        String one = stringValue(first);
        return StringUtils.hasText(one) ? one : stringValue(second);
    }

    private long longValue(Object value) {
        if (value instanceof Number) return ((Number) value).longValue();
        if (value == null) return 0L;
        try {
            return new BigDecimal(String.valueOf(value)).longValue();
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private Integer intValue(Object value) {
        if (value instanceof Number) return ((Number) value).intValue();
        if (value == null) return 0;
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private BigDecimal decimalValue(Object value) {
        if (value == null) return BigDecimal.ZERO;
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private Double km(long meters) {
        return BigDecimal.valueOf(meters)
                .divide(BigDecimal.valueOf(1000), 2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private Long minutes(long seconds) {
        return Math.max(0L, Math.round(seconds / 60.0));
    }

    private double haversineKm(GeoPointDTO a, GeoPointDTO b) {
        final double r = 6371.0;
        double dLat = Math.toRadians(b.getLat() - a.getLat());
        double dLng = Math.toRadians(b.getLng() - a.getLng());
        double s = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(a.getLat())) * Math.cos(Math.toRadians(b.getLat()))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return r * 2 * Math.atan2(Math.sqrt(s), Math.sqrt(1 - s));
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
