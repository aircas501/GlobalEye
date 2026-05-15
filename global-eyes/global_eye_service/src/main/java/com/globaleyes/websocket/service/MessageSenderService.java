package com.globaleyes.websocket.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.globaleyes.websocket.enums.MessageLevel;
import com.globaleyes.websocket.model.WebSocketMessage;
import com.globaleyes.websocket.session.SessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 消息发送服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageSenderService {
    
    private final SessionManager sessionManager;
    private final ObjectMapper objectMapper;
    
    /**
     * 会话级别的锁，防止并发写入
     */
    private final Map<String, Object> sessionLocks = new ConcurrentHashMap<>();
    
    /**
     * 发送消息到指定数据类型的所有订阅者
     *
     * @param dataType 数据类型（可以是任何字符串）
     * @param messageLevel 消息等级
     * @param content 消息内容
     */
    public void sendMessage(String dataType, MessageLevel messageLevel, Object content) {
        WebSocketMessage message = WebSocketMessage.create(dataType, messageLevel, content);
        sendToSubscribers(sessionManager.getSubscribersByType(dataType), message, dataType);
    }
    
    /**
     * 发送消息到指定数据类型的所有订阅者（使用已有消息对象）
     */
    public void sendMessage(String dataType, WebSocketMessage message) {
        if (message == null || dataType == null) {
            log.warn("消息或数据类型为 null，无法发送");
            return;
        }
        sendToSubscribers(sessionManager.getSubscribersByType(dataType), message, dataType);
    }
    
    /**
     * 广播消息到所有会话（不区分类型）
     */
    public void broadcastMessage(MessageLevel messageLevel, Object content) {
        WebSocketMessage message = WebSocketMessage.create(null, messageLevel, content);
        sendToSubscribers(sessionManager.getAllSessions(), message, "BROADCAST");
    }
    
    /**
     * 发送消息到指定会话
     */
    public void sendToSession(String sessionId, MessageLevel messageLevel, Object content) {
        WebSocketSession session = sessionManager.getSession(sessionId);
        if (session != null && session.isOpen()) {
            try {
                WebSocketMessage message = WebSocketMessage.create(null, messageLevel, content);
                sendMessageToSession(session, message);
            } catch (Exception e) {
                log.error("发送消息到会话 {} 失败", sessionId, e);
            }
        } else {
            log.warn("会话 {} 不存在或已关闭", sessionId);
        }
    }
    
    /**
     * 发送消息到多个订阅者
     */
    private void sendToSubscribers(Set<WebSocketSession> subscribers, WebSocketMessage message, String dataType) {
        if (subscribers == null || subscribers.isEmpty()) {
            log.warn("没有订阅者来发送数据：{}", dataType);
            return;
        }
        
        int successCount = 0;
        int failCount = 0;
        
        for (WebSocketSession session : subscribers) {
            try {
                sendMessageToSession(session, message);
                successCount++;
            } catch (IOException e) {
                log.error("发送消息到会话 {} 失败", session.getId(), e);
                failCount++;
                // 关闭的会话需要清理
                if (!session.isOpen()) {
                    sessionManager.removeSession(session.getId());
                }
            }
        }
        
        log.info("消息发送完成 - 数据类型：{}, 等级：{}, 成功：{}, 失败：{}", 
                dataType, message.getMessageLevel(), successCount, failCount);
    }
    
    /**
     * 发送消息到单个会话（带同步锁）
     */
    private void sendMessageToSession(WebSocketSession session, WebSocketMessage message) throws IOException {
        if (!session.isOpen()) {
            return;
        }
        
        String sessionId = session.getId();
        // 获取或创建会话锁
        Object lock = sessionLocks.computeIfAbsent(sessionId, k -> new Object());
        
        synchronized (lock) {
            try {
                if (session.isOpen()) {
                    String jsonMessage = objectMapper.writeValueAsString(message);
                    session.sendMessage(new TextMessage(jsonMessage));
                    log.debug("消息已发送到会话 {}: {}", sessionId, message.getMessageId());
                }
            } finally {
                // 清理不再使用的锁（可选优化）
                if (!session.isOpen()) {
                    sessionLocks.remove(sessionId);
                }
            }
        }
    }
    
    /**
     * 发送自定义消息
     */
    public void sendCustomMessage(WebSocketMessage message, String sessionId) {
        if (sessionId != null) {
            sendToSession(sessionId, message.getMessageLevel(), message.getContent());
        } else {
            // 如果没有指定 sessionId，广播到所有会话
            broadcastMessage(message.getMessageLevel(), message.getContent());
        }
    }
}
