package com.sky.logistics.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
public class Cargo {
    private String cargoId;
    private String cargoType;
    private BigDecimal weight;
    private String status;
    private String originName;
    private Double originLat;
    private Double originLng;
    private String destinationName;
    private Double destinationLat;
    private Double destinationLng;
    private OffsetDateTime loadedAt;
    private OffsetDateTime deliveredAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
