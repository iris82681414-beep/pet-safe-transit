package com.sky.logistics.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** 豆包 Seed Audio 非流式语音生成服务。 */
@Service
@Slf4j
public class DoubaoTtsService {

    @Value("${doubao.tts.endpoint:https://openspeech.bytedance.com/api/v3/tts/create}")
    private String endpoint;
    @Value("${doubao.tts.api-key:}")
    private String apiKey;
    @Value("${doubao.tts.model:seed-audio-1.0}")
    private String model;
    @Value("${doubao.tts.speaker:zh_male_xionger_uranus_bigtts}")
    private String speaker;

    private final RestTemplate restTemplate;

    public DoubaoTtsService() {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(8000);
        factory.setReadTimeout(120000);
        this.restTemplate = new RestTemplate(factory);
    }

    public byte[] synthesize(String text) throws IOException {
        if (!StringUtils.hasText(apiKey)) throw new IOException("未配置豆包语音 API Key");
        String normalized = text == null ? "" : text.trim();
        if (!StringUtils.hasText(normalized)) throw new IOException("播报文字为空");
        String instruction = "请使用可爱、温柔、活泼的年轻中文女声自然播报，语气亲切但不要夸张：";
        int maxTextLength = 3000 - instruction.length();
        if (normalized.length() > maxTextLength) normalized = normalized.substring(0, maxTextLength);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("text_prompt", instruction + normalized);
        body.put("references", Collections.singletonList(map("speaker", speaker)));
        body.put("audio_config", map(
                "format", "mp3",
                "sample_rate", 24000,
                "speech_rate", 4,
                "loudness_rate", 0,
                "pitch_rate", 2,
                "enable_subtitle", false
        ));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.set("X-Api-Key", apiKey);

        try {
            String requestId = UUID.randomUUID().toString();
            headers.set("X-Api-Request-Id", requestId);
            ResponseEntity<Map> response = restTemplate.exchange(
                    endpoint,
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    Map.class
            );
            Map<?, ?> result = response.getBody();
            if (result == null) throw new IOException("豆包语音接口未返回数据");
            int code = result.get("code") instanceof Number ? ((Number) result.get("code")).intValue() : -1;
            if (code != 0) throw new IOException("豆包语音生成失败: " + String.valueOf(result.get("message")));
            String audio = String.valueOf(result.get("audio"));
            if (!StringUtils.hasText(audio) || "null".equals(audio)) throw new IOException("豆包语音接口未返回音频");
            byte[] decoded = Base64.getDecoder().decode(audio);
            if (decoded.length == 0) throw new IOException("豆包语音接口返回了空音频");
            log.info("豆包 TTS 完成, requestId={}, logId={}, duration={}s, bytes={}",
                    requestId, response.getHeaders().getFirst("X-Tt-Logid"), result.get("duration"), decoded.length);
            return decoded;
        } catch (RestClientResponseException e) {
            log.error("豆包 TTS HTTP 请求失败: status={}, body={}", e.getRawStatusCode(), e.getResponseBodyAsString());
            throw new IOException("豆包语音服务请求失败: HTTP " + e.getRawStatusCode(), e);
        } catch (Exception e) {
            log.error("豆包 TTS 失败: {}", e.getMessage());
            if (e instanceof IOException) throw (IOException) e;
            throw new IOException("豆包语音生成失败: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> map(Object... pairs) {
        Map<String, Object> value = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) value.put(String.valueOf(pairs[i]), pairs[i + 1]);
        return value;
    }
}
