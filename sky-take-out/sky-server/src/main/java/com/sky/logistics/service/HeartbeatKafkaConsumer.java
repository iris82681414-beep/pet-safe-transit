package com.sky.logistics.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@Slf4j
public class HeartbeatKafkaConsumer {

    private final JdbcTemplate timescaleJdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public HeartbeatKafkaConsumer(@Qualifier("timescaleJdbcTemplate") JdbcTemplate timescaleJdbc) {
        this.timescaleJdbc = timescaleJdbc;
    }

    @KafkaListener(topics = "vehicle-heartbeats", groupId = "logistics-consumer-group",
            autoStartup = "${spring.kafka.listener.auto-startup:true}")
    public void consume(String message) {
        try {
            JsonNode data = objectMapper.readTree(message);
            String imei = data.has("imei") && !data.get("imei").isNull()
                    ? data.get("imei").asText() : null;
            if (imei == null || imei.isEmpty()) {
                log.warn("心跳消息缺少 imei，跳过");
                return;
            }

            String plate = data.has("plate") && !data.get("plate").isNull()
                    ? data.get("plate").asText() : null;
            int battery = data.has("battery") ? data.get("battery").asInt(0) : 0;
            int signal = data.has("signal") ? data.get("signal").asInt(0) : 0;
            int gnssSatellites = data.has("gnss_satellites") ? data.get("gnss_satellites").asInt(0) : 0;
            Double temp = data.has("temp") && !data.get("temp").isNull()
                    ? data.get("temp").asDouble() : null;

            timescaleJdbc.update(
                "INSERT INTO device_heartbeats (time, imei, plate, battery, signal, gnss_satellites, temp) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)",
                Instant.now(), imei, plate, battery, signal, gnssSatellites, temp
            );
            log.debug("设备心跳已写入 TimescaleDB, imei={}", imei);
        } catch (Exception e) {
            log.error("消费设备心跳失败, 消息将被跳过: {}", e.getMessage());
        }
    }
}
