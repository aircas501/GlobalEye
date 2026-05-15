package com.globaleyes.crawler.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * RSS爬虫配置类
 * 读取application.yml中的rss.crawler配置
 *
 * @author RSS News Crawler Team
 * @version 1.0.0
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "rss.crawler")
public class RssCrawlerConfig {

    /**
     * 是否启用RSS爬虫
     */
    private boolean enabled = true;

    /**
     * 定时任务cron表达式
     */
    private String cron = "0 */30 * * * ?";

    /**
     * 线程池大小
     */
    private int threadPoolSize = 10;

    /**
     * 连接超时时间(毫秒)
     */
    private int connectionTimeout = 30000;

    /**
     * 读取超时时间(毫秒)
     */
    private int readTimeout = 60000;

    /**
     * 最大连续失败次数,超过则禁用源
     */
    private int maxFailCount = 3;

    /**
     * User-Agent
     */
    private String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";
}
