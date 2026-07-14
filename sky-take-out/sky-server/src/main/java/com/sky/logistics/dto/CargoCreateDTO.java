package com.sky.logistics.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CargoCreateDTO {
    private String cargoId;
    private String ownerId;
    private String cargoType;
    private String petName;
    private String petBreed;
    private String petAge;
    private String petGender;
    private BigDecimal weight;
    private CargoLocationDTO origin;
    private CargoLocationDTO destination;
    private String contactName;
    private String contactPhone;
    private String receiverName;
    private String receiverPhone;
    private String requestNote;
}
