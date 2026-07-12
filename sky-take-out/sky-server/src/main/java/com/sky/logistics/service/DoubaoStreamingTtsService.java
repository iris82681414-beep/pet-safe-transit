package com.sky.logistics.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** 豆包 TTS 2.0 单向 WebSocket 流式合成服务。 */
@Service
@Slf4j
public class DoubaoStreamingTtsService {

    private static final int MSG_FULL_SERVER_RESPONSE = 0x9;
    private static final int MSG_AUDIO_ONLY_SERVER = 0xB;
    private static final int MSG_ERROR = 0xF;
    private static final int FLAG_WITH_EVENT = 0x4;
    private static final int EVENT_SESSION_FINISHED = 152;

    @Value("${doubao.tts.stream-endpoint:wss://openspeech.bytedance.com/api/v3/tts/unidirectional/stream}")
    private String endpoint;
    @Value("${doubao.tts.api-key:}")
    private String apiKey;
    @Value("${doubao.tts.resource-id:seed-tts-2.0}")
    private String resourceId;
    @Value("${doubao.tts.stream-model:seed-tts-2.0-standard}")
    private String model;
    @Value("${doubao.tts.speaker:zh_male_xionger_uranus_bigtts}")
    private String speaker;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public void stream(String text, Consumer<byte[]> onAudio) throws IOException {
        if (!StringUtils.hasText(apiKey)) throw new IOException("未配置豆包语音 API Key");
        String normalized = text == null ? "" : text.trim();
        if (!StringUtils.hasText(normalized)) throw new IOException("播报文字为空");

        String requestId = UUID.randomUUID().toString();
        StreamListener listener = new StreamListener();
        WebSocket webSocket;
        try {
            webSocket = httpClient.newWebSocketBuilder()
                    .header("X-Api-Key", apiKey)
                    .header("X-Api-Resource-Id", resourceId)
                    .header("X-Api-Request-Id", requestId)
                    .header("X-Api-Connect-Id", UUID.randomUUID().toString())
                    .header("X-Control-Require-Usage-Tokens-Return", "*")
                    .buildAsync(URI.create(endpoint), listener)
                    .get(20, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IOException("连接豆包流式语音服务失败: " + rootMessage(e), e);
        }

        try {
            byte[] request = fullClientRequest(buildRequest(normalized));
            webSocket.sendBinary(ByteBuffer.wrap(request), true).join();
            long totalBytes = 0;
            while (true) {
                ServerMessage message = listener.messages.poll(120, TimeUnit.SECONDS);
                if (message == null) throw new IOException("豆包流式语音响应超时");
                if (message.error != null) throw new IOException("豆包流式语音失败: " + message.error);
                if (message.audio != null && message.audio.length > 0) {
                    onAudio.accept(message.audio);
                    totalBytes += message.audio.length;
                }
                if (message.finished) break;
            }
            if (totalBytes == 0) throw new IOException("豆包流式语音未返回音频");
            log.info("豆包流式 TTS 完成, requestId={}, bytes={}", requestId, totalBytes);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("豆包流式语音请求被中断", e);
        } finally {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
        }
    }

    private byte[] buildRequest(String text) throws IOException {
        Map<String, Object> audioParams = new LinkedHashMap<>();
        audioParams.put("format", "mp3");
        audioParams.put("sample_rate", 24000);
        audioParams.put("bit_rate", 96000);
        audioParams.put("speech_rate", 4);
        audioParams.put("loudness_rate", 0);
        audioParams.put("enable_subtitle", false);

        Map<String, Object> additions = new LinkedHashMap<>();
        additions.put("disable_markdown_filter", true);
        additions.put("disable_emoji_filter", true);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("text", text);
        params.put("model", model);
        params.put("speaker", speaker);
        params.put("audio_params", audioParams);
        params.put("additions", objectMapper.writeValueAsString(additions));

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("req_params", params);
        return objectMapper.writeValueAsBytes(request);
    }

    private byte[] fullClientRequest(byte[] payload) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(payload.length + 8);
        output.write(0x11); // protocol v1, 4-byte header
        output.write(0x10); // full client request, no sequence
        output.write(0x10); // JSON serialization, no compression
        output.write(0x00);
        output.write(ByteBuffer.allocate(4).putInt(payload.length).array());
        output.write(payload);
        return output.toByteArray();
    }

