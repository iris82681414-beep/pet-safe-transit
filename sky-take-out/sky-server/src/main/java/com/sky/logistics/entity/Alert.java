package com.sky.logistics.entity;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class Alert {
    private String alertId;
    private String alertType;
    private String severity;
    private String status;
    private String vehiclePlate;
    private String cargoId;
    private String title;
    private String summary;
    private String description;
    private Double lat;
    private Double lng;
    private OffsetDateTime triggeredAt;
    private OffsetDateTime acknowledgedAt;
    private OffsetDateTime resolvedAt;
    private String resolution;
    private String remark;
}
