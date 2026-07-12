package com.sky.logistics.service;

import com.sky.logistics.common.PageResponse;

import java.util.List;
import java.util.Map;

public interface DeviceService {

    Map<String, Object> getDeviceStatus(String status, String keyword, Integer page, Integer size);

    Map<String, Object> getDeviceDetail(String imei);

    List<Map<String, Object>> getDeviceHeartbeats(String imei);

    PageResponse<Map<String, Object>> getDeviceCargoEvents(String imei, Integer page, Integer size);
}
