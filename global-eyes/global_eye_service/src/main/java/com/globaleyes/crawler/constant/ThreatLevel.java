package com.globaleyes.crawler.constant;

/**
 * 威胁程度枚举
 *
 * @author RSS News Crawler Team
 * @version 1.0.0
 */
public enum ThreatLevel {

    HIGH("高", "高威胁"),
    MEDIUM("中", "中等威胁"),
    LOW("低", "低威胁"),
    NONE("无", "无威胁");

    private final String value;
    private final String description;

    ThreatLevel(String value, String description) {
        this.value = value;
        this.description = description;
    }

    public String getValue() {
        return value;
    }

    public String getDescription() {
        return description;
    }

    public static ThreatLevel fromValue(String value) {
        if (value == null) {
            return NONE;
        }
        for (ThreatLevel t : values()) {
            if (t.value.equals(value)) {
                return t;
            }
        }
        return NONE;
    }
}
