package com.sky.logistics.controller;

import com.sky.logistics.common.ApiResponse;
import com.sky.logistics.dto.DeviationCheckDTO;
import com.sky.logistics.dto.RerouteSuggestionDTO;
import com.sky.logistics.dto.RoutePlanRequestDTO;
import com.sky.logistics.dto.TrajectoryCorrectDTO;
import com.sky.logistics.service.RoutePlanService;
import com.sky.logistics.vo.RoutePlanVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping({"/api/v1/routes", "/api/routes"})
@Api(tags = "智慧物流-高德路线规划")
public class RouteController {

    private final RoutePlanService routePlanService;

    public RouteController(RoutePlanService routePlanService) {
        this.routePlanService = routePlanService;
    }

    @PostMapping("/plan")
    @ApiOperation("普通路线规划")
    public ApiResponse<RoutePlanVO> plan(@RequestBody RoutePlanRequestDTO request) {
        return ApiResponse.success(routePlanService.plan(request));
    }

    @PostMapping("/truck-plan")
    @ApiOperation("货车路线规划")
    public ApiResponse<RoutePlanVO> truckPlan(@RequestBody RoutePlanRequestDTO request) {
        return ApiResponse.success(routePlanService.truckPlan(request));
    }

    @PostMapping("/replan")
    @ApiOperation("路线重算")
    public ApiResponse<RoutePlanVO> replan(@RequestBody RoutePlanRequestDTO request) {
        return ApiResponse.success(routePlanService.replan(request));
    }

    @PostMapping("/deviation/check")
    @ApiOperation("偏航检测")
    public ApiResponse<Map<String, Object>> deviationCheck(@RequestBody DeviationCheckDTO request) {
        return ApiResponse.success(routePlanService.checkDeviation(request));
    }

    @PostMapping("/reroute-suggestion")
    @ApiOperation("纠偏路线建议")
    public ApiResponse<Map<String, Object>> rerouteSuggestion(@RequestBody RerouteSuggestionDTO request) {
        return ApiResponse.success(routePlanService.rerouteSuggestion(request));
    }

    @PostMapping("/trajectory/correct")
    @ApiOperation("轨迹纠偏")
    public ApiResponse<Map<String, Object>> correctTrajectoryAlias(@RequestBody TrajectoryCorrectDTO request) {
        return ApiResponse.success(routePlanService.correctTrajectory(request));
    }
}
