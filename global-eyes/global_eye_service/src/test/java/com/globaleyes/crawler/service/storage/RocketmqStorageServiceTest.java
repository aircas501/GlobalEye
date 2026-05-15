package com.globaleyes.crawler.service.storage;

import com.globaleyes.crawler.config.StorageConfig;
import com.globaleyes.crawler.pojo.entity.NewsArticle;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RocketmqStorageServiceTest {

    @Mock
    private RocketMQTemplate rocketMQTemplate;

    @Test
    void saveSendsArticleSummaryJsonToConfiguredTopic() {
        StorageConfig storageConfig = new StorageConfig();
        storageConfig.getRocketmq().setTopic("news-articles");
        RocketmqStorageService service = new RocketmqStorageService(storageConfig, rocketMQTemplate);
        NewsArticle article = newsArticle();

        boolean saved = service.save(article);

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(rocketMQTemplate).syncSend(eq("news-articles"), payloadCaptor.capture(), eq(10_000L));
        assertThat(saved).isTrue();
        assertThat(payloadCaptor.getValue()).isInstanceOf(String.class);
        assertThat((String) payloadCaptor.getValue())
                .contains("\"originalContent\":\"RocketMQ is deployed.\"")
                .contains("\"summary\":\"RocketMQ summary\"");
    }

    @Test
    void saveReturnsFalseForNullArticle() {
        StorageConfig storageConfig = new StorageConfig();
        RocketmqStorageService service = new RocketmqStorageService(storageConfig, rocketMQTemplate);

        boolean saved = service.save(null);

        assertThat(saved).isFalse();
        verifyNoInteractions(rocketMQTemplate);
    }

    @Test
    void saveReturnsFalseWhenRocketmqSendFails() {
        StorageConfig storageConfig = new StorageConfig();
        RocketmqStorageService service = new RocketmqStorageService(storageConfig, rocketMQTemplate);
        NewsArticle article = newsArticle();
        when(rocketMQTemplate.syncSend(eq("news-articles"), any(Object.class), eq(10_000L)))
                .thenThrow(new RuntimeException("mq unavailable"));

        boolean saved = service.save(article);

        assertThat(saved).isFalse();
    }

    private NewsArticle newsArticle() {
        NewsArticle article = new NewsArticle();
        article.setTitle("Rocket News");
        article.setOriginalContent("RocketMQ is deployed.");
        article.setExtractedJson("{\"summary\":\"RocketMQ summary\"}");
        article.setSource("rss");
        article.setFingerprint("fp-rocket");
        article.setFetchedAt(LocalDateTime.of(2026, 5, 14, 9, 0));
        article.setPublishedAt(LocalDateTime.of(2026, 5, 14, 8, 30));
        return article;
    }
}
