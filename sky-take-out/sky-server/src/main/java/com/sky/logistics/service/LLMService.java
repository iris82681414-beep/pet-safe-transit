package com.sky.logistics.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Service
@Slf4j
public class LLMService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LLMService() {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(60000);
        this.restTemplate = new RestTemplate(factory);
    }

    @Value("${ai.llm.endpoint}")
    private String endpoint;

    @Value("${ai.llm.api-key}")
    private String apiKey;

    @Value("${ai.llm.model}")
    private String model;

    private List<Map<String, String>> buildMessages(String systemPrompt, String userMessage) {
        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> systemMsg = new LinkedHashMap<>();
        systemMsg.put("role", "system");
        systemMsg.put("content", systemPrompt);
        messages.add(systemMsg);
        Map<String, String> userMsg = new LinkedHashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", userMessage);
        messages.add(userMsg);
        return messages;
    }

    private String buildRequestBody(List<Map<String, String>> messages, boolean stream) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("messages", messages);
            body.put("stream", stream);
            body.put("temperature", 0.7);
            body.put("max_tokens", 1024);
            return objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new IllegalStateException("构造 LLM 请求体失败", e);
        }
    }

    public String chat(String systemPrompt, String userMessage) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            List<Map<String, String>> messages = buildMessages(systemPrompt, userMessage);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("messages", messages);
            body.put("temperature", 0.7);
            body.put("max_tokens", 1024);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    endpoint + "/v1/chat/completions", request, Map.class);

            Map<String, Object> responseBody = response.getBody();
            List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            String content = (String) message.get("content");

            log.debug("LLM 响应: preview={}", content.substring(0, Math.min(100, content.length())));
            return content;

        } catch (Exception e) {
            log.error("LLM API 调用失败: {}", e.getMessage());
            return "抱歉，AI 服务暂时不可用，请稍后重试。";
        }
    }

    /**
     * 使用 OpenAI 兼容的 tools/tool_calls 协议让大模型选择业务函数。
     * 这里只做“选工具 + 生成参数”，具体函数由前端注册表在用户确认后执行。
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> chooseTool(String systemPrompt, String userMessage,
                                           List<Map<String, Object>> tools) {
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("未配置 ai.llm.api-key");
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("messages", buildMessages(systemPrompt, userMessage));
            body.put("tools", tools);
            body.put("tool_choice", "auto");
            body.put("temperature", 0.1);
            body.put("max_tokens", 768);
            body.put("stream", false);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    endpoint + "/v1/chat/completions", request, Map.class);
            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null) throw new IllegalStateException("LLM 返回空响应");

            List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
            if (choices == null || choices.isEmpty()) throw new IllegalStateException("LLM 返回中没有 choices");
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            if (message == null) throw new IllegalStateException("LLM 返回中没有 message");

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("content", message.get("content"));
            List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) message.get("tool_calls");
            if (toolCalls != null && !toolCalls.isEmpty()) {
                Map<String, Object> toolCall = toolCalls.get(0);
                Map<String, Object> function = (Map<String, Object>) toolCall.get("function");
                if (function == null) throw new IllegalStateException("tool_calls 缺少 function");
                String name = String.valueOf(function.get("name"));
                String rawArguments = String.valueOf(function.getOrDefault("arguments", "{}"));
                Map<String, Object> arguments = objectMapper.readValue(rawArguments, Map.class);
                result.put("toolCallId", toolCall.get("id"));
                result.put("name", name);
                result.put("arguments", arguments);
            }
            return result;
        } catch (Exception e) {
            log.warn("LLM Function Calling 失败，将使用规则兜底: {}", e.getMessage());
            throw new IllegalStateException("LLM Function Calling 失败", e);
        }
    }

    public void chatStream(String systemPrompt, String userMessage, Consumer<String> onToken,
                            Runnable onComplete, Consumer<Exception> onError) {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                List<Map<String, String>> messages = buildMessages(systemPrompt, userMessage);
                String requestBody = buildRequestBody(messages, true);

                URL url = new URL(endpoint + "/v1/chat/completions");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);
                conn.setDoOutput(true);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(60000);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(requestBody.getBytes(StandardCharsets.UTF_8));
                    os.flush();
                }

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("data: ")) {
                            String data = line.substring(6);
                            if ("[DONE]".equals(data)) continue;
                            try {
                                JsonNode root = objectMapper.readTree(data);
                                JsonNode content = root.path("choices").path(0).path("delta").path("content");
                                if (!content.isMissingNode() && !content.isNull()) {
                                    onToken.accept(content.asText());
                                }
                            } catch (Exception ex) {
                                log.warn("解析 LLM 流式片段失败: {}", ex.getMessage());
                            }
                        }
                    }
                }
                onComplete.run();

            } catch (Exception e) {
                log.error("LLM 流式调用失败: {}", e.getMessage());
                onError.accept(e);
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    public String rewriteQuery(String originalQuestion) {
        String systemPrompt = "你是一个查询改写助手。将用户的口语化问题改写为更适合信息检索的关键词形式，保留关键实体（车牌号、货物ID、告警类型等）。只输出改写后的关键词，不要解释。";
        return chat(systemPrompt, originalQuestion);
    }
}
