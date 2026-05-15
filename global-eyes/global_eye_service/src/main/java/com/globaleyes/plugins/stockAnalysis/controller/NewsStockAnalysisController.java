package com.globaleyes.plugins.stockAnalysis.controller;

import com.globaleyes.plugins.stockAnalysis.model.NewsStockAnalysisRequest;
import com.globaleyes.plugins.stockAnalysis.model.NewsStockAnalysisResult;
import com.globaleyes.plugins.stockAnalysis.service.NewsStockAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 新闻股票分析控制器
 */
@RestController
@RequestMapping("/api/news-stock")
@CrossOrigin(origins = "*")
@Tag(name = "新闻股票分析", description = "跨领域新闻对股票影响分析接口")
@Validated
public class NewsStockAnalysisController {

    private static final Logger logger = LoggerFactory.getLogger(NewsStockAnalysisController.class);

    @Autowired
    private NewsStockAnalysisService analysisService;

    /**
     * 分析新闻对股票的影响
     */
    @PostMapping("/analyze")
    @Operation(
            summary = "分析新闻对股票的影响",
            description = "支持任意领域新闻，自动识别关联股票并分析影响。需要考虑新闻发布时间对时效性的影响。"
    )
    public ResponseEntity<NewsStockAnalysisResult> analyze(
            @Parameter(description = "新闻股票分析请求", required = true)
            @Valid @RequestBody NewsStockAnalysisRequest request) {

        logger.info("收到新闻股票分析请求，标题: {}", request.getTitle());

        try {
            NewsStockAnalysisResult result = analysisService.analyze(request);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("分析失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    @Operation(summary = "健康检查", description = "检查新闻股票分析服务是否正常运行")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("News Stock Analysis Service is running");
    }
}
