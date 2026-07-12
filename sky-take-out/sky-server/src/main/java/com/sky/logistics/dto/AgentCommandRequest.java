package com.sky.logistics.dto;

import lombok.Data;

/**
 * 文本 → Action 请求
 * {"text": "定位沪A C0291", "sourcePage": "FleetOverview"}
 */
@Data
public class AgentCommandRequest {
    private String text;
    private String sourcePage;
    private String selectedEntityId;
    private String selectedEntityType;
}
