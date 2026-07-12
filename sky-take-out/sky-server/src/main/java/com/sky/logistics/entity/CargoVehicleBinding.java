package com.sky.logistics.entity;

import lombok.Data;

import java.time.Instant;

@Data
public class CargoVehicleBinding {
    private String id;
    private String cargoId;
    private Long vehicleId;
    private String status;
    private Instant boundAt;
    private Instant unboundAt;
}
