package com.globaleyes.websocket.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.globaleyes.websocket.enums.MessageLevel;
import com.globaleyes.websocket.model.WebSocketMessage;
import com.globaleyes.websocket.service.MessageSenderService;
import com.globaleyes.websocket.session.SessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

/**
 * WebSocket 处理器实现
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketHandlerImpl extends TextWebSocketHandler {
    
    private final ObjectMapper objectMapper;
    private final SessionManager sessionManager;
    private final MessageSenderService messageSenderService;
    
    /**
     * 连接建立后
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("新连接建立：{}", session.getId());
        
        // 直接添加会话，不需要指定数据类型
        sessionManager.addSession(session);
        
        // 发送欢迎消息
        sendWelcomeMessage(session);
    }
    
    /**
     * 收到消息后
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.debug("收到消息：{}", payload);
        
        try {
            // 解析消息
            JsonNode jsonNode = objectMapper.readTree(payload);
            
            // 处理不同类型的动作
            if (jsonNode.has("action")) {
                String action = jsonNode.get("action").asText();
                handleAction(session, action, jsonNode);
            } else if (jsonNode.has("dataType") && jsonNode.has("content")) {
                // 推送到指定数据类型
                forwardMessage(jsonNode);
            }
        } catch (Exception e) {
            log.error("处理消息失败", e);
            sendMessageToSession(session, "error", "消息处理失败：" + e.getMessage());
        }
    }
    
    /**
     * 连接关闭后
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, org.springframework.web.socket.CloseStatus closeStatus) throws Exception {
        log.info("连接关闭：{}, 原因：{}", session.getId(), closeStatus.getReason());
        sessionManager.removeSession(session.getId());
    }
    
    /**
     * 传输错误
     */
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("传输错误：{}", session.getId(), exception);
        if (!session.isOpen()) {
            sessionManager.removeSession(session.getId());
        }
    }
    
    /**
     * 发送欢迎消息
     */
    private void sendWelcomeMessage(WebSocketSession session) throws IOException {
        WebSocketMessage welcomeMsg = WebSocketMessage.builder()
                .messageId(java.util.UUID.randomUUID().toString())
                .messageType(null)
                .messageLevel(MessageLevel.INFO)
                .content(String.format("欢迎连接到 WebSocket 平台！您的会话 ID：%s。请通过 subscribe 动作订阅数据类型。", session.getId()))
                .timestamp(java.time.LocalDateTime.now())
                .build();
        
        String jsonMessage = objectMapper.writeValueAsString(welcomeMsg);
        session.sendMessage(new TextMessage(jsonMessage));
    }
    
    /**
     * 处理动作请求
     */
    private void handleAction(WebSocketSession session, String action, JsonNode jsonNode) throws Exception {
        String sessionId = session.getId();
        
        switch (action) {
            case "subscribe":
                // 订阅数据类型
                if (jsonNode.has("dataType")) {
                    String dataType = jsonNode.get("dataType").asText();
                    sessionManager.subscribe(sessionId, dataType);
                    sendMessageToSession(session, "info", 
                            String.format("成功订阅数据类型：%s", dataType));
                    log.info("会话 {} 订阅了数据类型：{}", sessionId, dataType);
                } else {
                    sendMessageToSession(session, "error", "缺少 dataType 参数");
                }
                break;
                
            case "unsubscribe":
                // 取消订阅数据类型
                if (jsonNode.has("dataType")) {
                    String dataType = jsonNode.get("dataType").asText();
                    sessionManager.unsubscribe(sessionId, dataType);
                    sendMessageToSession(session, "info", 
                            String.format("取消订阅数据类型：%s", dataType));
                    log.info("会话 {} 取消了订阅：{}", sessionId, dataType);
                } else {
                    sendMessageToSession(session, "error", "缺少 dataType 参数");
                }
                break;
                
            case "getSubscriptions":
                // 获取当前订阅列表
                Set<String> subscriptions = sessionManager.getSessionSubscriptions(sessionId);
                sendMessageToSession(session, "info", 
                        Map.of("subscriptions", subscriptions));
                break;
                
            case "getDataTypes":
                // 获取所有数据类型
                Set<String> dataTypes = sessionManager.getAllDataTypes();
                sendMessageToSession(session, "info", 
                        Map.of("dataTypes", dataTypes));
                break;
                
            case "getStats":
                // 获取统计信息
                sendStatsToSession(session);
                break;
                
            case "ping":
                // 心跳检测
                sendMessageToSession(session, "info", "pong");
                break;
                
            default:
                log.warn("未知动作：{}", action);
                sendMessageToSession(session, "warn", "未知动作：" + action);
        }
    }
    
    /**
     * 转发消息
     */
    private void forwardMessage(JsonNode jsonNode) throws Exception {
        String dataType = jsonNode.get("dataType").asText();
        String levelCode = jsonNode.has("messageLevel") ? 
                jsonNode.get("messageLevel").asText() : "info";
        Object content = jsonNode.get("content");
        
        MessageLevel messageLevel = MessageLevel.fromCode(levelCode);
        
        messageSenderService.sendMessage(dataType, messageLevel, content);
    }
    
    /**
     * 发送统计信息到会话
     */
    private void sendStatsToSession(WebSocketSession session) throws Exception {
        var stats = sessionManager.getSubscriptionStatistics();
        WebSocketMessage statsMsg = WebSocketMessage.builder()
                .messageId(java.util.UUID.randomUUID().toString())
                .messageType(null)
                .messageLevel(MessageLevel.INFO)
                .content(Map.of(
                    "totalSessions", sessionManager.getTotalSessionCount(),
                    "subscriptions", stats,
                    "dataTypes", sessionManager.getAllDataTypes()
                ))
                .timestamp(java.time.LocalDateTime.now())
                .build();
        
        String jsonMessage = objectMapper.writeValueAsString(statsMsg);
        session.sendMessage(new TextMessage(jsonMessage));
    }
    
    /**
     * 发送消息到会话
     */
    private void sendMessageToSession(WebSocketSession session, String level, Object content) throws IOException {
        if (!session.isOpen()) {
            return;
        }
        
        MessageLevel messageLevel = MessageLevel.fromCode(level);
        WebSocketMessage message = WebSocketMessage.create(null, messageLevel, content);
        String jsonMessage = objectMapper.writeValueAsString(message);
        session.sendMessage(new TextMessage(jsonMessage));
    }
}
