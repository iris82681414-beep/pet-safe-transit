package com.sky.logistics.controller;

import com.sky.logistics.common.ApiResponse;
import com.sky.logistics.common.PageResponse;
import com.sky.logistics.service.AlertService;
import com.sky.logistics.service.LogisticsSecurityService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/alerts")
@Api(tags = "智慧物流-告警中心")
public class AlertController {

    private final AlertService alertService;
    private final LogisticsSecurityService securityService;

    public AlertController(AlertService alertService, LogisticsSecurityService securityService) {
        this.alertService = alertService;
        this.securityService = securityService;
    }

    @GetMapping
    @ApiOperation("获取告警列表")
    public ApiResponse<PageResponse<Map<String, Object>>> list(@RequestParam(required = false) String severity,
                                                               @RequestParam(required = false) String type,
                                                               @RequestParam(required = false) String status,
                                                               @RequestParam(required = false) String vehiclePlate,
                                                               @RequestParam(required = false) Integer page,
                                                               @RequestParam(required = false) Integer size,
                                                               @RequestHeader(value = "Authorization", required = false) String authorization) {
        securityService.rejectRole(authorization, "SHIPPER");
        return ApiResponse.success(alertService.listAlerts(severity, type, status, vehiclePlate, page, size));
    }

    @GetMapping("/{alertId}")
    @ApiOperation("获取告警详情")
    public ApiResponse<Map<String, Object>> detail(@PathVariable String alertId,
                                                   @RequestHeader(value = "Authorization", required = false) String authorization) {
        securityService.rejectRole(authorization, "SHIPPER");
        return ApiResponse.success(alertService.getAlertDetail(alertId));
    }

    @GetMapping("/stats")
    @ApiOperation("获取告警统计")
    public ApiResponse<Map<String, Object>> stats(@RequestHeader(value = "Authorization", required = false) String authorization) {
        securityService.rejectRole(authorization, "SHIPPER");
        return ApiResponse.success(alertService.getAlertStats());
    }

    @PostMapping("/{alertId}/acknowledge")
    @ApiOperation("确认告警")
    public ApiResponse<Map<String, Object>> acknowledge(@PathVariable String alertId,
                                                        @RequestBody Map<String, Object> request,
                                                        @RequestHeader(value = "Authorization", required = false) String authorization) {
        securityService.rejectRole(authorization, "SHIPPER");
        String remark = request != null ? (String) request.get("remark") : null;
        return ApiResponse.success(alertService.acknowledgeAlert(alertId, remark));
    }

    @PostMapping("/{alertId}/resolve")
    @ApiOperation("关闭告警")
    public ApiResponse<Map<String, Object>> resolve(@PathVariable String alertId,
                                                    @RequestBody Map<String, Object> request,
                                                    @RequestHeader(value = "Authorization", required = false) String authorization) {
        securityService.rejectRole(authorization, "SHIPPER");
        String resolution = request != null ? (String) request.get("resolution") : null;
        String remark = request != null ? (String) request.get("remark") : null;
        return ApiResponse.success(alertService.resolveAlert(alertId, resolution, remark));
    }
}
