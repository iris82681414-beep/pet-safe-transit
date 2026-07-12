package com.sky.logistics.service;

import com.sky.logistics.dto.AgentCommandRequest;

import java.util.Map;

public interface VoiceAgentService {

    /** 文本 → Agent → Action（核心） */
    Map<String, Object> textToAction(AgentCommandRequest request, String userId);

    /** 执行确认后的 Action */
    Map<String, Object> executeAction(String logId);

    /** 指令示例列表 */
    Map<String, Object> getExamples();

    /** 语音日志分页查询 */
    Map<String, Object> getLogs(String userId, String intent, Integer page, Integer size);
}
