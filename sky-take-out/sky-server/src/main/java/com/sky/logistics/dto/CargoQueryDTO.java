package com.sky.logistics.dto;

import lombok.Data;

@Data
public class CargoQueryDTO {
    private String status;
    private String keyword;
    private String ownerId;
    private Integer page;
    private Integer size;
}
