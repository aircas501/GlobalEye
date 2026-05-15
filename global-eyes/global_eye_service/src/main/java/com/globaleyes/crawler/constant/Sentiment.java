package com.globaleyes.crawler.constant;

/**
 * 关系立场枚举
 *
 * @author RSS News Crawler Team
 * @version 1.0.0
 */
public enum Sentiment {

    FRIENDLY("友好", "对头实体有利"),
    HOSTILE("敌对", "对头实体不利"),
    NEUTRAL("中立", "无明确立场");

    private final String value;
    private final String description;

    Sentiment(String value, String description) {
        this.value = value;
        this.description = description;
    }

    public String getValue() {
        return value;
    }

    public String getDescription() {
        return description;
    }

    public static Sentiment fromValue(String value) {
        if (value == null) {
            return NEUTRAL;
        }
        for (Sentiment s : values()) {
            if (s.value.equals(value)) {
                return s;
            }
        }
        return NEUTRAL;
    }
}
