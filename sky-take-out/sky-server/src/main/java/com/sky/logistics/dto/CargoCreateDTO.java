package com.sky.logistics.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CargoCreateDTO {
    private String cargoId;
    private String cargoType;
    private BigDecimal weight;
    private CargoLocationDTO origin;
    private CargoLocationDTO destination;
}
