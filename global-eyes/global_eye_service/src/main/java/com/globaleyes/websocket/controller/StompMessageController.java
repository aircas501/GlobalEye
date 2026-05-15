package com.globaleyes.websocket.controller;

import com.globaleyes.websocket.enums.MessageLevel;
import com.globaleyes.websocket.model.WebSocketMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * STOMP 消息控制器
 * 处理客户端通过 STOMP 协议发送的消息
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class StompMessageController {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 处理客户端发送到指定通道的消息
     * 客户端发送: /app/send/{channel}
     * 广播到: /topic/{channel}
     *
     * @param channel 通道名称（如 A, B, satellite, ship 等）
     * @param content 消息内容
     * @return 广播到订阅了该通道的所有客户端
     */
    @MessageMapping("/send/{channel}")
    @SendTo("/topic/{channel}")
    public WebSocketMessage sendMessageToChannel(
            @DestinationVariable String channel,
            Map<String, Object> content) {
        
        log.info("收到 STOMP 消息 - 通道: {}, 内容: {}", channel, content);
        
        // 构建 WebSocket 消息
        WebSocketMessage message = WebSocketMessage.builder()
                .messageId(UUID.randomUUID().toString())
                .messageType(channel)  // messageType 设置为通道名称
                .messageLevel(MessageLevel.INFO)
                .content(content)
                .timestamp(LocalDateTime.now())
                .sender("stomp-client")
                .build();
        
        log.info("消息已广播到通道 /topic/{}", channel);
        return message;
    }

    /**
     * 处理带消息等级的消息
     * 客户端发送: /app/sendWithLevel/{channel}/{level}
     */
    @MessageMapping("/sendWithLevel/{channel}/{level}")
    @SendTo("/topic/{channel}")
    public WebSocketMessage sendMessageWithLevel(
            @DestinationVariable String channel,
            @DestinationVariable String level,
            Map<String, Object> content) {
        
        MessageLevel messageLevel;
        try {
            messageLevel = MessageLevel.fromCode(level);
        } catch (IllegalArgumentException e) {
            messageLevel = MessageLevel.INFO;
        }
        
        log.info("收到 STOMP 消息 - 通道: {}, 等级: {}, 内容: {}", channel, level, content);
        
        WebSocketMessage message = WebSocketMessage.builder()
                .messageId(UUID.randomUUID().toString())
                .messageType(channel)
                .messageLevel(messageLevel)
                .content(content)
                .timestamp(LocalDateTime.now())
                .sender("stomp-client")
                .build();
        
        log.info("消息已广播到通道 /topic/{}", channel);
        return message;
    }

    /**
     * 服务端主动推送消息到指定通道
     * 这个方法可以被任何 Service 调用，实现服务端推送
     *
     * @param channel 通道名称
     * @param messageLevel 消息等级
     * @param content 消息内容
     */
    public void pushToChannel(String channel, MessageLevel messageLevel, Object content) {
        WebSocketMessage message = WebSocketMessage.builder()
                .messageId(UUID.randomUUID().toString())
                .messageType(channel)
                .messageLevel(messageLevel)
                .content(content)
                .timestamp(LocalDateTime.now())
                .sender("server")
                .build();
        
        // 发送到 /topic/{channel}，所有订阅了该通道的客户端都会收到
        messagingTemplate.convertAndSend("/topic/" + channel, message);
        
        log.info("服务端推送消息到通道 /topic/{} - 等级: {}", channel, messageLevel);
    }

    /**
     * 发送系统通知到所有通道
     */
    public void broadcastToAll(MessageLevel messageLevel, Object content) {
        WebSocketMessage message = WebSocketMessage.builder()
                .messageId(UUID.randomUUID().toString())
                .messageType("SYSTEM")
                .messageLevel(messageLevel)
                .content(content)
                .timestamp(LocalDateTime.now())
                .sender("server")
                .build();
        
        // 发送到 /topic/all，所有客户端都会收到
        messagingTemplate.convertAndSend("/topic/all", message);
        
        log.info("服务端广播系统消息 - 等级: {}", messageLevel);
    }
}
