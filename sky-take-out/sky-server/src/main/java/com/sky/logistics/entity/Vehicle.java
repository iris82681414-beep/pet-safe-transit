package com.sky.logistics.entity;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class Vehicle {
    private Long id;
    private String plate;
    private String vinTopic;
    private String vehicleType;
    private Integer capacity;
    private String driverName;
    private String driverPhone;
    private String deviceImei;
    private String status;
    private String deviceStatus;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
