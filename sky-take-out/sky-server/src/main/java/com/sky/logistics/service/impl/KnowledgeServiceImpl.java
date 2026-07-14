package com.sky.logistics.service.impl;

import com.sky.logistics.common.PageResponse;
import com.sky.logistics.entity.KnowledgeChunk;
import com.sky.logistics.entity.KnowledgeDocument;
import com.sky.logistics.mapper.KnowledgeMapper;
import com.sky.logistics.service.EmbeddingService;
import com.sky.logistics.service.KnowledgeService;
import com.sky.logistics.service.MinioService;
import com.sky.logistics.service.SemanticMarkdownChunker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class KnowledgeServiceImpl implements KnowledgeService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private final KnowledgeMapper knowledgeMapper;
    private final MinioService minioService;
    private final EmbeddingService embeddingService;
    private final SemanticMarkdownChunker semanticMarkdownChunker;
    private final String chunkingStrategy;

    public KnowledgeServiceImpl(KnowledgeMapper knowledgeMapper, MinioService minioService,
                                 EmbeddingService embeddingService,
                                 SemanticMarkdownChunker semanticMarkdownChunker,
                                 @Value("${knowledge.chunking.strategy:semantic}") String chunkingStrategy) {
        this.knowledgeMapper = knowledgeMapper;
        this.minioService = minioService;
        this.embeddingService = embeddingService;
        this.semanticMarkdownChunker = semanticMarkdownChunker;
        this.chunkingStrategy = chunkingStrategy;
    }

    @Override
    public Map<String, Object> upload(String title, String category, String objectKey) {
        KnowledgeDocument doc = KnowledgeDocument.create(title, category, objectKey);
        knowledgeMapper.insertDocument(doc);
        log.info("知识库文档已创建, documentId={}, title={}", doc.getDocumentId(), title);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("documentId", doc.getDocumentId());
        result.put("title", doc.getTitle());
        result.put("category", doc.getCategory());
        result.put("status", doc.getStatus());
        return result;
    }

    @Override
    public PageResponse<Map<String, Object>> listDocuments(Integer page, Integer size, String title, String category) {
        int p = page != null && page > 0 ? page : DEFAULT_PAGE;
        int s = size != null && size > 0 ? Math.min(size, MAX_SIZE) : DEFAULT_SIZE;
        int offset = (p - 1) * s;

        Long total = knowledgeMapper.countDocuments(title, category);
        if (total == null || total == 0) {
            return PageResponse.of(new ArrayList<>(), p, s);
        }

        List<KnowledgeDocument> docs = knowledgeMapper.findDocuments(title, category, offset, s);
        List<Map<String, Object>> content = new ArrayList<>();
        for (KnowledgeDocument doc : docs) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("documentId", doc.getDocumentId());
            item.put("title", doc.getTitle());
            item.put("category", doc.getCategory());
            item.put("status", doc.getStatus());
            item.put("chunkCount", doc.getChunkCount());
            item.put("createdAt", doc.getCreatedAt() != null ? doc.getCreatedAt().toString() : null);
            content.add(item);
        }

        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / s);
        return new PageResponse<>(content, p, s, total, totalPages);
    }

    @Override
    public Map<String, Object> getDocument(String documentId) {
        KnowledgeDocument doc = knowledgeMapper.findDocumentById(documentId);
        if (doc == null) {
            throw new IllegalArgumentException("文档不存在");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("documentId", doc.getDocumentId());
        result.put("title", doc.getTitle());
        result.put("category", doc.getCategory());
        result.put("objectKey", doc.getObjectKey());
        result.put("status", doc.getStatus());
        result.put("chunkCount", doc.getChunkCount());
        result.put("createdAt", doc.getCreatedAt() != null ? doc.getCreatedAt().toString() : null);
        return result;
    }

    @Override
    public Map<String, Object> indexDocument(String documentId) {
        KnowledgeDocument doc = knowledgeMapper.findDocumentById(documentId);
        if (doc == null) {
            throw new IllegalArgumentException("文档不存在");
        }
        if ("INDEXING".equals(doc.getStatus())) {
            throw new IllegalArgumentException("文档正在索引中，请稍后再试");
        }

        knowledgeMapper.updateDocumentStatus(documentId, "INDEXING", 0);
        log.info("开始索引文档, documentId={}, objectKey={}", documentId, doc.getObjectKey());

        String content;
        List<String> snippets;
        try {
            content = readMinioFile(doc.getObjectKey());
            snippets = splitMarkdown(content);
            log.info("文档切片完成, documentId={}, strategy={}, 切片数={}",
                    documentId, chunkingStrategy, snippets.size());
        } catch (Exception e) {
            log.error("读取文档内容失败, documentId={}: {}", documentId, e.getMessage());
            knowledgeMapper.updateDocumentStatus(documentId, "FAILED", 0);
            throw new RuntimeException("读取文档内容失败: " + e.getMessage(), e);
        }

        if (snippets.isEmpty()) {
            knowledgeMapper.updateDocumentStatus(documentId, "FAILED", 0);
            log.warn("文档切片为空, documentId={}", documentId);
            throw new RuntimeException("文档内容为空，无法索引");
        }

        List<KnowledgeChunk> chunkEntities = new ArrayList<>();
        List<List<Double>> chunkEmbeddings = embeddingService.embedBatch(snippets);
        for (int i = 0; i < snippets.size(); i++) {
            List<Double> vector = i < chunkEmbeddings.size() ? chunkEmbeddings.get(i) : null;
            String embeddingStr = embeddingService.vectorToString(vector);
            if (embeddingStr.isEmpty()) {
                log.warn("切片 {} embedding 失败，跳过, documentId={}", i, documentId);
                continue;
            }
            KnowledgeChunk chunk = KnowledgeChunk.create(documentId, i, snippets.get(i), embeddingStr);
            chunkEntities.add(chunk);
        }

        if (chunkEntities.isEmpty()) {
            knowledgeMapper.updateDocumentStatus(documentId, "FAILED", 0);
            throw new RuntimeException("所有切片 embedding 均失败");
        }

        try {
            insertChunksTransactional(chunkEntities, documentId);
        } catch (Exception e) {
            log.error("写入 chunks 失败, documentId={}: {}", documentId, e.getMessage());
            knowledgeMapper.updateDocumentStatus(documentId, "FAILED", 0);
            throw new RuntimeException("写入切片数据失败: " + e.getMessage(), e);
        }

        log.info("文档索引完成, documentId={}, 成功切片数={}", documentId, chunkEntities.size());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("documentId", documentId);
        result.put("status", "INDEXED");
        result.put("chunkCount", chunkEntities.size());
        return result;
    }

    @Transactional
    void insertChunksTransactional(List<KnowledgeChunk> chunks, String documentId) {
        for (KnowledgeChunk chunk : chunks) {
            knowledgeMapper.insertChunk(chunk);
        }
        knowledgeMapper.updateDocumentStatus(documentId, "INDEXED", chunks.size());
    }

    @Override
    @Transactional
    public void deleteDocument(String documentId) {
        KnowledgeDocument doc = knowledgeMapper.findDocumentById(documentId);
        if (doc == null) {
            throw new IllegalArgumentException("文档不存在");
        }
        knowledgeMapper.deleteChunksByDocumentId(documentId);
        knowledgeMapper.deleteDocument(documentId);
        try {
            minioService.delete(doc.getObjectKey());
        } catch (Exception e) {
            log.warn("删除 MinIO 文件失败, key={}: {}", doc.getObjectKey(), e.getMessage());
        }
        log.info("知识库文档已删除, documentId={}", documentId);
    }

    private String readMinioFile(String objectKey) throws Exception {
        try (InputStream is = minioService.download(objectKey);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String content = reader.lines().collect(Collectors.joining("\n"));
            return content.replace("\r\n", "\n").replace("\r", "\n");
        }
    }

    List<String> splitMarkdown(String content) {
        if (!"semantic".equalsIgnoreCase(chunkingStrategy)) {
            throw new IllegalStateException("不支持的知识库切块策略: " + chunkingStrategy);
        }
        return semanticMarkdownChunker.split(content);
    }
}
