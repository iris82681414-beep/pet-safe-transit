package com.sky.logistics.vo;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Builder
public class FaceBindingVO {

    private String userId;
    private String baiduUserId;
    private String groupId;
    private String faceImageUrl;
    private OffsetDateTime registeredAt;
    private OffsetDateTime updatedAt;
    private Boolean deleted;
}
