package com.sky.logistics.entity;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
public class KnowledgeChunk {

    private String chunkId;
    private String documentId;
    private Integer chunkIndex;
    private String content;
    private String embedding;
    private OffsetDateTime createdAt;

    public static KnowledgeChunk create(String documentId, int chunkIndex, String content, String embedding) {
        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.chunkId = "CHK-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        chunk.documentId = documentId;
        chunk.chunkIndex = chunkIndex;
        chunk.content = content;
        chunk.embedding = embedding;
        return chunk;
    }
}
