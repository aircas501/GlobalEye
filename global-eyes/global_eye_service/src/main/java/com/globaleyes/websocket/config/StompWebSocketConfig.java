package com.globaleyes.websocket.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP WebSocket 配置
 * 支持基于主题的发布-订阅模式，实现真正的通道隔离
 */
@Slf4j
@Configuration
@EnableWebSocketMessageBroker
public class StompWebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * 配置消息代理
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 启用简单内存消息代理，用于向客户端发送消息
        // /topic 前缀用于广播消息（一对多）
        // /queue 前缀用于点对点消息（一对一）
        config.enableSimpleBroker("/topic", "/queue");
        
        // 设置应用目的地前缀
        // 客户端发送到 /app/xxx 的消息会被路由到 @MessageMapping 方法
        config.setApplicationDestinationPrefixes("/app");
        
        log.info("STOMP 消息代理配置完成 - 主题前缀: /topic, /queue, 应用前缀: /app");
    }

    /**
     * 注册 STOMP 端点
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 注册 STOMP 端点，客户端通过此端点连接
        // 支持 SockJS  fallback
        registry.addEndpoint("/stomp")
                .setAllowedOriginPatterns("*")  // 允许所有来源（生产环境应限制）
                .withSockJS();  // 启用 SockJS 支持
        
        log.info("STOMP 端点注册完成 - 路径: /stomp, 支持 SockJS");
    }
}
