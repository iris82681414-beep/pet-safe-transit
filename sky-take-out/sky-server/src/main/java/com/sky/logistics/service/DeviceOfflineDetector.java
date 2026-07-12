package com.sky.logistics.service;

import com.sky.logistics.entity.Vehicle;
import com.sky.logistics.mapper.LogisticsVehicleMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class DeviceOfflineDetector {

    private static final String HEARTBEAT_KEY_PREFIX = "logistics:device:heartbeat:";

    private final LogisticsVehicleMapper vehicleMapper;
    private final AlertService alertService;
    private final StringRedisTemplate redisTemplate;

    public DeviceOfflineDetector(LogisticsVehicleMapper vehicleMapper,
                                  AlertService alertService,
                                  StringRedisTemplate redisTemplate) {
        this.vehicleMapper = vehicleMapper;
        this.alertService = alertService;
        this.redisTemplate = redisTemplate;
    }

    @Scheduled(fixedDelay = 30_000, initialDelay = 10_000)
    public void check() {
        try {
            List<Vehicle> vehicles = vehicleMapper.findAll();
            if (vehicles == null || vehicles.isEmpty()) return;

            for (Vehicle vehicle : vehicles) {
                String imei = vehicle.getDeviceImei();
                if (imei == null || imei.isEmpty()) continue;

                String key = HEARTBEAT_KEY_PREFIX + imei;
                Boolean online = redisTemplate.hasKey(key);

                if (Boolean.TRUE.equals(online)) {
                    if (!"ONLINE".equals(vehicle.getDeviceStatus())) {
                        vehicleMapper.updateDeviceStatus(vehicle.getId(), "ONLINE");
                        log.info("设备恢复在线, imei={}, plate={}", imei, vehicle.getPlate());
                    }
                } else {
                    if (!"OFFLINE".equals(vehicle.getDeviceStatus())) {
                        vehicleMapper.updateDeviceStatus(vehicle.getId(), "OFFLINE");
                    }
                    alertService.createAlert(
                            "DEVICE_OFFLINE", "CRITICAL",
                            vehicle.getPlate(), null,
                            "设备离线告警",
                            "设备 " + imei + " 心跳超时，已离线",
                            "车辆 " + vehicle.getPlate() + " 的设备 " + imei + " 超过 90 秒未上报心跳，判定为离线。",
                            null, null
                    );
                }
            }
        } catch (Exception e) {
            log.error("离线检测异常: {}", e.getMessage(), e);
        }
    }
}
