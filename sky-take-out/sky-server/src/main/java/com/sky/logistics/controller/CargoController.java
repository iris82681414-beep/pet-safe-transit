package com.sky.logistics.controller;

import com.sky.logistics.common.ApiResponse;
import com.sky.logistics.common.PageResponse;
import com.sky.logistics.dto.*;
import com.sky.logistics.service.CargoService;
import com.sky.logistics.service.ShipperAccessService;
import com.sky.logistics.service.TrackingService;
import com.sky.logistics.vo.CargoStatusLogVO;
import com.sky.logistics.vo.CargoVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/cargo")
@Api(tags = "智慧物流-货物管理")
public class CargoController {

    private final CargoService cargoService;
    private final TrackingService trackingService;
    private final ShipperAccessService shipperAccessService;

    public CargoController(CargoService cargoService,
                           TrackingService trackingService,
                           ShipperAccessService shipperAccessService) {
        this.cargoService = cargoService;
        this.trackingService = trackingService;
        this.shipperAccessService = shipperAccessService;
    }

    @GetMapping
    @ApiOperation("获取货物列表")
    public ApiResponse<PageResponse<CargoVO>> list(@RequestParam(required = false) String status,
                                                   @RequestParam(required = false) String keyword,
                                                   @RequestParam(required = false) Integer page,
                                                   @RequestParam(required = false) Integer size,
                                                   @RequestHeader(value = "Authorization", required = false) String authorization) {
        CargoQueryDTO queryDTO = new CargoQueryDTO();
        queryDTO.setStatus(status);
        queryDTO.setKeyword(keyword);
        queryDTO.setPage(page);
        queryDTO.setSize(size);
        shipperAccessService.applyOwnerScope(queryDTO, authorization);
        return ApiResponse.success(cargoService.page(queryDTO));
    }

    @PostMapping
    @ApiOperation("新增货物")
    public ApiResponse<CargoVO> create(@RequestBody CargoCreateDTO request,
                                       @RequestHeader(value = "Authorization", required = false) String authorization) {
        shipperAccessService.assignOwner(request, authorization);
        return ApiResponse.success(cargoService.create(request));
    }

    @PutMapping("/{cargoId}")
    @ApiOperation("修改货物")
    public ApiResponse<CargoVO> update(@PathVariable String cargoId, @RequestBody CargoUpdateDTO request,
                                       @RequestHeader(value = "Authorization", required = false) String authorization) {
        shipperAccessService.requireEditableIfShipper(cargoId, authorization);
        return ApiResponse.success(cargoService.update(cargoId, request));
    }

    @GetMapping("/{cargoId}")
    @ApiOperation("获取货物详情")
    public ApiResponse<CargoVO> detail(@PathVariable String cargoId,
                                       @RequestHeader(value = "Authorization", required = false) String authorization) {
        shipperAccessService.requireOwnedIfShipper(cargoId, authorization);
        return ApiResponse.success(cargoService.detail(cargoId));
    }

    @PostMapping("/{cargoId}/cancel")
    @ApiOperation("取消货物")
    public ApiResponse<CargoVO> cancel(@PathVariable String cargoId,
                                       @RequestHeader(value = "Authorization", required = false) String authorization) {
        shipperAccessService.requireCancelableIfShipper(cargoId, authorization);
        return ApiResponse.success(cargoService.cancel(cargoId));
    }

    @PostMapping("/bind")
    @ApiOperation("绑定货物与车辆")
    public ApiResponse<CargoVO> bind(@RequestBody CargoBindDTO request,
                                     @RequestHeader(value = "Authorization", required = false) String authorization) {
        shipperAccessService.rejectShipper(authorization, "货主不能调度或绑定运输车辆");
        return ApiResponse.success(cargoService.bind(request));
    }

    @PostMapping("/unbind")
    @ApiOperation("解绑货物与车辆")
    public ApiResponse<CargoVO> unbind(@RequestBody CargoUnbindDTO request,
                                       @RequestHeader(value = "Authorization", required = false) String authorization) {
        shipperAccessService.rejectShipper(authorization, "货主不能解绑或调度运输车辆");
        return ApiResponse.success(cargoService.unbind(request));
    }

    @PutMapping("/{cargoId}/status")
    @ApiOperation("更新货物状态")
    public ApiResponse<CargoVO> updateStatus(@PathVariable String cargoId, @RequestBody CargoStatusUpdateDTO request,
                                             @RequestHeader(value = "Authorization", required = false) String authorization) {
        shipperAccessService.rejectShipper(authorization, "货主不能直接修改运输状态或向司机下发指令");
        return ApiResponse.success(cargoService.updateStatus(cargoId, request));
    }

    @GetMapping("/{cargoId}/status-logs")
    @ApiOperation("获取货物状态日志")
    public ApiResponse<List<CargoStatusLogVO>> statusLogs(@PathVariable String cargoId,
                                                          @RequestHeader(value = "Authorization", required = false) String authorization) {
        shipperAccessService.requireOwnedIfShipper(cargoId, authorization);
        return ApiResponse.success(cargoService.getStatusLogs(cargoId));
    }

    @GetMapping("/{cargoId}/position")
    @ApiOperation("获取货物当前位置（Redis 实时）")
    public ApiResponse<Map<String, Object>> position(@PathVariable String cargoId,
                                                     @RequestHeader(value = "Authorization", required = false) String authorization) {
        shipperAccessService.requireOwnedIfShipper(cargoId, authorization);
        return ApiResponse.success(trackingService.getPosition(cargoId));
    }

    @GetMapping("/{cargoId}/trajectory")
    @ApiOperation("获取货物历史轨迹（TimescaleDB）")
    public ApiResponse<Map<String, Object>> trajectory(@PathVariable String cargoId,
                                                       @RequestHeader(value = "Authorization", required = false) String authorization) {
        shipperAccessService.requireOwnedIfShipper(cargoId, authorization);
        return ApiResponse.success(trackingService.getTrajectory(cargoId));
    }

    @GetMapping("/{cargoId}/eta")
    @ApiOperation("获取货物 ETA（Haversine 距离计算）")
    public ApiResponse<Map<String, Object>> eta(@PathVariable String cargoId,
                                                @RequestHeader(value = "Authorization", required = false) String authorization) {
        shipperAccessService.requireOwnedIfShipper(cargoId, authorization);
        return ApiResponse.success(trackingService.getEta(cargoId));
    }

    @GetMapping("/{cargoId}/timeline")
    @ApiOperation("获取货物运输时间线")
    public ApiResponse<Map<String, Object>> timeline(@PathVariable String cargoId,
                                                     @RequestHeader(value = "Authorization", required = false) String authorization) {
        shipperAccessService.requireOwnedIfShipper(cargoId, authorization);
        return ApiResponse.success(trackingService.getTimeline(cargoId));
    }
}
