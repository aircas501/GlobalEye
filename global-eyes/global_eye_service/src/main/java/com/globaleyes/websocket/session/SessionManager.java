package com.globaleyes.websocket.session;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 会话管理器
 * 支持动态数据类型和订阅模式
 */
@Slf4j
@Component
public class SessionManager {
    
    /**
     * 存储所有会话（sessionId -> session）
     */
    private final Map<String, WebSocketSession> allSessions = new ConcurrentHashMap<>();
    
    /**
     * 按数据类型存储会话订阅（dataType -> Set<sessionId>）
     * 数据类型是动态的，可以是任何字符串
     */
    private final Map<String, Set<String>> subscriptionsByType = new ConcurrentHashMap<>();
    
    /**
     * 记录每个会话订阅的数据类型（sessionId -> Set<dataType>）
     */
    private final Map<String, Set<String>> sessionSubscriptions = new ConcurrentHashMap<>();
    
    /**
     * 添加会话（不指定数据类型，直接连接）
     */
    public void addSession(WebSocketSession session) {
        if (session == null || !session.isOpen()) {
            log.warn("尝试添加无效或已关闭的会话");
            return;
        }
        
        String sessionId = session.getId();
        allSessions.put(sessionId, session);
        sessionSubscriptions.put(sessionId, ConcurrentHashMap.newKeySet());
        
        log.info("添加会话：{}", sessionId);
    }
    
    /**
     * 移除会话
     */
    public void removeSession(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return;
        }
        
        // 清理该会话的所有订阅
        Set<String> subscribedTypes = sessionSubscriptions.remove(sessionId);
        if (subscribedTypes != null) {
            for (String dataType : subscribedTypes) {
                Set<String> sessions = subscriptionsByType.get(dataType);
                if (sessions != null) {
                    sessions.remove(sessionId);
                    if (sessions.isEmpty()) {
                        subscriptionsByType.remove(dataType);
                    }
                }
            }
        }
        
        WebSocketSession session = allSessions.remove(sessionId);
        if (session != null) {
            log.info("移除会话：{}", sessionId);
        }
    }
    
    /**
     * 订阅数据类型
     */
    public void subscribe(String sessionId, String dataType) {
        if (sessionId == null || dataType == null) {
            return;
        }
        
        WebSocketSession session = getSession(sessionId);
        if (session == null) {
            log.warn("会话 {} 不存在，无法订阅", sessionId);
            return;
        }
        
        // 添加订阅关系
        subscriptionsByType.computeIfAbsent(dataType, k -> ConcurrentHashMap.newKeySet())
                          .add(sessionId);
        sessionSubscriptions.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet())
                           .add(dataType);
        
        log.info("会话 {} 订阅数据类型：{}，当前订阅数：{}", 
                sessionId, dataType, subscriptionsByType.get(dataType).size());
    }
    
    /**
     * 取消订阅数据类型
     */
    public void unsubscribe(String sessionId, String dataType) {
        if (sessionId == null || dataType == null) {
            return;
        }
        
        Set<String> sessions = subscriptionsByType.get(dataType);
        if (sessions != null) {
            sessions.remove(sessionId);
            if (sessions.isEmpty()) {
                subscriptionsByType.remove(dataType);
            }
        }
        
        Set<String> userSubscriptions = sessionSubscriptions.get(sessionId);
        if (userSubscriptions != null) {
            userSubscriptions.remove(dataType);
        }
        
        log.info("会话 {} 取消订阅数据类型：{}", sessionId, dataType);
    }
    
    /**
     * 获取订阅了指定数据类型的所有会话
     */
    public Set<WebSocketSession> getSubscribersByType(String dataType) {
        Set<String> sessionIds = subscriptionsByType.getOrDefault(dataType, Collections.emptySet());
        return sessionIds.stream()
                .map(allSessions::get)
                .filter(session -> session != null && session.isOpen())
                .collect(java.util.stream.Collectors.toSet());
    }
    
    /**
     * 获取指定会话
     */
    public WebSocketSession getSession(String sessionId) {
        WebSocketSession session = allSessions.get(sessionId);
        if (session != null && !session.isOpen()) {
            allSessions.remove(sessionId);
            return null;
        }
        return session;
    }
    
    /**
     * 获取所有会话
     */
    public Set<WebSocketSession> getAllSessions() {
        return allSessions.values().stream()
                .filter(session -> session != null && session.isOpen())
                .collect(java.util.stream.Collectors.toSet());
    }
    
    /**
     * 获取所有在线会话数量
     */
    public int getTotalSessionCount() {
        return getAllSessions().size();
    }
    
    /**
     * 获取指定数据类型的订阅数量
     */
    public int getSubscriberCountByType(String dataType) {
        return getSubscribersByType(dataType).size();
    }
    
    /**
     * 获取所有数据类型的统计信息
     */
    public Map<String, Integer> getSubscriptionStatistics() {
        Map<String, Integer> statistics = new ConcurrentHashMap<>();
        for (String dataType : subscriptionsByType.keySet()) {
            statistics.put(dataType, getSubscriberCountByType(dataType));
        }
        return statistics;
    }
    
    /**
     * 获取会话的订阅列表
     */
    public Set<String> getSessionSubscriptions(String sessionId) {
        return sessionSubscriptions.getOrDefault(sessionId, Collections.emptySet());
    }
    
    /**
     * 获取所有数据类型列表
     */
    public Set<String> getAllDataTypes() {
        return Collections.unmodifiableSet(subscriptionsByType.keySet());
    }
}
