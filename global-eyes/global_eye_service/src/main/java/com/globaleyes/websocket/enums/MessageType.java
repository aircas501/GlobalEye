package com.globaleyes.websocket.enums;

/**
 * 消息类型枚举
 */
public enum MessageType {
    
    /**
     * 星链消息
     */
    SATELLITE("satellite", "星链"),
    
    /**
     * 船消息
     */
    SHIP("ship", "船"),
    
    /**
     * 飞机消息
     */
    AIRCRAFT("aircraft", "飞机"),
    
    /**
     * 车辆消息
     */
    VEHICLE("vehicle", "车辆"),
    
    /**
     * 人员消息
     */
    PERSON("person", "人员"),
    
    /**
     * 设备消息
     */
    DEVICE("device", "设备"),
    
    /**
     * 系统消息
     */
    SYSTEM("system", "系统");
    
    private final String code;
    private final String description;
    
    MessageType(String code, String description) {
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
     * 根据 code 获取 MessageType
     */
    public static MessageType fromCode(String code) {
        for (MessageType type : MessageType.values()) {
            if (type.getCode().equalsIgnoreCase(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown message type: " + code);
    }
}
