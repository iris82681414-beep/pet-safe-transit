package com.sky.logistics.service;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

@Service
@Slf4j
public class MinioService {

    private final MinioClient minioClient;

    @Value("${minio.bucket}")
    private String bucket;

    public MinioService(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    public void upload(MultipartFile file, String objectKey) {
        try (InputStream in = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(in, file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
            log.info("MinIO 上传成功, key={}, size={}", objectKey, file.getSize());
        } catch (Exception e) {
            log.error("MinIO 上传失败, key={}: {}", objectKey, e.getMessage(), e);
            throw new RuntimeException("文件上传失败", e);
        }
    }

    public InputStream download(String objectKey) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
        } catch (Exception e) {
            log.error("MinIO 下载失败, key={}: {}", objectKey, e.getMessage(), e);
            throw new RuntimeException("文件下载失败", e);
        }
    }

    public void delete(String objectKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
            log.info("MinIO 删除成功, key={}", objectKey);
        } catch (Exception e) {
            log.error("MinIO 删除失败, key={}: {}", objectKey, e.getMessage(), e);
        }
    }
}
