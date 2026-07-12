package com.sky.logistics.entity;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class Command {
    private String commandId;
    private String plate;
    private String vinTopic;
    private String commandType;
    private String priority;
    private String payload;
    private String status;
    private String mqttTopic;
    private String createdBy;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
