package com.globaleyes.crawler.service.crawler;

import com.globaleyes.crawler.constant.ProcessingStatus;
import com.globaleyes.crawler.pojo.dto.crawler.CrawlResult;
import com.globaleyes.crawler.pojo.entity.CrawlStatistics;
import com.globaleyes.crawler.repository.crawl.CrawlStatisticsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 爬取统计服务
 * 负责记录和查询爬取统计数据
 *
 * @author RSS News Crawler Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CrawlStatisticsService {

    private final CrawlStatisticsRepository statisticsRepository;

    /**
     * 开始爬取任务
     * 创建统计记录，状态为RUNNING
     *
     * @param crawlType 爬取类型：SCHEDULED / MANUAL
     * @return 批次ID
     */
    @Transactional
    public String startCrawl(String crawlType) {
        String batchId = UUID.randomUUID().toString().replace("-", "");
        
        CrawlStatistics statistics = new CrawlStatistics();
        statistics.setBatchId(batchId);
        statistics.setCrawlType(crawlType);
        statistics.setStartTime(LocalDateTime.now());
        statistics.setStatus(ProcessingStatus.RUNNING);
        statistics.setTotalSources(0);
        statistics.setSuccessSources(0);
        statistics.setFailedSources(0);
        statistics.setTotalArticles(0);
        statistics.setNewArticles(0);
        statistics.setSkippedArticles(0);
        statistics.setDuplicateArticles(0);
        
        statisticsRepository.save(statistics);
        
        log.info("Crawl statistics started: batchId={}, type={}", batchId, crawlType);
        return batchId;
    }

    /**
     * 结束爬取任务
     * 更新统计记录，设置结束时间和统计结果
     *
     * @param batchId 批次ID
     * @param result  爬取结果
     */
    @Transactional
    public void endCrawl(String batchId, CrawlResult result) {
        endCrawl(batchId, result, ProcessingStatus.SUCCESS);
    }

    /**
     * 结束爬取任务
     * 更新统计记录，设置结束时间和统计结果
     *
     * @param batchId 批次ID
     * @param result  爬取结果
     * @param status  状态：SUCCESS / FAILED
     */
    @Transactional
    public void endCrawl(String batchId, CrawlResult result, String status) {
        Optional<CrawlStatistics> optional = statisticsRepository.findByBatchId(batchId);
        
        if (optional.isEmpty()) {
            log.warn("Crawl statistics not found: batchId={}", batchId);
            return;
        }
        
        CrawlStatistics statistics = optional.get();
        LocalDateTime endTime = LocalDateTime.now();
        
        statistics.setEndTime(endTime);
        statistics.setDurationMs(
            java.time.Duration.between(statistics.getStartTime(), endTime).toMillis()
        );
        statistics.setStatus(status);
        
        if (result != null) {
            statistics.setTotalSources(result.getTotalSources().get());
            statistics.setSuccessSources(result.getSuccessSources().get());
            statistics.setFailedSources(result.getFailedSources().get());
            statistics.setTotalArticles(result.getTotalArticles().get());
            statistics.setNewArticles(result.getNewArticles().get());
            statistics.setSkippedArticles(result.getSkippedArticles().get());
            statistics.setDuplicateArticles(result.getDuplicateArticles().get());
        }
        
        statisticsRepository.save(statistics);
        
        log.info("Crawl statistics ended: batchId={}, status={}, newArticles={}, duration={}ms",
                batchId, status, 
                result != null ? result.getNewArticles().get() : 0,
                statistics.getDurationMs());
    }

    /**
     * 标记爬取失败
     *
     * @param batchId 批次ID
     */
    @Transactional
    public void markFailed(String batchId) {
        endCrawl(batchId, null, ProcessingStatus.FAILED);
    }

    /**
     * 根据批次ID查询统计记录
     *
     * @param batchId 批次ID
     * @return 统计记录
     */
    public Optional<CrawlStatistics> findByBatchId(String batchId) {
        return statisticsRepository.findByBatchId(batchId);
    }

    /**
     * 查询指定时间范围内的统计记录
     *
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 统计记录列表
     */
    public List<CrawlStatistics> findByTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        return statisticsRepository.findByStartTimeBetween(startTime, endTime);
    }

    /**
     * 统计指定时间范围内的总新闻数
     *
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 总新闻数
     */
    public long sumNewArticlesByTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        return statisticsRepository.sumNewArticlesByStartTimeBetween(startTime, endTime);
    }

    /**
     * 删除指定时间之前的统计记录
     *
     * @param time 时间阈值
     */
    @Transactional
    public void deleteByCreatedAtBefore(LocalDateTime time) {
        statisticsRepository.deleteByCreatedAtBefore(time);
        log.info("Deleted crawl statistics before: {}", time);
    }
}
