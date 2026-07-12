package com.sky.logistics.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

@Service
@Slf4j
public class FaceImageStorageService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final int MAX_IMAGE_BYTES = 3 * 1024 * 1024;

    private final Path uploadRoot = Paths.get(System.getProperty("user.dir"), "uploads").toAbsolutePath().normalize();

    public StoredFaceImage save(String userId, String imageBase64) {
        if (!StringUtils.hasText(imageBase64)) {
            throw new IllegalArgumentException("人脸标准照不能为空");
        }
        byte[] bytes = decode(imageBase64);
        if (bytes.length == 0) {
            throw new IllegalArgumentException("人脸标准照内容为空");
        }
        if (bytes.length > MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException("人脸标准照不能超过 3MB");
        }

        String dateDir = "faces-" + LocalDate.now().format(DATE_FORMATTER);
        String safeUserId = sanitize(userId);
        String fileName = safeUserId + "-" + UUID.randomUUID().toString().replace("-", "") + ".jpg";
        Path targetDir = uploadRoot.resolve(dateDir).normalize();
        Path target = targetDir.resolve(fileName).normalize();
        if (!target.startsWith(targetDir)) {
            throw new IllegalArgumentException("非法人脸图片路径");
        }

        try {
            Files.createDirectories(targetDir);
            Files.write(target, bytes);
            String objectKey = dateDir + "/" + fileName;
            return new StoredFaceImage(objectKey, "/api/v1/files/" + objectKey);
        } catch (IOException e) {
            log.error("保存人脸标准照失败, userId={}: {}", userId, e.getMessage(), e);
            throw new IllegalStateException("保存人脸标准照失败", e);
        }
    }

    public void deleteByUrl(String imageUrl) {
        if (!StringUtils.hasText(imageUrl) || !imageUrl.startsWith("/api/v1/files/")) return;
        String objectKey = imageUrl.substring("/api/v1/files/".length());
        Path target = uploadRoot.resolve(objectKey).normalize();
        if (!target.startsWith(uploadRoot)) return;
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            log.warn("删除旧人脸标准照失败, url={}, error={}", imageUrl, e.getMessage());
        }
    }

    private byte[] decode(String imageBase64) {
        String payload = imageBase64.trim();
        int comma = payload.indexOf(',');
        if (payload.startsWith("data:image/") && comma >= 0) {
            payload = payload.substring(comma + 1);
        }
        try {
            return Base64.getDecoder().decode(payload);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("人脸图片不是有效的 Base64 编码");
        }
    }

    private String sanitize(String value) {
        String source = StringUtils.hasText(value) ? value : "user";
        String safe = source.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "-");
        return safe.length() > 48 ? safe.substring(0, 48) : safe;
    }

    public static class StoredFaceImage {
        private final String objectKey;
        private final String url;

        public StoredFaceImage(String objectKey, String url) {
            this.objectKey = objectKey;
            this.url = url;
        }

        public String getObjectKey() {
            return objectKey;
        }

        public String getUrl() {
            return url;
        }
    }
}
