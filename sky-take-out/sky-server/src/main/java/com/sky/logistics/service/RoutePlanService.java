package com.sky.logistics.service;

import com.sky.logistics.dto.DeviationCheckDTO;
import com.sky.logistics.dto.RerouteSuggestionDTO;
import com.sky.logistics.dto.RoutePlanRequestDTO;
import com.sky.logistics.dto.TrajectoryCorrectDTO;
import com.sky.logistics.vo.RoutePlanVO;

import java.util.Map;

public interface RoutePlanService {

    RoutePlanVO plan(RoutePlanRequestDTO request);

    RoutePlanVO truckPlan(RoutePlanRequestDTO request);

    RoutePlanVO replan(RoutePlanRequestDTO request);

    Map<String, Object> checkDeviation(DeviationCheckDTO request);

    Map<String, Object> rerouteSuggestion(RerouteSuggestionDTO request);

    Map<String, Object> correctTrajectory(TrajectoryCorrectDTO request);
}
