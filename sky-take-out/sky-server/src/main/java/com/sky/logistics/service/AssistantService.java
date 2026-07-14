package com.sky.logistics.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

public interface AssistantService {

    Map<String, Object> chat(String question, String sessionId, String authorization, String cargoId);

    SseEmitter chatStream(String question, String sessionId, String authorization, String cargoId);

    List<String> getSuggestions(String role);

    List<Map<String, Object>> getMessages(String sessionId);
}
