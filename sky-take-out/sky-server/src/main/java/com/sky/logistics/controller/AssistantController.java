package com.sky.logistics.controller;

import com.sky.constant.JwtClaimsConstant;
import com.sky.logistics.common.ApiResponse;
import com.sky.logistics.common.PageResponse;
import com.sky.logistics.service.AssistantService;
import com.sky.logistics.service.KnowledgeService;
import com.sky.logistics.service.MinioService;
import com.sky.logistics.service.ShipperAccessService;
import com.sky.properties.JwtProperties;
import com.sky.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Api(tags = "智慧物流-智能问答与知识库")
public class AssistantController {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;
    private static final String[] ALLOWED_EXTENSIONS = {".md", ".txt", ".markdown"};

    private final AssistantService assistantService;
    private final KnowledgeService knowledgeService;
    private final MinioService minioService;
    private final JwtProperties jwtProperties;
    private final ShipperAccessService shipperAccessService;

    public AssistantController(AssistantService assistantService, KnowledgeService knowledgeService,
                                MinioService minioService, JwtProperties jwtProperties,
                                ShipperAccessService shipperAccessService) {
        this.assistantService = assistantService;
        this.knowledgeService = knowledgeService;
        this.minioService = minioService;
        this.jwtProperties = jwtProperties;
        this.shipperAccessService = shipperAccessService;
    }

    @PostMapping("/assistant/chat")
    @ApiOperation("智能问答")
    public ApiResponse<Map<String, Object>> chat(@RequestBody Map<String, Object> request,
                                                  @RequestHeader(value = "Authorization", required = false) String authorization) {
        String question = request != null ? (String) request.get("question") : null;
        String sessionId = request != null ? (String) request.getOrDefault("sessionId",
                "SESSION-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase()) : null;
        String cargoId = request != null && request.get("cargoId") != null
                ? String.valueOf(request.get("cargoId")).trim() : null;
        if (question == null || question.trim().isEmpty()) {
            return ApiResponse.error(40001, "问题不能为空", null);
        }
        if (cargoId != null && !cargoId.isEmpty()) {
            shipperAccessService.requireOwnedIfShipper(cargoId, authorization);
        }
        return ApiResponse.success(assistantService.chat(question, sessionId, authorization, cargoId));
    }

    @PostMapping(value = "/assistant/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ApiOperation("智能问答（流式）")
    public ResponseEntity<SseEmitter> chatStream(@RequestBody Map<String, Object> request,
                                                 @RequestHeader(value = "Authorization", required = false) String authorization) {
        String question = request != null ? (String) request.get("question") : null;
        String sessionId = request != null ? (String) request.getOrDefault("sessionId",
                "SESSION-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase()) : null;
        String cargoId = request != null && request.get("cargoId") != null
                ? String.valueOf(request.get("cargoId")).trim() : null;
        if (question == null || question.trim().isEmpty()) {
            SseEmitter emitter = new SseEmitter();
            try {
                emitter.send(SseEmitter.event().name("error").data(Collections.singletonMap("message", "问题不能为空"), MediaType.APPLICATION_JSON));
            } catch (Exception ignored) {}
            emitter.complete();
            return streamResponse(emitter);
        }
        if (cargoId != null && !cargoId.isEmpty()) {
            shipperAccessService.requireOwnedIfShipper(cargoId, authorization);
        }
        return streamResponse(assistantService.chatStream(question, sessionId, authorization, cargoId));
    }

    @GetMapping("/assistant/suggestions")
    @ApiOperation("获取问答建议")
    public ApiResponse<List<String>> suggestions(@RequestHeader(value = "Authorization", required = false) String authorization) {
        String role = extractRole(authorization);
        return ApiResponse.success(assistantService.getSuggestions(role));
    }

    @GetMapping("/assistant/sessions/{sessionId}/messages")
    @ApiOperation("获取会话消息")
    public ApiResponse<List<Map<String, Object>>> messages(@PathVariable String sessionId) {
        return ApiResponse.success(assistantService.getMessages(sessionId));
    }

    @PostMapping(value = "/knowledge/documents", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @ApiOperation("上传知识库文档")
    public ApiResponse<Map<String, Object>> upload(@RequestParam("file") MultipartFile file,
                                                    @RequestParam(required = false) String title,
                                                    @RequestParam(required = false) String category) {
        if (file == null || file.isEmpty()) {
            return ApiResponse.error(40001, "文件不能为空", null);
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            return ApiResponse.error(40001, "文件大小不能超过10MB", null);
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !isAllowedExtension(originalFilename)) {
            return ApiResponse.error(40001, "仅支持 .md 格式文件", null);
        }

        String docTitle = (title != null && !title.isEmpty()) ? title : originalFilename;
        String docCategory = (category != null && !category.isEmpty()) ? category : "REGULATION";
        String safeName = originalFilename.replaceAll("[^a-zA-Z0-9._\\-\\u4e00-\\u9fff]", "_");
        String objectKey = "knowledge/" + UUID.randomUUID().toString().replace("-", "") + "/" + safeName;

        minioService.upload(file, objectKey);
        Map<String, Object> result = knowledgeService.upload(docTitle, docCategory, objectKey);
        return ApiResponse.success(result);
    }

    @PostMapping("/knowledge/documents/{documentId}/index")
    @ApiOperation("索引知识库文档")
    public ApiResponse<Map<String, Object>> index(@PathVariable String documentId) {
        return ApiResponse.success(knowledgeService.indexDocument(documentId));
    }

    @GetMapping("/knowledge/documents")
    @ApiOperation("获取知识库文档列表")
    public ApiResponse<PageResponse<Map<String, Object>>> documents(@RequestParam(required = false) Integer page,
                                                                      @RequestParam(required = false) Integer size,
                                                                      @RequestParam(required = false) String title,
                                                                      @RequestParam(required = false) String category) {
        return ApiResponse.success(knowledgeService.listDocuments(page, size, title, category));
    }

    @DeleteMapping("/knowledge/documents/{documentId}")
    @ApiOperation("删除知识库文档")
    public ApiResponse<Void> delete(@PathVariable String documentId) {
        knowledgeService.deleteDocument(documentId);
        return ApiResponse.success();
    }

    private String extractRole(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) return null;
        try {
            String token = authorization.substring("Bearer ".length());
            Claims claims = JwtUtil.parseJWT(jwtProperties.getAdminSecretKey(), token);
            return (String) claims.get(JwtClaimsConstant.ROLE);
        } catch (Exception e) {
            return null;
        }
    }

    private ResponseEntity<SseEmitter> streamResponse(SseEmitter emitter) {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                .header("X-Accel-Buffering", "no")
                .body(emitter);
    }

    private boolean isAllowedExtension(String filename) {
        String lower = filename.toLowerCase();
        for (String ext : ALLOWED_EXTENSIONS) {
            if (lower.endsWith(ext)) return true;
        }
        return false;
    }
}
