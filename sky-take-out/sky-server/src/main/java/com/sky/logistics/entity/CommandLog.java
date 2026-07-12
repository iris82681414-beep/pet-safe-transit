package com.sky.logistics.entity;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class CommandLog {
    private String id;
    private String commandId;
    private String status;
    private String source;
    private String rawPayload;
    private OffsetDateTime createdAt;
}
