package com.sky.logistics.entity;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class UnloadAddressRecord {
    private Long id;
    private String recordId;
    private String orderId;
    private String recordType;
    private String address;
    private Double lng;
    private Double lat;
    private String abnormalType;
    private String description;
    private String photos;
    private String remark;
    private String operatorId;
    private String operatorRole;
    private OffsetDateTime createdAt;
}
