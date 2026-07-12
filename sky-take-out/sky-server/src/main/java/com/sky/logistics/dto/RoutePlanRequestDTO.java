package com.sky.logistics.dto;

import lombok.Data;

import java.util.List;

@Data
public class RoutePlanRequestDTO {
    private String mode;
    private GeoPointDTO origin;
    private GeoPointDTO destination;
    private List<GeoPointDTO> waypoints;
    private String strategy;
    private String plate;
    private TruckInfoDTO truck;
    private TruckInfoDTO vehicle;
}
