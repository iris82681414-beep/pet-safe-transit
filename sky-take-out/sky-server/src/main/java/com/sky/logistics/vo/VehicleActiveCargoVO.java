package com.sky.logistics.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VehicleActiveCargoVO {

    private String cargoId;

    private String cargoType;

    private BigDecimal weight;

    private String status;

    private String originName;

    private String destinationName;

    private OffsetDateTime boundAt;
}