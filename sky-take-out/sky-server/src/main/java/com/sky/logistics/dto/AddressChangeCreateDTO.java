package com.sky.logistics.dto;

import lombok.Data;

@Data
public class AddressChangeCreateDTO {
    private AddressDTO newAddress;
    private String contactName;
    private String contactPhone;
    private String reason;
}
