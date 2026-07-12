package com.sky.logistics.dto;

import lombok.Data;

@Data
public class VehicleQueryDTO {
    private String status;
    private String keyword;
    private Integer page;
    private Integer size;
}