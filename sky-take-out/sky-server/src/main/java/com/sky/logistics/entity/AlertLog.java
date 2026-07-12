package com.sky.logistics.entity;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class AlertLog {
    private String id;
    private String alertId;
    private String operatorId;
    private String operatorName;
    private String action;
    private String remark;
    private OffsetDateTime createdAt;
}
