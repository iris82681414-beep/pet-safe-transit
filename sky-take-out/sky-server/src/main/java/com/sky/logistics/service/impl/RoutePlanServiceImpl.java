package com.sky.logistics.service.impl;

import com.sky.logistics.dto.DeviationCheckDTO;
import com.sky.logistics.dto.GeoPointDTO;
import com.sky.logistics.dto.RerouteSuggestionDTO;
import com.sky.logistics.dto.RoutePlanRequestDTO;
import com.sky.logistics.dto.TrajectoryCorrectDTO;
import com.sky.logistics.service.AmapService;
import com.sky.logistics.service.RoutePlanService;
import com.sky.logistics.vo.RoutePlanVO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RoutePlanServiceImpl implements RoutePlanService {

    private static final double DEFAULT_DEVIATION_THRESHOLD_KM = 3.0;

    private final AmapService amapService;

    public RoutePlanServiceImpl(AmapService amapService) {
        this.amapService = amapService;
    }

    @Override
    public RoutePlanVO plan(RoutePlanRequestDTO request) {
        if (request != null && "TRUCK".equalsIgnoreCase(request.getMode())) {
            return amapService.planTruckRoute(request);
        }
        return amapService.planDrivingRoute(request);
    }

    @Override
    public RoutePlanVO truckPlan(RoutePlanRequestDTO request) {
        return amapService.planTruckRoute(request);
    }

    @Override
    public RoutePlanVO replan(RoutePlanRequestDTO request) {
        return plan(request);
    }

    @Override
    public Map<String, Object> checkDeviation(DeviationCheckDTO request) {
        if (request == null || !isValid(request.getCurrentLocation())) {
            throw new IllegalArgumentException("当前位置不能为空");
        }
        if (request.getRoutePolyline() == null || request.getRoutePolyline().size() < 2) {
            throw new IllegalArgumentException("规划路线至少需要两个点");
        }

        double threshold = request.getThresholdKm() == null || request.getThresholdKm() <= 0
                ? DEFAULT_DEVIATION_THRESHOLD_KM
                : request.getThresholdKm();
        double distance = minDistanceToPolylineKm(request.getCurrentLocation(), request.getRoutePolyline());
        boolean deviated = distance > threshold;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cargoId", request.getCargoId());
        result.put("vehiclePlate", request.getVehiclePlate());
        result.put("deviated", deviated);
        result.put("distanceToRouteKm", round(distance));
        result.put("thresholdKm", threshold);
        result.put("level", deviated ? (distance > threshold * 2 ? "HIGH" : "MEDIUM") : "LOW");
        result.put("suggestion", deviated ? "车辆已偏离规划路线，建议重新规划路线并联系司机确认" : "车辆仍在规划路线容差范围内");
        result.put("checkedAt", Instant.now().toString());
        return result;
    }

    @Override
    public Map<String, Object> rerouteSuggestion(RerouteSuggestionDTO request) {
        if (request == null || !isValid(request.getCurrentLocation()) || !isValid(request.getDestination())) {
            throw new IllegalArgumentException("当前位置和目的地不能为空");
        }

        RoutePlanRequestDTO routeRequest = new RoutePlanRequestDTO();
        routeRequest.setOrigin(request.getCurrentLocation());
        routeRequest.setDestination(request.getDestination());
        routeRequest.setPlate(StringUtils.hasText(request.getPlate()) ? request.getPlate() : request.getVehiclePlate());
        routeRequest.setStrategy(request.getStrategy());

        RoutePlanVO route = plan(routeRequest);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cargoId", request.getCargoId());
        result.put("vehiclePlate", request.getVehiclePlate());
        result.put("route", route);
        result.put("commandType", "REROUTE");
        result.put("message", "已生成从当前位置到目的地的纠偏路线，可下发给司机");
        result.put("createdAt", Instant.now().toString());
        return result;
    }

    @Override
    public Map<String, Object> correctTrajectory(TrajectoryCorrectDTO request) {
        if (request == null || request.getPoints() == null) {
            throw new IllegalArgumentException("轨迹点不能为空");
        }
        List<GeoPointDTO> corrected = amapService.correctTrajectory(request.getPoints());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cargoId", request.getCargoId());
        result.put("vehiclePlate", request.getVehiclePlate());
        result.put("rawPointCount", request.getPoints().size());
        result.put("correctedPointCount", corrected.size());
        result.put("correctedPoints", corrected);
        result.put("source", "AMAP_OR_LOCAL_FALLBACK");
        result.put("correctedAt", Instant.now().toString());
        return result;
    }

    private boolean isValid(GeoPointDTO point) {
        return point != null && point.getLng() != null && point.getLat() != null;
    }

    private double minDistanceToPolylineKm(GeoPointDTO point, List<GeoPointDTO> polyline) {
        double min = Double.MAX_VALUE;
        List<GeoPointDTO> valid = new ArrayList<>();
        for (GeoPointDTO item : polyline) {
            if (isValid(item)) {
                valid.add(item);
            }
        }
        for (int i = 1; i < valid.size(); i++) {
            min = Math.min(min, distanceToSegmentKm(point, valid.get(i - 1), valid.get(i)));
        }
        return min == Double.MAX_VALUE ? 0.0 : min;
    }

    private double distanceToSegmentKm(GeoPointDTO point, GeoPointDTO start, GeoPointDTO end) {
        double latScale = 111.32;
        double lngScale = 111.32 * Math.cos(Math.toRadians(point.getLat()));
        double px = point.getLng() * lngScale;
        double py = point.getLat() * latScale;
        double ax = start.getLng() * lngScale;
        double ay = start.getLat() * latScale;
        double bx = end.getLng() * lngScale;
        double by = end.getLat() * latScale;

        double dx = bx - ax;
        double dy = by - ay;
        if (dx == 0 && dy == 0) {
            return Math.sqrt(Math.pow(px - ax, 2) + Math.pow(py - ay, 2));
        }
        double t = ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy);
        t = Math.max(0, Math.min(1, t));
        double closestX = ax + t * dx;
        double closestY = ay + t * dy;
        return Math.sqrt(Math.pow(px - closestX, 2) + Math.pow(py - closestY, 2));
    }

    private double round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
