package com.sky.logistics.dto;

import lombok.Data;

@Data
public class DriverRatingDimensionsDTO {
    private Integer punctuality;
    private Integer serviceAttitude;
    private Integer cargoIntegrity;
    private Integer communication;
}
