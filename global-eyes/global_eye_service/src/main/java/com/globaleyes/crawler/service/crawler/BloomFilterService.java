package com.globaleyes.crawler.service.crawler;

import com.globaleyes.crawler.config.BloomFilterConfig;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/**
 * 布隆过滤器服务
 * 实现URL和内容哈希的双重去重机制
 * 使用本地布隆过滤器 + Redis布隆过滤器双层架构
 *
 * @author RSS News Crawler Team
 * @version 1.0.0
 */
@Slf4j
@Service
public class BloomFilterService {

    private final BloomFilterConfig config;
    private final RedissonClient redissonClient;

    /**
     * 本地URL布隆过滤器
     */
    private BloomFilter<String> localUrlFilter;

    /**
     * 本地内容哈希布隆过滤器
     */
    private BloomFilter<String> localContentFilter;

    /**
     * Redis URL布隆过滤器
     */
    private RBloomFilter<String> redisUrlFilter;

    /**
     * Redis内容哈希布隆过滤器
     */
    private RBloomFilter<String> redisContentFilter;

    /**
     * 构造函数注入配置和RedissonClient
     *
     * @param config 布隆过滤器配置
     * @param redissonClient Redisson客户端
     */
    @Autowired
    public BloomFilterService(BloomFilterConfig config, RedissonClient redissonClient) {
        this.config = config;
        this.redissonClient = redissonClient;
    }

    /**
     * 初始化布隆过滤器
     * 在Bean属性注入完成后执行
     */
    @PostConstruct
    public void init() {
        initLocalFilters();
        initRedisFilters();
        log.info("BloomFilter initialized: expectedInsertions={}, falseProbability={}",
                config.getExpectedInsertions(), config.getFalseProbability());
    }

    /**
     * 初始化本地布隆过滤器
     */
    private void initLocalFilters() {
        this.localUrlFilter = BloomFilter.create(
                Funnels.stringFunnel(StandardCharsets.UTF_8),
                config.getExpectedInsertions(),
                config.getFalseProbability()
        );

        this.localContentFilter = BloomFilter.create(
                Funnels.stringFunnel(StandardCharsets.UTF_8),
                config.getExpectedInsertions(),
                config.getFalseProbability()
        );
    }

    /**
     * 初始化Redis布隆过滤器
     */
    private void initRedisFilters() {
        try {
            this.redisUrlFilter = redissonClient.getBloomFilter(config.getRedisKey());
            if (!this.redisUrlFilter.isExists()) {
                this.redisUrlFilter.tryInit(config.getExpectedInsertions(), config.getFalseProbability());
            }

            this.redisContentFilter = redissonClient.getBloomFilter(config.getContentHashKey());
            if (!this.redisContentFilter.isExists()) {
                this.redisContentFilter.tryInit(config.getExpectedInsertions(), config.getFalseProbability());
            }
        } catch (Exception e) {
            log.warn("Redis bloom filter init failed, will use local filter only: {}", e.getMessage());
        }
    }

    /**
     * 检查URL是否可能已存在
     * 先检查本地过滤器,再检查Redis过滤器
     *
     * @param url 待检查的URL
     * @return true-可能已存在(需要跳过), false-确定不存在(需要处理)
     */
    public boolean mightContainUrl(String url) {
        if (url == null || url.isEmpty()) {
            return true;
        }

        if (localUrlFilter.mightContain(url)) {
            return true;
        }

        if (redisUrlFilter != null && redisUrlFilter.contains(url)) {
            return true;
        }

        return false;
    }

    /**
     * 检查内容哈希是否可能已存在
     *
     * @param contentHash 内容哈希值
     * @return true-可能已存在, false-确定不存在
     */
    public boolean mightContainContent(String contentHash) {
        if (contentHash == null || contentHash.isEmpty()) {
            return true;
        }

        if (localContentFilter.mightContain(contentHash)) {
            return true;
        }

        if (redisContentFilter != null && redisContentFilter.contains(contentHash)) {
            return true;
        }

        return false;
    }

    /**
     * 添加URL到布隆过滤器
     *
     * @param url 待添加的URL
     */
    public void addUrl(String url) {
        if (url == null || url.isEmpty()) {
            return;
        }

        localUrlFilter.put(url);

        if (redisUrlFilter != null) {
            try {
                redisUrlFilter.add(url);
            } catch (Exception e) {
                log.debug("Failed to add URL to Redis filter: {}", e.getMessage());
            }
        }
    }

    /**
     * 添加内容哈希到布隆过滤器
     *
     * @param contentHash 内容哈希值
     */
    public void addContentHash(String contentHash) {
        if (contentHash == null || contentHash.isEmpty()) {
            return;
        }

        localContentFilter.put(contentHash);

        if (redisContentFilter != null) {
            try {
                redisContentFilter.add(contentHash);
            } catch (Exception e) {
                log.debug("Failed to add content hash to Redis filter: {}", e.getMessage());
            }
        }
    }

    /**
     * 检查并添加URL(原子操作)
     * 如果URL不存在则添加并返回true,否则返回false
     *
     * @param url 待检查和添加的URL
     * @return true-新URL(已添加), false-已存在(未添加)
     */
    public boolean checkAndAddUrl(String url) {
        if (mightContainUrl(url)) {
            return false;
        }
        addUrl(url);
        return true;
    }

    /**
     * 检查并添加内容哈希(原子操作)
     *
     * @param contentHash 待检查和添加的内容哈希
     * @return true-新内容(已添加), false-已存在(未添加)
     */
    public boolean checkAndAddContentHash(String contentHash) {
        if (mightContainContent(contentHash)) {
            return false;
        }
        addContentHash(contentHash);
        return true;
    }

    /**
     * 双重去重检查
     * 先检查URL,再检查内容哈希
     *
     * @param url URL
     * @param contentHash 内容哈希
     * @return true-需要处理(新内容), false-需要跳过(已存在)
     */
    public boolean checkAndAdd(String url, String contentHash) {
        if (!checkAndAddUrl(url)) {
            return false;
        }
        return checkAndAddContentHash(contentHash);
    }

    /**
     * 重置布隆过滤器
     */
    public void reset() {
        log.warn("Resetting BloomFilter - all stored data will be deleted");

        initLocalFilters();

        if (redisUrlFilter != null) {
            try {
                redisUrlFilter.delete();
                redisUrlFilter.tryInit(config.getExpectedInsertions(), config.getFalseProbability());
            } catch (Exception e) {
                log.warn("Failed to reset Redis URL filter: {}", e.getMessage());
            }
        }

        if (redisContentFilter != null) {
            try {
                redisContentFilter.delete();
                redisContentFilter.tryInit(config.getExpectedInsertions(), config.getFalseProbability());
            } catch (Exception e) {
                log.warn("Failed to reset Redis content filter: {}", e.getMessage());
            }
        }
    }
}
