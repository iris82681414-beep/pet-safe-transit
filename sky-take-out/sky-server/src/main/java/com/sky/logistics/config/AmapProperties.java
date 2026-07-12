package com.sky.logistics.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "amap")
public class AmapProperties {

    private String webKey;

    private String baseUrl = "https://restapi.amap.com";

    private Integer connectTimeoutMs = 3000;

    private Integer readTimeoutMs = 5000;

    private Integer cacheMinutes = 30;
}
