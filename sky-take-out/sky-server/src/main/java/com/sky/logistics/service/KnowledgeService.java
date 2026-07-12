package com.sky.logistics.service;

import com.sky.logistics.common.PageResponse;

import java.util.Map;

public interface KnowledgeService {

    Map<String, Object> upload(String title, String category, String objectKey);

    PageResponse<Map<String, Object>> listDocuments(Integer page, Integer size, String title, String category);

    Map<String, Object> getDocument(String documentId);

    Map<String, Object> indexDocument(String documentId);

    void deleteDocument(String documentId);
}
