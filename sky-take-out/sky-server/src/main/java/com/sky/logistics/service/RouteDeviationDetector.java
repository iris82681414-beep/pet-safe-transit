package com.sky.logistics.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.logistics.entity.CargoRecord;
import com.sky.logistics.mapper.LogisticsCargoMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class RouteDeviationDetector {

    private static final double DEVIATION_THRESHOLD_KM = 3.0;
    private static final long DEVIATION_DURATION_SECONDS = 10 * 60;
    private static final String DEVIATION_START_KEY = "logistics:deviation:start:";
    private static final String GPS_LATEST_KEY = "logistics:vehicle:latest:";

    private final LogisticsCargoMapper cargoMapper;
    private final AlertService alertService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RouteDeviationDetector(LogisticsCargoMapper cargoMapper,
                                   AlertService alertService,
                                   StringRedisTemplate redisTemplate) {
        this.cargoMapper = cargoMapper;
        this.alertService = alertService;
        this.redisTemplate = redisTemplate;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void check() {
        try {
            List<CargoRecord> cargos = cargoMapper.findInTransitWithVehicle();
            if (cargos == null || cargos.isEmpty()) return;

            for (CargoRecord cargo : cargos) {
                if (cargo.getOriginLat() == null || cargo.getOriginLng() == null
                        || cargo.getDestinationLat() == null || cargo.getDestinationLng() == null) {
                    continue;
                }
                if (cargo.getVehiclePlate() == null) continue;

                // 从 Redis 获取车辆最新 GPS 位置
                String gpsKey = GPS_LATEST_KEY + cargo.getVehiclePlate();
                String gpsJson = redisTemplate.opsForValue().get(gpsKey);
                if (gpsJson == null) continue;

                double[] gps = parseLatLng(gpsJson);
                if (gps == null) continue;
                double lat = gps[0], lng = gps[1];

                // 计算 GPS 点到起止点连线的垂直距离
                double distanceKm = crossTrackDistance(
                        cargo.getOriginLat(), cargo.getOriginLng(),
                        cargo.getDestinationLat(), cargo.getDestinationLng(),
                        lat, lng);

                String deviationKey = DEVIATION_START_KEY + cargo.getVehiclePlate() + ":" + cargo.getCargoId();

                if (distanceKm > DEVIATION_THRESHOLD_KM) {
                    // 偏航：检查持续时间
                    String startStr = redisTemplate.opsForValue().get(deviationKey);
                    if (startStr == null) {
                        // 首次检测到偏航，记录开始时间
                        redisTemplate.opsForValue().set(deviationKey,
                                Instant.now().toString(), Duration.ofHours(1));
                        log.info("检测到偏航, plate={}, cargoId={}, distance={}km",
                                cargo.getVehiclePlate(), cargo.getCargoId(), String.format("%.1f", distanceKm));
                    } else {
                        // 已偏航一段时间，检查是否超过阈值
                        Instant startTime = Instant.parse(startStr);
                        long elapsed = Duration.between(startTime, Instant.now()).getSeconds();
                        if (elapsed >= DEVIATION_DURATION_SECONDS) {
                            alertService.createAlert(
                                    "ROUTE_DEVIATION", "WARNING",
                                    cargo.getVehiclePlate(), cargo.getCargoId(),
                                    "偏航告警",
                                    "偏离预设路线 " + String.format("%.1f", distanceKm) + "km",
                                    "车辆 " + cargo.getVehiclePlate() + " 偏离预设路线超过 " + String.format("%.1f", distanceKm) + "km，已持续 " + (elapsed / 60) + " 分钟。",
                                    lat, lng
                            );
                            // 清除偏差开始时间，避免重复告警（去重由 AlertService 保证）
                            redisTemplate.delete(deviationKey);
                            log.warn("偏航告警已触发, plate={}, distance={}km, duration={}min",
                                    cargo.getVehiclePlate(), String.format("%.1f", distanceKm), elapsed / 60);
                        }
                    }
                } else {
                    // 未偏航，清除偏差记录
                    if (Boolean.TRUE.equals(redisTemplate.hasKey(deviationKey))) {
                        redisTemplate.delete(deviationKey);
                        log.debug("车辆已回到路线, plate={}", cargo.getVehiclePlate());
                    }
                }
            }
        } catch (Exception e) {
            log.error("偏航检测异常: {}", e.getMessage(), e);
        }
    }

    private double[] parseLatLng(String gpsJson) {
        try {
            Map data = objectMapper.readValue(gpsJson, Map.class);
            Object latObj = data.get("lat");
            Object lngObj = data.get("lng");
            if (latObj == null || lngObj == null) return null;
            double lat = latObj instanceof Number ? ((Number) latObj).doubleValue() : Double.parseDouble(latObj.toString());
            double lng = lngObj instanceof Number ? ((Number) lngObj).doubleValue() : Double.parseDouble(lngObj.toString());
            return new double[]{lat, lng};
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 计算 GPS 点到起止点连线的垂直距离（Haversine 近似）
     * 返回单位：公里
     */
    private double crossTrackDistance(double lat1, double lng1, double lat2, double lng2,
                                       double lat3, double lng3) {
        double R = 6371.0; // 地球半径 km

        // 转换为弧度
        double rlat1 = Math.toRadians(lat1);
        double rlng1 = Math.toRadians(lng1);
        double rlat2 = Math.toRadians(lat2);
        double rlng2 = Math.toRadians(lng2);
        double rlat3 = Math.toRadians(lat3);
        double rlng3 = Math.toRadians(lng3);

        // 起点到 GPS 点的角距离
        double d13 = haversine(rlat1, rlng1, rlat3, rlng3);
        // 起点到 GPS 点的方位角
        double brng13 = bearing(rlat1, rlng1, rlat3, rlng3);
        // 起点到终点的方位角
        double brng12 = bearing(rlat1, rlng1, rlat2, rlng2);

        // 垂直交叉距离
        double dxt = Math.asin(Math.sin(d13) * Math.sin(brng13 - brng12)) * R;

        return Math.abs(dxt);
    }

    private double haversine(double lat1, double lng1, double lat2, double lng2) {
        double dlat = lat2 - lat1;
        double dlng = lng2 - lng1;
        double a = Math.sin(dlat / 2) * Math.sin(dlat / 2)
                + Math.cos(lat1) * Math.cos(lat2)
                * Math.sin(dlng / 2) * Math.sin(dlng / 2);
        return 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private double bearing(double lat1, double lng1, double lat2, double lng2) {
        double dlng = lng2 - lng1;
        double y = Math.sin(dlng) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2)
                - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dlng);
        return Math.atan2(y, x);
    }
}
