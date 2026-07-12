package com.sky.logistics.entity;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class LogisticsUser {

    private String id;
    private String username;
    private String passwordHash;
    private String name;
    private String role;
    private String phone;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
