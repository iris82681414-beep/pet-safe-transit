package com.sky.logistics.entity;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class FaceBinding {

    private String id;
    private String userId;
    private String groupId;
    private String baiduUserId;
    private String faceImageUrl;
    private String faceImageObjectKey;
    private Boolean enabled;
    private OffsetDateTime createdAt;
    private OffsetDateTime lastUpdatedAt;
}
