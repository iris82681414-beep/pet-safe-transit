package com.sky.logistics.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class TruckInfoDTO {
    private String plate;
    private String size;
    private BigDecimal heightMeters;
    private BigDecimal widthMeters;
    private BigDecimal loadWeightTons;
    private BigDecimal totalWeightTons;
    private Integer axis;
}
