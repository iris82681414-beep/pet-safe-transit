package com.sky.logistics.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoiceCommandLog {
    private String id;
    private String userId;
    private String recognizedText;
    private String intent;
    private String actionType;
    private String actionJson;
    private Boolean needConfirm;
    private Boolean confirmed;
    private Boolean executed;
    private String reply;
    private String sourcePage;
    private OffsetDateTime createdAt;
}
