package com.globaleyes.crawler.repository.crawl;

import com.globaleyes.crawler.pojo.entity.CrawlStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 爬取统计Repository
 *
 * @author RSS News Crawler Team
 * @version 1.0.0
 */
@Repository
public interface CrawlStatisticsRepository extends JpaRepository<CrawlStatistics, Long> {

    /**
     * 根据批次ID查询统计记录
     *
     * @param batchId 批次ID
     * @return 统计记录
     */
    Optional<CrawlStatistics> findByBatchId(String batchId);

    /**
     * 查询指定时间范围内的统计记录
     *
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 统计记录列表
     */
    List<CrawlStatistics> findByStartTimeBetween(LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 统计指定时间范围内的总新闻数
     *
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 总新闻数
     */
    @Query("SELECT COALESCE(SUM(cs.newArticles), 0) FROM CrawlStatistics cs WHERE cs.startTime BETWEEN :startTime AND :endTime")
    long sumNewArticlesByStartTimeBetween(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    /**
     * 删除指定时间之前的统计记录
     *
     * @param time 时间阈值
     */
    void deleteByCreatedAtBefore(LocalDateTime time);
}
