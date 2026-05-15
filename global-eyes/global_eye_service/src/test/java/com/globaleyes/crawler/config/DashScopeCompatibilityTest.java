package com.globaleyes.crawler.config;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DashScopeCompatibilityTest {

    @Test
    void tokenUsageAcceptsDashScopeTokenDetailFields() throws Exception {
        String usageJson = """
                {
                  "output_tokens": 1,
                  "input_tokens": 2,
                  "total_tokens": 3,
                  "prompt_tokens_details": null
                }
                """;

        DashScopeApi.TokenUsage usage = new ObjectMapper()
                .readValue(usageJson, DashScopeApi.TokenUsage.class);

        assertThat(usage.totalTokens()).isEqualTo(3);
    }
}
