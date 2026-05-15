package com.globaleyes.crawler.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StorageConfigTest {

    @Test
    void defaultsRocketmqTopicToExistingNewsArticleTopic() {
        StorageConfig storageConfig = new StorageConfig();

        assertThat(storageConfig.getRocketmq().getTopic()).isEqualTo("news-articles");
    }

    @Test
    void defaultsRocketmqSendTimeoutAboveRocketmqClientDefault() {
        StorageConfig storageConfig = new StorageConfig();

        assertThat(storageConfig.getRocketmq().getSendTimeoutMs()).isEqualTo(10_000L);
    }
}
