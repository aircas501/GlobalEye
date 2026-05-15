package com.globaleyes.crawler.service.storage;

import com.globaleyes.crawler.pojo.entity.NewsArticle;

/**
 * Standard interface for news storage services.
 */
public interface NewsStorageService {

    /**
     * Stores a news article.
     *
     * @param article news article entity
     * @return true when the article is stored successfully
     */
    boolean save(NewsArticle article);

    /**
     * Returns the storage type: local/mysql/elasticsearch/rocketmq.
     */
    String getStorageType();

    /**
     * Checks whether the storage service is available.
     */
    boolean isAvailable();
}
