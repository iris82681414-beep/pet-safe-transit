package com.sky.logistics.dto;

import lombok.Data;

import java.util.List;

@Data
public class TrajectoryCorrectDTO {
    private String cargoId;
    private String vehiclePlate;
    private List<GpsPointDTO> points;
}
