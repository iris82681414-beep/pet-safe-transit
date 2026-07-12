package com.sky.logistics.service.impl;

import com.sky.logistics.common.PageResponse;
import com.sky.logistics.dto.LoginRequest;
import com.sky.logistics.service.LogisticsStarterService;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class LogisticsStarterServiceImpl implements LogisticsStarterService {

    private final List<Map<String, Object>> users = new ArrayList<>();
    private final List<Map<String, Object>> vehicles = new ArrayList<>();
    private final List<Map<String, Object>> cargo = new ArrayList<>();
    private final List<Map<String, Object>> alerts = new ArrayList<>();
    private final List<Map<String, Object>> commands = new ArrayList<>();

    @PostConstruct
    public void init() {
        users.add(user("USR-001", "shipper", "李货主", "SHIPPER", "13800000001"));
        users.add(user("USR-002", "dispatcher", "王调度", "DISPATCHER", "13800000002"));
        users.add(user("USR-003", "warehouse", "赵仓管", "WAREHOUSE", "13800000003"));
        users.add(user("USR-004", "admin", "系统管理员", "ADMIN", "13800000004"));
        users.add(user("USR-005", "driver", "张司机", "DRIVER", "13800000005"));

        Map<String, Object> position = map(
                "lat", 30.4219,
                "lng", 120.5738,
                "speed", 68,
                "heading", 225
        );
        vehicles.add(map(
                "plate", "沪A·C0291",
                "vinTopic", "沪A-C0291",
                "vehicleType", "厢式货车",
                "capacity", 5000,
                "driverName", "张建国",
                "driverPhone", "13800000001",
                "deviceImei", "861234567890123",
                "deviceStatus", "ONLINE",
                "status", "MOVING",
                "cargoId", "SH-HZ-20260629-0291",
                "position", position,
                "locationDesc", "G320 国道海宁段",
                "updatedAt", now()
        ));

        cargo.add(map(
                "cargoId", "SH-HZ-20260629-0291",
                "cargoType", "电子产品",
                "weight", new BigDecimal("2500"),
                "vehiclePlate", "沪A·C0291",
                "driverName", "张建国",
                "driverPhone", "13800000001",
                "status", "IN_TRANSIT",
                "origin", point("上海仓储中心", 31.2304, 121.4737),
                "destination", point("杭州余杭物流中心", 30.2741, 120.1551),
                "loadedAt", now(),
                "eta", now(),
                "progress", 0.68,
                "distanceTotal", 190,
                "distanceRemaining", 62
        ));

        alerts.add(map(
                "alertId", "ALT-20260629-001",
                "alertType", "ROUTE_DEVIATION",
                "severity", "WARNING",
                "status", "PENDING",
                "vehiclePlate", "沪A·C0291",
                "cargoId", "SH-HZ-20260629-0291",
                "title", "偏航告警",
                "summary", "偏离 G60 沪昆高速预设路线 3.2km",
                "triggeredAt", now(),
                "location", position
        ));
    }

    @Override
    public Map<String, Object> login(LoginRequest request) {
        Map<String, Object> user = findUser(request == null ? null : request.getUsername());
        if (user == null || request == null || !"123456".equals(request.getPassword())) {
            return null;
        }
        String username = String.valueOf(user.get("username"));
        return map(
                "accessToken", "starter-token-" + username,
                "refreshToken", "starter-refresh-" + username,
                "expiresIn", 7200,
                "user", publicUser(user)
        );
    }

    @Override
    public Map<String, Object> currentUser(String authorization) {
        String username = "dispatcher";
        if (authorization != null && authorization.contains("starter-token-")) {
            username = authorization.substring(authorization.indexOf("starter-token-") + "starter-token-".length());
        }
        Map<String, Object> user = findUser(username);
        return publicUser(user == null ? findUser("dispatcher") : user);
    }

    @Override
    public PageResponse<Map<String, Object>> vehicles(String status, String keyword, Integer page, Integer size) {
        return PageResponse.of(vehicles, page, size);
    }

    @Override
    public Map<String, Object> vehicleDetail(String plate) {
        Map<String, Object> vehicle = findBy(vehicles, "plate", plate);
        return vehicle == null ? new LinkedHashMap<String, Object>() : vehicle;
    }

    @Override
    public Map<String, Object> createVehicle(Map<String, Object> request) {
        Map<String, Object> vehicle = new LinkedHashMap<>(request == null ? new LinkedHashMap<String, Object>() : request);
        String plate = String.valueOf(vehicle.get("plate"));
        vehicle.put("vinTopic", plate.replace("·", "-"));
        vehicle.put("status", "OFFLINE");
        vehicle.put("deviceStatus", "OFFLINE");
        vehicle.put("registeredAt", now());
        vehicles.add(vehicle);
        return vehicle;
    }

    @Override
    public Map<String, Object> activeVehicleTask(String plate) {
        return map(
                "plate", plate,
                "hasActiveTask", true,
                "cargoId", "SH-HZ-20260629-0291",
                "cargoStatus", "IN_TRANSIT"
        );
    }

    @Override
    public PageResponse<Map<String, Object>> cargo(String status, String keyword, Integer page, Integer size) {
        return PageResponse.of(cargo, page, size);
    }

    @Override
    public Map<String, Object> cargoDetail(String cargoId) {
        Map<String, Object> item = findBy(cargo, "cargoId", cargoId);
        return item == null ? new LinkedHashMap<String, Object>() : item;
    }

    @Override
    public Map<String, Object> createCargo(Map<String, Object> request) {
        Map<String, Object> item = new LinkedHashMap<>(request == null ? new LinkedHashMap<String, Object>() : request);
        item.put("status", "CREATED");
        item.put("createdAt", now());
        cargo.add(item);
        return item;
    }

    @Override
    public Map<String, Object> bindCargo(Map<String, Object> request) {
        return map(
                "bindingId", "BND-" + UUID.randomUUID().toString().substring(0, 8),
                "cargoId", request == null ? null : request.get("cargoId"),
                "plate", request == null ? null : request.get("plate"),
                "status", "ACTIVE",
                "boundAt", now()
        );
    }

    @Override
    public Map<String, Object> unbindCargo(Map<String, Object> request) {
        return map(
                "cargoId", request == null ? null : request.get("cargoId"),
                "status", "UNBOUND",
                "unboundAt", now()
        );
    }

    @Override
    public Map<String, Object> updateCargoStatus(String cargoId, Map<String, Object> request) {
        return map(
                "cargoId", cargoId,
                "status", request == null ? null : request.get("status"),
                "updatedAt", now()
        );
    }

    @Override
    public List<Map<String, Object>> cargoStatusLogs(String cargoId) {
        return Arrays.asList(map(
                "time", now(),
                "status", "LOADED",
                "operator", "warehouse",
                "remark", "starter status log",
                "location", map("lat", 31.2304, "lng", 121.4737)
        ));
    }

    @Override
    public Map<String, Object> cargoPosition(String cargoId) {
        return map(
                "cargoId", cargoId,
                "vehiclePlate", "沪A·C0291",
                "driverName", "张建国",
                "position", map("lat", 30.4219, "lng", 120.5738, "speed", 68, "heading", 225, "accuracy", 3.5),
                "status", "IN_TRANSIT",
                "locationDesc", "G320 国道海宁段",
                "updatedAt", now(),
                "source", "STARTER",
                "deviceImei", "861234567890123"
        );
    }

    @Override
    public Map<String, Object> cargoTrajectory(String cargoId) {
        return map(
                "cargoId", cargoId,
                "vehiclePlate", "沪A·C0291",
                "startTime", now(),
                "endTime", now(),
                "points", Arrays.asList(
                        map("time", now(), "lat", 31.2304, "lng", 121.4737, "speed", 0, "heading", 0),
                        map("time", now(), "lat", 30.4219, "lng", 120.5738, "speed", 68, "heading", 225)
                )
        );
    }

    @Override
    public Map<String, Object> cargoEta(String cargoId) {
        return map(
                "cargoId", cargoId,
                "eta", now(),
                "remainingMinutes", 52,
                "distanceRemaining", 62,
                "progress", 0.68,
                "trend", "ON_TRACK",
                "calculatedAt", now()
        );
    }

    @Override
    public Map<String, Object> cargoTimeline(String cargoId) {
        return map(
                "cargoId", cargoId,
                "events", Arrays.asList(
                        map("time", now(), "type", "LOADED", "title", "货物装车", "description", "starter timeline event"),
                        map("time", now(), "type", "ALERT", "title", "偏航告警", "description", "starter alert event", "alertId", "ALT-20260629-001")
                )
        );
    }

    @Override
    public PageResponse<Map<String, Object>> alerts(String severity, String type, String status, Integer page, Integer size) {
        return PageResponse.of(alerts, page, size);
    }

    @Override
    public Map<String, Object> alertDetail(String alertId) {
        Map<String, Object> alert = findBy(alerts, "alertId", alertId);
        Map<String, Object> detail = new LinkedHashMap<>(alert == null ? new LinkedHashMap<String, Object>() : alert);
        detail.put("description", "车辆偏离预设路线，后续由同学们接入真实告警规则。");
        detail.put("logs", Arrays.asList(map("time", now(), "operator", "SYSTEM", "action", "starter alert")));
        return detail;
    }

    @Override
    public Map<String, Object> alertStats() {
        return map(
                "pending", map("critical", 0, "warning", 1, "info", 0),
                "resolvedToday", 0,
                "totalThisMonth", 1,
                "averageResolveTimeMinutes", 0,
                "byType", map("ROUTE_DEVIATION", 1, "ABNORMAL_STOP", 0, "CARGO_DOOR", 0, "DEVICE_OFFLINE", 0)
        );
    }

    @Override
    public Map<String, Object> acknowledgeAlert(String alertId, Map<String, Object> request) {
        return map("alertId", alertId, "status", "ACKNOWLEDGED", "acknowledgedAt", now(), "remark", request == null ? null : request.get("remark"));
    }

    @Override
    public Map<String, Object> resolveAlert(String alertId, Map<String, Object> request) {
        return map("alertId", alertId, "status", "RESOLVED", "resolvedAt", now(), "resolution", request == null ? null : request.get("resolution"));
    }

    @Override
    public Map<String, Object> createCommand(String plate, Map<String, Object> request) {
        String commandId = "CMD-" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> command = map(
                "commandId", commandId,
                "plate", plate,
                "vinTopic", plate.replace("·", "-"),
                "mqttTopic", "vehicle/" + plate.replace("·", "-") + "/command",
                "commandType", request == null ? null : request.get("commandType"),
                "priority", request == null ? "NORMAL" : request.get("priority"),
                "status", "SENT",
                "createdAt", now()
        );
        commands.add(command);
        return command;
    }

    @Override
    public Map<String, Object> commandDetail(String plate, String commandId) {
        return map(
                "commandId", commandId,
                "plate", plate,
                "commandType", "REROUTE",
                "priority", "NORMAL",
                "status", "SENT",
                "timeline", Arrays.asList(map("time", now(), "status", "SENT", "source", "PLATFORM"))
        );
    }

    @Override
    public PageResponse<Map<String, Object>> commands(String plate, Integer page, Integer size) {
        return PageResponse.of(commands, page, size);
    }

    @Override
    public Map<String, Object> deviceStatus(String status, String keyword, Integer page, Integer size) {
        List<Map<String, Object>> devices = Arrays.asList(map(
                "imei", "861234567890123",
                "plate", "沪A·C0291",
                "status", "ONLINE",
                "lastHeartbeat", now(),
                "battery", 85,
                "signal", 4,
                "gnssSatellites", 12,
                "temp", 42
        ));
        return map("devices", devices, "onlineCount", 1, "offlineCount", 0, "total", 1);
    }

    @Override
    public Map<String, Object> deviceDetail(String imei) {
        return map("imei", imei, "plate", "沪A·C0291", "status", "ONLINE", "lastHeartbeat", now());
    }

    @Override
    public List<Map<String, Object>> deviceHeartbeats(String imei) {
        return Arrays.asList(map("time", now(), "imei", imei, "battery", 85, "signal", 4, "temp", 42));
    }

    @Override
    public PageResponse<Map<String, Object>> deviceCargoEvents(String imei, Integer page, Integer size) {
        return PageResponse.of(new ArrayList<Map<String, Object>>(), page, size);
    }

    @Override
    public List<String> assistantSuggestions(String cargoId) {
        return Arrays.asList("偏航告警应该怎么处理？", "设备离线超过 90 秒怎么办？", "货物运输中如何更新 ETA？");
    }

    @Override
    public Map<String, Object> assistantChat(Map<String, Object> request) {
        return map(
                "sessionId", request == null || request.get("sessionId") == null ? UUID.randomUUID().toString() : request.get("sessionId"),
                "answer", "这是 starter 兜底回答。请在实践中接入 MinIO、pgvector、embedding 和大模型调用。",
                "sources", Arrays.asList(map("documentId", "DOC-STARTER", "title", "物流异常处理手册", "chunkId", "chunk-1", "score", 0.0)),
                "confidence", 0.0,
                "answeredAt", now()
        );
    }

    @Override
    public PageResponse<Map<String, Object>> knowledgeDocuments(Integer page, Integer size) {
        return PageResponse.of(new ArrayList<Map<String, Object>>(), page, size);
    }

    @Override
    public Map<String, Object> systemStatus() {
        return map("postgres", "TODO", "redis", "TODO", "kafka", "TODO", "emqx", "TODO", "minio", "TODO", "llm", "TODO");
    }

    private Map<String, Object> user(String id, String username, String name, String role, String phone) {
        return map(
                "id", id,
                "username", username,
                "name", name,
                "role", role,
                "phone", phone,
                "permissions", Arrays.asList(role + "_STARTER")
        );
    }

    private Map<String, Object> publicUser(Map<String, Object> user) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (user == null) {
            return result;
        }
        result.putAll(user);
        return result;
    }

    private Map<String, Object> findUser(String username) {
        return findBy(users, "username", username);
    }

    private Map<String, Object> findBy(List<Map<String, Object>> list, String key, String value) {
        if (value == null) {
            return null;
        }
        for (Map<String, Object> item : list) {
            if (value.equals(String.valueOf(item.get(key)))) {
                return item;
            }
        }
        return null;
    }

    private Map<String, Object> point(String name, double lat, double lng) {
        return map("name", name, "lat", lat, "lng", lng);
    }

    private String now() {
        return Instant.now().toString();
    }

    private Map<String, Object> map(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) {
            result.put(String.valueOf(values[i]), values[i + 1]);
        }
        return result;
    }
}
