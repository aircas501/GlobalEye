package com.globaleyes.plugins.stockAnalysis.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 新闻股票分析请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "新闻股票分析请求")
public class NewsStockAnalysisRequest {

    @NotBlank(message = "新闻标题不能为空")
    @Schema(description = "新闻标题", example = "苹果发布新款iPhone，搭载自研AI芯片")
    private String title;

    @NotBlank(message = "新闻内容不能为空")
    @Schema(description = "新闻内容", example = "苹果公司今日发布了最新款iPhone...")
    private String content;

    @NotNull(message = "发布时间不能为空")
    @Schema(description = "新闻发布时间", example = "2024-01-15T14:30:00")
    private LocalDateTime publishTime;

    @Schema(description = "新闻来源", example = "新浪财经")
    private String source;

    @Schema(description = "原始URL")
    private String url;

    @Schema(description = "目标股票代码（可选，不填则自动识别）", example = "AAPL")
    private String targetStockCode;

    @Schema(description = "分析深度", example = "STANDARD", allowableValues = {"QUICK", "STANDARD", "DEEP"})
    @Builder.Default
    private AnalysisDepth depth = AnalysisDepth.STANDARD;

    @Schema(description = "是否考虑历史相似案例")
    @Builder.Default
    private Boolean includeHistoricalCases = true;

    /**
     * 分析深度枚举
     */
    public enum AnalysisDepth {
        QUICK,      // 快速分析
        STANDARD,   // 标准分析
        DEEP        // 深度分析
    }
}
