package com.sky.logistics.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RouteStepVO {
    private String instruction;
    private String roadName;
    private Long distanceMeters;
    private Long durationSeconds;
    private Double distanceKm;
    private Long durationMinutes;
}
