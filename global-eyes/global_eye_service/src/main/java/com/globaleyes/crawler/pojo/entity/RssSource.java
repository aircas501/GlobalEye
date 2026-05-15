package com.globaleyes.crawler.pojo.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * RSS源实体类
 * 用于存储RSS订阅源信息
 *
 * @author RSS News Crawler Team
 * @version 1.0.0
 */
@Data
@Entity
@Table(name = "rss_source", indexes = {
    @Index(name = "idx_rss_url", columnList = "url", unique = true),
    @Index(name = "idx_rss_active", columnList = "active"),
    @Index(name = "idx_rss_country", columnList = "country")
})
public class RssSource {

    /**
     * 主键ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * RSS源名称
     */
    @Column(nullable = false, length = 200)
    private String name;

    /**
     * RSS订阅URL
     */
    @Column(nullable = false, unique = true, length = 1000)
    private String url;

    /**
     * 所属国家
     */
    @Column(length = 100)
    private String country;

    /**
     * 所属地区
     */
    @Column(length = 100)
    private String region;

    /**
     * 语言代码(如en, zh, ja等)
     */
    @Column(length = 20)
    private String language;

    /**
     * 新闻来源类型(news/sports/tech/finance等)
     */
    @Column(length = 50)
    private String category;

    /**
     * 权威等级(A/B/C/D/E)
     */
    @Column(length = 1)
    private String authorityLevel;

    /**
     * 是否启用
     */
    @Column(nullable = false)
    private Boolean active = true;

    /**
     * 连续失败次数
     */
    @Column(nullable = false)
    private Integer failCount = 0;

    /**
     * 最后一次采集时间
     */
    private LocalDateTime lastFetchedAt;

    /**
     * 最后一次失败时间
     */
    private LocalDateTime lastFailAt;

    /**
     * 最后一次失败原因
     */
    @Column(length = 500)
    private String lastFailReason;

    /**
     * 总采集文章数
     */
    @Column(nullable = false)
    private Long totalArticles = 0L;

    /**
     * 创建时间
     */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 描述信息
     */
    @Column(length = 500)
    private String description;

    /**
     * 持久化前自动设置创建时间
     */
    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 更新前自动设置更新时间
     */
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
