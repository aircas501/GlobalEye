package com.globaleyes.crawler.service.translate;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * AI翻译服务实现
 * 使用阿里百炼AI模型进行文本翻译
 *
 * @author RSS News Crawler Team
 * @version 1.0.0
 */
@Slf4j
@Service("aiTranslateService")
public class AiTranslateService implements TranslateService {

    private final ChatClient chatClient;

    /**
     * 构造函数注入ChatClient
     *
     * @param chatClient Spring AI ChatClient
     */
    public AiTranslateService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public String translateToChinese(String text, String sourceLanguage) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        if (!isAvailable()) {
            log.warn("AI translate service is not available");
            return text;
        }

        try {
            String languageName = getLanguageName(sourceLanguage);
            String prompt = buildTranslatePrompt(text, languageName);

            String result = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            if (result != null && !result.isEmpty()) {
                return result.trim();
            }
            return text;
        } catch (Exception e) {
            log.error("AI translate error: {}", e.getMessage());
            return text;
        }
    }

    /**
     * 构建翻译提示词
     *
     * @param text 待翻译文本
     * @param languageName 语言名称
     * @return 提示词
     */
    private String buildTranslatePrompt(String text, String languageName) {
        return String.format(
                "请将以下%s文本翻译成中文。要求：\n" +
                "1. 保持原文的意思和语气\n" +
                "2. 使用流畅自然的中文表达\n" +
                "3. 只输出翻译结果，不要添加任何解释或说明\n\n" +
                "原文：\n%s",
                languageName, text
        );
    }

    /**
     * 获取语言名称
     *
     * @param languageCode 语言代码
     * @return 语言名称
     */
    private String getLanguageName(String languageCode) {
        if (languageCode == null) {
            return "英文";
        }
        return switch (languageCode.toLowerCase()) {
            case "zh", "zh-cn", "zh-tw" -> "中文";
            case "en" -> "英文";
            case "ja" -> "日文";
            case "ko" -> "韩文";
            case "fr" -> "法文";
            case "de" -> "德文";
            case "ru" -> "俄文";
            case "es" -> "西班牙文";
            case "pt" -> "葡萄牙文";
            case "it" -> "意大利文";
            case "ar" -> "阿拉伯文";
            default -> "外文";
        };
    }

    @Override
    public String getServiceName() {
        return "ai";
    }

    @Override
    public boolean isAvailable() {
        return chatClient != null;
    }
}
