package com.globaleyes.crawler.constant;

/**
 * 实体在事件中的角色枚举
 *
 * @author RSS News Crawler Team
 * @version 1.0.0
 */
public enum RoleInEvent {

    AGGRESSOR("Aggressor", "侵略/主动进攻方"),
    DEFENDER("Defender", "防御/抵抗方"),
    TARGET("Target", "被攻击/被针对目标"),
    SUPPORTER("Supporter", "支援/援助方"),
    MEDIATOR("Mediator", "调停/斡旋方"),
    OBSERVER("Observer", "观察/监视方"),
    PARTICIPANT("Participant", "一般参与方"),
    UNKNOWN("Unknown", "无法判断");

    private final String value;
    private final String description;

    RoleInEvent(String value, String description) {
        this.value = value;
        this.description = description;
    }

    public String getValue() {
        return value;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 根据字符串值获取枚举
     *
     * @param value 字符串值
     * @return 对应的枚举，未找到则返回 UNKNOWN
     */
    public static RoleInEvent fromValue(String value) {
        if (value == null) {
            return UNKNOWN;
        }
        for (RoleInEvent role : values()) {
            if (role.value.equalsIgnoreCase(value)) {
                return role;
            }
        }
        return UNKNOWN;
    }
}
