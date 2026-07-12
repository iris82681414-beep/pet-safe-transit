package com.sky.logistics.service;

import com.sky.logistics.common.PageResponse;

import java.util.Map;

public interface AlertService {

    PageResponse<Map<String, Object>> listAlerts(String severity, String type, String status,
                                                  String vehiclePlate, Integer page, Integer size);

    Map<String, Object> getAlertDetail(String alertId);

    Map<String, Object> createAlert(String alertType, String severity, String vehiclePlate,
                                    String cargoId, String title, String summary, String description,
                                    Double lat, Double lng);

    Map<String, Object> acknowledgeAlert(String alertId, String remark);

    Map<String, Object> resolveAlert(String alertId, String resolution, String remark);

    Map<String, Object> getAlertStats();
}
