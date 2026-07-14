package com.sky.logistics.service;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SemanticMarkdownChunkerTest {

    @Test
    void splitsAtSemanticChangeAndKeepsHeadingContext() {
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        when(embeddingService.embedBatch(anyList())).thenReturn(Arrays.asList(
                Arrays.asList(1.0, 0.0),
                Arrays.asList(0.9, 0.1),
                Arrays.asList(-1.0, 0.0),
                Arrays.asList(-0.9, 0.1)
        ));
        SemanticMarkdownChunker chunker = new SemanticMarkdownChunker(embeddingService, 20, 500, 30);

        String markdown = "# 宠物运输规范\n\n"
                + "## 温控与应急\n\n"
                + "### 温控标准\n\n车厢温度保持在安全范围。\n\n温度异常时立即告警。\n\n"
                + "### 逃逸处置\n\n发现逃逸后启动定位。\n\n通知调度员建立应急工单。";

        List<String> chunks = chunker.split(markdown);

        assertEquals(2, chunks.size());
        assertTrue(chunks.get(0).contains("### 温控标准"));
        assertTrue(chunks.get(1).contains("### 逃逸处置"));
        assertTrue(chunks.stream().allMatch(chunk -> chunk.startsWith("# 宠物运输规范")));
    }

    @Test
    void alwaysSeparatesSecondLevelMarkdownSections() {
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        when(embeddingService.embedBatch(anyList())).thenReturn(Arrays.asList(
                Arrays.asList(1.0, 0.0),
                Arrays.asList(1.0, 0.0)
        ));
        SemanticMarkdownChunker chunker = new SemanticMarkdownChunker(embeddingService, 20, 500, 30);

        List<String> chunks = chunker.split("# 规范\n## 运输要求\n保持通风并固定笼具。\n## 签收要求\n本人持身份证签收。 ");

        assertEquals(2, chunks.size());
        assertTrue(chunks.get(0).contains("## 运输要求"));
        assertTrue(chunks.get(1).contains("## 签收要求"));
    }
}
