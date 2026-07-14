package com.sky.logistics.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class EmbeddingService {

    private final RestTemplate restTemplate;

    public EmbeddingService() {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(30000);
        this.restTemplate = new RestTemplate(factory);
        this.objectMapper = new ObjectMapper();
    }

    private final ObjectMapper objectMapper;

    @Value("${ai.embedding.endpoint}")
    private String endpoint;

    @Value("${ai.embedding.api-key}")
    private String apiKey;

    @Value("${ai.embedding.model}")
    private String model;

    public List<Double> embed(String text) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("input", text);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    endpoint + "/v1/embeddings", request, Map.class);

            Map<String, Object> data = (Map<String, Object>) response.getBody();
            List<Map<String, Object>> dataList = (List<Map<String, Object>>) data.get("data");
            List<Double> embedding = (List<Double>) dataList.get(0).get("embedding");

            log.debug("Embedding 生成成功, 维度={}, 预览={}", embedding.size(),
                    embedding.subList(0, Math.min(3, embedding.size())));
            return embedding;

        } catch (Exception e) {
            log.error("Embedding API 调用失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    public List<List<Double>> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return new ArrayList<>();
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("input", texts);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    endpoint + "/v1/embeddings", request, Map.class);

            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null || !(responseBody.get("data") instanceof List)) {
                throw new IllegalStateException("Embedding 批量响应缺少 data");
            }

            List<Map<String, Object>> dataList = (List<Map<String, Object>>) responseBody.get("data");
            dataList.sort(Comparator.comparingInt(item -> {
                Object index = item.get("index");
                return index instanceof Number ? ((Number) index).intValue() : 0;
            }));

            List<List<Double>> result = new ArrayList<>();
            for (Map<String, Object> item : dataList) {
                Object vector = item.get("embedding");
                result.add(vector instanceof List ? (List<Double>) vector : new ArrayList<>());
            }
            if (result.size() != texts.size()) {
                throw new IllegalStateException("Embedding 批量响应数量不一致");
            }
            log.debug("Embedding 批量生成成功, 数量={}", result.size());
            return result;
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden e) {
            log.error("Embedding API 鉴权失败，请检查 SiliconFlow API Key");
            return emptyBatch(texts.size());
        } catch (Exception e) {
            log.warn("Embedding 批量调用失败，降级为逐条生成: {}", e.getMessage());
            return texts.stream().map(this::embed).collect(Collectors.toList());
        }
    }

    public String embedToString(String text) {
        List<Double> vector = embed(text);
        return vectorToString(vector);
    }

    public String vectorToString(List<Double> vector) {
        if (vector == null || vector.isEmpty()) return "";
        return "[" + vector.stream().map(String::valueOf).collect(Collectors.joining(",")) + "]";
    }

    private List<List<Double>> emptyBatch(int size) {
        List<List<Double>> result = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            result.add(new ArrayList<>());
        }
        return result;
    }
}
