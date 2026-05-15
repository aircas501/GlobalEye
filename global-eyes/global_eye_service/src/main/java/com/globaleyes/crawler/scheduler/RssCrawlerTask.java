package com.globaleyes.crawler.scheduler;

import com.globaleyes.crawler.config.RssCrawlerConfig;
import com.globaleyes.crawler.pojo.dto.crawler.CrawlResult;
import com.globaleyes.crawler.service.crawler.CrawlStatisticsService;
import com.globaleyes.crawler.service.crawler.RssCrawlerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * RSS爬虫定时任务
 * 定时执行RSS新闻采集任务
 *
 * @author RSS News Crawler Team
 * @version 1.0.0
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "rss.crawler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RssCrawlerTask {

    private final RssCrawlerService rssCrawlerService;
    private final CrawlStatisticsService crawlStatisticsService;
    private final RssCrawlerConfig config;

    /**
     * 构造函数注入依赖
     *
     * @param rssCrawlerService RSS爬虫服务
     * @param crawlStatisticsService 爬取统计服务
     * @param config RSS爬虫配置
     */
    public RssCrawlerTask(RssCrawlerService rssCrawlerService,
                          CrawlStatisticsService crawlStatisticsService,
                          RssCrawlerConfig config) {
        this.rssCrawlerService = rssCrawlerService;
        this.crawlStatisticsService = crawlStatisticsService;
        this.config = config;
    }

    /**
     * 执行RSS采集任务
     * 默认每两小时执行一次
     */
    @Scheduled(cron = "${rss.crawler.cron:0 */2 * * *}")
    public void executeCrawlTask() {
        log.info("RSS crawler scheduled task started");

        String batchId = null;
        try {
            batchId = crawlStatisticsService.startCrawl("SCHEDULED");

            CrawlResult result = rssCrawlerService.crawlAllSources();

            crawlStatisticsService.endCrawl(batchId, result);

            log.info("RSS crawler scheduled task completed: newArticles={}, successSources={}, failedSources={}",
                    result.getNewArticles().get(),
                    result.getSuccessSources().get(),
                    result.getFailedSources().get());
        } catch (Exception e) {
            log.error("RSS crawler scheduled task failed: {}", e.getMessage(), e);
            if (batchId != null) {
                crawlStatisticsService.markFailed(batchId);
            }
        }
    }
}
