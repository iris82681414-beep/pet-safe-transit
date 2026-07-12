package com.sky.logistics.entity;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class AddressChangeLog {
    private Long id;
    private String requestId;
    private String operatorId;
    private String operatorRole;
    private String action;
    private String remark;
    private OffsetDateTime createdAt;
}
