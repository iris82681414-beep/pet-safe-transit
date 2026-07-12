package com.sky.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class MinioConfig {

    @Value("${minio.endpoint}")
    private String endpoint;

    @Value("${minio.access-key}")
    private String accessKey;

    @Value("${minio.secret-key}")
    private String secretKey;

    @Value("${minio.bucket}")
    private String bucket;

    @Bean
    public MinioClient minioClient() {
        MinioClient client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
        createBucketIfNotExists(client);
        log.info("MinIO 客户端初始化完成, endpoint={}, bucket={}", endpoint, bucket);
        return client;
    }

    private void createBucketIfNotExists(MinioClient client) {
        try {
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("MinIO bucket 已创建: {}", bucket);
            }
        } catch (io.minio.errors.ErrorResponseException e) {
            if ("BucketAlreadyOwnedByYou".equals(e.errorResponse().code())
                    || "BucketAlreadyExists".equals(e.errorResponse().code())) {
                log.info("MinIO bucket 已存在: {}", bucket);
                return;
            }
            log.warn("MinIO bucket 初始化失败，应用将继续启动，文件上传接口会在调用时返回错误: {}", e.errorResponse().message());
        } catch (Exception e) {
            log.warn("MinIO bucket 初始化失败，应用将继续启动，文件上传接口会在调用时返回错误: {}", e.getMessage());
        }
    }
}
