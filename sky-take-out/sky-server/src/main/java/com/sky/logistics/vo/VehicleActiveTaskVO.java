package com.sky.logistics.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VehicleActiveTaskVO {

    private Long vehicleId;

    private String plate;

    private String vinTopic;

    private String driverName;

    private String driverPhone;

    private String status;

    private String deviceStatus;

    private List<VehicleActiveCargoVO> cargos;
}