package com.sky.logistics.entity;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
public class KnowledgeDocument {

    private String documentId;
    private String title;
    private String category;
    private String objectKey;
    private String status;
    private Integer chunkCount;
    private OffsetDateTime createdAt;

    public static KnowledgeDocument create(String title, String category, String objectKey) {
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.documentId = "DOC-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        doc.title = title;
        doc.category = category;
        doc.objectKey = objectKey;
        doc.status = "UPLOADED";
        doc.chunkCount = 0;
        return doc;
    }
}
