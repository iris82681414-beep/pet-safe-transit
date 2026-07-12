package com.sky.logistics.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
public class AddressChangeRequest {
    private Long id;
    private String requestId;
    private String orderId;
    private String customerId;
    private String oldAddress;
    private Double oldLng;
    private Double oldLat;
    private String newAddress;
    private Double newLng;
    private Double newLat;
    private String contactName;
    private String contactPhone;
    private String reason;
    private String status;
    private String impactLevel;
    private BigDecimal extraDistanceKm;
    private Integer estimatedDelayMinutes;
    private BigDecimal extraCost;
    private Boolean needDispatcherReview;
    private Boolean needDriverConfirm;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
