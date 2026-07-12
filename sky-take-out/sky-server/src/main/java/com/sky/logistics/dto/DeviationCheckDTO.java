package com.sky.logistics.dto;

import lombok.Data;

import java.util.List;

@Data
public class DeviationCheckDTO {
    private String cargoId;
    private String vehiclePlate;
    private GeoPointDTO currentLocation;
    private List<GeoPointDTO> routePolyline;
    private Double thresholdKm;
}
