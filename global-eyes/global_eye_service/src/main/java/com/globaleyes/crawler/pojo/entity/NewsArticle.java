package com.globaleyes.crawler.pojo.entity;

import com.globaleyes.crawler.constant.ProcessingStatus;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 新闻文章实体类
 * 用于存储RSS采集到的新闻文章原始数据
 *
 * @author RSS News Crawler Team
 * @version 2.0.0
 */
@Data
@Entity
@Table(name = "news_article", indexes = {
    @Index(name = "idx_article_fingerprint", columnList = "fingerprint", unique = true),
    @Index(name = "idx_article_source", columnList = "source"),
    @Index(name = "idx_article_published", columnList = "publishedAt"),
    @Index(name = "idx_article_status", columnList = "processingStatus"),
    @Index(name = "idx_article_rss_source", columnList = "rssSourceId")
})
public class NewsArticle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 1000)
    private String title;

    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String originalContent;

    @Column(nullable = false, length = 200)
    private String source;

    private Long rssSourceId;

    private LocalDateTime publishedAt;

    @Column(nullable = false)
    private LocalDateTime fetchedAt;

    @Column(length = 1)
    private String authorityLevel;

    @Column(length = 64, unique = true)
    private String fingerprint;

    @Column(columnDefinition = "LONGTEXT")
    private String extractedJson;

    @Column(length = 20)
    private String processingStatus;

    @Column(length = 256)
    private String newsUrl;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.fetchedAt == null) {
            this.fetchedAt = LocalDateTime.now();
        }
        if (this.processingStatus == null) {
            this.processingStatus = ProcessingStatus.PENDING;
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
