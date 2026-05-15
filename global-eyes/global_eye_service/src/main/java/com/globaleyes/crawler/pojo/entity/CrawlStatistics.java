package com.globaleyes.crawler.pojo.entity;

import com.globaleyes.crawler.constant.ProcessingStatus;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 爬取统计实体类
 * 记录每次爬取任务的统计信息
 *
 * @author RSS News Crawler Team
 * @version 1.0.0
 */
@Data
@Entity
@Table(name = "crawl_statistics", indexes = {
    @Index(name = "idx_batch_id", columnList = "batch_id"),
    @Index(name = "idx_start_time", columnList = "start_time"),
    @Index(name = "idx_status", columnList = "status")
})
public class CrawlStatistics {

    /**
     * 主键ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 批次ID（UUID）
     */
    @Column(name = "batch_id", nullable = false, length = 64, unique = true)
    private String batchId;

    /**
     * 爬取类型：SCHEDULED（定时任务）/ MANUAL（手动触发）
     */
    @Column(name = "crawl_type", nullable = false, length = 20)
    private String crawlType;

    /**
     * 开始时间
     */
    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    @Column(name = "end_time")
    private LocalDateTime endTime;

    /**
     * 耗时（毫秒）
     */
    @Column(name = "duration_ms")
    private Long durationMs;

    /**
     * 总RSS源数
     */
    @Column(name = "total_sources")
    private Integer totalSources = 0;

    /**
     * 成功源数
     */
    @Column(name = "success_sources")
    private Integer successSources = 0;

    /**
     * 失败源数
     */
    @Column(name = "failed_sources")
    private Integer failedSources = 0;

    /**
     * 总文章数
     */
    @Column(name = "total_articles")
    private Integer totalArticles = 0;

    /**
     * 新增文章数
     */
    @Column(name = "new_articles")
    private Integer newArticles = 0;

    /**
     * 跳过文章数（时间过滤）
     */
    @Column(name = "skipped_articles")
    private Integer skippedArticles = 0;

    /**
     * 重复文章数
     */
    @Column(name = "duplicate_articles")
    private Integer duplicateArticles = 0;

    /**
     * 状态：RUNNING / SUCCESS / FAILED
     */
    @Column(name = "status", length = 20)
    private String status = ProcessingStatus.RUNNING;

    /**
     * 创建时间
     */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /**
     * 持久化前自动设置创建时间
     */
    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
