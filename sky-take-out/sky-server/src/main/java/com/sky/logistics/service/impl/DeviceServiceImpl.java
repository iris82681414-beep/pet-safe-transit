package com.sky.logistics.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.logistics.common.PageResponse;
import com.sky.logistics.entity.Alert;
import com.sky.logistics.entity.Vehicle;
import com.sky.logistics.mapper.AlertMapper;
import com.sky.logistics.mapper.LogisticsVehicleMapper;
import com.sky.logistics.service.DeviceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class DeviceServiceImpl implements DeviceService {

    private static final String HEARTBEAT_PREFIX = "logistics:device:heartbeat:";

    private final StringRedisTemplate redisTemplate;
    private final LogisticsVehicleMapper vehicleMapper;
    private final AlertMapper alertMapper;
    private final JdbcTemplate timescaleJdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DeviceServiceImpl(StringRedisTemplate redisTemplate,
                              LogisticsVehicleMapper vehicleMapper,
                              AlertMapper alertMapper,
                              @Qualifier("timescaleJdbcTemplate") JdbcTemplate timescaleJdbc) {
        this.redisTemplate = redisTemplate;
        this.vehicleMapper = vehicleMapper;
        this.alertMapper = alertMapper;
        this.timescaleJdbc = timescaleJdbc;
    }

    @Override
    public Map<String, Object> getDeviceStatus(String status, String keyword,
                                                Integer page, Integer size) {
        List<Vehicle> vehicles = vehicleMapper.findAll();
        List<Map<String, Object>> devices = new ArrayList<>();
        int onlineCount = 0;
        int offlineCount = 0;

        for (Vehicle v : vehicles) {
            String imei = v.getDeviceImei();
            if (imei == null || imei.isEmpty()) continue;

            String key = HEARTBEAT_PREFIX + imei;
            String heartbeatJson = redisTemplate.opsForValue().get(key);
            boolean online = heartbeatJson != null;

            if (keyword != null && !keyword.isEmpty()) {
                if (!imei.contains(keyword)
                        && (v.getPlate() == null || !v.getPlate().contains(keyword))) {
                    continue;
                }
            }

            String deviceStatus = online ? "ONLINE" : "OFFLINE";
            if (status != null && !status.isEmpty() && !status.equals(deviceStatus)) {
                continue;
            }

            if (online) onlineCount++; else offlineCount++;

            Map<String, Object> device = new LinkedHashMap<>();
            device.put("imei", imei);
            device.put("plate", v.getPlate());
            device.put("status", deviceStatus);

            if (online) {
                try {
                    Map data = objectMapper.readValue(heartbeatJson, Map.class);
                    device.put("lastHeartbeat", data.get("lastHeartbeat"));
                } catch (Exception ignored) {}
            }
            devices.add(device);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("devices", devices);
        result.put("onlineCount", onlineCount);
        result.put("offlineCount", offlineCount);
        result.put("total", onlineCount + offlineCount);
        return result;
    }

    @Override
    public Map<String, Object> getDeviceDetail(String imei) {
        String key = HEARTBEAT_PREFIX + imei;
        String heartbeatJson = redisTemplate.opsForValue().get(key);

        Vehicle vehicle = vehicleMapper.findByImei(imei);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("imei", imei);
        detail.put("plate", vehicle != null ? vehicle.getPlate() : null);
        detail.put("status", heartbeatJson != null ? "ONLINE" : "OFFLINE");

        if (heartbeatJson != null) {
            try {
                Map data = objectMapper.readValue(heartbeatJson, Map.class);
                detail.put("lastHeartbeat", data.get("lastHeartbeat"));
            } catch (Exception ignored) {}
        }
        return detail;
    }

    @Override
    public List<Map<String, Object>> getDeviceHeartbeats(String imei) {
        List<Map<String, Object>> rows = timescaleJdbc.queryForList(
            "SELECT time, imei, plate, battery, signal, gnss_satellites AS gnssSatellites, temp " +
            "FROM device_heartbeats WHERE imei = ? ORDER BY time DESC LIMIT 100", imei);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("time", row.get("time") != null ? row.get("time").toString() : null);
            item.put("imei", row.get("imei"));
            item.put("plate", row.get("plate"));
            item.put("battery", row.get("battery"));
            item.put("signal", row.get("signal"));
            item.put("gnssSatellites", row.get("gnsssatellites") != null ? row.get("gnsssatellites") : row.get("gnssSatellites"));
            item.put("temp", row.get("temp"));
            result.add(item);
        }
        return result;
    }

    @Override
    public PageResponse<Map<String, Object>> getDeviceCargoEvents(String imei, Integer page, Integer size) {
        Vehicle vehicle = vehicleMapper.findByImei(imei);
        if (vehicle == null) {
            return PageResponse.of(new ArrayList<>(), page != null && page > 0 ? page : 1,
                                   size != null && size > 0 ? size : 20);
        }

        int p = page != null && page > 0 ? page : 1;
        int s = size != null && size > 0 ? Math.min(size, 100) : 20;
        int offset = (p - 1) * s;

        List<Alert> alerts = alertMapper.findPage(null, "CARGO_DOOR", null, vehicle.getPlate(), offset, s);
        List<Map<String, Object>> content = new ArrayList<>();
        for (Alert alert : alerts) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("alertId", alert.getAlertId());
            item.put("eventType", alert.getAlertType());
            item.put("severity", alert.getSeverity());
            item.put("status", alert.getStatus());
            item.put("title", alert.getTitle());
            item.put("triggeredAt", alert.getTriggeredAt() != null ? alert.getTriggeredAt().toString() : null);
            content.add(item);
        }
        return PageResponse.of(content, p, s);
    }
}
