package org.lijian.interview001.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FunASRService {

    private static final Logger log = LoggerFactory.getLogger(FunASRService.class);

    private final WebSocketClient webSocketClient;
    private final ObjectMapper objectMapper;

    @Value("${parameters.serverIpPort}")
    private String serverIpPort;

    @Value("${parameters.model}")
    private String model;

    @Value("${parameters.hotWords:}")
    private String hotWords;

    private final Map<String, CompletableFuture<String>> resultMap = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<WebSocketSession>> sessionMap = new ConcurrentHashMap<>();
    private final Map<String, StringBuilder> resultTextMap = new ConcurrentHashMap<>();

    public FunASRService(WebSocketClient webSocketClient, ObjectMapper objectMapper) {
        this.webSocketClient = webSocketClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 开始音频识别流程
     * @param audioData 音频数据字节数组
     * @return 返回识别结果的CompletableFuture
     */
    public CompletableFuture<String> recognizeAudio(byte[] audioData) {
        String requestId = UUID.randomUUID().toString();
        return startSession(requestId)
                .thenCompose(session -> {
                    try {
                        // 发送音频数据
                        sendAudioData(requestId, audioData);
                        // 告知服务器音频结束
                        finishAudioRecognition(requestId);
                        return resultMap.get(requestId);
                    } catch (IOException e) {
                        CompletableFuture<String> failedFuture = new CompletableFuture<>();
                        failedFuture.completeExceptionally(e);
                        return failedFuture;
                    }
                });
    }

    /**
     * 开始一个WebSocket会话
     * @param requestId 请求ID
     * @return 返回会话的CompletableFuture
     */
    private CompletableFuture<WebSocketSession> startSession(String requestId) {
        CompletableFuture<WebSocketSession> sessionFuture = new CompletableFuture<>();
        CompletableFuture<String> resultFuture = new CompletableFuture<>();

        resultMap.put(requestId, resultFuture);
        sessionMap.put(requestId, sessionFuture);
        resultTextMap.put(requestId, new StringBuilder());

        try {
            URI uri = URI.create(serverIpPort);
            FunASRWebSocketHandler handler = new FunASRWebSocketHandler(requestId);

            // 使用CompletableFuture来处理WebSocket连接
            try {
                // 使用execute方法替代已弃用的doHandshake方法
                CompletableFuture<WebSocketSession> webSocketFuture = webSocketClient.execute(handler, uri.toString());

                // 当WebSocketSession就绪时，完成我们的sessionFuture
                webSocketFuture.whenComplete((session, ex) -> {
                    if (ex != null) {
                        log.error("连接FunASR服务失败: {}", ex.getMessage(), ex);
                        sessionFuture.completeExceptionally(ex);
                        resultFuture.completeExceptionally(ex);
                        cleanup(requestId);
                    }
                    // 成功案例在FunASRWebSocketHandler.afterConnectionEstablished中处理
                });
            } catch (Exception e) {
                log.error("创建WebSocket连接失败: {}", e.getMessage(), e);
                sessionFuture.completeExceptionally(e);
                resultFuture.completeExceptionally(e);
                cleanup(requestId);
            }
        } catch (Exception e) {
            log.error("启动音频识别失败: {}", e.getMessage(), e);
            sessionFuture.completeExceptionally(e);
            resultFuture.completeExceptionally(e);
            cleanup(requestId);
        }

        return sessionFuture;
    }

    /**
     * 发送音频数据到FunASR服务
     * @param requestId 请求ID
     * @param audioData 音频数据
     * @throws IOException 发送失败时抛出
     */
    public void sendAudioData(String requestId, byte[] audioData) throws IOException {
        CompletableFuture<WebSocketSession> sessionFuture = sessionMap.get(requestId);
        if (sessionFuture == null) {
            throw new IOException("WebSocket会话不存在");
        }

        try {
            // 尝试获取会话，如果会话还未建立，会抛出异常
            WebSocketSession session = sessionFuture.getNow(null);
            if (session == null) {
                throw new IOException("WebSocket会话尚未建立");
            }

            if (session.isOpen()) {
                // 对于大文件，分块发送
                int chunkSize = 8192; // 8KB 每块

                for (int i = 0; i < audioData.length; i += chunkSize) {
                    int length = Math.min(chunkSize, audioData.length - i);
                    byte[] chunk = new byte[length];
                    System.arraycopy(audioData, i, chunk, 0, length);

                    // 使用BinaryMessage发送二进制数据
                    ByteBuffer buffer = ByteBuffer.wrap(chunk);
                    session.sendMessage(new BinaryMessage(buffer));

                    // 短暂暂停，避免发送过快
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IOException("发送音频数据被中断", e);
                    }
                }
            } else {
                throw new IOException("WebSocket会话已关闭");
            }
        } catch (Exception e) {
            throw new IOException("发送音频数据失败", e);
        }
    }

    /**
     * 结束音频识别
     * @param requestId 请求ID
     * @throws IOException 发送结束消息失败时抛出
     */
    public void finishAudioRecognition(String requestId) throws IOException {
        CompletableFuture<WebSocketSession> sessionFuture = sessionMap.get(requestId);
        if (sessionFuture == null) {
            throw new IOException("WebSocket会话不存在");
        }

        try {
            WebSocketSession session = sessionFuture.getNow(null);
            if (session == null) {
                throw new IOException("WebSocket会话尚未建立");
            }

            if (session.isOpen()) {
                Map<String, Object> endParams = new HashMap<>();
                endParams.put("end", true);
                String endMessage = objectMapper.writeValueAsString(endParams);
                session.sendMessage(new TextMessage(endMessage));
            } else {
                throw new IOException("WebSocket会话已关闭");
            }
        } catch (Exception e) {
            throw new IOException("发送结束消息失败", e);
        }
    }

    /**
     * 清理资源
     * @param requestId 请求ID
     */
    private void cleanup(String requestId) {
        CompletableFuture<WebSocketSession> sessionFuture = sessionMap.remove(requestId);
        if (sessionFuture != null) {
            try {
                WebSocketSession session = sessionFuture.getNow(null);
                if (session != null && session.isOpen()) {
                    session.close();
                }
            } catch (Exception e) {
                log.warn("关闭WebSocket会话失败: {}", e.getMessage());
            }
        }
        resultMap.remove(requestId);
        resultTextMap.remove(requestId);
    }

    /**
     * FunASR WebSocket处理器
     */
    private class FunASRWebSocketHandler extends TextWebSocketHandler {
        private final String requestId;

        public FunASRWebSocketHandler(String requestId) {
            this.requestId = requestId;
        }

        @Override
        public void afterConnectionEstablished(WebSocketSession session) throws Exception {
            log.info("与FunASR服务器连接建立成功，requestId: {}", requestId);

            // 会话建立成功，完成sessionFuture
            CompletableFuture<WebSocketSession> sessionFuture = sessionMap.get(requestId);
            if (sessionFuture != null && !sessionFuture.isDone()) {
                sessionFuture.complete(session);
            }

            // 发送初始化消息
            Map<String, Object> initParams = new HashMap<>();
            initParams.put("mode", model); // 使用配置的模式(offline/online/2pass)

            if (hotWords != null && !hotWords.isEmpty()) {
                initParams.put("hot_words", hotWords);
            }

            String initMessage = objectMapper.writeValueAsString(initParams);
            session.sendMessage(new TextMessage(initMessage));
        }

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
            String payload = message.getPayload();
            log.debug("收到FunASR消息: {}", payload);

            Map<String, Object> response = objectMapper.readValue(payload, Map.class);

            if (response.containsKey("text")) {
                String text = (String) response.get("text");
                StringBuilder builder = resultTextMap.get(requestId);
                if (builder != null) {
                    builder.append(text);
                }
            }

            if (response.containsKey("is_final") && (Boolean) response.get("is_final")) {
                StringBuilder builder = resultTextMap.get(requestId);
                if (builder != null) {
                    String finalResult = builder.toString();
                    log.info("识别完成，结果: {}", finalResult);
                    CompletableFuture<String> future = resultMap.get(requestId);
                    if (future != null) {
                        future.complete(finalResult);
                    }
                }

                // 清理资源
                cleanup(requestId);
            }
        }

        @Override
        public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
            log.error("WebSocket传输错误: {}", exception.getMessage(), exception);

            CompletableFuture<String> future = resultMap.get(requestId);
            if (future != null && !future.isDone()) {
                future.completeExceptionally(exception);
            }

            cleanup(requestId);
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, org.springframework.web.socket.CloseStatus status) throws Exception {
            log.info("WebSocket连接已关闭: {}, requestId: {}", status, requestId);

            StringBuilder builder = resultTextMap.get(requestId);
            if (builder != null) {
                CompletableFuture<String> future = resultMap.get(requestId);
                if (future != null && !future.isDone()) {
                    future.complete(builder.toString());
                }
            }

            cleanup(requestId);
        }
    }
}