    private static ServerMessage parse(byte[] data) throws IOException {
        if (data.length < 4) throw new IOException("豆包返回了无效协议帧");
        ByteBuffer buffer = ByteBuffer.wrap(data);
        int versionAndHeader = Byte.toUnsignedInt(buffer.get());
        int headerBytes = (versionAndHeader & 0x0F) * 4;
        int typeAndFlag = Byte.toUnsignedInt(buffer.get());
        int type = (typeAndFlag >> 4) & 0x0F;
        int flag = typeAndFlag & 0x0F;
        buffer.get();
        buffer.get();
        buffer.position(Math.min(headerBytes, buffer.limit()));

        int event = 0;
        if (flag == FLAG_WITH_EVENT && buffer.remaining() >= 4) {
            event = buffer.getInt();
            if (type != MSG_ERROR && event != 50 && event != 51 && event != 52 && buffer.remaining() >= 4) {
                skipString(buffer);
            } else if ((event == 50 || event == 51 || event == 52) && buffer.remaining() >= 4) {
                skipString(buffer);
            }
        }
        if (type == MSG_ERROR && buffer.remaining() >= 4) buffer.getInt();
        byte[] payload = readPayload(buffer);
        if (type == MSG_AUDIO_ONLY_SERVER) return ServerMessage.audio(payload);
        if (type == MSG_ERROR) return ServerMessage.error(new String(payload, StandardCharsets.UTF_8));
        if (type == MSG_FULL_SERVER_RESPONSE && event == EVENT_SESSION_FINISHED) return ServerMessage.finished();
        return ServerMessage.empty();
    }

    private static void skipString(ByteBuffer buffer) throws IOException {
        int length = buffer.getInt();
        if (length < 0 || length > buffer.remaining()) throw new IOException("豆包协议字符串长度无效");
        buffer.position(buffer.position() + length);
    }

    private static byte[] readPayload(ByteBuffer buffer) throws IOException {
        if (buffer.remaining() < 4) return new byte[0];
        int length = buffer.getInt();
        if (length < 0 || length > buffer.remaining()) throw new IOException("豆包协议载荷长度无效");
        byte[] payload = new byte[length];
        buffer.get(payload);
        return payload;
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private static final class ServerMessage {
        private final byte[] audio;
        private final String error;
        private final boolean finished;

        private ServerMessage(byte[] audio, String error, boolean finished) {
            this.audio = audio;
            this.error = error;
            this.finished = finished;
        }

        private static ServerMessage audio(byte[] audio) { return new ServerMessage(audio, null, false); }
        private static ServerMessage error(String error) { return new ServerMessage(null, error, false); }
        private static ServerMessage finished() { return new ServerMessage(null, null, true); }
        private static ServerMessage empty() { return new ServerMessage(null, null, false); }
    }

    private static final class StreamListener implements WebSocket.Listener {
        private final BlockingQueue<ServerMessage> messages = new LinkedBlockingQueue<>();
        private final ByteArrayOutputStream current = new ByteArrayOutputStream();

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            byte[] chunk = new byte[data.remaining()];
            data.get(chunk);
            current.write(chunk, 0, chunk.length);
            if (last) {
                try {
                    messages.offer(parse(current.toByteArray()));
                } catch (Exception e) {
                    messages.offer(ServerMessage.error(rootMessage(e)));
                } finally {
                    current.reset();
                }
            }
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            messages.offer(ServerMessage.error(rootMessage(error)));
        }
    }
}
