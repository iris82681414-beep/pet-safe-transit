package com.sky.logistics.service;

import com.sky.logistics.common.PageResponse;
import com.sky.logistics.dto.LoginRequest;

import java.util.List;
import java.util.Map;

public interface LogisticsStarterService {

    Map<String, Object> login(LoginRequest request);

    Map<String, Object> currentUser(String authorization);

    PageResponse<Map<String, Object>> vehicles(String status, String keyword, Integer page, Integer size);

    Map<String, Object> vehicleDetail(String plate);

    Map<String, Object> createVehicle(Map<String, Object> request);

    Map<String, Object> activeVehicleTask(String plate);

    PageResponse<Map<String, Object>> cargo(String status, String keyword, Integer page, Integer size);

    Map<String, Object> cargoDetail(String cargoId);

    Map<String, Object> createCargo(Map<String, Object> request);

    Map<String, Object> bindCargo(Map<String, Object> request);

    Map<String, Object> unbindCargo(Map<String, Object> request);

    Map<String, Object> updateCargoStatus(String cargoId, Map<String, Object> request);

    List<Map<String, Object>> cargoStatusLogs(String cargoId);

    Map<String, Object> cargoPosition(String cargoId);

    Map<String, Object> cargoTrajectory(String cargoId);

    Map<String, Object> cargoEta(String cargoId);

    Map<String, Object> cargoTimeline(String cargoId);

    PageResponse<Map<String, Object>> alerts(String severity, String type, String status, Integer page, Integer size);

    Map<String, Object> alertDetail(String alertId);

    Map<String, Object> alertStats();

    Map<String, Object> acknowledgeAlert(String alertId, Map<String, Object> request);

    Map<String, Object> resolveAlert(String alertId, Map<String, Object> request);

    Map<String, Object> createCommand(String plate, Map<String, Object> request);

    Map<String, Object> commandDetail(String plate, String commandId);

    PageResponse<Map<String, Object>> commands(String plate, Integer page, Integer size);

    Map<String, Object> deviceStatus(String status, String keyword, Integer page, Integer size);

    Map<String, Object> deviceDetail(String imei);

    List<Map<String, Object>> deviceHeartbeats(String imei);

    PageResponse<Map<String, Object>> deviceCargoEvents(String imei, Integer page, Integer size);

    List<String> assistantSuggestions(String cargoId);

    Map<String, Object> assistantChat(Map<String, Object> request);

    PageResponse<Map<String, Object>> knowledgeDocuments(Integer page, Integer size);

    Map<String, Object> systemStatus();
}
