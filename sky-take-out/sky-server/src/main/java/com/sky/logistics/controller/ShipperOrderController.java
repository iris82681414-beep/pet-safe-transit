package com.sky.logistics.controller;

import com.sky.logistics.common.ApiResponse;
import com.sky.logistics.service.ShipperOrderService;
import com.sky.logistics.vo.CargoVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/shipper/orders")
@Api(tags = "智慧物流-货主工作台")
public class ShipperOrderController {

    private final ShipperOrderService shipperOrderService;

    public ShipperOrderController(ShipperOrderService shipperOrderService) {
        this.shipperOrderService = shipperOrderService;
    }

    @PostMapping("/{cargoId}/confirm-receipt")
    @ApiOperation("货主确认收到宠物")
    public ApiResponse<CargoVO> confirmReceipt(@PathVariable String cargoId,
                                               @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.success(shipperOrderService.confirmReceipt(cargoId, authorization));
    }

    @GetMapping("/{cargoId}/environment")
    @ApiOperation("查看本人订单最新环境状态")
    public ApiResponse<Map<String, Object>> environment(@PathVariable String cargoId,
                                                        @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.success(shipperOrderService.environment(cargoId, authorization));
    }

    @GetMapping("/{cargoId}/notifications")
    @ApiOperation("查看本人订单风险通知")
    public ApiResponse<List<Map<String, Object>>> notifications(@PathVariable String cargoId,
                                                                @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.success(shipperOrderService.notifications(cargoId, authorization));
    }
}
