package com.globaleyes.plugins.stockAnalysis.util;

import com.globaleyes.plugins.stockAnalysis.model.NewsStockAnalysisRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.temporal.ChronoUnit;

/**
 * 置信度计算器 - 多维度评估分析结果的可信程度
 */
public class ConfidenceCalculator {

    private static final Logger logger = LoggerFactory.getLogger(ConfidenceCalculator.class);

    /**
     * 计算总体置信度
     * 
     * @param request 分析请求
     * @param hasClearEntities 是否有明确的实体识别
     * @param hasHistoricalCases 是否有历史案例参考
     * @param hasRealtimeData 是否有实时数据支撑
     * @param newsQuality 新闻质量评分 (0-1)
     * @return 置信度 (0-1)
     */
    public static double calculateConfidence(
            NewsStockAnalysisRequest request,
            boolean hasClearEntities,
            boolean hasHistoricalCases,
            boolean hasRealtimeData,
            double newsQuality) {
        
        double baseScore = 0.4; // 基础分
        
        logger.debug("开始计算置信度...");
        
        // 1. 新闻清晰度（是否明确提到公司名）- 权重 20%
        double clarityScore = hasClearEntities ? 0.2 : 0.05;
        logger.debug("清晰度评分: {}", clarityScore);
        
        // 2. 历史案例匹配度 - 权重 15%
        double historyScore = hasHistoricalCases ? 0.15 : 0.05;
        logger.debug("历史案例评分: {}", historyScore);
        
        // 3. 实时数据支撑 - 权重 10%
        double dataScore = hasRealtimeData ? 0.1 : 0.02;
        logger.debug("实时数据评分: {}", dataScore);
        
        // 4. 新闻质量 - 权重 15%
        double qualityScore = newsQuality * 0.15;
        logger.debug("新闻质量评分: {}", qualityScore);
        
        // 5. 时效性 - 权重 10%
        double timelinessScore = calculateTimelinessScore(request.getPublishTime());
        logger.debug("时效性评分: {}", timelinessScore);
        
        // 6. 信息完整性 - 权重 10%
        double completenessScore = calculateCompletenessScore(request);
        logger.debug("完整性评分: {}", completenessScore);
        
        // 计算总分
        double totalScore = baseScore + clarityScore + historyScore + dataScore 
                          + qualityScore + timelinessScore + completenessScore;
        
        // 限制在 0.1-0.95 范围（避免过高或过低）
        double finalConfidence = Math.min(0.95, Math.max(0.1, totalScore));
        
        logger.info("置信度计算完成: 基础={}, 清晰度={}, 历史={}, 数据={}, 质量={}, 时效={}, 完整={}, 总计={}",
                baseScore, clarityScore, historyScore, dataScore, 
                qualityScore, timelinessScore, completenessScore, finalConfidence);
        
        return Math.round(finalConfidence * 100.0) / 100.0; // 保留两位小数
    }

    /**
     * 计算时效性评分
     */
    private static double calculateTimelinessScore(java.time.LocalDateTime publishTime) {
        long hoursElapsed = ChronoUnit.HOURS.between(publishTime, java.time.LocalDateTime.now());
        
        if (hoursElapsed <= 2) {
            return 0.1;  // 非常新鲜
        } else if (hoursElapsed <= 24) {
            return 0.07; // 较新鲜
        } else if (hoursElapsed <= 72) {
            return 0.04; // 一般
        } else {
            return 0.02; // 较旧
        }
    }

    /**
     * 计算信息完整性评分
     */
    private static double calculateCompletenessScore(NewsStockAnalysisRequest request) {
        double score = 0.0;
        
        // 有标题
        if (request.getTitle() != null && !request.getTitle().isEmpty()) {
            score += 0.03;
        }
        
        // 内容长度足够
        if (request.getContent() != null && request.getContent().length() > 100) {
            score += 0.04;
        } else if (request.getContent() != null && request.getContent().length() > 50) {
            score += 0.02;
        }
        
        // 有来源
        if (request.getSource() != null && !request.getSource().isEmpty()) {
            score += 0.02;
        }
        
        // 有链接
        if (request.getUrl() != null && !request.getUrl().isEmpty()) {
            score += 0.01;
        }
        
        return score;
    }

    /**
     * 评估新闻质量
     */
    public static double evaluateNewsQuality(String title, String content) {
        if (title == null || content == null) {
            return 0.3;
        }
        
        double quality = 0.5; // 基础分
        
        // 标题长度适中
        if (title.length() >= 10 && title.length() <= 50) {
            quality += 0.1;
        }
        
        // 内容长度充足
        if (content.length() >= 200) {
            quality += 0.2;
        } else if (content.length() >= 100) {
            quality += 0.1;
        }
        
        // 包含数字（通常更有信息量）
        if (content.matches(".*\\d+.*")) {
            quality += 0.1;
        }
        
        // 包含专业术语
        String[] keywords = {"增长", "下降", "上涨", "下跌", "利好", "利空", "政策", "财报", "业绩"};
        for (String keyword : keywords) {
            if (content.contains(keyword)) {
                quality += 0.05;
                break;
            }
        }
        
        return Math.min(1.0, quality);
    }

    /**
     * 检查是否有明确的实体（公司名称、股票代码）
     */
    public static boolean hasClearEntities(String content) {
        if (content == null || content.isEmpty()) {
            return false;
        }
        
        // 检查是否包含常见公司名关键词
        String[] companyKeywords = {
            "公司", "集团", "股份", "科技", "银行", "保险", 
            "汽车", "医药", "能源", "电子", "网络", "平台"
        };
        
        for (String keyword : companyKeywords) {
            if (content.contains(keyword)) {
                return true;
            }
        }
        
        // 检查是否包含股票代码格式
        if (content.matches(".*\\b[A-Z]{1,5}\\b.*") || // 美股代码
            content.matches(".*\\b[0-9]{6}\\b.*")) {   // A股代码
            return true;
        }
        
        return false;
    }

    /**
     * 获取置信度等级
     */
    public static ConfidenceLevel getConfidenceLevel(double confidence) {
        if (confidence >= 0.8) {
            return ConfidenceLevel.HIGH;
        } else if (confidence >= 0.6) {
            return ConfidenceLevel.MEDIUM;
        } else if (confidence >= 0.4) {
            return ConfidenceLevel.LOW;
        } else {
            return ConfidenceLevel.VERY_LOW;
        }
    }

    /**
     * 置信度等级枚举
     */
    public enum ConfidenceLevel {
        HIGH(0.8, 1.0, "高置信度", "🟢", "分析依据充分，结果可信度高"),
        MEDIUM(0.6, 0.8, "中等置信度", "🟡", "有一定分析依据，建议结合其他信息"),
        LOW(0.4, 0.6, "低置信度", "🟠", "分析依据有限，仅供参考"),
        VERY_LOW(0.0, 0.4, "极低置信度", "🔴", "分析依据不足，谨慎参考");

        private final double min;
        private final double max;
        private final String label;
        private final String icon;
        private final String description;

        ConfidenceLevel(double min, double max, String label, String icon, String description) {
            this.min = min;
            this.max = max;
            this.label = label;
            this.icon = icon;
            this.description = description;
        }

        public double getMin() { return min; }
        public double getMax() { return max; }
        public String getLabel() { return label; }
        public String getIcon() { return icon; }
        public String getDescription() { return description; }
    }
}
