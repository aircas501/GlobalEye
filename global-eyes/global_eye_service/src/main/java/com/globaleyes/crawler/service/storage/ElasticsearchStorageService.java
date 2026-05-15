package com.globaleyes.crawler.service.storage;

import com.globaleyes.crawler.config.StorageConfig;
import com.globaleyes.crawler.pojo.entity.NewsArticle;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Service;

/**
 * Elasticsearch存储服务实现
 * 将新闻文章存储到Elasticsearch
 * 只有当storage.type=elasticsearch时才会加载
 *
 * @author RSS News Crawler Team
 * @version 1.0.0
 */
@Slf4j
@Service("elasticsearchStorageService")
@ConditionalOnProperty(name = "storage.type", havingValue = "elasticsearch")
public class ElasticsearchStorageService implements NewsStorageService {

    private final StorageConfig.ElasticsearchConfig config;
    private final ElasticsearchOperations elasticsearchOperations;
    private final ObjectMapper objectMapper;

    /**
     * 构造函数注入配置和ElasticsearchOperations
     *
     * @param storageConfig 存储配置
     * @param elasticsearchOperations Elasticsearch操作类
     */
    public ElasticsearchStorageService(StorageConfig storageConfig,
                                       ElasticsearchOperations elasticsearchOperations) {
        this.config = storageConfig.getElasticsearch();
        this.elasticsearchOperations = elasticsearchOperations;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @Override
    public boolean save(NewsArticle article) {
        if (article == null) {
            return false;
        }

        try {
            String json = objectMapper.writeValueAsString(article);
            Document document = Document.parse(json);

            IndexCoordinates indexCoordinates = IndexCoordinates.of(config.getIndexName());
            IndexOperations indexOps = elasticsearchOperations.indexOps(indexCoordinates);

            if (!indexOps.exists()) {
                indexOps.create();
            }

            elasticsearchOperations.save(document, indexCoordinates);

            log.debug("Saved article to Elasticsearch: {}", article.getTitle());
            return true;
        } catch (Exception e) {
            log.error("Failed to save article to Elasticsearch: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String getStorageType() {
        return "elasticsearch";
    }

    @Override
    public boolean isAvailable() {
        return elasticsearchOperations != null;
    }
}
