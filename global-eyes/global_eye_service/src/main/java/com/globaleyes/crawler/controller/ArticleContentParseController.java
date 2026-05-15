package com.globaleyes.crawler.controller;

import com.globaleyes.crawler.service.crawler.ArticleContentParseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 文章内容解析控制器
 * 提供地名提取和坐标标注功能
 *
 * @author RSS News Crawler Team
 * @version 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/article")
@Tag(name = "文章内容解析", description = "文章地名提取和坐标标注接口")
public class ArticleContentParseController {

    private final ArticleContentParseService articleContentParseService;

    public ArticleContentParseController(ArticleContentParseService articleContentParseService) {
        this.articleContentParseService = articleContentParseService;
    }

    /**
     * 根据文章ID解析内容，提取地名并标注坐标
     *
     * @param id 文章ID
     * @return 解析后的文章内容
     */
    @GetMapping("/parse/{id}")
    @Operation(
            summary = "解析文章内容", 
            description = "根据文章ID获取新闻内容，使用AI提取地名并标注经纬度坐标"
    )
    public ResponseEntity<Map<String, Object>> parseArticleContent(
            @Parameter(description = "文章ID", example = "1") 
            @PathVariable Long id) {
        
        Map<String, Object> result = new HashMap<>();

        try {
            log.info("收到文章解析请求，ID: {}", id);

            // 调用服务层解析文章
            String parsedContent = articleContentParseService.parseArticleContent(id);

            result.put("success", true);
            result.put("articleId", id);
            result.put("parsedContent", parsedContent);
            result.put("message", "解析成功");

            log.info("文章解析完成，ID: {}", id);
            return ResponseEntity.ok(result);

        } catch (RuntimeException e) {
            log.error("文章解析失败，ID: {}, 错误: {}", id, e.getMessage());
            
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("articleId", id);

            return ResponseEntity.status(404).body(result);

        } catch (Exception e) {
            log.error("文章解析发生未知错误，ID: {}", id, e);
            
            result.put("success", false);
            result.put("error", "服务器内部错误: " + e.getMessage());
            result.put("articleId", id);

            return ResponseEntity.status(500).body(result);
        }
    }

    /**
     * 批量解析文章内容（可选扩展）
     *
     * @param ids 文章ID列表
     * @return 解析结果Map
     */
    @PostMapping("/parse/batch")
    @Operation(
            summary = "批量解析文章内容", 
            description = "根据多个文章ID批量解析内容，提取地名并标注坐标"
    )
    public ResponseEntity<Map<String, Object>> batchParseArticles(
            @Parameter(description = "文章ID列表") 
            @RequestBody java.util.List<Long> ids) {
        
        Map<String, Object> result = new HashMap<>();
        Map<Long, Object> parsedResults = new HashMap<>();

        try {
            log.info("收到批量文章解析请求，数量: {}", ids.size());

            for (Long id : ids) {
                try {
                    String parsedContent = articleContentParseService.parseArticleContent(id);
                    parsedResults.put(id, parsedContent);
                } catch (Exception e) {
                    log.warn("文章解析失败，ID: {}, 错误: {}", id, e.getMessage());
                    parsedResults.put(id, "解析失败: " + e.getMessage());
                }
            }

            result.put("success", true);
            result.put("totalCount", ids.size());
            result.put("results", parsedResults);
            result.put("message", "批量解析完成");

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("批量文章解析发生错误", e);
            
            result.put("success", false);
            result.put("error", "批量解析失败: " + e.getMessage());

            return ResponseEntity.status(500).body(result);
        }
    }
}
