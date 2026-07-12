package com.sky.logistics.dto;

import lombok.Data;

import java.util.List;

@Data
public class DriverRatingCreateDTO {
    private String driverId;
    private Integer score;
    private DriverRatingDimensionsDTO dimensions;
    private List<String> tags;
    private String comment;
}
