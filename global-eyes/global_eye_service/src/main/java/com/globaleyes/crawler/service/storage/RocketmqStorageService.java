package com.globaleyes.crawler.service.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.globaleyes.crawler.config.StorageConfig;
import com.globaleyes.crawler.pojo.dto.crawler.ArticleAnalysisOutputDTO;
import com.globaleyes.crawler.pojo.entity.NewsArticle;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * RocketMQ storage service.
 */
@Slf4j
@Service("rocketmqStorageService")
@ConditionalOnProperty(name = "storage.type", havingValue = "rocketmq")
public class RocketmqStorageService implements NewsStorageService {

    private final StorageConfig.RocketmqConfig config;
    private final RocketMQTemplate rocketMQTemplate;
    private final ObjectMapper objectMapper;

    public RocketmqStorageService(StorageConfig storageConfig, RocketMQTemplate rocketMQTemplate) {
        this.config = storageConfig.getRocketmq();
        this.rocketMQTemplate = rocketMQTemplate;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @Override
    public boolean save(NewsArticle article) {
        if (article == null) {
            return false;
        }

        try {
            Map<String, Object> sendToKafkaMsg = new HashMap<>();
            sendToKafkaMsg.put("originalContent", article.getOriginalContent());
            sendToKafkaMsg.put("summary", objectMapper.readValue(article.getExtractedJson(), ArticleAnalysisOutputDTO.class).getSummary());
            rocketMQTemplate.syncSend(config.getTopic(), objectMapper.writeValueAsString(sendToKafkaMsg), config.getSendTimeoutMs());
            log.debug("Sent article to RocketMQ topic {}: {}", config.getTopic(), article.getTitle());
            return true;
        } catch (Exception e) {
            log.error("Failed to send article to RocketMQ topic {} within {} ms: {}",
                    config.getTopic(), config.getSendTimeoutMs(), e.getMessage());
            return false;
        }
    }

    @Override
    public String getStorageType() {
        return "rocketmq";
    }

    @Override
    public boolean isAvailable() {
        return rocketMQTemplate != null;
    }
}
