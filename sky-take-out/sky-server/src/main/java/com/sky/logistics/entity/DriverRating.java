package com.sky.logistics.entity;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class DriverRating {
    private Long id;
    private String ratingId;
    private String orderId;
    private String customerId;
    private String driverId;
    private String plate;
    private Integer score;
    private Integer punctuality;
    private Integer serviceAttitude;
    private Integer cargoIntegrity;
    private Integer communication;
    private String comment;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
