package com.sky.logistics.dto;

import lombok.Data;

@Data
public class FaceLoginDTO {
    private String imageBase64;
    private String deviceId;
    private String clientTime;
}
