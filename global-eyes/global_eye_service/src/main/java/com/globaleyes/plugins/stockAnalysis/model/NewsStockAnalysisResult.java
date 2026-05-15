package com.globaleyes.plugins.stockAnalysis.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 新闻股票分析结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "新闻股票分析结果")
public class NewsStockAnalysisResult {

    @Schema(description = "新闻基本信息")
    private NewsInfo newsInfo;

    @Schema(description = "时间分析")
    private TimeAnalysis timeAnalysis;

    @Schema(description = "受影响股票列表（按关联度排序）")
    private List<StockImpact> affectedStocks;

    @Schema(description = "整体市场影响评估")
    private MarketImpact marketImpact;

    @Schema(description = "总体置信度(0-1)", example = "0.75")
    private Double overallConfidence;

    @Schema(description = "分析摘要")
    private String analysisSummary;

    @Schema(description = "分析生成时间")
    private LocalDateTime analysisTime;

    @Schema(description = "免责声明")
    @Builder.Default
    private String disclaimer = "⚠️ 本分析仅供参考，不构成投资建议。股市有风险，投资需谨慎。";

    /**
     * 新闻基本信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "新闻信息")
    public static class NewsInfo {
        @Schema(description = "新闻标题")
        private String title;

        @Schema(description = "新闻摘要")
        private String summary;

        @Schema(description = "新闻来源")
        private String source;

        @Schema(description = "发布时间")
        private LocalDateTime publishTime;

        @Schema(description = "原始URL")
        private String url;
    }

    /**
     * 时间分析
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "时间维度分析")
    public static class TimeAnalysis {
        @Schema(description = "新闻发布时间")
        private LocalDateTime publishTime;

        @Schema(description = "距今时长", example = "2小时前")
        private String timeSincePublished;

        @Schema(description = "是否在交易时段发布")
        private Boolean duringTradingHours;

        @Schema(description = "时效性评级", example = "HIGH", allowableValues = {"HIGH", "MEDIUM", "LOW"})
        private String timelinessLevel;

        @Schema(description = "新闻新鲜度评分(0-1)", example = "0.9")
        private Double freshnessScore;

        @Schema(description = "时间衰减系数(0-1)", example = "0.8")
        private Double timeDecayFactor;

        @Schema(description = "建议关注时段", example = "IMMEDIATE")
        private String recommendedWatchPeriod;
    }

    /**
     * 股票影响详情
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "单只股票影响分析")
    public static class StockImpact {
        @Schema(description = "股票代码", example = "AAPL")
        private String stockCode;

        @Schema(description = "股票名称", example = "苹果公司")
        private String stockName;

        @Schema(description = "所属市场", example = "美股", allowableValues = {"A股", "港股", "美股"})
        private String market;

        @Schema(description = "关联类型", example = "DIRECT_MENTION")
        private String relationType;

        @Schema(description = "关联度评分(0-1)", example = "0.95")
        private Double relevanceScore;

        @Schema(description = "影响方向", example = "POSITIVE", allowableValues = {"POSITIVE", "NEGATIVE", "NEUTRAL"})
        private String impactDirection;

        @Schema(description = "影响程度", example = "HIGH", allowableValues = {"HIGH", "MODERATE", "LOW"})
        private String impactSeverity;

        @Schema(description = "预计涨跌幅百分比", example = "3.5")
        private Double predictedChangePercent;

        @Schema(description = "影响持续时间", example = "SHORT_TERM")
        private String duration;

        @Schema(description = "置信度(0-1)", example = "0.8")
        private Double confidence;

        @Schema(description = "影响逻辑说明")
        private String reasoning;

        @Schema(description = "关键影响因素")
        private List<String> keyFactors;
    }

    /**
     * 整体市场影响
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "整体市场影响")
    public static class MarketImpact {
        @Schema(description = "对市场整体情绪的影响", example = "SLIGHTLY_POSITIVE")
        private String marketSentiment;

        @Schema(description = "可能受影响的板块")
        private List<String> affectedSectors;

        @Schema(description = "是否需要关注系统性风险")
        private Boolean systemicRisk;

        @Schema(description = "建议关注的重点")
        private List<String> focusPoints;
    }
}
