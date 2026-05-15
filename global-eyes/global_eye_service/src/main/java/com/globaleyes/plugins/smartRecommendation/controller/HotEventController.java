package com.globaleyes.plugins.smartRecommendation.controller;

import com.globaleyes.crawler.pojo.entity.neo4j.HotEventNode;
import com.globaleyes.crawler.pojo.entity.neo4j.NewsArticleNode;
import com.globaleyes.plugins.smartRecommendation.service.HotEventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 热点事件控制器
 * 提供热点事件查询及关联文章的REST API
 *
 * @author RSS News Crawler Team
 * @version 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/hot-events")
@Tag(name = "热点事件管理", description = "热点事件查询及关联文章接口")
public class HotEventController {

    private final HotEventService hotEventService;

    public HotEventController(HotEventService hotEventService) {
        this.hotEventService = hotEventService;
    }

     /**
     * 根据事件名称和领域查询关联的新闻文章（模糊匹配）
     *
     * @param eventName 热点事件名称
     * @param topic 领域（如：军事、政治、经济）
     * @return 新闻文章列表
     */
    @GetMapping("/articles-filtered/{eventName}")
    @Operation(
            summary = "根据领域筛选事件的关联文章",
            description = "根据热点事件名称和领域（topic）查询关联的新闻文章，使用模糊匹配，按发布时间降序排列"
    )
    public ResponseEntity<Map<String, Object>> getArticlesByEventNameFiltered(
            @Parameter(description = "热点事件名称", example = "俄乌冲突")
            @PathVariable String eventName,
            @Parameter(description = "领域（模糊匹配）", example = "军事", required = true)
            @RequestParam String topic) {

        Map<String, Object> result = new HashMap<>();

        try {
            log.info("收到查询请求：事件[{}]、领域[{}]的关联文章", eventName, topic);

            // 参数校验
            if (topic == null || topic.trim().isEmpty()) {
                result.put("success", false);
                result.put("error", "领域参数不能为空");
                result.put("eventName", eventName);
                return ResponseEntity.badRequest().body(result);
            }

            List<NewsArticleNode> articles = hotEventService.getArticlesByEventNameAndTopic(eventName, topic.trim());

            result.put("success", true);
            result.put("eventName", eventName);
            result.put("topic", topic);
            result.put("articles", articles);
            result.put("totalCount", articles.size());
            result.put("message", "查询成功");

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("查询事件[{}]、领域[{}]的关联文章失败", eventName, topic, e);

            result.put("success", false);
            result.put("error", "查询失败: " + e.getMessage());
            result.put("eventName", eventName);
            result.put("topic", topic);

            return ResponseEntity.status(500).body(result);
        }
    }

    /**
     * 根据地区名称查询相关的热点事件
     *
     * @param locationName 地区名称
     * @return 热点事件列表
     */
    @GetMapping("/by-location/{locationName}")
    @Operation(
            summary = "根据地区查询热点事件",
            description = "根据地区名称查询所有相关的热点事件，关系为 (HotEvent)-[:OCCURS_IN]->(Location)"
    )
    public ResponseEntity<Map<String, Object>> getEventsByLocation(
            @Parameter(description = "地区名称", example = "中东")
            @PathVariable String locationName) {

        Map<String, Object> result = new HashMap<>();

        try {
            log.info("收到查询请求：地区[{}]相关的热点事件", locationName);

            List<HotEventNode> events =
                    hotEventService.getEventsByLocationName(locationName);

            result.put("success", true);
            result.put("locationName", locationName);
            result.put("events", events);
            result.put("totalCount", events.size());
            result.put("message", "查询成功");

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("查询地区[{}]相关的热点事件失败", locationName, e);

            result.put("success", false);
            result.put("error", "查询失败: " + e.getMessage());
            result.put("locationName", locationName);

            return ResponseEntity.status(500).body(result);
        }
    }

