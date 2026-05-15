package com.globaleyes.websocket.enums;

/**
 * 消息等级枚举
 */
public enum MessageLevel {
    
    /**
     * 正常消息
     */
    INFO("info", "正常消息"),
    
    /**
     * 警告消息
     */
    WARN("warn", "警告消息"),
    
    /**
     * 错误消息
     */
    ERROR("error", "错误消息");
    
    private final String code;
    private final String description;
    
    MessageLevel(String code, String description) {
        this.code = code;
        this.description = description;
    }
    
    public String getCode() {
        return code;
    }
    
    public String getDescription() {
        return description;
    }
    
    /**
     * 根据 code 获取 MessageLevel
     */
    public static MessageLevel fromCode(String code) {
        for (MessageLevel level : MessageLevel.values()) {
            if (level.getCode().equalsIgnoreCase(code)) {
                return level;
            }
        }
        throw new IllegalArgumentException("Unknown message level: " + code);
    }
}
