package com.globaleyes.crawler.service.crawler;

import com.globaleyes.crawler.pojo.dto.crawler.ArticleAnalysisOutputDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class MilitaryAnalysisService {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public MilitaryAnalysisService(ChatClient chatClient) {
        this.chatClient = chatClient;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 分析新闻文章
     *
     * @param title       文章标题
     * @param content     文章内容
     * @param source      文章来源
     * @param publishDate 发布日期（格式：YYYY-MM-DD，若未知传"未知"）
     * @return 分析结果DTO
     */
    public ArticleAnalysisOutputDTO analyze(String title, String content, String source, String publishDate) {
        if (title == null || content == null) {
            log.warn("Title or content is null, skip analysis");
            return null;
        }

        try {
            String prompt = buildAnalysisPrompt(title, content, source, publishDate);

            ChatResponse chatResponse = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .chatResponse();

            if (chatResponse == null) {
                log.warn("Chat response is null for article: {}", title);
                return null;
            }

            logTokenUsage(chatResponse, title);

            String response = null;
            if (chatResponse.getResult() != null && chatResponse.getResult().getOutput() != null) {
                response = chatResponse.getResult().getOutput().getText();
            } else {
                log.warn("AI response structure incomplete for article: {}", title);
                return null;
            }

            if (response == null || response.isEmpty()) {
                log.warn("AI response content is empty for article: {}", title);
                return null;
            }

            ArticleAnalysisOutputDTO result = parseResponse(response);
            log.debug("Military analysis completed for article: {}", title);
            return result;
        } catch (NonTransientAiException e) {
            String errorMsg = e.getMessage();
            if (errorMsg != null && errorMsg.contains("DataInspectionFailed")) {
                log.warn("Content safety check failed for article [{}]: {}", truncateTitle(title, 30), errorMsg);
            } else {
                log.error("AI API error for article [{}]: {}", truncateTitle(title, 30), errorMsg);
            }
            return null;
        } catch (Exception e) {
            log.error("Military analysis failed for article: {}, error: {}", title, e.getMessage());
            return null;
        }
    }

    /**
     * 记录 Token 使用量
     *
     * @param chatResponse 聊天响应
     * @param title        文章标题（用于日志）
     */
    private void logTokenUsage(ChatResponse chatResponse, String title) {
        try {
            ChatResponseMetadata metadata = chatResponse.getMetadata();
            if (metadata != null && metadata.getUsage() != null) {
                var usage = metadata.getUsage();
                Long promptTokens = usage.getPromptTokens() != null ? usage.getPromptTokens().longValue() : null;
                Long completionTokens = usage.getCompletionTokens() != null ? usage.getCompletionTokens().longValue() : null;
                Long totalTokens = usage.getTotalTokens() != null ? usage.getTotalTokens().longValue() : null;

                log.info("Token Usage for article [{}] - Prompt: {}, Completion: {}, Total: {}",
                        truncateTitle(title, 30),
                        promptTokens != null ? promptTokens : "N/A",
                        completionTokens != null ? completionTokens : "N/A",
                        totalTokens != null ? totalTokens : "N/A");
            } else {
                log.debug("Token usage metadata not available for article: {}", truncateTitle(title, 30));
            }
        } catch (Exception e) {
            log.debug("Failed to extract token usage: {}", e.getMessage());
        }
    }

    /**
     * 截断标题用于日志显示
     */
    private String truncateTitle(String title, int maxLength) {
        if (title == null) {
            return "N/A";
        }
        if (title.length() <= maxLength) {
            return title;
        }
        return title.substring(0, maxLength) + "...";
    }

    /**
     * 构建分析提示词
     */
    private String buildAnalysisPrompt(String title, String content, String source, String publishDate) {
        return String.format("""
                        请对提供的新闻文章进行深度军事偏向分析，并严格按照下方定义的JSON格式输出结果。
                        输出内容必须仅为有效的JSON字符串，不包含任何额外的解释、说明或Markdown代码块标记（如```json）。
                        ## 输入信息
                        - 文章标题：%s
                        - 文章内容：%s
                        - 文章来源：%s
                        - 发布日期：%s
                        ## 输出JSON结构
                        {
                            "summary": "客观概括核心内容，100-200字，只陈述事实，无评价或推测。",
                            "topic": "从以下列表中选择最匹配的一项：[军事·冲突, 军事·技术, 军事·预算, 军事·演习, 军事·情报, 军事·其他, 科技, 财经, 政治, 体育, 娱乐, 社会, 国际, 教育, 健康, 环境, 其他]",
                            "authorityLevel": "A/B/C/D/E（A=官方/顶级机构，B=主流正规媒体，C=地方/行业媒体，D=门户/自媒体有可靠信源，E=来源不明/可信度低）",
                            "hotEvent": {
                                "name": "事件名称，无则填'无'",
                                "startDate": "格式YYYY-MM-DD，未知则填'未知'",
                                "scope": "全球/区域/局部/未知"
                            },
                            "sentiment": "正面/负面/中立/极端负面（从军事利益角度判断）",
                            "country": "核心事件发生的主要国家（如'美国'、'中国'、'俄罗斯'），多个或不明则填'未知'",
                            "region": "具体地域（如'中东地区'、'南海'、'东欧'），无则填'无'",
                            "publishDate": "直接使用输入的发布日期（格式YYYY-MM-DD），若输入为'未知'则填'未知'"
                        }
                """,
                title,
                content,
                source,
                publishDate != null ? publishDate : "未知"
        );
    }


    /**
     * 解析AI响应
     */
    private ArticleAnalysisOutputDTO parseResponse(String response) {
        if (response == null || response.isEmpty()) {
            log.warn("AI response is empty");
            return null;
        }

        try {
            String jsonStr = extractJson(response);
            return objectMapper.readValue(jsonStr, ArticleAnalysisOutputDTO.class);
        } catch (Exception e) {
            log.error("Failed to parse AI response: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从响应中提取JSON字符串
     */
    private String extractJson(String response) {
        if (response == null || response.isEmpty()) {
            return "{}";
        }

        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');

        if (start >= 0 && end > start) {
            return response.substring(start, end + 1);
        }

        return "{}";
    }
}
