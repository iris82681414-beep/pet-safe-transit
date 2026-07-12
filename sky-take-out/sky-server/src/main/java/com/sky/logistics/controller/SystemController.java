package com.sky.logistics.controller;

import com.sky.logistics.common.ApiResponse;
import com.sky.logistics.service.LogisticsStarterService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/system")
@Api(tags = "智慧物流-系统状态")
public class SystemController {

    private final LogisticsStarterService starterService;

    public SystemController(LogisticsStarterService starterService) {
        this.starterService = starterService;
    }

    @GetMapping("/status")
    @ApiOperation("获取系统状态")
    public ApiResponse<Map<String, Object>> status() {
        return ApiResponse.success(starterService.systemStatus());
    }
}
