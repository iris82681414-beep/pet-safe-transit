package com.sky.logistics.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AmapAddressVO {
    private String formattedAddress;
    private String province;
    private String city;
    private String district;
    private String road;
    private String poiName;
    private Double lng;
    private Double lat;
    private String level;
    private String adcode;
    private String source;
}
