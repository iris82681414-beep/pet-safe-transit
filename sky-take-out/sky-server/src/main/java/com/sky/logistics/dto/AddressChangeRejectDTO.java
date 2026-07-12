package com.sky.logistics.dto;

import lombok.Data;

@Data
public class AddressChangeRejectDTO {
    private String reason;
    private String suggestion;
}
