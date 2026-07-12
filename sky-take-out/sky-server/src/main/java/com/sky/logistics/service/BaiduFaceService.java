package com.sky.logistics.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@Slf4j
public class BaiduFaceService {

    @Value("${baidu.face.api-key:}")
    private String apiKey;

    @Value("${baidu.face.secret-key:}")
    private String secretKey;

    @Value("${baidu.face.group-id:logistics_user}")
    private String groupId;

    @Value("${baidu.face.threshold:80}")
    private BigDecimal threshold;

    private static final String TOKEN_URL = "https://aip.baidubce.com/oauth/2.0/token";
    private static final String SEARCH_URL = "https://aip.baidubce.com/rest/2.0/face/v3/search";
    private static final String ADD_URL = "https://aip.baidubce.com/rest/2.0/face/v3/faceset/user/add";
    private static final String UPDATE_URL = "https://aip.baidubce.com/rest/2.0/face/v3/faceset/user/update";
    private static final String DELETE_URL = "https://aip.baidubce.com/rest/2.0/face/v3/faceset/user/delete";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private volatile String cachedToken;
    private volatile long tokenExpireTime;

    public boolean configured() {
        return StringUtils.hasText(apiKey) && StringUtils.hasText(secretKey);
    }

    public String defaultGroupId() {
        return groupId;
    }

    public FaceMatchResult search(String imageBase64) {
        if (!configured()) {
            log.warn("百度人脸 Key 未配置，使用 dispatcher 演示兜底登录");
            return new FaceMatchResult("dispatcher", BigDecimal.valueOf(100), threshold, true, "LOCAL_DEMO");
        }

        JsonNode root = postJson(SEARCH_URL, toMap(
                "image", imageBase64,
                "image_type", "BASE64",
                "group_id_list", groupId,
                "quality_control", "NORMAL",
                "liveness_control", "LOW"
        ));

        int code = root.path("error_code").asInt(-1);
        if (code != 0) {
            String message = root.path("error_msg").asText("百度人脸识别失败");
            return new FaceMatchResult(null, BigDecimal.ZERO, threshold, false, message);
        }

        JsonNode user = root.path("result").path("user_list").isArray() && root.path("result").path("user_list").size() > 0
                ? root.path("result").path("user_list").get(0)
                : null;
        if (user == null) {
            return new FaceMatchResult(null, BigDecimal.ZERO, threshold, false, "NO_FACE_MATCHED");
        }

        BigDecimal score = BigDecimal.valueOf(user.path("score").asDouble(0));
        boolean passed = score.compareTo(threshold) >= 0;
        return new FaceMatchResult(user.path("user_id").asText(), score, threshold, passed,
                passed ? "PASSED" : "CONFIDENCE_TOO_LOW");
    }

    public void register(String userId, String imageBase64, String targetGroupId) {
        if (!configured()) {
            log.warn("百度人脸 Key 未配置，跳过远端人脸注册 userId={}", userId);
            return;
        }
        JsonNode root = postJson(ADD_URL, toMap(
                "image", imageBase64,
                "image_type", "BASE64",
                "group_id", StringUtils.hasText(targetGroupId) ? targetGroupId : groupId,
                "user_id", userId,
                "quality_control", "NORMAL",
                "liveness_control", "LOW"
        ));
        ensureSuccess(root, "注册人脸");
    }

    public void update(String userId, String imageBase64, String targetGroupId) {
        if (!configured()) {
            log.warn("百度人脸 Key 未配置，跳过远端人脸更新 userId={}", userId);
            return;
        }
        JsonNode root = postJson(UPDATE_URL, toMap(
                "image", imageBase64,
                "image_type", "BASE64",
                "group_id", StringUtils.hasText(targetGroupId) ? targetGroupId : groupId,
                "user_id", userId,
                "quality_control", "NORMAL",
                "liveness_control", "LOW"
        ));
        ensureSuccess(root, "更新人脸");
    }

    public void delete(String userId, String targetGroupId) {
        if (!configured()) {
            log.warn("百度人脸 Key 未配置，跳过远端人脸删除 userId={}", userId);
            return;
        }
        JsonNode root = postJson(DELETE_URL, toMap(
                "group_id", StringUtils.hasText(targetGroupId) ? targetGroupId : groupId,
                "user_id", userId
        ));
        ensureSuccess(root, "删除人脸");
    }

    private JsonNode postJson(String url, Map<String, Object> body) {
        String token = getAccessToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response;
        try {
            response = restTemplate.postForEntity(
                    url + "?access_token=" + token,
                    new HttpEntity<>(body, headers),
                    String.class
            );
        } catch (RestClientException e) {
            throw new IllegalArgumentException("百度人脸接口调用失败：" + e.getMessage(), e);
        }
        try {
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            throw new IllegalArgumentException("解析百度人脸响应失败", e);
        }
    }

    private void ensureSuccess(JsonNode root, String operation) {
        int code = root.path("error_code").asInt(0);
        if (code == 0) return;
        String message = root.path("error_msg").asText("百度人脸接口返回错误");
        throw new IllegalArgumentException(operation + "失败：" + readableBaiduError(message) + "（" + message + "）");
    }

    private synchronized String getAccessToken() {
        if (cachedToken != null && System.currentTimeMillis() < tokenExpireTime) {
            return cachedToken;
        }
        String url = TOKEN_URL + "?grant_type=client_credentials"
                + "&client_id=" + apiKey
                + "&client_secret=" + secretKey;
        ResponseEntity<String> response = restTemplate.postForEntity(url, null, String.class);
        try {
            JsonNode result = objectMapper.readTree(response.getBody());
            if (!StringUtils.hasText(result.path("access_token").asText())) {
                String message = result.path("error_description").asText(result.path("error").asText("百度人脸 token 获取失败"));
                throw new IllegalArgumentException("获取百度人脸 access_token 失败：" + message);
            }
            cachedToken = result.path("access_token").asText();
            int expiresIn = result.path("expires_in").asInt(2592000);
            tokenExpireTime = System.currentTimeMillis() + (expiresIn - 3600) * 1000L;
            return cachedToken;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("获取百度人脸 access_token 失败", e);
        }
    }

    private String readableBaiduError(String message) {
        String lower = message == null ? "" : message.toLowerCase();
        if (lower.contains("no permission to access data")) {
            return "百度应用没有人脸库数据访问权限，请在百度智能云开通人脸识别并使用人脸应用的 API Key";
        }
        if (lower.contains("pic not has face")) return "图片中未检测到人脸，请正对摄像头重试";
        if (lower.contains("match user is not found")) return "未匹配到已注册人脸";
        if (lower.contains("quality") || lower.contains("fuzzy")) return "图片质量不足，请保持光线充足并重新拍摄";
        if (lower.contains("liveness")) return "活体检测未通过";
        if (lower.contains("image format")) return "图片格式不正确";
        return message;
    }

    private Map<String, Object> toMap(Object... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keysAndValues.length; i += 2) {
            map.put(String.valueOf(keysAndValues[i]), keysAndValues[i + 1]);
        }
        return map;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FaceMatchResult {
        private String userId;
        private BigDecimal confidence;
        private BigDecimal threshold;
        private boolean passed;
        private String reason;
    }
}
