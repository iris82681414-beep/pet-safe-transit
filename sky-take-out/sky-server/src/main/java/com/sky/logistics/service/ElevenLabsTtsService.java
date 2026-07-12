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

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** ElevenLabs 桌宠语音播报服务。 */
@Service
@Slf4j
public class ElevenLabsTtsService {

    @Value("${elevenlabs.endpoint:https://api.elevenlabs.io/v1}")
    private String endpoint;
    @Value("${elevenlabs.api-key}")
    private String apiKey;
    @Value("${elevenlabs.voice-id:JBFqnCBsd6RMkjVDRZzb}")
    private String voiceId;
    @Value("${elevenlabs.model-id:eleven_v3}")
    private String modelId;
    @Value("${elevenlabs.output-format:mp3_44100_128}")
    private String outputFormat;

    private final RestTemplate restTemplate;

    public ElevenLabsTtsService() {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(8000);
        factory.setReadTimeout(90000);
        this.restTemplate = new RestTemplate(factory);
    }

    public byte[] synthesize(String text) throws IOException {
        if (!StringUtils.hasText(apiKey)) throw new IOException("未配置 ElevenLabs API Key");
        String normalized = text == null ? "" : text.trim();
        if (!StringUtils.hasText(normalized)) throw new IOException("播报文字为空");
        if (normalized.length() > 3000) normalized = normalized.substring(0, 3000);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("text", normalized);
        body.put("model_id", modelId);
        body.put("language_code", "zh");
        body.put("voice_settings", map("stability", 0.48, "similarity_boost", 0.72, "style", 0.25, "use_speaker_boost", true));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.Collections.singletonList(MediaType.valueOf("audio/mpeg")));
        headers.set("xi-api-key", apiKey);
        String url = endpoint.replaceAll("/$", "") + "/text-to-speech/" + voiceId + "?output_format=" + outputFormat;
        try {
            ResponseEntity<byte[]> response = restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), byte[].class);
            byte[] audio = response.getBody();
            if (audio == null || audio.length == 0) throw new IOException("ElevenLabs 未返回音频");
            return audio;
        } catch (Exception e) {
            log.error("ElevenLabs TTS 失败: {}", e.getMessage());
            if (e instanceof IOException) throw (IOException) e;
            throw new IOException("ElevenLabs 语音合成失败: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> map(Object... pairs) {
        Map<String, Object> value = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) value.put(String.valueOf(pairs[i]), pairs[i + 1]);
        return value;
    }
}
