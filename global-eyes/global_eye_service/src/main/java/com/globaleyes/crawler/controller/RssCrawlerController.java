package com.globaleyes.crawler.controller;

import com.globaleyes.crawler.pojo.dto.crawler.CrawlResult;
import com.globaleyes.crawler.pojo.entity.NewsArticle;
import com.globaleyes.crawler.pojo.entity.RssSource;
import com.globaleyes.crawler.repository.crawl.NewsArticleRepository;
import com.globaleyes.crawler.repository.crawl.RssSourceRepository;
import com.globaleyes.crawler.service.crawler.CrawlStatisticsService;
import com.globaleyes.crawler.service.crawler.RssCrawlerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RSS爬虫控制器
 * 提供RSS源管理和新闻文章查询的REST API
 *
 * @author RSS News Crawler Team
 * @version 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/rss")
@Tag(name = "RSS爬虫管理", description = "RSS源管理和新闻文章查询接口")
public class RssCrawlerController {

    private final RssCrawlerService rssCrawlerService;
    private final RssSourceRepository rssSourceRepository;
    private final NewsArticleRepository newsArticleRepository;
    private final CrawlStatisticsService crawlStatisticsService;

    /**
     * 构造函数注入依赖
     *
     * @param rssCrawlerService RSS爬虫服务
     * @param rssSourceRepository RSS源Repository
     * @param newsArticleRepository 新闻文章Repository
     * @param crawlStatisticsService 爬取统计服务
     */
    public RssCrawlerController(RssCrawlerService rssCrawlerService,
                                RssSourceRepository rssSourceRepository,
                                NewsArticleRepository newsArticleRepository,
                                CrawlStatisticsService crawlStatisticsService) {
        this.rssCrawlerService = rssCrawlerService;
        this.rssSourceRepository = rssSourceRepository;
        this.newsArticleRepository = newsArticleRepository;
        this.crawlStatisticsService = crawlStatisticsService;
    }

