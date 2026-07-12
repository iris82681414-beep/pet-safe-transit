package com.sky.logistics.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.constant.JwtClaimsConstant;
import com.sky.logistics.entity.KnowledgeChunk;
import com.sky.logistics.entity.Vehicle;
import com.sky.logistics.mapper.KnowledgeMapper;
import com.sky.logistics.mapper.LogisticsVehicleMapper;
import com.sky.logistics.service.AlertService;
import com.sky.logistics.service.AssistantService;
import com.sky.logistics.service.EmbeddingService;
import com.sky.logistics.service.LLMService;
import com.sky.logistics.service.VehicleStatusService;
import com.sky.properties.JwtProperties;
import com.sky.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AssistantServiceImpl implements AssistantService {

    private static final int TOP_K = 5;
    private static final int HYBRID_LIMIT = 20;
    private static final double RRF_K = 60.0;
    private static final Pattern PLATE_PATTERN = Pattern.compile("[京津沪渝冀豫云辽黑湘皖鲁新苏浙赣鄂桂甘晋蒙陕吉闽贵粤川青藏琼宁][A-Z][·\\.]?[A-Z0-9]{4,5}");
    private static final Pattern CARGO_ID_PATTERN = Pattern.compile("[A-Z]{2}-[A-Z]{2}-\\d{8}-\\d{4}");

    private final EmbeddingService embeddingService;
    private final LLMService llmService;
    private final KnowledgeMapper knowledgeMapper;
    private final LogisticsVehicleMapper vehicleMapper;
    private final VehicleStatusService vehicleStatusService;
    private final AlertService alertService;
    private final StringRedisTemplate redisTemplate;
    private final JwtProperties jwtProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AssistantServiceImpl(EmbeddingService embeddingService, LLMService llmService,
                                KnowledgeMapper knowledgeMapper, LogisticsVehicleMapper vehicleMapper,
                                VehicleStatusService vehicleStatusService, AlertService alertService,
                                StringRedisTemplate redisTemplate, JwtProperties jwtProperties) {
        this.embeddingService = embeddingService;
        this.llmService = llmService;
        this.knowledgeMapper = knowledgeMapper;
        this.vehicleMapper = vehicleMapper;
        this.vehicleStatusService = vehicleStatusService;
        this.alertService = alertService;
        this.redisTemplate = redisTemplate;
        this.jwtProperties = jwtProperties;
    }

    @Override
    public Map<String, Object> chat(String question, String sessionId, String authorization) {
        Map<String, Object> result = new LinkedHashMap<>();

        String userId = null;
        String role = null;
        String userName = null;
        if (authorization != null && authorization.startsWith("Bearer ")) {
            try {
                String token = authorization.substring("Bearer ".length());
                Claims claims = JwtUtil.parseJWT(jwtProperties.getAdminSecretKey(), token);
                userId = String.valueOf(claims.get(JwtClaimsConstant.USER_ID));
                role = (String) claims.get(JwtClaimsConstant.ROLE);
                userName = (String) claims.get(JwtClaimsConstant.NAME);
            } catch (Exception e) {
                log.warn("JWT 解析失败, 将使用匿名模式: {}", e.getMessage());
            }
        }

        Map<String, String> entities = extractEntities(question);
        Map<String, Object> businessContext = buildBusinessContext(entities);
        result.put("context", businessContext);

        String rewrittenQuery = llmService.rewriteQuery(question);
        log.debug("查询改写: {} -> {}", question, rewrittenQuery);

        List<KnowledgeChunk> chunks = hybridSearch(rewrittenQuery, TOP_K);
        String systemPrompt = buildSystemPrompt(role, userName, businessContext, chunks);
        String answer = llmService.chat(systemPrompt, question);

        result.put("sessionId", sessionId);
        result.put("answer", answer);
        result.put("answeredAt", java.time.OffsetDateTime.now().toString());
        result.put("sources", buildSources(chunks));
        saveMessage(sessionId, question, answer);
        return result;
    }

    @Override
    public SseEmitter chatStream(String question, String sessionId, String authorization) {
        SseEmitter emitter = new SseEmitter(120000L);
        try {
            emitter.send(SseEmitter.event().name("ready").data(Collections.singletonMap("sessionId", sessionId), MediaType.APPLICATION_JSON));
        } catch (Exception e) {
            emitter.completeWithError(e);
            return emitter;
        }
        new Thread(() -> doChatStream(question, sessionId, authorization, emitter),
                "assistant-stream-" + UUID.randomUUID().toString().substring(0, 8)).start();
        return emitter;
    }

    private void doChatStream(String question, String sessionId, String authorization, SseEmitter emitter) {
        String userId = null;
        String role = null;
        String userName = null;
        if (authorization != null && authorization.startsWith("Bearer ")) {
            try {
                String token = authorization.substring("Bearer ".length());
                Claims claims = JwtUtil.parseJWT(jwtProperties.getAdminSecretKey(), token);
                userId = String.valueOf(claims.get(JwtClaimsConstant.USER_ID));
                role = (String) claims.get(JwtClaimsConstant.ROLE);
                userName = (String) claims.get(JwtClaimsConstant.NAME);
            } catch (Exception e) {
                log.warn("JWT 解析失败: {}", e.getMessage());
            }
        }

        try {
            Map<String, String> entities = extractEntities(question);
            Map<String, Object> businessContext = buildBusinessContext(entities);
            // Keep RAG in the streaming path, but avoid waiting for the remote
            // embedding service before the first model token is visible.
            List<KnowledgeChunk> chunks = keywordSearch(question, TOP_K);
            String systemPrompt = buildSystemPrompt(role, userName, businessContext, chunks);
            List<Map<String, Object>> sources = buildSources(chunks);

            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("sessionId", sessionId);
            meta.put("sources", sources);
            meta.put("context", businessContext);
            meta.put("answeredAt", java.time.OffsetDateTime.now().toString());

            StringBuilder fullAnswer = new StringBuilder();
            final String sid = sessionId;
            final String q = question;

            llmService.chatStream(systemPrompt, question,
                token -> {
                    try {
                        fullAnswer.append(token);
                        emitter.send(SseEmitter.event().name("token").data(Collections.singletonMap("text", token), MediaType.APPLICATION_JSON));
                    } catch (Exception e) {
                        log.error("SSE 发送失败: {}", e.getMessage());
                    }
                },
                () -> {
                    try {
                        emitter.send(SseEmitter.event().name("meta").data(meta, MediaType.APPLICATION_JSON));
                        emitter.complete();
                        saveMessage(sid, q, fullAnswer.toString());
                    } catch (Exception e) {
                        sendStreamError(emitter, "智能问答响应发送失败");
                    }
                },
                error -> {
                    log.error("流式调用失败: {}", error.getMessage());
                    sendStreamError(emitter, "AI 服务暂时不可用，请稍后重试。");
                }
            );
        } catch (Exception e) {
            log.error("智能问答流式处理失败: {}", e.getMessage());
            sendStreamError(emitter, "智能问答处理失败，请稍后重试。");
        }
    }

    private void sendStreamError(SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event().name("error").data(Collections.singletonMap("message", message), MediaType.APPLICATION_JSON));
        } catch (Exception ignored) {
        } finally {
            emitter.complete();
        }
    }

    @Override
    public List<String> getSuggestions(String role) {
        if ("DISPATCHER".equals(role)) {
            return Arrays.asList("如何查看未处理的告警？", "当前有哪些车辆在线？", "偏航告警如何关闭？");
        } else if ("SHIPPER".equals(role)) {
            return Arrays.asList("托运宠物现在在哪里？", "如何查看宠物旅程的预计抵达时间？", "宠物运输途中出现风险怎么办？");
        } else {
            return Arrays.asList("如何使用伴生云途工作人员平台？", "平台支持哪些宠物托运功能？", "动物福利风险如何处理？");
        }
    }

    @Override
    public List<Map<String, Object>> getMessages(String sessionId) {
        if (sessionId == null) return Collections.emptyList();
        String key = "logistics:assistant:session:" + sessionId;
        List<String> messages = redisTemplate.opsForList().range(key, 0, -1);
        if (messages == null) return Collections.emptyList();
        return messages.stream().map(msg -> {
            try {
                return (Map<String, Object>) objectMapper.readValue(msg, Map.class);
            } catch (Exception e) {
                return null;
            }
        }).filter(Objects::nonNull).collect(Collectors.toList());
    }

    private Map<String, String> extractEntities(String question) {
        Map<String, String> entities = new LinkedHashMap<>();
        Matcher plateMatcher = PLATE_PATTERN.matcher(question);
        if (plateMatcher.find()) {
            entities.put("plate", plateMatcher.group());
        }
        Matcher cargoMatcher = CARGO_ID_PATTERN.matcher(question);
        if (cargoMatcher.find()) {
            entities.put("cargoId", cargoMatcher.group());
        }
        return entities;
    }

    private Map<String, Object> buildBusinessContext(Map<String, String> entities) {
        Map<String, Object> context = new LinkedHashMap<>();
        String plate = entities.get("plate");
        if (plate != null) {
            buildSpecificContext(context, plate);
        } else {
            buildOverviewContext(context);
        }
        return context;
    }

    private void buildSpecificContext(Map<String, Object> context, String plate) {
        Vehicle vehicle = vehicleMapper.findByPlate(plate);
        if (vehicle != null) {
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("plate", vehicle.getPlate());
            v.put("driverName", vehicle.getDriverName());
            v.put("status", vehicle.getStatus());
            v.put("deviceStatus", vehicle.getDeviceStatus());
            try {
                Map<String, Object> pos = vehicleStatusService.getCurrentStatus(plate);
                if (pos != null) v.put("position", pos);
            } catch (Exception e) {
                log.warn("获取车辆位置失败: {}", e.getMessage());
            }
            context.put("vehicle", v);

            try {
                com.sky.logistics.common.PageResponse<Map<String, Object>> alertsPage =
                        alertService.listAlerts(null, null, null, plate, 1, 10);
                if (alertsPage != null && alertsPage.getContent() != null && !alertsPage.getContent().isEmpty()) {
                    context.put("alerts", alertsPage.getContent());
                }
            } catch (Exception e) {
                log.warn("获取告警列表失败: {}", e.getMessage());
            }
        }
    }

    private void buildOverviewContext(Map<String, Object> context) {
        List<Vehicle> vehicles = vehicleMapper.findAll();
        context.put("totalVehicles", vehicles.size());
        long onlineCount = vehicles.stream().filter(v -> "ONLINE".equals(v.getDeviceStatus())).count();
        context.put("onlineVehicles", onlineCount);

        Map<String, Object> stats = alertService.getAlertStats();
        context.put("alertStats", stats);
    }

    private List<KnowledgeChunk> hybridSearch(String query, int topK) {
        String vecStr = embeddingService.embedToString(query);
        List<KnowledgeChunk> vectorResults = Collections.emptyList();
        if (!vecStr.isEmpty()) {
            vectorResults = knowledgeMapper.findSimilarChunksByVector(vecStr, HYBRID_LIMIT);
        }

        List<String> keywords = extractKeywords(query);
        List<KnowledgeChunk> keywordResults = Collections.emptyList();
        if (!keywords.isEmpty()) {
            keywordResults = knowledgeMapper.findChunksByKeyword(keywords, HYBRID_LIMIT);
        }

        if (vectorResults.isEmpty() && keywordResults.isEmpty()) {
            return Collections.emptyList();
        }
        if (vectorResults.isEmpty()) {
            return keywordResults.size() <= topK ? keywordResults : keywordResults.subList(0, topK);
        }
        if (keywordResults.isEmpty()) {
            return vectorResults.size() <= topK ? vectorResults : vectorResults.subList(0, topK);
        }

        Map<String, Double> rrfScores = new LinkedHashMap<>();
        for (int i = 0; i < vectorResults.size(); i++) {
            rrfScores.put(vectorResults.get(i).getChunkId(), 1.0 / (RRF_K + i + 1));
        }
        for (int i = 0; i < keywordResults.size(); i++) {
            rrfScores.merge(keywordResults.get(i).getChunkId(), 1.0 / (RRF_K + i + 1), Double::sum);
        }

        List<String> sortedIds = rrfScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        Map<String, KnowledgeChunk> chunkMap = new LinkedHashMap<>();
        for (KnowledgeChunk c : vectorResults) chunkMap.putIfAbsent(c.getChunkId(), c);
        for (KnowledgeChunk c : keywordResults) chunkMap.putIfAbsent(c.getChunkId(), c);

        List<KnowledgeChunk> merged = new ArrayList<>();
        for (String id : sortedIds) {
            KnowledgeChunk c = chunkMap.get(id);
            if (c != null) merged.add(c);
        }
        return merged;
    }

    private List<KnowledgeChunk> keywordSearch(String query, int topK) {
        List<String> keywords = extractKeywords(query);
        if (keywords.isEmpty()) return Collections.emptyList();
        try {
            List<KnowledgeChunk> results = knowledgeMapper.findChunksByKeyword(keywords, topK);
            if (results == null || results.size() <= topK) {
                return results == null ? Collections.emptyList() : results;
            }
            return results.subList(0, topK);
        } catch (Exception e) {
            log.warn("流式问答关键词检索失败，将使用业务上下文继续回答: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<String> extractKeywords(String query) {
        Set<String> stopWords = new HashSet<>(Arrays.asList(
                "怎么", "如何", "什么", "哪里", "为什么", "可以", "是", "的", "吗", "了", "我", "你", "他", "这", "那"));
        List<String> keywords = new ArrayList<>();
        for (String word : query.split("[\\s，。！？、,]+")) {
            if (!stopWords.contains(word) && word.length() >= 2) {
                keywords.add(word);
            }
        }
        if (keywords.isEmpty() && query.length() >= 2) {
            keywords.add(query);
        }
        return keywords;
    }

    private String buildSystemPrompt(String role, String userName, Map<String, Object> businessContext,
                                     List<KnowledgeChunk> chunks) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是伴生云途宠物托运工作人员平台的智能助手羊小智。当前时间: ").append(java.time.OffsetDateTime.now()).append("。");
        sb.append("你的服务对象是调度员、运输司机和中转照护员；回答时优先关注宠物安全、健康、通风、温湿度、饮水、航空箱固定和安全交接。内部 cargo 字段代表宠物托运任务，不要把宠物称为货物。");
        if (role != null) {
            sb.append("当前用户角色:").append(role).append("(").append(userName != null ? userName : "").append(")。");
        }
        if (!businessContext.isEmpty()) {
            sb.append("\n以下是与用户问题相关的实时业务数据:\n");
            try {
                sb.append(objectMapper.writeValueAsString(businessContext));
            } catch (Exception e) {
                sb.append(businessContext.toString());
            }
        }
        if (!chunks.isEmpty()) {
            sb.append("\n以下是来自知识库的参考资料:\n");
            for (int i = 0; i < chunks.size(); i++) {
                sb.append("[资料").append(i + 1).append("] ").append(chunks.get(i).getContent()).append("\n");
            }
        }
        sb.append("\n请基于以上信息回答用户问题。可以使用简洁 Markdown 组织要点、步骤和表格，避免过度排版。直接输出回答内容，不要输出 JSON，不要给每个词加引号。如果业务数据和参考资料不足以回答，请如实说明。");
        return sb.toString();
    }

    private List<Map<String, Object>> buildSources(List<KnowledgeChunk> chunks) {
        List<Map<String, Object>> sources = new ArrayList<>();
        for (KnowledgeChunk chunk : chunks) {
            Map<String, Object> source = new LinkedHashMap<>();
            source.put("chunkId", chunk.getChunkId());
            source.put("documentId", chunk.getDocumentId());
            source.put("chunkContent", chunk.getContent());
            sources.add(source);
        }
        return sources;
    }

    private void saveMessage(String sessionId, String question, String answer) {
        if (sessionId == null) return;
        try {
            Map<String, String> msg = new LinkedHashMap<>();
            msg.put("role", "user");
            msg.put("content", question);
            redisTemplate.opsForList().rightPush("logistics:assistant:session:" + sessionId,
                    objectMapper.writeValueAsString(msg));

            msg = new LinkedHashMap<>();
            msg.put("role", "assistant");
            msg.put("content", answer);
            redisTemplate.opsForList().rightPush("logistics:assistant:session:" + sessionId,
                    objectMapper.writeValueAsString(msg));

            redisTemplate.expire("logistics:assistant:session:" + sessionId, Duration.ofHours(1));
        } catch (Exception e) {
            log.warn("保存会话消息失败: {}", e.getMessage());
        }
    }
}
