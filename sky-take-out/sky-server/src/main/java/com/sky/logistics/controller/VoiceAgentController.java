package com.sky.logistics.controller;

import com.sky.logistics.common.ApiResponse;
import com.sky.logistics.dto.AgentCommandRequest;
import com.sky.logistics.service.DoubaoStreamingTtsService;
import com.sky.logistics.service.SiliconFlowAsrService;
import com.sky.logistics.service.VoiceAgentService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.LinkedHashMap;
import java.util.Map;
import java.io.IOException;
import java.io.UncheckedIOException;

@RestController
@RequestMapping("/api/v1")
@Api(tags = "伴生云途-语音Agent指令中心")
public class VoiceAgentController {

    private final VoiceAgentService voiceAgentService;
    private final SiliconFlowAsrService asrService;
    private final DoubaoStreamingTtsService ttsService;

    public VoiceAgentController(VoiceAgentService voiceAgentService,
                                SiliconFlowAsrService asrService,
                                DoubaoStreamingTtsService ttsService) {
        this.voiceAgentService = voiceAgentService;
        this.asrService = asrService;
        this.ttsService = ttsService;
    }

    /**
     * 录音 → SiliconFlow SenseVoice → 文字
     */
    @PostMapping("/voice/recognize")
    @ApiOperation("语音转文字：上传录音文件，调用 SiliconFlow SenseVoice 返回识别文字")
    public ApiResponse<Map<String, Object>> recognize(@RequestPart("audio") MultipartFile audioFile) {
        try {
            String text = asrService.recognize(audioFile);
            Map<String, Object> ok = new LinkedHashMap<>();
            ok.put("text", text);
            ok.put("source", "SILICONFLOW_ASR");
            ok.put("fileName", audioFile.getOriginalFilename());
            return ApiResponse.success(ok);
        } catch (Exception e) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("text", "");
            err.put("source", "ERROR");
            err.put("message", "语音识别失败: " + e.getMessage());
            return ApiResponse.success(err);
        }
    }

    /**
     * 录音 → SiliconFlow ASR → Agent 意图识别 → Action（全链路）
     */
    @PostMapping("/voice/command")
    @ApiOperation("语音转Action：上传录音 → SiliconFlow ASR → LLM Agent → 返回操作指令")
    public ApiResponse<Map<String, Object>> voiceCommand(
            @RequestPart("audio") MultipartFile audioFile,
            @RequestPart(value = "sourcePage", required = false) String sourcePage,
            @RequestPart(value = "selectedEntityId", required = false) String selectedEntityId,
            @RequestPart(value = "selectedEntityType", required = false) String selectedEntityType) {
        try {
            // 1. 语音 → 文字
            String text = asrService.recognize(audioFile);

            // 2. 文字 → Agent → Action
            AgentCommandRequest request = new AgentCommandRequest();
            request.setText(text);
            request.setSourcePage(sourcePage);
            request.setSelectedEntityId(selectedEntityId);
            request.setSelectedEntityType(selectedEntityType);
            return ApiResponse.success(voiceAgentService.textToAction(request, "SYSTEM"));

        } catch (Exception e) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("recognizedText", "");
            err.put("intent", "ERROR");
            err.put("reply", "语音识别失败: " + e.getMessage());
            return ApiResponse.success(err);
        }
    }

    /** 智能问答桌宠文字转语音，音频二进制直接返回浏览器。 */
    @PostMapping(value = "/assistant/speech", produces = "audio/mpeg")
    @ApiOperation("豆包智能问答语音播报")
    public ResponseEntity<byte[]> speech(@RequestBody Map<String, String> request) {
        try {
            byte[] audio = ttsService.synthesize(request.get("text"));
            return ResponseEntity.ok()
                    .contentType(MediaType.valueOf("audio/mpeg"))
                    .cacheControl(CacheControl.noStore())
                    .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(audio.length))
                    .body(audio);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage(), e);
        }
    }

    /** 宠物家长端使用的流式地址；豆包生成音频后按块写回。 */
    @PostMapping(value = "/assistant/speech/stream", produces = "audio/mpeg")
    @ApiOperation("豆包羊小智流式语音播报")
    public ResponseEntity<StreamingResponseBody> speechStream(@RequestBody Map<String, String> request) {
        String text = request == null ? null : request.get("text");
        StreamingResponseBody body = output -> {
            try {
                ttsService.stream(text, audio -> {
                    try {
                        output.write(audio);
                        output.flush();
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            } catch (UncheckedIOException e) {
                throw e.getCause();
            }
        };
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("audio/mpeg"))
                .cacheControl(CacheControl.noStore())
                .header("X-Accel-Buffering", "no")
                .body(body);
    }

    /**
     * 文本 → Agent → Action（跳过语音识别）
     */
    @PostMapping("/agent/command")
    @ApiOperation("文本转Action：输入自然语言文本，返回标准操作指令")
    public ApiResponse<Map<String, Object>> agentCommand(@RequestBody AgentCommandRequest request) {
        return ApiResponse.success(voiceAgentService.textToAction(request, "SYSTEM"));
    }

    /**
     * 指令示例
     */
    @GetMapping("/agent/commands/examples")
    @ApiOperation("获取语音指令示例列表")
    public ApiResponse<Map<String, Object>> examples() {
        return ApiResponse.success(voiceAgentService.getExamples());
    }

    /**
     * 确认执行 CALL_API 类 Action
     */
    @PostMapping("/agent/actions/execute")
    @ApiOperation("执行确认后的Action")
    public ApiResponse<Map<String, Object>> execute(@RequestBody Map<String, String> request) {
        String logId = request.get("logId");
        return ApiResponse.success(voiceAgentService.executeAction(logId));
    }

    /**
     * 语音日志查询
     */
    @GetMapping("/admin/voice-command/logs")
    @ApiOperation("查询语音指令日志")
    public ApiResponse<Map<String, Object>> logs(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String intent,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return ApiResponse.success(voiceAgentService.getLogs(userId, intent, page, size));
    }
}