    /**
     * 手动触发RSS采集任务
     *
     * @return 操作结果
     */
    @PostMapping("/crawl")
    @Operation(summary = "手动触发采集", description = "手动触发RSS新闻采集任务")
    public ResponseEntity<Map<String, Object>> triggerCrawl() {
        log.info("Manual crawl triggered");

        new Thread(() -> {
            String batchId = null;
            try {
                batchId = crawlStatisticsService.startCrawl("MANUAL");
                CrawlResult result = rssCrawlerService.crawlAllSources();
                crawlStatisticsService.endCrawl(batchId, result);
                log.info("Manual crawl completed: newArticles={}", result.getNewArticles().get());
            } catch (Exception e) {
                log.error("Manual crawl failed: {}", e.getMessage());
                if (batchId != null) {
                    crawlStatisticsService.markFailed(batchId);
                }
            }
        }).start();

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "Crawl task started");
        return ResponseEntity.ok(result);
    }

    /**
     * 采集指定的RSS源
     *
     * @param sourceId RSS源ID
     * @return 操作结果
     */
    @PostMapping("/crawl/{sourceId}")
    @Operation(summary = "采集指定源", description = "采集指定RSS源的新闻")
    public ResponseEntity<Map<String, Object>> crawlSource(
            @Parameter(description = "RSS源ID") @PathVariable Long sourceId) {
        log.info("Crawling source: {}", sourceId);

        rssCrawlerService.crawlSourceAsync(sourceId);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "Crawl task started for source: " + sourceId);
        return ResponseEntity.ok(result);
    }

    /**
     * 获取所有启用的RSS源
     *
     * @return RSS源列表
     */
    @GetMapping("/sources")
    @Operation(summary = "获取RSS源列表", description = "获取所有启用的RSS源")
    public ResponseEntity<List<RssSource>> getAllSources() {
        List<RssSource> sources = rssSourceRepository.findByActiveTrue();
        return ResponseEntity.ok(sources);
    }

    /**
     * 分页获取RSS源
     *
     * @param page 页码
     * @param size 每页大小
     * @return 分页结果
     */
    @GetMapping("/sources/page")
    @Operation(summary = "分页获取RSS源", description = "分页获取RSS源列表")
    public ResponseEntity<Page<RssSource>> getSourcesPage(
            @Parameter(description = "页码") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<RssSource> sources = rssSourceRepository.findByActiveTrue(pageable);
        return ResponseEntity.ok(sources);
    }

    /**
     * 添加新的RSS源
     *
     * @param source RSS源信息
     * @return 添加结果
     */
    @PostMapping("/sources")
    @Operation(summary = "添加RSS源", description = "添加新的RSS源")
    public ResponseEntity<Map<String, Object>> addSource(@RequestBody RssSource source) {
        Map<String, Object> result = new HashMap<>();

        try {
            if (!rssCrawlerService.validateRssSource(source.getUrl())) {
                result.put("success", false);
                result.put("message", "RSS source is not valid or not accessible");
                return ResponseEntity.badRequest().body(result);
            }

            RssSource saved = rssCrawlerService.addRssSource(source);
            result.put("success", true);
            result.put("data", saved);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "Failed to add RSS source: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * 禁用RSS源
     *
     * @param sourceId RSS源ID
     * @return 操作结果
     */
    @DeleteMapping("/sources/{sourceId}")
    @Operation(summary = "禁用RSS源", description = "禁用指定的RSS源")
    public ResponseEntity<Map<String, Object>> deactivateSource(
            @Parameter(description = "RSS源ID") @PathVariable Long sourceId) {
        Map<String, Object> result = new HashMap<>();

        try {
            rssSourceRepository.deactivateSource(sourceId, java.time.LocalDateTime.now());
            result.put("success", true);
            result.put("message", "Source deactivated");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "Failed to deactivate source: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * 验证RSS源
     *
     * @param url RSS URL
     * @return 验证结果
     */
    @GetMapping("/validate")
    @Operation(summary = "验证RSS源", description = "验证RSS源是否可用")
    public ResponseEntity<Map<String, Object>> validateSource(
            @Parameter(description = "RSS URL") @RequestParam String url) {
        Map<String, Object> result = new HashMap<>();
        boolean valid = rssCrawlerService.validateRssSource(url);
        result.put("valid", valid);
        result.put("url", url);
        return ResponseEntity.ok(result);
    }

    /**
     * 分页获取新闻文章
     *
     * @param page 页码
     * @param size 每页大小
     * @return 分页结果
     */
    @GetMapping("/articles")
    @Operation(summary = "分页获取文章", description = "分页获取新闻文章列表")
    public ResponseEntity<Page<NewsArticle>> getArticlesPage(
            @Parameter(description = "页码") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("publishedAt").descending());
        Page<NewsArticle> articles = newsArticleRepository.findAll(pageable);
        return ResponseEntity.ok(articles);
    }

    /**
     * 根据来源获取文章
     *
     * @param source 来源
     * @param page 页码
     * @param size 每页大小
     * @return 分页结果
     */
    @GetMapping("/articles/source/{source}")
    @Operation(summary = "按来源获取文章", description = "根据新闻来源获取文章")
    public ResponseEntity<Page<NewsArticle>> getArticlesBySource(
            @Parameter(description = "来源") @PathVariable String source,
            @Parameter(description = "页码") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("publishedAt").descending());
        Page<NewsArticle> articles = newsArticleRepository.findBySource(source, pageable);
        return ResponseEntity.ok(articles);
    }

    /**
     * 搜索文章
     *
     * @param keyword 关键词
     * @param page 页码
     * @param size 每页大小
     * @return 分页结果
     */
    @GetMapping("/articles/search")
    @Operation(summary = "搜索文章", description = "根据关键词搜索文章")
    public ResponseEntity<Page<NewsArticle>> searchArticles(
            @Parameter(description = "关键词") @RequestParam String keyword,
            @Parameter(description = "页码") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("publishedAt").descending());
        Page<NewsArticle> articles = newsArticleRepository.searchByKeyword(keyword, pageable);
        return ResponseEntity.ok(articles);
    }

    /**
     * 获取统计信息
     *
     * @return 统计信息
     */
    @GetMapping("/stats")
    @Operation(summary = "获取统计信息", description = "获取RSS源和文章的统计信息")
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> stats = new HashMap<>();

        stats.put("totalSources", rssSourceRepository.countByActiveTrue());
        stats.put("totalArticles", newsArticleRepository.count());
        stats.put("sourcesByCountry", rssSourceRepository.countByCountryGroup());
        stats.put("articlesBySource", newsArticleRepository.countBySourceGroup());

        return ResponseEntity.ok(stats);
    }
}
