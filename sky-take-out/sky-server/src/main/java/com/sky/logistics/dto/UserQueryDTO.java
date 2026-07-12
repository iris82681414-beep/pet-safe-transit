package com.sky.logistics.dto;

import lombok.Data;

@Data
public class UserQueryDTO {
    private String role;
    private String keyword;
    private Integer page;
    private Integer size;
}
