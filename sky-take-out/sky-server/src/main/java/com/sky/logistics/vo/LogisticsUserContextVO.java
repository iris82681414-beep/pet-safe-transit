package com.sky.logistics.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LogisticsUserContextVO {
    private String userId;
    private String username;
    private String name;
    private String role;
}
