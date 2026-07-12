package com.sky.logistics.dto;

import lombok.Data;

@Data
public class GpsPointDTO {
    private String time;
    private Double lng;
    private Double lat;
    private Integer speed;
    private Integer heading;
    private Double accuracy;
}
