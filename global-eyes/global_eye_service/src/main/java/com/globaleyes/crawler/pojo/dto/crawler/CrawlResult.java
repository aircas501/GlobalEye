package com.globaleyes.crawler.pojo.dto.crawler;

import lombok.Data;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 爬取结果DTO
 * 用于统计爬取过程中的各项数据
 * 使用AtomicInteger保证并发安全
 *
 * @author RSS News Crawler Team
 * @version 1.0.0
 */
@Data
public class CrawlResult {

    private final AtomicInteger totalSources = new AtomicInteger(0);
    private final AtomicInteger successSources = new AtomicInteger(0);
    private final AtomicInteger failedSources = new AtomicInteger(0);
    private final AtomicInteger totalArticles = new AtomicInteger(0);
    private final AtomicInteger newArticles = new AtomicInteger(0);
    private final AtomicInteger skippedArticles = new AtomicInteger(0);
    private final AtomicInteger duplicateArticles = new AtomicInteger(0);

    public void incrementTotalSources() {
        totalSources.incrementAndGet();
    }

    public void incrementSuccessSources() {
        successSources.incrementAndGet();
    }

    public void incrementFailedSources() {
        failedSources.incrementAndGet();
    }

    public void addTotalArticles(int count) {
        totalArticles.addAndGet(count);
    }

    public void addNewArticles(int count) {
        newArticles.addAndGet(count);
    }

    public void addSkippedArticles(int count) {
        skippedArticles.addAndGet(count);
    }

    public void addDuplicateArticles(int count) {
        duplicateArticles.addAndGet(count);
    }

    public void merge(CrawlResult other) {
        if (other != null) {
            totalSources.addAndGet(other.totalSources.get());
            successSources.addAndGet(other.successSources.get());
            failedSources.addAndGet(other.failedSources.get());
            totalArticles.addAndGet(other.totalArticles.get());
            newArticles.addAndGet(other.newArticles.get());
            skippedArticles.addAndGet(other.skippedArticles.get());
            duplicateArticles.addAndGet(other.duplicateArticles.get());
        }
    }
}
