package com.globaleyes.plugins.stockAnalysis.util;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

/**
 * 股票交易时间工具类
 */
public class StockTradingTimeUtil {

    /**
     * 判断是否在A股交易时段
     * A股交易时间：周一至周五 9:30-11:30, 13:00-15:00
     */
    public static boolean isTradingHours(LocalDateTime time) {
        DayOfWeek day = time.getDayOfWeek();
        LocalTime localTime = time.toLocalTime();

        // 周一至周五
        if (day.getValue() >= 1 && day.getValue() <= 5) {
            // 上午 9:30-11:30 或 下午 13:00-15:00
            return (localTime.isAfter(LocalTime.of(9, 29)) && localTime.isBefore(LocalTime.of(11, 31))) ||
                   (localTime.isAfter(LocalTime.of(12, 59)) && localTime.isBefore(LocalTime.of(15, 1)));
        }
        return false;
    }

    /**
     * 计算时间衰减系数
     * 新闻越新，影响越大；随时间推移，影响递减
     */
    public static double calculateTimeDecay(LocalDateTime publishTime) {
        long hoursElapsed = ChronoUnit.HOURS.between(publishTime, LocalDateTime.now());

        if (hoursElapsed <= 2) {
            return 1.0;  // 2小时内，无衰减
        } else if (hoursElapsed <= 24) {
            return 0.8;  // 24小时内，轻微衰减
        } else if (hoursElapsed <= 72) {
            return 0.5;  // 3天内，中度衰减
        } else if (hoursElapsed <= 168) {
            return 0.3;  // 1周内，较大衰减
        } else {
            return 0.1;  // 超过1周，影响很小
        }
    }

    /**
     * 计算新鲜度评分 (0-1)
     */
    public static double calculateFreshnessScore(LocalDateTime publishTime) {
        long hoursElapsed = ChronoUnit.HOURS.between(publishTime, LocalDateTime.now());

        if (hoursElapsed <= 1) {
            return 1.0;
        } else if (hoursElapsed <= 6) {
            return 0.9;
        } else if (hoursElapsed <= 24) {
            return 0.7;
        } else if (hoursElapsed <= 72) {
            return 0.5;
        } else {
            return Math.max(0.1, 1.0 - (hoursElapsed / 168.0));
        }
    }

    /**
     * 获取时效性评级
     */
    public static String getTimelinessLevel(LocalDateTime publishTime) {
        long hoursElapsed = ChronoUnit.HOURS.between(publishTime, LocalDateTime.now());

        if (hoursElapsed <= 2) {
            return "HIGH";
        } else if (hoursElapsed <= 24) {
            return "MEDIUM";
        } else {
            return "LOW";
        }
    }

    /**
     * 获取距今时长的可读字符串
     */
    public static String getTimeSincePublished(LocalDateTime publishTime) {
        long minutes = ChronoUnit.MINUTES.between(publishTime, LocalDateTime.now());
        long hours = ChronoUnit.HOURS.between(publishTime, LocalDateTime.now());
        long days = ChronoUnit.DAYS.between(publishTime, LocalDateTime.now());

        if (minutes < 60) {
            return minutes + "分钟前";
        } else if (hours < 24) {
            return hours + "小时前";
        } else {
            return days + "天前";
        }
    }

    /**
     * 获取建议关注时段
     */
    public static String getRecommendedWatchPeriod(LocalDateTime publishTime) {
        long hoursElapsed = ChronoUnit.HOURS.between(publishTime, LocalDateTime.now());

        if (hoursElapsed <= 2) {
            return "IMMEDIATE";  // 立即关注
        } else if (hoursElapsed <= 24) {
            return "TODAY";      // 今日关注
        } else if (hoursElapsed <= 72) {
            return "THIS_WEEK";  // 本周关注
        } else {
            return "MONITOR";    // 持续监控
        }
    }
}
