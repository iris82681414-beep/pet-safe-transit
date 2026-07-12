package com.sky.logistics.service.impl;

import com.sky.logistics.common.PageResponse;
import com.sky.logistics.controller.LogisticsWebSocketServer;
import com.sky.logistics.entity.Alert;
import com.sky.logistics.entity.AlertLog;
import com.sky.logistics.mapper.AlertMapper;
import com.sky.logistics.service.AlertService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class AlertServiceImpl implements AlertService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final AlertMapper alertMapper;
    private final StringRedisTemplate redisTemplate;

    public AlertServiceImpl(AlertMapper alertMapper, StringRedisTemplate redisTemplate) {
        this.alertMapper = alertMapper;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public PageResponse<Map<String, Object>> listAlerts(String severity, String type, String status,
                                                         String vehiclePlate, Integer page, Integer size) {
        int p = normalizePage(page);
        int s = normalizeSize(size);
        int offset = (p - 1) * s;

        Long total = alertMapper.count(severity, type, status, vehiclePlate);
        if (total == null || total == 0) {
            return new PageResponse<>(new ArrayList<>(), p, s, 0L, 0);
        }

        List<Alert> alerts = alertMapper.findPage(severity, type, status, vehiclePlate, offset, s);
        List<Map<String, Object>> content = new ArrayList<>();
        for (Alert alert : alerts) {
            content.add(toAlertSummary(alert));
        }

        int totalPages = (int) Math.ceil((double) total / s);
        return new PageResponse<>(content, p, s, total, totalPages);
    }

    @Override
    public Map<String, Object> getAlertDetail(String alertId) {
        Alert alert = alertMapper.findByAlertId(alertId);
        if (alert == null) {
            throw new IllegalArgumentException("告警不存在: " + alertId);
        }

        Map<String, Object> detail = toAlertDetail(alert);

        List<AlertLog> logs = alertMapper.findLogsByAlertId(alertId);
        List<Map<String, Object>> logList = new ArrayList<>();
        for (AlertLog log : logs) {
            Map<String, Object> logMap = new LinkedHashMap<>();
            logMap.put("time", log.getCreatedAt() != null ? log.getCreatedAt().toString() : null);
            logMap.put("operator", log.getOperatorName() != null ? log.getOperatorName() : (log.getOperatorId() != null ? log.getOperatorId() : "SYSTEM"));
            logMap.put("action", log.getAction());
            if (log.getRemark() != null) {
                logMap.put("remark", log.getRemark());
            }
            logList.add(logMap);
        }
        detail.put("logs", logList);

        return detail;
    }

    @Override
    @Transactional
    public Map<String, Object> createAlert(String alertType, String severity, String vehiclePlate,
                                            String cargoId, String title, String summary, String description,
                                            Double lat, Double lng) {
        // 去重：同一车辆+同一类型，未关闭前不重复生成
        String dedupKey = "logistics:alert:dedup:" + vehiclePlate + ":" + alertType;
        Boolean exists = redisTemplate.hasKey(dedupKey);
        if (Boolean.TRUE.equals(exists)) {
            log.debug("告警去重跳过, plate={}, type={}", vehiclePlate, alertType);
            return null;
        }

        String alertId = "ALT-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        Alert alert = new Alert();
        alert.setAlertId(alertId);
        alert.setAlertType(alertType);
        alert.setSeverity(severity);
        alert.setStatus("PENDING");
        alert.setVehiclePlate(vehiclePlate);
        alert.setCargoId(cargoId);
        alert.setTitle(title);
        alert.setSummary(summary);
        alert.setDescription(description);
        alert.setLat(lat);
        alert.setLng(lng);
        alert.setTriggeredAt(now);

        alertMapper.insert(alert);

        // 写告警日志
        AlertLog alertLog = new AlertLog();
        alertLog.setId("ALG-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
        alertLog.setAlertId(alertId);
        alertLog.setOperatorId("SYSTEM");
        alertLog.setOperatorName("SYSTEM");
        alertLog.setAction("CREATED");
        alertLog.setRemark("自动检测触发告警");
        alertLog.setCreatedAt(now);
        alertMapper.insertLog(alertLog);

        // 去重标记，TTL 24h，过期后同类型可重新告警
        redisTemplate.opsForValue().set(dedupKey, alertId, Duration.ofHours(24));

        log.info("告警已创建, alertId={}, type={}, plate={}", alertId, alertType, vehiclePlate);

        // WebSocket 实时推送
        Map<String, Object> pushData = new LinkedHashMap<>();
        pushData.put("alertId", alertId);
        pushData.put("alertType", alertType);
        pushData.put("severity", severity);
        pushData.put("vehiclePlate", vehiclePlate);
        pushData.put("title", title);
        pushData.put("triggeredAt", now.toString());
        LogisticsWebSocketServer.broadcast("alert.triggered", pushData);

        return toAlertDetail(alert);
    }

    private Map<String, Object> toAlertSummary(Alert alert) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("alertId", alert.getAlertId());
        map.put("alertType", alert.getAlertType());
        map.put("severity", alert.getSeverity());
        map.put("status", alert.getStatus());
        map.put("vehiclePlate", alert.getVehiclePlate());
        map.put("cargoId", alert.getCargoId());
        map.put("title", alert.getTitle());
        map.put("summary", alert.getSummary());
        map.put("triggeredAt", alert.getTriggeredAt() != null ? alert.getTriggeredAt().toString() : null);
        if (alert.getLat() != null && alert.getLng() != null) {
            Map<String, Double> location = new HashMap<>();
            location.put("lat", alert.getLat());
            location.put("lng", alert.getLng());
            map.put("location", location);
        }
        return map;
    }

    private Map<String, Object> toAlertDetail(Alert alert) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("alertId", alert.getAlertId());
        map.put("alertType", alert.getAlertType());
        map.put("severity", alert.getSeverity());
        map.put("status", alert.getStatus());
        map.put("vehiclePlate", alert.getVehiclePlate());
        map.put("cargoId", alert.getCargoId());
        map.put("title", alert.getTitle());
        map.put("description", alert.getDescription());
        map.put("triggeredAt", alert.getTriggeredAt() != null ? alert.getTriggeredAt().toString() : null);
        if (alert.getLat() != null && alert.getLng() != null) {
            Map<String, Double> location = new HashMap<>();
            location.put("lat", alert.getLat());
            location.put("lng", alert.getLng());
            map.put("location", location);
        }
        return map;
    }

    @Override
    @Transactional
    public Map<String, Object> acknowledgeAlert(String alertId, String remark) {
        Alert alert = alertMapper.findByAlertId(alertId);
        if (alert == null) {
            throw new IllegalArgumentException("告警不存在: " + alertId);
        }
        if (!"PENDING".equals(alert.getStatus())) {
            throw new IllegalArgumentException("只有 PENDING 状态的告警才能确认，当前状态: " + alert.getStatus());
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        alertMapper.updateStatus(alertId, "ACKNOWLEDGED", null, remark, null, now);

        AlertLog alertLog = new AlertLog();
        alertLog.setId("ALG-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
        alertLog.setAlertId(alertId);
        alertLog.setOperatorId("DISPATCHER");
        alertLog.setOperatorName("调度员");
        alertLog.setAction("ACKNOWLEDGED");
        alertLog.setRemark(remark);
        alertLog.setCreatedAt(now);
        alertMapper.insertLog(alertLog);

        log.info("告警已确认, alertId={}, remark={}", alertId, remark);

        return getAlertDetail(alertId);
    }

    @Override
    @Transactional
    public Map<String, Object> resolveAlert(String alertId, String resolution, String remark) {
        Alert alert = alertMapper.findByAlertId(alertId);
        if (alert == null) {
            throw new IllegalArgumentException("告警不存在: " + alertId);
        }
        if ("RESOLVED".equals(alert.getStatus())) {
            throw new IllegalArgumentException("告警已关闭，无需重复操作");
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        alertMapper.updateStatus(alertId, "RESOLVED", resolution, remark, now, null);

        // 删除去重标记，同车同类型可以重新生成告警
        String dedupKey = "logistics:alert:dedup:" + alert.getVehiclePlate() + ":" + alert.getAlertType();
        redisTemplate.delete(dedupKey);

        AlertLog alertLog = new AlertLog();
        alertLog.setId("ALG-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
        alertLog.setAlertId(alertId);
        alertLog.setOperatorId("DISPATCHER");
        alertLog.setOperatorName("调度员");
        alertLog.setAction("RESOLVED");
        alertLog.setRemark(remark);
        alertLog.setCreatedAt(now);
        alertMapper.insertLog(alertLog);

        log.info("告警已关闭, alertId={}, resolution={}, remark={}", alertId, resolution, remark);

        return getAlertDetail(alertId);
    }

    @Override
    public Map<String, Object> getAlertStats() {
        List<Map<String, Object>> byStatus = alertMapper.statsByStatus();
        List<Map<String, Object>> byType = alertMapper.statsByType();

        long pending = 0, acknowledged = 0, resolved = 0;
        for (Map<String, Object> row : byStatus) {
            String st = String.valueOf(row.get("status"));
            long cnt = ((Number) row.get("cnt")).longValue();
            if ("PENDING".equals(st)) pending = cnt;
            else if ("ACKNOWLEDGED".equals(st)) acknowledged = cnt;
            else if ("RESOLVED".equals(st)) resolved = cnt;
        }

        Map<String, Long> byTypeMap = new LinkedHashMap<>();
        byTypeMap.put("ROUTE_DEVIATION", 0L);
        byTypeMap.put("ABNORMAL_STOP", 0L);
        byTypeMap.put("CARGO_DOOR", 0L);
        byTypeMap.put("DEVICE_OFFLINE", 0L);
        for (Map<String, Object> row : byType) {
            String typ = String.valueOf(row.get("alertType"));
            long cnt = ((Number) row.get("cnt")).longValue();
            byTypeMap.put(typ, cnt);
        }

        Map<String, Object> stats = new LinkedHashMap<>();
        Map<String, Object> pendingStats = new LinkedHashMap<>();
        pendingStats.put("critical", 0);
        pendingStats.put("warning", pending);
        pendingStats.put("info", 0);
        stats.put("pending", pendingStats);
        stats.put("resolvedToday", resolved);
        stats.put("totalThisMonth", pending + acknowledged + resolved);
        stats.put("averageResolveTimeMinutes", 0);
        stats.put("byType", byTypeMap);
        return stats;
    }

    private int normalizePage(Integer page) {
        return page == null || page < 1 ? DEFAULT_PAGE : page;
    }

    private int normalizeSize(Integer size) {
        if (size == null || size < 1) return DEFAULT_SIZE;
        return Math.min(size, MAX_SIZE);
    }
}
