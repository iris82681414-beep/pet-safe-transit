package com.sky.logistics.controller;

import com.sky.logistics.common.ApiResponse;
import com.sky.logistics.common.PageResponse;
import com.sky.logistics.service.OrderExtensionService;
import com.sky.logistics.service.ShipperAccessService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping({"/api/v1/drivers", "/api/drivers"})
@Api(tags = "智慧物流-司机评分")
public class DriverController {

    private final OrderExtensionService orderExtensionService;
    private final ShipperAccessService shipperAccessService;

    public DriverController(OrderExtensionService orderExtensionService, ShipperAccessService shipperAccessService) {
        this.orderExtensionService = orderExtensionService;
        this.shipperAccessService = shipperAccessService;
    }

    @GetMapping("/{driverId}/rating-summary")
    @ApiOperation("司机评分汇总")
    public ApiResponse<Map<String, Object>> ratingSummary(@PathVariable String driverId,
                                                          @RequestHeader(value = "Authorization", required = false) String authorization) {
        shipperAccessService.rejectShipper(authorization, "货主不能查看全局司机资料或其他货主评价");
        return ApiResponse.success(orderExtensionService.driverRatingSummary(driverId));
    }

    @GetMapping("/{driverId}/ratings")
    @ApiOperation("司机评分列表")
    public ApiResponse<PageResponse<Map<String, Object>>> ratings(@PathVariable String driverId,
                                                                  @RequestParam(required = false) Integer page,
                                                                  @RequestParam(required = false) Integer size,
                                                                  @RequestHeader(value = "Authorization", required = false) String authorization) {
        shipperAccessService.rejectShipper(authorization, "货主不能查看全局司机资料或其他货主评价");
        return ApiResponse.success(orderExtensionService.driverRatings(driverId, page, size));
    }
}
