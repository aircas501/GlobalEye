package com.globaleyes.crawler.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 布隆过滤器配置类
 * 读取application.yml中的bloomfilter配置
 *
 * @author RSS News Crawler Team
 * @version 1.0.0
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "bloomfilter")
public class BloomFilterConfig {

    /**
     * 预期插入数量
     */
    private long expectedInsertions = 10000000L;

    /**
     * 误判率
     */
    private double falseProbability = 0.0001;

    /**
     * Redis中URL布隆过滤器的key
     */
    private String redisKey = "news:bloom:urls";

    /**
     * Redis中内容哈希布隆过滤器的key
     */
    private String contentHashKey = "news:bloom:content";
}
