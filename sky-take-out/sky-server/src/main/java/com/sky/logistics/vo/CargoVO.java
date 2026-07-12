package com.sky.logistics.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@Builder
public class CargoVO {
    private String cargoId;
    private String cargoType;
    private BigDecimal weight;
    private String status;
    private CargoLocationVO origin;
    private CargoLocationVO destination;
    private Long vehicleId;
    private String vehiclePlate;
    private String driverName;
    private String driverPhone;
    private OffsetDateTime loadedAt;
    private OffsetDateTime deliveredAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
