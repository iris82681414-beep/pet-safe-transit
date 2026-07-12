package com.sky.logistics.controller;

import com.sky.logistics.common.ApiResponse;
import com.sky.logistics.common.PageResponse;
import com.sky.logistics.service.DeviceService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/devices")
@Api(tags = "智慧物流-设备在线")
public class DeviceController {

    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @GetMapping("/status")
    @ApiOperation("获取设备在线状态列表")
    public ApiResponse<Map<String, Object>> status(@RequestParam(required = false) String status,
                                                   @RequestParam(required = false) String keyword,
                                                   @RequestParam(required = false) Integer page,
                                                   @RequestParam(required = false) Integer size) {
        return ApiResponse.success(deviceService.getDeviceStatus(status, keyword, page, size));
    }

    @GetMapping("/{imei}")
    @ApiOperation("获取设备详情")
    public ApiResponse<Map<String, Object>> detail(@PathVariable String imei) {
        return ApiResponse.success(deviceService.getDeviceDetail(imei));
    }

    @GetMapping("/{imei}/heartbeats")
    @ApiOperation("获取设备心跳记录")
    public ApiResponse<List<Map<String, Object>>> heartbeats(@PathVariable String imei) {
        return ApiResponse.success(deviceService.getDeviceHeartbeats(imei));
    }

    @GetMapping("/{imei}/cargo-events")
    @ApiOperation("获取设备货物事件")
    public ApiResponse<PageResponse<Map<String, Object>>> cargoEvents(@PathVariable String imei,
                                                                      @RequestParam(required = false) Integer page,
                                                                      @RequestParam(required = false) Integer size) {
        return ApiResponse.success(deviceService.getDeviceCargoEvents(imei, page, size));
    }
}
