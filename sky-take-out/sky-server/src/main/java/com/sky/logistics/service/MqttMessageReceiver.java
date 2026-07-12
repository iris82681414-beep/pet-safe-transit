package com.sky.logistics.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.logistics.controller.LogisticsWebSocketServer;
import com.sky.logistics.dto.GpsData;
import com.sky.logistics.entity.CommandLog;
import com.sky.logistics.mapper.CommandMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * MQTT 消息接收器
 *
 * 核心链路：
 * MQTT 消息 → 解析 GPS JSON → 提取 vinTopic → 发送 Kafka gps-points
 */
@Service
@Slf4j
public class MqttMessageReceiver {

    private static final String MQTT_RECEIVED_TOPIC_HEADER = "mqtt_receivedTopic";
    private static final String MQTT_TOPIC_HEADER = "mqtt_topic";

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private CommandMapper commandMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 监听 mqttInputChannel，处理所有到达的 MQTT 消息
     */
    @ServiceActivator(inputChannel = "mqttInputChannel")
    public void handleMessage(Message<?> message) {
        // 1. 获取 MQTT 主题
        String topic = resolveTopic(message);
        if (topic == null) {
            log.warn("收到没有 MQTT topic header 的消息，headers={}，丢弃", message.getHeaders().keySet());
            return;
        }
        log.info("MQTT 收到消息，主题: {}", topic);

        // 2. 获取消息体（字节数组）
        byte[] payload;
        Object body = message.getPayload();
        if (body instanceof byte[]) {
            payload = (byte[]) body;
        } else if (body instanceof String) {
            payload = ((String) body).getBytes(StandardCharsets.UTF_8);
        } else {
            log.warn("不支持的消息类型: {}", body.getClass().getName());
            return;
        }

        String json = new String(payload, StandardCharsets.UTF_8);
        log.debug("MQTT 消息内容: {}", json);

        // 3. 根据主题类型分发处理
        if (topic.endsWith("/gps")) {
            handleGps(topic, json);
        } else if (topic.endsWith("/heartbeat")) {
            handleHeartbeat(topic, json);
        } else if (topic.endsWith("/command/ack")) {
            handleCommandAck(topic, json);
        } else {
            log.warn("未知的 MQTT 主题: {}", topic);
        }
    }

    private String resolveTopic(Message<?> message) {
        Object topic = message.getHeaders().get(MQTT_RECEIVED_TOPIC_HEADER);
        if (topic == null) {
            topic = message.getHeaders().get(MQTT_TOPIC_HEADER);
        }
        return topic == null ? null : String.valueOf(topic);
    }

    /**
     * 处理 GPS 数据：解析 → 提取 vinTopic → 发 Kafka gps-points
     */
    private void handleGps(String topic, String json) {
        try {
            // 从主题 vehicle/沪A-C0291/gps 中提取 vinTopic
            String vinTopic = extractVin(topic);
            if (vinTopic == null) {
                log.warn("无法从主题中提取 vinTopic: {}", topic);
                return;
            }

            GpsData gps = objectMapper.readValue(json, GpsData.class);
            gps.setVinTopic(vinTopic);

            // 基础校验
            if (gps.getLat() == null || gps.getLng() == null) {
                log.warn("GPS 数据缺少经纬度，丢弃: {}", json);
                return;
            }
            if (gps.getLat() < -90 || gps.getLat() > 90 || gps.getLng() < -180 || gps.getLng() > 180) {
                log.warn("GPS 坐标越界, lat={}, lng={}", gps.getLat(), gps.getLng());
                return;
            }

            // 序列化后发送到 Kafka gps-points
            String kafkaValue = objectMapper.writeValueAsString(gps);
            // Key 使用 vinTopic，保证同一车辆的消息有序
            kafkaTemplate.send("gps-points", vinTopic, kafkaValue);

            log.info("GPS 已发送 Kafka, vinTopic={}, lat={}, lng={}, speed={}",
                    vinTopic, gps.getLat(), gps.getLng(), gps.getSpeed());

        } catch (Exception e) {
            log.error("处理 GPS 消息失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 处理心跳数据：解析 → 发 Kafka vehicle-heartbeats
     */
    private void handleHeartbeat(String topic, String json) {
        try {
            String vinTopic = extractVin(topic);
            if (vinTopic == null) return;

            kafkaTemplate.send("vehicle-heartbeats", vinTopic, json);
            updateDeviceOnline(vinTopic, json);

            log.debug("心跳已处理, vinTopic={}", vinTopic);

        } catch (Exception e) {
            log.error("处理心跳消息失败: {}", e.getMessage(), e);
        }
    }

    private void updateDeviceOnline(String vinTopic, String json) {
        try {
            String imei = null;
            try {
                Map data = objectMapper.readValue(json, Map.class);
                Object imeiObj = data.get("imei");
                if (imeiObj != null) {
                    imei = imeiObj.toString();
                }
            } catch (Exception ignored) {
            }

            String deviceId = (imei != null && !imei.isEmpty()) ? imei : vinTopic;
            String key = "logistics:device:heartbeat:" + deviceId;

            Map<String, String> onlineInfo = new LinkedHashMap<>();
            onlineInfo.put("vinTopic", vinTopic);
            if (imei != null) onlineInfo.put("imei", imei);
            onlineInfo.put("lastHeartbeat", Instant.now().toString());

            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(onlineInfo), Duration.ofSeconds(90));
            log.debug("Redis 设备在线已更新, key={}", key);
        } catch (Exception e) {
            log.error("Redis 设备在线状态更新失败: {}", e.getMessage(), e);
        }
    }

    private void handleCommandAck(String topic, String json) {
        try {
            Map data = objectMapper.readValue(json, Map.class);
            String commandId = data.get("commandId") != null ? data.get("commandId").toString() : null;
            String status = data.get("status") != null ? data.get("status").toString() : null;
            if (commandId == null || status == null) return;

            commandMapper.updateStatus(commandId, status);

            CommandLog cmdLog = new CommandLog();
            cmdLog.setId("CML-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
            cmdLog.setCommandId(commandId);
            cmdLog.setStatus(status);
            cmdLog.setSource("MQTT_ACK");
            cmdLog.setRawPayload(json);
            commandMapper.insertLog(cmdLog);

            Map<String, Object> pushData = new LinkedHashMap<>();
            pushData.put("commandId", commandId);
            pushData.put("status", status);
            pushData.put("timestamp", Instant.now().toString());
            LogisticsWebSocketServer.broadcast("command.ack", pushData);

            log.info("指令回执已处理, commandId={}, status={}", commandId, status);
        } catch (Exception e) {
            log.error("处理指令回执失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 从 MQTT 主题中提取 vinTopic
     * 输入: vehicle/沪A-C0291/gps → 输出: 沪A-C0291
     */
    private String extractVin(String topic) {
        if (topic == null) return null;
        String[] parts = topic.split("/");
        if (parts.length >= 2) {
            return parts[1];
        }
        return null;
    }
}
