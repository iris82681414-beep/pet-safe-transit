package com.sky.logistics.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/** SiliconFlow SenseVoice 语音转文字服务。 */
@Service
@Slf4j
public class SiliconFlowAsrService {

    @Value("${siliconflow.asr.endpoint}")
    private String endpoint;

    @Value("${siliconflow.asr.api-key}")
    private String apiKey;

    @Value("${siliconflow.asr.model:FunAudioLLM/SenseVoiceSmall}")
    private String model;

    private final RestTemplate restTemplate;

    public SiliconFlowAsrService() {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(8000);
        factory.setReadTimeout(90000);
        this.restTemplate = new RestTemplate(factory);
    }

    @SuppressWarnings("unchecked")
    public String recognize(MultipartFile audioFile) throws IOException {
        if (!StringUtils.hasText(apiKey)) throw new IOException("未配置 SiliconFlow ASR API Key");
        if (audioFile == null || audioFile.isEmpty()) throw new IOException("录音文件为空");

        String filename = StringUtils.hasText(audioFile.getOriginalFilename())
                ? audioFile.getOriginalFilename() : "voice.wav";
        ByteArrayResource resource = new ByteArrayResource(audioFile.getBytes()) {
            @Override public String getFilename() { return filename; }
        };

        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(resolveContentType(filename, audioFile.getContentType()));
        LinkedMultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new HttpEntity<>(resource, fileHeaders));
        form.add("model", model);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setAccept(java.util.Collections.singletonList(MediaType.APPLICATION_JSON));

        log.info("SiliconFlow ASR 请求, model={}, file={}, size={}", model, filename, audioFile.getSize());
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(endpoint, new HttpEntity<>(form, headers), Map.class);
            Map<String, Object> body = response.getBody();
            String text = body == null ? null : String.valueOf(body.getOrDefault("text", ""));
            if (!StringUtils.hasText(text)) throw new IOException("SiliconFlow 未返回识别文字");
            log.info("SiliconFlow ASR 识别成功, chars={}", text.length());
            return text.trim();
        } catch (Exception e) {
            log.error("SiliconFlow ASR 失败: {}", e.getMessage());
            if (e instanceof IOException) throw (IOException) e;
            throw new IOException("SiliconFlow 语音识别失败: " + e.getMessage(), e);
        }
    }

    private MediaType resolveContentType(String filename, String supplied) {
        if (StringUtils.hasText(supplied)) {
            try { return MediaType.parseMediaType(supplied); } catch (Exception ignored) {}
        }
        String lower = filename.toLowerCase();
        if (lower.endsWith(".mp3")) return MediaType.valueOf("audio/mpeg");
        if (lower.endsWith(".m4a")) return MediaType.valueOf("audio/mp4");
        if (lower.endsWith(".webm")) return MediaType.valueOf("audio/webm");
        return MediaType.valueOf("audio/wav");
    }
}