    /**
     * 根据地区名称和领域查询相关的热点事件
     *
     * @param locationName 地区名称
     * @param topic 领域（如：军事、政治、经济）
     * @return 热点事件列表
     */
    @GetMapping("/by-location-filtered/{locationName}")
    @Operation(
            summary = "根据地区和领域查询热点事件",
            description = "根据地区名称和领域（topic）查询相关的热点事件，通过 (HotEvent)-[:INCLUDES]->(NewsArticle) 关系获取文章的topic字段进行筛选"
    )
    public ResponseEntity<Map<String, Object>> getEventsByLocationFiltered(
            @Parameter(description = "地区名称", example = "中东")
            @PathVariable String locationName,
            @Parameter(description = "领域", example = "军事", required = true)
            @RequestParam String topic) {

        Map<String, Object> result = new HashMap<>();

        try {
            log.info("收到查询请求：地区[{}]、领域[{}]相关的热点事件", locationName, topic);

            // 参数校验
            if (topic == null || topic.trim().isEmpty()) {
                result.put("success", false);
                result.put("error", "领域参数不能为空");
                result.put("locationName", locationName);
                return ResponseEntity.badRequest().body(result);
            }

            List<HotEventNode> events =
                    hotEventService.getEventsByLocationAndTopic(locationName, topic.trim());

            result.put("success", true);
            result.put("locationName", locationName);
            result.put("topic", topic);
            result.put("events", events);
            result.put("totalCount", events.size());
            result.put("message", "查询成功");

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("查询地区[{}]、领域[{}]相关的热点事件失败", locationName, topic, e);

            result.put("success", false);
            result.put("error", "查询失败: " + e.getMessage());
            result.put("locationName", locationName);
            result.put("topic", topic);

            return ResponseEntity.status(500).body(result);
        }
    }


    // ======================== 分割线 ===================================

    // /**
    //  * 根据事件名称查询关联的新闻文章
    //  *
    //  * @param eventName 热点事件名称
    //  * @return 新闻文章列表
    //  */
    // @GetMapping("/articles/{eventName}")
    // @Operation(
    //         summary = "查询事件的关联文章",
    //         description = "根据热点事件名称查询所有关联的新闻文章，按发布时间降序排列"
    // )
    // public ResponseEntity<Map<String, Object>> getArticlesByEventName(
    //         @Parameter(description = "热点事件名称", example = "俄乌冲突")
    //         @PathVariable String eventName) {
    //
    //     Map<String, Object> result = new HashMap<>();
    //
    //     try {
    //         log.info("收到查询请求：事件[{}]的关联文章", eventName);
    //
    //         List<NewsArticleNode> articles = hotEventService.getArticlesByEventName(eventName);
    //
    //         result.put("success", true);
    //         result.put("eventName", eventName);
    //         result.put("articles", articles);
    //         result.put("totalCount", articles.size());
    //         result.put("message", "查询成功");
    //
    //         return ResponseEntity.ok(result);
    //
    //     } catch (Exception e) {
    //         log.error("查询事件[{}]的关联文章失败", eventName, e);
    //
    //         result.put("success", false);
    //         result.put("error", "查询失败: " + e.getMessage());
    //         result.put("eventName", eventName);
    //
    //         return ResponseEntity.status(500).body(result);
    //     }
    // }



    // /**
    //  * 根据事件ID查询关联的新闻文章（分页）
    //  *
    //  * @param eventId 热点事件ID
    //  * @param page    页码（从0开始）
    //  * @param size    每页数量
    //  * @return 分页的新闻文章列表
    //  */
    // @GetMapping("/id/{eventId}/articles")
    // @Operation(
    //         summary = "分页查询事件的关联文章",
    //         description = "根据热点事件ID分页查询关联的新闻文章，支持大规模数据查询"
    // )
    // public ResponseEntity<Map<String, Object>> getArticlesByEventId(
    //         @Parameter(description = "热点事件ID", example = "1")
    //         @PathVariable Long eventId,
    //         @Parameter(description = "页码（从0开始）", example = "0")
    //         @RequestParam(defaultValue = "0") int page,
    //         @Parameter(description = "每页数量", example = "10")
    //         @RequestParam(defaultValue = "10") int size) {
    //
    //     Map<String, Object> result = new HashMap<>();
    //
    //     try {
    //         log.info("收到分页查询请求：事件ID[{}], 页码: {}, 每页: {}", eventId, page, size);
    //
    //         Page<NewsArticleNode> articlesPage = hotEventService.getArticlesByEventId(eventId, page, size);
    //
    //         result.put("success", true);
    //         result.put("eventId", eventId);
    //         result.put("articles", articlesPage.getContent());
    //         result.put("totalCount", articlesPage.getTotalElements());
    //         result.put("totalPages", articlesPage.getTotalPages());
    //         result.put("currentPage", page);
    //         result.put("pageSize", size);
    //         result.put("message", "查询成功");
    //
    //         return ResponseEntity.ok(result);
    //
    //     } catch (Exception e) {
    //         log.error("分页查询事件ID[{}]的关联文章失败", eventId, e);
    //
    //         result.put("success", false);
    //         result.put("error", "查询失败: " + e.getMessage());
    //         result.put("eventId", eventId);
    //
    //         return ResponseEntity.status(500).body(result);
    //     }
    // }

