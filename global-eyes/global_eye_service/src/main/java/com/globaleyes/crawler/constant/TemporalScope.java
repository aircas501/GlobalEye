package com.globaleyes.crawler.constant;

/**
 * 时间状态枚举
 *
 * @author RSS News Crawler Team
 * @version 1.0.0
 */
public enum TemporalScope {

    ONGOING("ongoing", "正在发生/进行中"),
    COMPLETED("completed", "已完成/已结束"),
    PLANNED("planned", "计划中/未来将发生"),
    UNKNOWN("unknown", "无法从文中判断");

    private final String value;
    private final String description;

    TemporalScope(String value, String description) {
        this.value = value;
        this.description = description;
    }

    public String getValue() {
        return value;
    }

    public String getDescription() {
        return description;
    }

    public static TemporalScope fromValue(String value) {
        if (value == null) {
            return UNKNOWN;
        }
        for (TemporalScope t : values()) {
            if (t.value.equalsIgnoreCase(value)) {
                return t;
            }
        }
        return UNKNOWN;
    }
}
