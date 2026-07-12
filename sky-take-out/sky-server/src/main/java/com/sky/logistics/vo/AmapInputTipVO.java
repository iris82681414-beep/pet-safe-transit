package com.sky.logistics.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AmapInputTipVO {
    private String name;
    private String district;
    private String address;
    private Double lng;
    private Double lat;
    private String adcode;
}
