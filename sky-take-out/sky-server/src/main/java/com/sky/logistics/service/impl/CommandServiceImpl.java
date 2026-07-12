package com.sky.logistics.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.logistics.common.PageResponse;
import com.sky.logistics.controller.LogisticsWebSocketServer;
import com.sky.logistics.entity.Command;
import com.sky.logistics.entity.CommandLog;
import com.sky.logistics.mapper.CommandMapper;
import com.sky.logistics.service.CommandService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class CommandServiceImpl implements CommandService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;

    private final CommandMapper commandMapper;
    private final MessageChannel mqttOutboundChannel;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CommandServiceImpl(CommandMapper commandMapper,
                               @Qualifier("mqttOutboundChannel") MessageChannel mqttOutboundChannel) {
        this.commandMapper = commandMapper;
        this.mqttOutboundChannel = mqttOutboundChannel;
    }

    @Override
    @Transactional
    public Map<String, Object> createCommand(String plate, String commandType, String priority,
                                              Map<String, Object> payloadMap, String createdBy) {
        if (commandType == null || commandType.isEmpty()) {
            throw new IllegalArgumentException("指令类型(commandType)不能为空");
        }
        if (plate == null || plate.isEmpty()) {
            throw new IllegalArgumentException("车牌(plate)不能为空");
        }

        String commandId = "CMD-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        String vinTopic = plate.replace("·", "-");
        String mqttTopic = "vehicle/" + vinTopic + "/command";
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        String payloadJson = null;
        try {
            payloadJson = objectMapper.writeValueAsString(payloadMap);
        } catch (Exception ignored) {}

        Command command = new Command();
        command.setCommandId(commandId);
        command.setPlate(plate);
        command.setVinTopic(vinTopic);
        command.setCommandType(commandType);
        command.setPriority(priority != null ? priority : "NORMAL");
        command.setPayload(payloadJson);
        command.setStatus("SENT");
        command.setMqttTopic(mqttTopic);
        command.setCreatedBy(createdBy);
        command.setCreatedAt(now);
        command.setUpdatedAt(now);

        commandMapper.insert(command);

        CommandLog cmdLog = new CommandLog();
        cmdLog.setId("CML-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
        cmdLog.setCommandId(commandId);
        cmdLog.setStatus("SENT");
        cmdLog.setSource("PLATFORM");
        cmdLog.setCreatedAt(now);
        commandMapper.insertLog(cmdLog);

        // MQTT 发布指令到车辆
        try {
            Map<String, Object> mqttPayload = new LinkedHashMap<>();
            mqttPayload.put("commandId", commandId);
            mqttPayload.put("type", commandType);
            mqttPayload.put("priority", priority);
            mqttPayload.put("payload", payloadMap);
            mqttPayload.put("ts", now.toEpochSecond());

            String mqttJson = objectMapper.writeValueAsString(mqttPayload);
            mqttOutboundChannel.send(
                MessageBuilder.withPayload(mqttJson)
                    .setHeader("mqtt_topic", mqttTopic)
                    .build()
            );
            log.info("指令已发布 MQTT, commandId={}, topic={}", commandId, mqttTopic);
        } catch (Exception e) {
            log.error("MQTT 发布指令失败, commandId={}, error={}", commandId, e.getMessage());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("commandId", commandId);
        result.put("plate", plate);
        result.put("vinTopic", vinTopic);
        result.put("mqttTopic", mqttTopic);
        result.put("commandType", commandType);
        result.put("priority", priority);
        result.put("status", "SENT");
        result.put("createdAt", now.toString());
        return result;
    }

    @Override
    public Map<String, Object> getCommandDetail(String plate, String commandId) {
        Command command = commandMapper.findByCommandId(commandId);
        if (command == null) {
            throw new IllegalArgumentException("指令不存在: " + commandId);
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("commandId", command.getCommandId());
        detail.put("plate", command.getPlate());
        detail.put("commandType", command.getCommandType());
        detail.put("priority", command.getPriority());
        detail.put("payload", command.getPayload());
        detail.put("status", command.getStatus());

        List<CommandLog> logs = commandMapper.findLogsByCommandId(commandId);
        List<Map<String, Object>> timeline = new ArrayList<>();
        for (CommandLog log : logs) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("time", log.getCreatedAt() != null ? log.getCreatedAt().toString() : null);
            entry.put("status", log.getStatus());
            entry.put("source", log.getSource() != null ? log.getSource() : "PLATFORM");
            timeline.add(entry);
        }
        detail.put("timeline", timeline);
        return detail;
    }

    @Override
    public Map<String, Object> listCommands(String plate, Integer page, Integer size) {
        int p = page == null || page < 1 ? DEFAULT_PAGE : page;
        int s = size == null || size < 1 ? DEFAULT_SIZE : Math.min(size, 100);
        int offset = (p - 1) * s;

        Long total = commandMapper.countByPlate(plate);
        List<Command> commands = commandMapper.findByPlate(plate, offset, s);

        List<Map<String, Object>> content = new ArrayList<>();
        for (Command cmd : commands) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("commandId", cmd.getCommandId());
            item.put("plate", cmd.getPlate());
            item.put("commandType", cmd.getCommandType());
            item.put("priority", cmd.getPriority());
            item.put("status", cmd.getStatus());
            item.put("createdAt", cmd.getCreatedAt() != null ? cmd.getCreatedAt().toString() : null);
            content.add(item);
        }

        int totalPages = total == null || total == 0 ? 0 : (int) Math.ceil((double) total / s);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", content);
        result.put("page", p);
        result.put("size", s);
        result.put("totalElements", total != null ? total : 0L);
        result.put("totalPages", totalPages);
        return result;
    }
}
