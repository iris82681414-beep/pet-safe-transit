package com.sky.logistics.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CargoLocationVO {
    private String name;
    private Double lat;
    private Double lng;
}
