package com.globaleyes.crawler.service.aircraft;

import com.globaleyes.crawler.repository.aircraft.AircraftBasicInfoRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 飞机型号两级缓存服务
 * L1: Caffeine本地缓存
 * L2: Redis缓存(Redisson)
 *
 * @author Aircraft Info Team
 * @version 1.0.0
 */
@Slf4j
@Service
public class AircraftModelCacheService {

    private static final String REDIS_KEY_PREFIX = "aircraft:model:";

    private final RedissonClient redissonClient;
    private final AircraftBasicInfoRepository repository;
    private final Cache<String, String> localCache;
    private final boolean cacheEnabled;
    private final long redisTtlSeconds;

    public AircraftModelCacheService(RedissonClient redissonClient,
                                     AircraftBasicInfoRepository repository,
                                     @Value("${aircraft.cache.enabled:true}") boolean cacheEnabled,
                                     @Value("${aircraft.cache.local.max-size:10000}") int localMaxSize,
                                     @Value("${aircraft.cache.local.expire-minutes:60}") int localExpireMinutes,
                                     @Value("${aircraft.cache.redis.ttl-seconds:86400}") long redisTtlSeconds) {
        this.redissonClient = redissonClient;
        this.repository = repository;
        this.cacheEnabled = cacheEnabled;
        this.redisTtlSeconds = redisTtlSeconds;

        this.localCache = Caffeine.newBuilder()
                .maximumSize(localMaxSize)
                .expireAfterWrite(localExpireMinutes, TimeUnit.MINUTES)
                .build();

        log.info("AircraftModelCacheService initialized: enabled={}, localMaxSize={}, localExpireMinutes={}, redisTtlSeconds={}",
                cacheEnabled, localMaxSize, localExpireMinutes, redisTtlSeconds);
    }

    /**
     * 根据icao24获取model（走两级缓存）
     */
    public Optional<String> getModel(String icao24) {
        // todo 若是分布式环境需要考虑缓存一致性问题，不同节点内存中缓存更新问题
        if (!cacheEnabled || icao24 == null || icao24.isBlank()) {
            return queryFromDatabase(icao24);
        }

        String model = getFromLocalCache(icao24);
        if (model != null) {
            log.debug("Cache hit (L1): icao24={}", icao24);
            return Optional.of(model);
        }

        model = getFromRedis(icao24);
        if (model != null) {
            log.debug("Cache hit (L2): icao24={}", icao24);
            putToLocalCache(icao24, model);
            return Optional.of(model);
        }

        Optional<String> dbModel = queryFromDatabase(icao24);
        dbModel.ifPresent(m -> {
            putToRedis(icao24, m);
            putToLocalCache(icao24, m);
            log.debug("Cache miss, loaded from DB: icao24={}, model={}", icao24, m);
        });

        return dbModel;
    }

    /**
     * 更新缓存
     */
    public void put(String icao24, String model) {
        if (!cacheEnabled || icao24 == null || icao24.isBlank() || model == null) {
            return;
        }
        putToRedis(icao24, model);
        putToLocalCache(icao24, model);
        log.debug("Cache updated: icao24={}, model={}", icao24, model);
    }

    /**
     * 删除缓存
     */
    public void evict(String icao24) {
        if (!cacheEnabled || icao24 == null || icao24.isBlank()) {
            return;
        }
        removeFromRedis(icao24);
        removeFromLocalCache(icao24);
        log.debug("Cache evicted: icao24={}", icao24);
    }

    private String getFromLocalCache(String icao24) {
        return localCache.getIfPresent(icao24);
    }

    private void putToLocalCache(String icao24, String model) {
        localCache.put(icao24, model);
    }

    private void removeFromLocalCache(String icao24) {
        localCache.invalidate(icao24);
    }

    private String getFromRedis(String icao24) {
        try {
            RBucket<String> bucket = redissonClient.getBucket(REDIS_KEY_PREFIX + icao24);
            return bucket.get();
        } catch (Exception e) {
            log.error("Failed to get from Redis: icao24={}, error={}", icao24, e.getMessage());
            return null;
        }
    }

    private void putToRedis(String icao24, String model) {
        try {
            RBucket<String> bucket = redissonClient.getBucket(REDIS_KEY_PREFIX + icao24);
            bucket.set(model, redisTtlSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Failed to put to Redis: icao24={}, error={}", icao24, e.getMessage());
        }
    }

    private void removeFromRedis(String icao24) {
        try {
            RBucket<String> bucket = redissonClient.getBucket(REDIS_KEY_PREFIX + icao24);
            bucket.delete();
        } catch (Exception e) {
            log.error("Failed to remove from Redis: icao24={}, error={}", icao24, e.getMessage());
        }
    }

    private Optional<String> queryFromDatabase(String icao24) {
        if (icao24 == null || icao24.isBlank()) {
            return Optional.empty();
        }
        return repository.findModelByIcao24(icao24);
    }
}
