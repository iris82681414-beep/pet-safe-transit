package com.sky.logistics.dto;

import lombok.Data;

@Data
public class RerouteSuggestionDTO {
    private String cargoId;
    private String vehiclePlate;
    private String plate;
    private GeoPointDTO currentLocation;
    private GeoPointDTO destination;
    private String strategy;
}
