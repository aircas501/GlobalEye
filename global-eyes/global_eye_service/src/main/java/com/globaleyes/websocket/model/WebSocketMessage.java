package com.globaleyes.websocket.model;

import com.globaleyes.websocket.enums.MessageLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * WebSocket 消息模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebSocketMessage {
    
    /**
     * 消息 ID
     */
    private String messageId;
    
    /**
     * 消息类型（字符串类型，支持动态类型）
     */
    private String messageType;
    
    /**
     * 消息等级
     */
    private MessageLevel messageLevel;
    
    /**
     * 消息内容
     */
    private Object content;
    
    /**
     * 发送时间
     */
    private LocalDateTime timestamp;
    
    /**
     * 发送者
     */
    private String sender;
    
    /**
     * 接收者（可选，为空表示广播）
     */
    private String receiver;
    
    /**
     * 扩展数据
     */
    private Object extraData;
    
    /**
     * 创建带默认值的消息
     *
     * @param messageType 消息类型（字符串，如 "satellite", "ship", "A", "B" 等）
     * @param messageLevel 消息等级
     * @param content 消息内容
     */
    public static WebSocketMessage create(String messageType, MessageLevel messageLevel, Object content) {
        return WebSocketMessage.builder()
                .messageId(java.util.UUID.randomUUID().toString())
                .messageType(messageType)
                .messageLevel(messageLevel)
                .content(content)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
