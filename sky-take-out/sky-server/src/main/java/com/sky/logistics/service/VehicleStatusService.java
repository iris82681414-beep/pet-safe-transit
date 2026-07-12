package com.sky.logistics.service;

import java.util.Map;

/**
 * 车辆实时状态查询 —— 供后端3 告警引擎使用
 */
public interface VehicleStatusService {

    /**
     * 获取车辆当前完整状态（位置、速度、航向、最后更新时间）
     */
    Map<String, Object> getCurrentStatus(String plate);

    /**
     * 获取车辆当前速度 (km/h)，Redis 不可用时回退到 TimescaleDB
     */
    int getCurrentSpeed(String plate);

    /**
     * 获取车辆停留时长（秒），速度 < 5 时累计；行驶中返回 0
     */
    long getStopDurationSeconds(String plate);

    /**
     * 获取最后 GPS 上报时间（ISO-8601 字符串）
     */
    String getLastUpdateTime(String plate);
}
