package com.sky.logistics.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.logistics.entity.Vehicle;
import com.sky.logistics.mapper.LogisticsVehicleMapper;
import com.sky.logistics.service.VehicleStatusService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 车辆实时状态查询 —— 供后端3 告警引擎调用
 *
 * 告警用例：
 * - 偏航检测：拿当前位置 + 预设路线对比
 * - 异常停车：速度 < 5 且停留时间 > 阈值
 * - 设备离线：最后更新时间 > 阈值
 */
@Service
@Slf4j
public class VehicleStatusServiceImpl implements VehicleStatusService {

    private final StringRedisTemplate redisTemplate;
    private final JdbcTemplate timescaleJdbc;
    private final LogisticsVehicleMapper vehicleMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String REDIS_LATEST_PREFIX = "logistics:vehicle:latest:";
    private static final int STOP_SPEED_THRESHOLD = 5; // km/h

    public VehicleStatusServiceImpl(
            StringRedisTemplate redisTemplate,
            @Qualifier("timescaleJdbcTemplate") JdbcTemplate timescaleJdbc,
            LogisticsVehicleMapper vehicleMapper) {
        this.redisTemplate = redisTemplate;
        this.timescaleJdbc = timescaleJdbc;
        this.vehicleMapper = vehicleMapper;
    }

    @Override
    public Map<String, Object> getCurrentStatus(String plate) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("plate", plate);

        // 1. 从 Redis 读最新位置
        String json = redisTemplate.opsForValue().get(REDIS_LATEST_PREFIX + plate);
        if (json != null) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> pos = objectMapper.readValue(json, LinkedHashMap.class);
                result.put("lat", pos.get("lat"));
                result.put("lng", pos.get("lng"));
                result.put("speed", pos.getOrDefault("speed", 0));
                result.put("heading", pos.getOrDefault("heading", 0));
                result.put("accuracy", pos.getOrDefault("accuracy", 0));
                result.put("updatedAt", pos.get("updatedAt"));
                result.put("source", "REDIS");

                // 计算停留时长
                int speed = toInt(pos.get("speed"), 0);
                if (speed < STOP_SPEED_THRESHOLD && pos.get("updatedAt") != null) {
                    result.put("isStopped", true);
                    result.put("stopDurationSeconds", calcStopDuration(String.valueOf(pos.get("updatedAt"))));
                } else {
                    result.put("isStopped", false);
                    result.put("stopDurationSeconds", 0);
                }
                return result;
            } catch (Exception e) {
                log.error("解析 Redis 位置数据失败: {}", e.getMessage());
            }
        }

        // 2. Redis 不可用时回退到 TimescaleDB
        log.debug("Redis 无数据，回退到 TimescaleDB 查询 plate={}", plate);
        Vehicle vehicle = vehicleMapper.findByPlate(plate);
        if (vehicle == null) {
            result.put("status", "UNKNOWN_VEHICLE");
            return result;
        }

        List<Map<String, Object>> points = timescaleJdbc.queryForList(
            "SELECT time, lat, lng, speed, heading, accuracy " +
            "FROM gps_points WHERE vehicle_id = ? " +
            "ORDER BY time DESC LIMIT 1",
            vehicle.getId()
        );

        if (!points.isEmpty()) {
            Map<String, Object> latest = points.get(0);
            result.put("lat", latest.get("lat"));
            result.put("lng", latest.get("lng"));
            result.put("speed", latest.getOrDefault("speed", 0));
            result.put("heading", latest.getOrDefault("heading", 0));
            result.put("accuracy", latest.getOrDefault("accuracy", 0));
            result.put("updatedAt", String.valueOf(latest.get("time")));
            result.put("source", "TIMESCALEDB_FALLBACK");
        } else {
            result.put("status", "NO_GPS");
        }

        return result;
    }

    @Override
    public int getCurrentSpeed(String plate) {
        String json = redisTemplate.opsForValue().get(REDIS_LATEST_PREFIX + plate);
        if (json != null) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> pos = objectMapper.readValue(json, LinkedHashMap.class);
                return toInt(pos.get("speed"), 0);
            } catch (Exception e) {
                log.error("解析速度失败: {}", e.getMessage());
            }
        }
        return 0;
    }

    @Override
    public long getStopDurationSeconds(String plate) {
        String json = redisTemplate.opsForValue().get(REDIS_LATEST_PREFIX + plate);
        if (json == null) {
            return -1; // 无数据
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> pos = objectMapper.readValue(json, LinkedHashMap.class);
            int speed = toInt(pos.get("speed"), 0);

            if (speed >= STOP_SPEED_THRESHOLD) {
                return 0; // 行驶中，未停留
            }

            Object updatedAt = pos.get("updatedAt");
            if (updatedAt == null) {
                return -1;
            }

            return calcStopDuration(String.valueOf(updatedAt));
        } catch (Exception e) {
            log.error("计算停留时长失败: {}", e.getMessage());
            return -1;
        }
    }

    @Override
    public String getLastUpdateTime(String plate) {
        String json = redisTemplate.opsForValue().get(REDIS_LATEST_PREFIX + plate);
        if (json != null) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> pos = objectMapper.readValue(json, LinkedHashMap.class);
                Object updatedAt = pos.get("updatedAt");
                return updatedAt != null ? String.valueOf(updatedAt) : null;
            } catch (Exception e) {
                log.error("解析更新时间失败: {}", e.getMessage());
            }
        }
        return null;
    }

    // ─── 工具方法 ──────────────────────────────────────

    private long calcStopDuration(String updatedAtStr) {
        try {
            Instant updatedAt = Instant.parse(updatedAtStr);
            return Duration.between(updatedAt, Instant.now()).getSeconds();
        } catch (Exception e) {
            return -1;
        }
    }

    private int toInt(Object value, int defaultVal) {
        if (value instanceof Number) return ((Number) value).intValue();
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return defaultVal;
            }
        }
        return defaultVal;
    }
}
