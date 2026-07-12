package com.sky.logistics.dto;

import lombok.Data;

import java.util.List;

@Data
public class UnloadAddressAbnormalDTO {
    private String type;
    private String description;
    private Double lng;
    private Double lat;
    private List<String> photos;
}
