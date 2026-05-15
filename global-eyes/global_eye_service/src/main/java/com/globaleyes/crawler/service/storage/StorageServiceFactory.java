package com.globaleyes.crawler.service.storage;

import com.globaleyes.crawler.config.StorageConfig;
import com.globaleyes.crawler.pojo.entity.NewsArticle;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Selects the configured news storage service.
 */
@Slf4j
@Service
public class StorageServiceFactory {

    private final StorageConfig storageConfig;
    private final Map<String, NewsStorageService> services;

    @Autowired
    public StorageServiceFactory(StorageConfig storageConfig,
                                 LocalStorageService localStorageService,
                                 MysqlStorageService mysqlStorageService,
                                 @Autowired(required = false) ElasticsearchStorageService elasticsearchStorageService,
                                 @Autowired(required = false) RocketmqStorageService rocketmqStorageService) {
        this.storageConfig = storageConfig;
        this.services = new ConcurrentHashMap<>();

        this.services.put("local", localStorageService);
        this.services.put("mysql", mysqlStorageService);

        Optional.ofNullable(elasticsearchStorageService)
                .ifPresent(service -> this.services.put("elasticsearch", service));

        Optional.ofNullable(rocketmqStorageService)
                .ifPresent(service -> this.services.put("rocketmq", service));

        log.info("StorageServiceFactory initialized with services: {}", services.keySet());
    }

    public NewsStorageService getStorageService() {
        String type = storageConfig.getType();
        return getStorageService(type);
    }

    public NewsStorageService getStorageService(String type) {
        NewsStorageService service = services.get(type);
        if (service != null && service.isAvailable()) {
            return service;
        }

        log.warn("Storage service '{}' is not available, trying fallback", type);

        for (Map.Entry<String, NewsStorageService> entry : services.entrySet()) {
            if (entry.getValue().isAvailable()) {
                log.info("Using fallback storage service: {}", entry.getKey());
                return entry.getValue();
            }
        }

        log.error("No available storage service found");
        return new NoOpStorageService();
    }

    private static class NoOpStorageService implements NewsStorageService {
        @Override
        public boolean save(NewsArticle article) {
            log.warn("No storage service available, article will not be saved: {}", article.getTitle());
            return false;
        }

        @Override
        public String getStorageType() {
            return "noop";
        }

        @Override
        public boolean isAvailable() {
            return true;
        }
    }
}
