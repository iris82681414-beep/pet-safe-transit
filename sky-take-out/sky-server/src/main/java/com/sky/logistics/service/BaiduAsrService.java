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
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** 百度智能云短语音识别服务，接收前端生成的 16kHz 单声道 WAV。 */
@Service
@Slf4j
public class BaiduAsrService {

    private static final String TOKEN_URL = "https://aip.baidubce.com/oauth/2.0/token";

    @Value("${baidu.asr.endpoint:https://vop.baidu.com/server_api}")
    private String endpoint;

    @Value("${baidu.asr.api-key:}")
    private String apiKey;

    @Value("${baidu.asr.secret-key:}")
    private String secretKey;

    @Value("${baidu.asr.dev-pid:1537}")
    private int devPid;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private volatile String cachedToken;
    private volatile long tokenExpireTime;

    public BaiduAsrService() {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(8000);
        factory.setReadTimeout(90000);
        this.restTemplate = new RestTemplate(factory);
    }

    public String recognize(MultipartFile audioFile) throws IOException {
        if (!StringUtils.hasText(apiKey) || !StringUtils.hasText(secretKey)) {
            throw new IOException("未配置百度语音识别 API Key 或 Secret Key");
        }
        if (audioFile == null || audioFile.isEmpty()) throw new IOException("录音文件为空");

        byte[] audio = audioFile.getBytes();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("format", "wav");
        body.put("rate", 16000);
        body.put("channel", 1);
        body.put("cuid", "pet-safe-transit-" + UUID.randomUUID());
        body.put("token", getAccessToken());
        body.put("dev_pid", devPid);
        body.put("speech", Base64.getEncoder().encodeToString(audio));
        body.put("len", audio.length);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    endpoint, new HttpEntity<>(body, headers), String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            int errorNumber = root.path("err_no").asInt(-1);
            if (errorNumber != 0) {
                throw new IOException(readableError(errorNumber, root.path("err_msg").asText("未知错误")));
            }
            JsonNode results = root.path("result");
            String text = results.isArray() && results.size() > 0 ? results.get(0).asText().trim() : "";
            if (!StringUtils.hasText(text)) throw new IOException("百度语音识别未返回文字");
            log.info("百度 ASR 识别成功, bytes={}, chars={}", audio.length, text.length());
            return text;
        } catch (RestClientResponseException e) {
            log.error("百度 ASR HTTP 请求失败: status={}, body={}", e.getRawStatusCode(), e.getResponseBodyAsString());
            throw new IOException("百度语音识别服务请求失败: HTTP " + e.getRawStatusCode(), e);
        } catch (IOException e) {
            log.warn("百度 ASR 识别失败: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("百度 ASR 识别失败: {}", e.getMessage());
            throw new IOException("百度语音识别失败: " + e.getMessage(), e);
        }
    }

    private synchronized String getAccessToken() throws IOException {
        if (StringUtils.hasText(cachedToken) && System.currentTimeMillis() < tokenExpireTime) return cachedToken;
        String url = TOKEN_URL + "?grant_type=client_credentials&client_id=" + apiKey + "&client_secret=" + secretKey;
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, null, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            String token = root.path("access_token").asText();
            if (!StringUtils.hasText(token)) {
                throw new IOException("获取百度语音 access_token 失败: "
                        + root.path("error_description").asText(root.path("error").asText("未知错误")));
            }
            cachedToken = token;
            int expiresIn = root.path("expires_in").asInt(2592000);
            tokenExpireTime = System.currentTimeMillis() + Math.max(60, expiresIn - 3600) * 1000L;
            return cachedToken;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("获取百度语音 access_token 失败: " + e.getMessage(), e);
        }
    }

    private String readableError(int code, String message) {
        if (code == 3301) return "没有识别到清晰语音，请靠近麦克风后重试";
        if (code == 3302) return "录音质量过低，请检查麦克风或减少环境噪声";
        if (code == 3304 || code == 3305) return "百度语音识别额度不足或请求过于频繁";
        if (code == 3310 || code == 3311) return "录音格式不受支持，请重新录音";
        return "百度语音识别失败（" + code + "）：" + message;
    }
}
