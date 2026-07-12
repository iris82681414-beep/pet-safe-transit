package com.sky.logistics.vo;

import com.sky.logistics.dto.GeoPointDTO;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class RoutePlanVO {
    private String routeId;
    private String routeType;
    private Long distanceMeters;
    private Long durationSeconds;
    private Double distanceKm;
    private Long durationMinutes;
    private BigDecimal tolls;
    private BigDecimal tollCost;
    private Integer trafficLights;
    private String restriction;
    private List<String> restrictionWarnings;
    private List<GeoPointDTO> polyline;
    private List<RouteStepVO> steps;
    private String source;
}
