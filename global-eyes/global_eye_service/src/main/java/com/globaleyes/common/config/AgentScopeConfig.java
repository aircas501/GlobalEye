package com.globaleyes.common.config;

import io.agentscope.core.model.DashScopeChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AgentScope 配置类
 */
@Configuration
public class AgentScopeConfig {

    @Value("${agentscope.model.api-key}")
    private String apiKey;

    @Value("${agentscope.model.model-name}")
    private String modelName;

    /**
     * 配置 DashScope 聊天模型
     */
    @Bean
    public DashScopeChatModel chatModel() {
        return DashScopeChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .enableSearch(true)
                .stream(true)
                .build();
    }
}
