package com.sky.logistics.dto;

import lombok.Data;

@Data
public class StatusVerifyDTO {
    private String reportedStatus;
    private GeoPointDTO reportLocation;
}
