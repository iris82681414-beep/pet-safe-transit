package com.sky.logistics.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.websocket.OnClose;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ServerEndpoint("/api/v1/ws")
@Slf4j
public class LogisticsWebSocketServer {

    private static final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(Session session) {
        sessions.put(session.getId(), session);
        log.info("WebSocket 连接建立, sessionId={}, 当前连接数={}", session.getId(), sessions.size());
        send(session, "{\"channel\":\"system.connected\",\"data\":{\"status\":\"OK\",\"sessionId\":\"" + session.getId() + "\"}}");
    }

    @OnClose
    public void onClose(Session session) {
        sessions.remove(session.getId());
        log.info("WebSocket 连接关闭, sessionId={}, 当前连接数={}", session.getId(), sessions.size());
    }

    public static void broadcast(String channel, String jsonData) {
        String payload = "{\"channel\":\"" + channel + "\",\"data\":" + jsonData + "}";
        for (Session session : sessions.values()) {
            send(session, payload);
        }
        log.info("WebSocket 广播, channel={}, 推送数={}", channel, sessions.size());
    }

    public static void broadcast(String channel, Map<String, Object> data) {
        String eventId = "EVT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        StringBuilder json = new StringBuilder();
        json.append("{\"channel\":\"").append(channel).append("\",");
        json.append("\"eventId\":\"").append(eventId).append("\",");
        json.append("\"data\":{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (!first) json.append(",");
            first = false;
            Object value = entry.getValue();
            if (value instanceof String) {
                json.append("\"").append(entry.getKey()).append("\":\"").append(escape((String) value)).append("\"");
            } else if (value instanceof Number || value instanceof Boolean) {
                json.append("\"").append(entry.getKey()).append("\":").append(value);
            } else {
                json.append("\"").append(entry.getKey()).append("\":\"").append(escape(String.valueOf(value))).append("\"");
            }
        }
        json.append("}}");

        String payload = json.toString();
        for (Session session : sessions.values()) {
            send(session, payload);
        }
        log.info("WebSocket 广播, channel={}, 推送数={}", channel, sessions.size());
    }

    private static void send(Session session, String text) {
        if (session != null && session.isOpen()) {
            synchronized (session) {
                try {
                    session.getBasicRemote().sendText(text);
                } catch (IOException e) {
                    log.error("WebSocket 发送失败: {}", e.getMessage());
                    sessions.remove(session.getId());
                }
            }
        }
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}