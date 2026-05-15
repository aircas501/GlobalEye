package com.globaleyes.crawler.service.crawler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.globaleyes.crawler.config.RssCrawlerConfig;
import com.globaleyes.crawler.constant.CommonConstants;
import com.globaleyes.crawler.constant.ProcessingStatus;
import com.globaleyes.crawler.pojo.dto.crawler.ArticleAnalysisOutputDTO;
import com.globaleyes.crawler.pojo.dto.crawler.CrawlResult;
import com.globaleyes.crawler.pojo.entity.NewsArticle;
import com.globaleyes.crawler.pojo.entity.RssSource;
import com.globaleyes.crawler.repository.crawl.NewsArticleRepository;
import com.globaleyes.crawler.repository.crawl.RssSourceRepository;
import com.globaleyes.crawler.service.neo4j.Neo4jStorageService;
import com.globaleyes.crawler.service.storage.NewsStorageService;
import com.globaleyes.crawler.service.storage.StorageServiceFactory;
import com.globaleyes.crawler.service.translate.TranslateService;
import com.globaleyes.crawler.service.translate.TranslateServiceFactory;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * RSS爬虫服务
 * 负责RSS源的解析、新闻采集、翻译、AI分析和存储
 * 支持首次采集最近一个月新闻，后续增量采集
 *
 * @author RSS News Crawler Team
 * @version 1.0.0
 */
@Slf4j
@Service
public class RssCrawlerService {

    private final RssCrawlerConfig config;
    private final RssSourceRepository rssSourceRepository;
    private final NewsArticleRepository newsArticleRepository;
    private final BloomFilterService bloomFilterService;
    private final TranslateServiceFactory translateServiceFactory;
    private final StorageServiceFactory storageServiceFactory;
    private final RssFetchTimeService rssFetchTimeService;
    private final Executor taskExecutor;
    private final OkHttpClient httpClient;
    private final MilitaryAnalysisService militaryAnalysisService;
    private final AnalysisValidationService validationService;
    private final ObjectMapper objectMapper;
    private final Neo4jStorageService neo4jStorageService;