    // /**
    //  * 获取热点事件详情及关联文章
    //  *
    //  * @param eventName 热点事件名称
    //  * @return 事件信息和文章列表
    //  */
    // @GetMapping("/detail/{eventName}")
    // @Operation(
    //         summary = "获取事件详情及文章",
    //         description = "获取热点事件的详细信息及其关联的所有新闻文章"
    // )
    // public ResponseEntity<Map<String, Object>> getEventDetail(
    //         @Parameter(description = "热点事件名称", example = "俄乌冲突")
    //         @PathVariable String eventName) {
    //
    //     Map<String, Object> result = new HashMap<>();
    //
    //     try {
    //         log.info("收到事件详情查询请求：{}", eventName);
    //
    //         Map<String, Object> eventData = hotEventService.getEventWithArticles(eventName);
    //
    //         if (eventData == null) {
    //             result.put("success", false);
    //             result.put("error", "热点事件不存在: " + eventName);
    //             result.put("eventName", eventName);
    //             return ResponseEntity.status(404).body(result);
    //         }
    //
    //         result.put("success", true);
    //         result.putAll(eventData);
    //         result.put("message", "查询成功");
    //
    //         return ResponseEntity.ok(result);
    //
    //     } catch (Exception e) {
    //         log.error("查询事件[{}]详情失败", eventName, e);
    //
    //         result.put("success", false);
    //         result.put("error", "查询失败: " + e.getMessage());
    //         result.put("eventName", eventName);
    //
    //         return ResponseEntity.status(500).body(result);
    //     }
    // }

    // /**
    //  * 获取热点事件详情及关联文章（分页版本）
    //  *
    //  * @param eventName 热点事件名称
    //  * @param page      页码
    //  * @param size      每页数量
    //  * @return 事件信息和分页文章列表
    //  */
    // @GetMapping("/detail-paged/{eventName}")
    // @Operation(
    //         summary = "获取事件详情及分页文章",
    //         description = "获取热点事件的详细信息及其关联的分页新闻文章，适合大数据量场景"
    // )
    // public ResponseEntity<Map<String, Object>> getEventDetailPaged(
    //         @Parameter(description = "热点事件名称", example = "俄乌冲突")
    //         @PathVariable String eventName,
    //         @Parameter(description = "页码（从0开始）", example = "0")
    //         @RequestParam(defaultValue = "0") int page,
    //         @Parameter(description = "每页数量", example = "10")
    //         @RequestParam(defaultValue = "10") int size) {
    //
    //     Map<String, Object> result = new HashMap<>();
    //
    //     try {
    //         log.info("收到事件详情分页查询请求：{}, 页码: {}, 每页: {}", eventName, page, size);
    //
    //         Map<String, Object> eventData = hotEventService.getEventWithArticlesPaged(eventName, page, size);
    //
    //         if (eventData == null) {
    //             result.put("success", false);
    //             result.put("error", "热点事件不存在: " + eventName);
    //             result.put("eventName", eventName);
    //             return ResponseEntity.status(404).body(result);
    //         }
    //
    //         result.put("success", true);
    //         result.putAll(eventData);
    //         result.put("message", "查询成功");
    //
    //         return ResponseEntity.ok(result);
    //
    //     } catch (Exception e) {
    //         log.error("分页查询事件[{}]详情失败", eventName, e);
    //
    //         result.put("success", false);
    //         result.put("error", "查询失败: " + e.getMessage());
    //         result.put("eventName", eventName);
    //
    //         return ResponseEntity.status(500).body(result);
    //     }
    // }



}
