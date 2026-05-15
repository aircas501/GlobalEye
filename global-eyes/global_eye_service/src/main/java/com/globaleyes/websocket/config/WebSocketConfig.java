package com.globaleyes.websocket.config;

import com.globaleyes.websocket.handler.WebSocketHandlerImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置类
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {
    
    private final WebSocketHandlerImpl webSocketHandler;
    
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 注册 WebSocket 处理器，支持跨域访问
        // 使用 allowedOriginPatterns 替代 setAllowedOrigins 以支持更灵活的跨域配置
        registry.addHandler(webSocketHandler, "/ws")
                .setAllowedOriginPatterns("*")
                .setAllowedOrigins("*")
                .addInterceptors(new WebSocketHandshakeInterceptor());
        
        // 添加 SockJS 支持（用于不支持 WebSocket 的浏览器）
        registry.addHandler(webSocketHandler, "/ws-sockjs")
                .setAllowedOriginPatterns("*")
                .setAllowedOrigins("*")
                .addInterceptors(new WebSocketHandshakeInterceptor())
                .withSockJS();
    }
}
