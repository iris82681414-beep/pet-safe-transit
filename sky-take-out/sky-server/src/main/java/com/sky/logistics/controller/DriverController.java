package com.sky.logistics.controller;

import com.sky.logistics.common.ApiResponse;
import com.sky.logistics.common.PageResponse;
import com.sky.logistics.service.OrderExtensionService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping({"/api/v1/drivers", "/api/drivers"})
@Api(tags = "智慧物流-司机评分")
public class DriverController {

    private final OrderExtensionService orderExtensionService;

    public DriverController(OrderExtensionService orderExtensionService) {
        this.orderExtensionService = orderExtensionService;
    }

    @GetMapping("/{driverId}/rating-summary")
    @ApiOperation("司机评分汇总")
    public ApiResponse<Map<String, Object>> ratingSummary(@PathVariable String driverId) {
        return ApiResponse.success(orderExtensionService.driverRatingSummary(driverId));
    }

    @GetMapping("/{driverId}/ratings")
    @ApiOperation("司机评分列表")
    public ApiResponse<PageResponse<Map<String, Object>>> ratings(@PathVariable String driverId,
                                                                  @RequestParam(required = false) Integer page,
                                                                  @RequestParam(required = false) Integer size) {
        return ApiResponse.success(orderExtensionService.driverRatings(driverId, page, size));
    }
}