    public RssCrawlerService(RssCrawlerConfig config,
                             RssSourceRepository rssSourceRepository,
                             NewsArticleRepository newsArticleRepository,
                             BloomFilterService bloomFilterService,
                             TranslateServiceFactory translateServiceFactory,
                             StorageServiceFactory storageServiceFactory,
                             RssFetchTimeService rssFetchTimeService,
                             @Qualifier("applicationTaskExecutor") Executor taskExecutor,
                             MilitaryAnalysisService militaryAnalysisService,
                             AnalysisValidationService validationService,
                             ObjectMapper objectMapper,
                             Neo4jStorageService neo4jStorageService) {
        this.config = config;
        this.rssSourceRepository = rssSourceRepository;
        this.newsArticleRepository = newsArticleRepository;
        this.bloomFilterService = bloomFilterService;
        this.translateServiceFactory = translateServiceFactory;
        this.storageServiceFactory = storageServiceFactory;
        this.rssFetchTimeService = rssFetchTimeService;
        this.taskExecutor = taskExecutor;
        this.militaryAnalysisService = militaryAnalysisService;
        this.validationService = validationService;
        this.objectMapper = objectMapper;
        this.neo4jStorageService = neo4jStorageService;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(config.getConnectionTimeout(), TimeUnit.MILLISECONDS)
                .readTimeout(config.getReadTimeout(), TimeUnit.MILLISECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build();
    }

    /**
     * 执行RSS采集任务
     * 获取所有启用的RSS源并并行采集
     *
     * @return 爬取结果统计
     */
    public CrawlResult crawlAllSources() {
        log.info("Starting RSS crawl task");

        CrawlResult totalResult = new CrawlResult();

        List<RssSource> sources = rssSourceRepository.findByActiveTrue();
        log.info("Found {} active RSS sources", sources.size());

        List<CompletableFuture<SourceCrawlResult>> futures = new ArrayList<>();

        for (RssSource source : sources) {
            CompletableFuture<SourceCrawlResult> future = CompletableFuture.supplyAsync(
                    () -> crawlSource(source), taskExecutor);
            futures.add(future);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        for (CompletableFuture<SourceCrawlResult> future : futures) {
            try {
                SourceCrawlResult result = future.get();
                totalResult.addNewArticles(result.getNewArticles());
                totalResult.addSkippedArticles(result.getSkippedArticles());
                totalResult.addDuplicateArticles(result.getDuplicateArticles());
                totalResult.addTotalArticles(result.getNewArticles() + result.getSkippedArticles() + result.getDuplicateArticles());
                totalResult.incrementTotalSources();
                if (result.isSuccess()) {
                    totalResult.incrementSuccessSources();
                } else {
                    totalResult.incrementFailedSources();
                }
            } catch (Exception e) {
                log.error("Error getting crawl result: {}", e.getMessage());
                totalResult.incrementTotalSources();
                totalResult.incrementFailedSources();
            }
        }

        log.info("RSS crawl task completed: totalSources={}, successSources={}, newArticles={}",
                totalResult.getTotalSources().get(),
                totalResult.getSuccessSources().get(),
                totalResult.getNewArticles().get());

        return totalResult;
    }

    /**
     * 采集单个RSS源
     *
     * @param source RSS源
     * @return 采集结果
     */
    public SourceCrawlResult crawlSource(RssSource source) {
        boolean isFirstFetch = rssFetchTimeService.isFirstFetch(source.getId());
        log.info("Crawling RSS source: {} ({})", source.getName(), isFirstFetch ? "首次采集" : "增量采集");

        SourceCrawlResult result = new SourceCrawlResult(source.getId(), source.getName());

        try {
            String feedContent = fetchFeedContent(source.getUrl());
            if (feedContent == null || feedContent.isEmpty()) {
                handleSourceFailure(source, "Failed to fetch feed content");
                return result;
            }

            SyndFeed feed = parseFeed(feedContent);
            if (feed == null) {
                handleSourceFailure(source, "Failed to parse feed");
                return result;
            }

            FeedProcessResult processResult = processFeedEntries(source, feed, isFirstFetch);
            result.setNewArticles(processResult.newArticles);
            result.setSkippedArticles(processResult.skippedArticles);
            result.setDuplicateArticles(processResult.duplicateArticles);
            result.setSuccess(true);

            rssSourceRepository.resetFailCount(source.getId(), LocalDateTime.now(), processResult.newArticles);

            rssFetchTimeService.updateFetchTimeToNow(source.getId());

            log.info("Successfully crawled {} articles from: {}", processResult.newArticles, source.getName());

        } catch (Exception e) {
            log.error("Error crawling RSS source {}: {}", source.getName(), e.getMessage());
            handleSourceFailure(source, e.getMessage());
        }

        return result;
    }

    /**
     * 获取RSS Feed内容
     *
     * @param url RSS URL
     * @return Feed内容
     */
    private String fetchFeedContent(String url) {
        try {
            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", config.getUserAgent())
                    .header("Accept", "application/rss+xml, application/xml, text/xml")
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    return response.body().string();
                } else {
                    log.error("Failed to fetch feed: HTTP {}", response.code());
                    return null;
                }
            }
        } catch (Exception e) {
            log.error("Error fetching feed content: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 解析RSS Feed
     *
     * @param feedContent Feed内容
     * @return SyndFeed对象
     */
    private SyndFeed parseFeed(String feedContent) {
        try {
            SyndFeedInput input = new SyndFeedInput();
            return input.build(new XmlReader(new ByteArrayInputStream(
                    feedContent.getBytes(StandardCharsets.UTF_8))));
        } catch (Exception e) {
            log.error("Error parsing feed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 处理Feed中的条目
     *
     * @param source RSS源
     * @param feed Feed对象
     * @param isFirstFetch 是否首次采集
     * @return 处理结果
     */
    private FeedProcessResult processFeedEntries(RssSource source, SyndFeed feed, boolean isFirstFetch) {
        int newArticles = 0;
        int skippedArticles = 0;
        int duplicateArticles = 0;
        TranslateService translateService = translateServiceFactory.getTranslateService();
        NewsStorageService storageService = storageServiceFactory.getStorageService();

        for (SyndEntry entry : feed.getEntries()) {
            try {
                LocalDateTime publishedAt = null;
                if (entry.getPublishedDate() != null) {
                    publishedAt = convertToLocalDateTime(entry.getPublishedDate());
                }

                if (!rssFetchTimeService.shouldFetch(source.getId(), publishedAt)) {
                    skippedArticles++;
                    log.debug("Skipped article (not in time range): {}", entry.getTitle());
                    continue;
                }

                NewsArticle article = createArticleFromEntry(source, entry, translateService);
                if (article == null) {
                    continue;
                }

                if (!bloomFilterService.checkAndAddContentHash(article.getFingerprint())) {
                    duplicateArticles++;
                    log.debug("Article already exists (bloom filter): {}", article.getTitle());
                    continue;
                }

                if (!"local".equals(storageService.getStorageType())) {
                    try {
                        newsArticleRepository.save(article);
                    } catch (DataIntegrityViolationException e) {
                        duplicateArticles++;
                        log.debug("Article already exists (duplicate key): {}", article.getTitle());
                        continue;
                    }
                }

                analyzeAndSaveToNeo4j(article);
                storageService.save(article);
                newArticles++;
            } catch (Exception e) {
                log.error("Error processing entry: {}", e.getMessage());
            }
        }

        if (skippedArticles > 0) {
            log.debug("Skipped {} articles due to time filter for source: {}", skippedArticles, source.getName());
        }
        if (duplicateArticles > 0) {
            log.debug("Skipped {} duplicate articles for source: {}", duplicateArticles, source.getName());
        }

        return new FeedProcessResult(newArticles, skippedArticles, duplicateArticles);
    }

    /**
     * 分析文章并保存到Neo4j
     *
     * @param article 文章实体
     */
    private void analyzeAndSaveToNeo4j(NewsArticle article) {
        try {
            String publishDate = article.getPublishedAt() != null
                    ? article.getPublishedAt().format(DateTimeFormatter.ISO_LOCAL_DATE)
                    : CommonConstants.UNKNOWN;

            ArticleAnalysisOutputDTO analysisResult = militaryAnalysisService.analyze(
                    article.getTitle(),
                    article.getOriginalContent(),
                    article.getSource(),
                    publishDate
            );

            if (analysisResult == null) {
                log.warn("AI analysis failed for article: {}", article.getTitle());
                article.setProcessingStatus(ProcessingStatus.ANALYSIS_FAILED);
                newsArticleRepository.save(article);
                return;
            }

            var validationResult = validationService.validate(analysisResult);

            if (!validationResult.isValid()) {
                log.warn("Analysis validation failed for article: {}", article.getTitle());
                article.setProcessingStatus(ProcessingStatus.VALIDATION_FAILED);
                newsArticleRepository.save(article);
                return;
            }
            article.setExtractedJson(objectMapper.writeValueAsString(analysisResult));
            article.setProcessingStatus(ProcessingStatus.PROCESSED);
            article.setAuthorityLevel(analysisResult.getAuthorityLevel());
            newsArticleRepository.save(article);

            neo4jStorageService.saveToNeo4j(
                    article.getId(),
                    article.getTitle(),
                    article.getSource(),
                    analysisResult
            );

        } catch (Exception e) {
            log.error("Error analyzing article {}: {}", article.getTitle(), e.getMessage());
            try {
                article.setProcessingStatus(ProcessingStatus.ANALYSIS_ERROR);
                newsArticleRepository.save(article);
            } catch (Exception ex) {
                log.error("Error updating article status: {}", ex.getMessage());
            }
        }
    }

    /**
     * 从Feed条目创建文章实体
     */
    private NewsArticle createArticleFromEntry(RssSource source, SyndEntry entry,
                                               TranslateService translateService) {
        String url = entry.getLink();
        if (url == null || url.isEmpty()) {
            return null;
        }

        NewsArticle article = new NewsArticle();
        article.setSource(source.getName());
        article.setRssSourceId(source.getId());
        article.setFetchedAt(LocalDateTime.now());
        article.setNewsUrl(url);

        String originalTitle = entry.getTitle();

        if (needsTranslation(source.getLanguage())) {
            article.setTitle(translateService.translateToChinese(originalTitle, source.getLanguage()));
        } else {
            article.setTitle(originalTitle);
        }

        if (entry.getDescription() != null) {
            String originalContent = entry.getDescription().getValue();
            article.setOriginalContent(originalContent);
        }

        if (entry.getPublishedDate() != null) {
            article.setPublishedAt(convertToLocalDateTime(entry.getPublishedDate()));
        }

        article.setFingerprint(generateFingerprint(article));

        return article;
    }

    private boolean needsTranslation(String language) {
        return language != null && !language.startsWith("zh");
    }

    private LocalDateTime convertToLocalDateTime(Date date) {
        return LocalDateTime.ofInstant(date.toInstant(), ZoneId.of("Asia/Shanghai"));
    }

    private String generateFingerprint(NewsArticle article) {
        try {
            String content = article.getTitle() + article.getOriginalContent();
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(System.currentTimeMillis());
        }
    }

    private void handleSourceFailure(RssSource source, String reason) {
        log.warn("RSS source failed: {} - {}", source.getName(), reason);

        rssSourceRepository.updateFailInfo(source.getId(), LocalDateTime.now(), reason);

        if (source.getFailCount() + 1 >= config.getMaxFailCount()) {
            rssSourceRepository.deactivateSource(source.getId(), LocalDateTime.now());
            log.warn("RSS source deactivated due to consecutive failures: {}", source.getName());
        }
    }

    @Async
    public CompletableFuture<Void> crawlSourceAsync(Long sourceId) {
        rssSourceRepository.findById(sourceId).ifPresent(this::crawlSource);
        return CompletableFuture.completedFuture(null);
    }

    public RssSource addRssSource(RssSource source) {
        if (rssSourceRepository.existsByUrl(source.getUrl())) {
            throw new IllegalArgumentException("RSS source already exists: " + source.getUrl());
        }

        source.setActive(true);
        source.setFailCount(0);
        source.setTotalArticles(0L);
        source.setCreatedAt(LocalDateTime.now());
        source.setUpdatedAt(LocalDateTime.now());

        return rssSourceRepository.save(source);
    }

    public boolean validateRssSource(String url) {
        try {
            String content = fetchFeedContent(url);
            if (content == null || content.isEmpty()) {
                return false;
            }

            SyndFeed feed = parseFeed(content);
            return feed != null && feed.getEntries() != null && !feed.getEntries().isEmpty();
        } catch (Exception e) {
            log.error("RSS source validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 单个源爬取结果
     */
    @Data
    @AllArgsConstructor
    private static class SourceCrawlResult {
        private Long sourceId;
        private String sourceName;
        private int newArticles = 0;
        private int skippedArticles = 0;
        private int duplicateArticles = 0;
        private boolean success = false;

        public SourceCrawlResult(Long sourceId, String sourceName) {
            this.sourceId = sourceId;
            this.sourceName = sourceName;
        }
    }

    /**
     * Feed处理结果
     */
    @Data
    @AllArgsConstructor
    private static class FeedProcessResult {
        private int newArticles;
        private int skippedArticles;
        private int duplicateArticles;
    }
}
