package com.globaleyes.crawler.service.crawler;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RSS源采集时间服务
 * 使用Redis+内存双层缓存管理RSS源的最后采集时间
 *
 * @author RSS News Crawler Team
 * @version 1.0.0
 */
@Slf4j
@Service
public class RssFetchTimeService {

    private static final String REDIS_KEY_PREFIX = "rss:fetch:";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final StringRedisTemplate redisTemplate;

    /**
     * 内存缓存：存储RSS源的最后采集时间
     */
    private final Map<Long, LocalDateTime> memoryCache;

    /**
     * 构造函数注入RedisTemplate
     *
     * @param redisTemplate Redis字符串模板
     */
    public RssFetchTimeService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.memoryCache = new ConcurrentHashMap<>();
    }

    /**
     * 初始化：从Redis加载所有已存在的采集时间到内存
     */
    @PostConstruct
    public void init() {
        log.info("RssFetchTimeService initialized");
    }

    /**
     * 获取RSS源的最后采集时间
     * 先查内存缓存，再查Redis
     *
     * @param sourceId RSS源ID
     * @return 最后采集时间，如果不存在返回null
     */
    public LocalDateTime getLastFetchTime(Long sourceId) {
        LocalDateTime memoryTime = memoryCache.get(sourceId);
        if (memoryTime != null) {
            return memoryTime;
        }

        String redisKey = getRedisKey(sourceId);
        String timeStr = redisTemplate.opsForValue().get(redisKey);
        if (timeStr != null && !timeStr.isEmpty()) {
            try {
                LocalDateTime redisTime = LocalDateTime.parse(timeStr, FORMATTER);
                memoryCache.put(sourceId, redisTime);
                return redisTime;
            } catch (Exception e) {
                log.warn("Failed to parse fetch time from Redis: {}", timeStr);
            }
        }

        return null;
    }

    /**
     * 判断是否为首次采集
     *
     * @param sourceId RSS源ID
     * @return true-首次采集, false-非首次采集
     */
    public boolean isFirstFetch(Long sourceId) {
        return getLastFetchTime(sourceId) == null;
    }

    /**
     * 获取首次采集的截止时间（当前时间减去一个月）
     *
     * @return 一个月前的时间
     */
    public LocalDateTime getFirstFetchCutoffTime() {
        return LocalDateTime.now().minusMonths(1);
    }

    /**
     * 更新RSS源的最后采集时间
     * 同时更新内存缓存和Redis
     *
     * @param sourceId RSS源ID
     * @param fetchTime 采集时间
     */
    public void updateFetchTime(Long sourceId, LocalDateTime fetchTime) {
        memoryCache.put(sourceId, fetchTime);

        String redisKey = getRedisKey(sourceId);
        String timeStr = fetchTime.format(FORMATTER);
        redisTemplate.opsForValue().set(redisKey, timeStr);

        log.debug("Updated fetch time for source {}: {}", sourceId, timeStr);
    }

    /**
     * 更新RSS源的最后采集时间为当前时间
     *
     * @param sourceId RSS源ID
     */
    public void updateFetchTimeToNow(Long sourceId) {
        updateFetchTime(sourceId, LocalDateTime.now());
    }

    /**
     * 判断新闻是否应该被采集
     * 首次采集：只采集最近一个月的新闻
     * 后续采集：只采集上次采集时间之后的新闻
     *
     * @param sourceId RSS源ID
     * @param publishedAt 新闻发布时间
     * @return true-应该采集, false-应该跳过
     */
    public boolean shouldFetch(Long sourceId, LocalDateTime publishedAt) {
        if (publishedAt == null) {
            return true;
        }

        LocalDateTime lastFetchTime = getLastFetchTime(sourceId);

        if (lastFetchTime == null) {
            LocalDateTime cutoffTime = getFirstFetchCutoffTime();
            return publishedAt.isAfter(cutoffTime);
        } else {
            return publishedAt.isAfter(lastFetchTime);
        }
    }

    /**
     * 清除指定RSS源的采集时间缓存
     *
     * @param sourceId RSS源ID
     */
    public void clearFetchTime(Long sourceId) {
        memoryCache.remove(sourceId);
        String redisKey = getRedisKey(sourceId);
        redisTemplate.delete(redisKey);
        log.info("Cleared fetch time for source {}", sourceId);
    }

    /**
     * 清除所有RSS源的采集时间缓存
     */
    public void clearAllFetchTime() {
        memoryCache.clear();
        var keys = redisTemplate.keys(REDIS_KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        log.info("Cleared all fetch time cache");
    }

    /**
     * 生成Redis Key
     *
     * @param sourceId RSS源ID
     * @return Redis Key
     */
    private String getRedisKey(Long sourceId) {
        return REDIS_KEY_PREFIX + sourceId;
    }
}
