package com.sky.logistics.controller;

import com.sky.logistics.common.ApiResponse;
import com.sky.logistics.dto.TrajectoryCorrectDTO;
import com.sky.logistics.service.RoutePlanService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping({"/api/v1/trajectory", "/api/trajectory"})
@Api(tags = "智慧物流-轨迹纠偏")
public class TrajectoryController {

    private final RoutePlanService routePlanService;

    public TrajectoryController(RoutePlanService routePlanService) {
        this.routePlanService = routePlanService;
    }

    @PostMapping("/correct")
    @ApiOperation("轨迹纠偏")
    public ApiResponse<Map<String, Object>> correct(@RequestBody TrajectoryCorrectDTO request) {
        return ApiResponse.success(routePlanService.correctTrajectory(request));
    }
}
