package com.sky.logistics.controller;

import com.sky.logistics.common.ApiResponse;
import com.sky.logistics.dto.AddressChangeCreateDTO;
import com.sky.logistics.dto.AddressChangeImpactDTO;
import com.sky.logistics.dto.DriverRatingCreateDTO;
import com.sky.logistics.dto.StatusVerifyDTO;
import com.sky.logistics.dto.UnloadAddressAbnormalDTO;
import com.sky.logistics.dto.UnloadAddressConfirmDTO;
import com.sky.logistics.service.OrderExtensionService;
import com.sky.logistics.vo.AddressChangeImpactVO;
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
@RequestMapping({"/api/v1/orders", "/api/orders"})
@Api(tags = "智慧物流-订单扩展流程")
public class OrderExtensionController {

    private final OrderExtensionService orderExtensionService;

    public OrderExtensionController(OrderExtensionService orderExtensionService) {
        this.orderExtensionService = orderExtensionService;
    }

    @PostMapping("/{orderId}/address-change-impact")
    @ApiOperation("计算改址影响")
    public ApiResponse<AddressChangeImpactVO> addressChangeImpact(@PathVariable String orderId,
                                                                  @RequestBody AddressChangeImpactDTO request) {
        return ApiResponse.success(orderExtensionService.calculateAddressChangeImpact(orderId, request));
    }

    @PostMapping("/{orderId}/address-change-requests")
    @ApiOperation("提交改址申请")
    public ApiResponse<Map<String, Object>> createAddressChange(@PathVariable String orderId,
                                                                @RequestBody AddressChangeCreateDTO request,
                                                                @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.success(orderExtensionService.createAddressChangeRequest(orderId, request, authorization));
    }

    @GetMapping("/{orderId}/address-change-history")
    @ApiOperation("查看改址历史")
    public ApiResponse<Map<String, Object>> addressChangeHistory(@PathVariable String orderId) {
        return ApiResponse.success(orderExtensionService.addressChangeHistory(orderId));
    }

    @GetMapping("/{orderId}/delay-prediction")
    @ApiOperation("订单延误预测")
    public ApiResponse<Map<String, Object>> delayPrediction(@PathVariable String orderId) {
        return ApiResponse.success(orderExtensionService.delayPrediction(orderId));
    }

    @GetMapping("/{orderId}/risk-score")
    @ApiOperation("订单风险评分")
    public ApiResponse<Map<String, Object>> riskScore(@PathVariable String orderId) {
        return ApiResponse.success(orderExtensionService.riskScore(orderId));
    }

    @PostMapping("/{orderId}/status/verify")
    @ApiOperation("订单状态可信度校验")
    public ApiResponse<Map<String, Object>> verifyStatus(@PathVariable String orderId,
                                                         @RequestBody StatusVerifyDTO request) {
        return ApiResponse.success(orderExtensionService.verifyStatus(orderId, request));
    }

    @GetMapping("/{orderId}/exception-summary")
    @ApiOperation("客户订单异常摘要")
    public ApiResponse<Map<String, Object>> exceptionSummary(@PathVariable String orderId) {
        return ApiResponse.success(orderExtensionService.exceptionSummary(orderId));
    }

    @GetMapping("/{orderId}/unload-address/suggestions")
    @ApiOperation("卸货点建议")
    public ApiResponse<Map<String, Object>> unloadAddressSuggestions(@PathVariable String orderId) {
        return ApiResponse.success(orderExtensionService.unloadAddressSuggestions(orderId));
    }

    @PostMapping("/{orderId}/unload-address/confirm")
    @ApiOperation("确认卸货点")
    public ApiResponse<Map<String, Object>> confirmUnloadAddress(@PathVariable String orderId,
                                                                 @RequestBody UnloadAddressConfirmDTO request,
                                                                 @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.success(orderExtensionService.confirmUnloadAddress(orderId, request, authorization));
    }

    @PostMapping("/{orderId}/unload-address/abnormal")
    @ApiOperation("司机反馈卸货点异常")
    public ApiResponse<Map<String, Object>> reportUnloadAddressAbnormal(@PathVariable String orderId,
                                                                        @RequestBody UnloadAddressAbnormalDTO request,
                                                                        @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.success(orderExtensionService.reportUnloadAddressAbnormal(orderId, request, authorization));
    }

    @PostMapping("/{orderId}/driver-rating")
    @ApiOperation("客户提交司机评分")
    public ApiResponse<Map<String, Object>> submitDriverRating(@PathVariable String orderId,
                                                               @RequestBody DriverRatingCreateDTO request,
                                                               @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.success(orderExtensionService.submitDriverRating(orderId, request, authorization));
    }

    @GetMapping("/{orderId}/driver-rating")
    @ApiOperation("查询订单司机评分")
    public ApiResponse<Map<String, Object>> getDriverRating(@PathVariable String orderId) {
        return ApiResponse.success(orderExtensionService.getDriverRating(orderId));
    }
}
