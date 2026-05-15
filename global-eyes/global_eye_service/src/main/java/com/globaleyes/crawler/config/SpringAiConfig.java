package com.globaleyes.crawler.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI配置类
 * 配置ChatClient用于AI翻译和分析服务
 * ChatModel由spring-ai-alibaba-starter-dashscope自动配置
 *
 * @author RSS News Crawler Team
 * @version 1.0.0
 */
@Configuration
public class SpringAiConfig {

    /**
     * 创建ChatClient实例
     * ChatModel由Spring AI Alibaba自动配置注入
     *
     * @param chatClientBuilder chatClientBuilder(由自动配置提供)
     * @return ChatClient实例
     */
    @Bean
    @ConditionalOnMissingBean
    public ChatClient chatClient(ChatClient.Builder chatClientBuilder) {
        return chatClientBuilder.build();
    }
}
