package com.sky.logistics.vo;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Builder
public class FaceStatusVO {

    private String userId;
    private Boolean bound;
    private String groupId;
    private String baiduUserId;
    private String faceImageUrl;
    private OffsetDateTime lastUpdatedAt;
}
