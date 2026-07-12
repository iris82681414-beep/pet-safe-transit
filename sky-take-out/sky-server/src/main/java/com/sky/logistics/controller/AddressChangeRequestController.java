package com.sky.logistics.controller;

import com.sky.logistics.common.ApiResponse;
import com.sky.logistics.dto.AddressChangeApproveDTO;
import com.sky.logistics.dto.AddressChangeRejectDTO;
import com.sky.logistics.dto.DriverConfirmDTO;
import com.sky.logistics.service.OrderExtensionService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping({"/api/v1/address-change-requests", "/api/address-change-requests"})
@Api(tags = "智慧物流-改址审核")
public class AddressChangeRequestController {

    private final OrderExtensionService orderExtensionService;

    public AddressChangeRequestController(OrderExtensionService orderExtensionService) {
        this.orderExtensionService = orderExtensionService;
    }

    @GetMapping("/{requestId}")
    @ApiOperation("改址申请详情")
    public ApiResponse<Map<String, Object>> detail(@PathVariable String requestId) {
        return ApiResponse.success(orderExtensionService.addressChangeDetail(requestId));
    }

    @PostMapping("/{requestId}/approve")
    @ApiOperation("调度员通过改址")
    public ApiResponse<Map<String, Object>> approve(@PathVariable String requestId,
                                                    @RequestBody AddressChangeApproveDTO request,
                                                    @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.success(orderExtensionService.approveAddressChange(requestId, request, authorization));
    }

    @PostMapping("/{requestId}/reject")
    @ApiOperation("调度员拒绝改址")
    public ApiResponse<Map<String, Object>> reject(@PathVariable String requestId,
                                                   @RequestBody AddressChangeRejectDTO request,
                                                   @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.success(orderExtensionService.rejectAddressChange(requestId, request, authorization));
    }

    @PostMapping("/{requestId}/driver-confirm")
    @ApiOperation("司机确认新地址")
    public ApiResponse<Map<String, Object>> driverConfirm(@PathVariable String requestId,
                                                          @RequestBody DriverConfirmDTO request,
                                                          @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.success(orderExtensionService.driverConfirmAddressChange(requestId, request, authorization));
    }
}
