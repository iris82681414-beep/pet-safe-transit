package com.sky.logistics.dto;

import lombok.Data;

@Data
public class UnloadAddressConfirmDTO {
    private String address;
    private Double lng;
    private Double lat;
    private String remark;
}
