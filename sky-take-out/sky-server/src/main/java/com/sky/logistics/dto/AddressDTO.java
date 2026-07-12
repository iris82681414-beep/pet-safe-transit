package com.sky.logistics.dto;

import lombok.Data;

@Data
public class AddressDTO {
    private String province;
    private String city;
    private String district;
    private String detail;
    private Double lng;
    private Double lat;
}
