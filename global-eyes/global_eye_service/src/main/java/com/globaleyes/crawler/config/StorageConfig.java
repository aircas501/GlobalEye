package com.globaleyes.crawler.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Storage service configuration.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "storage")
public class StorageConfig {

    /**
     * Storage type: local/mysql/elasticsearch/rocketmq.
     */
    private String type = "local";

    /**
     * Local file storage configuration.
     */
    private LocalConfig local = new LocalConfig();

    /**
     * Elasticsearch configuration.
     */
    private ElasticsearchConfig elasticsearch = new ElasticsearchConfig();

    /**
     * RocketMQ configuration.
     */
    private RocketmqConfig rocketmq = new RocketmqConfig();

    /**
     * Local file storage configuration.
     */
    @Data
    public static class LocalConfig {
        /**
         * Storage path.
         */
        private String path = "./news-data";

        /**
         * File name pattern.
         */
        private String fileNamePattern = "{sequence}_{timestamp}_{title}_{index}.txt";
    }

    /**
     * Elasticsearch configuration.
     */
    @Data
    public static class ElasticsearchConfig {
        /**
         * Index name.
         */
        private String indexName = "news_articles";
    }

    /**
     * RocketMQ configuration.
     */
    @Data
    public static class RocketmqConfig {
        /**
         * Topic name.
         */
        private String topic = "news-articles";

        /**
         * Send timeout in milliseconds.
         */
        private long sendTimeoutMs = 10_000L;
    }
}
