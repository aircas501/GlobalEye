package com.globaleyes.crawler.service.translate;

import com.globaleyes.crawler.config.TranslateConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.TimeUnit;

/**
 * 有道翻译服务实现
 * 使用有道翻译API进行文本翻译
 *
 * @author RSS News Crawler Team
 * @version 1.0.0
 */
@Slf4j
@Service("youdaoTranslateService")
public class YoudaoTranslateService implements TranslateService {

    private final TranslateConfig.YoudaoConfig config;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    /**
     * 构造函数注入配置
     *
     * @param translateConfig 翻译配置
     */
    public YoudaoTranslateService(TranslateConfig translateConfig) {
        this.config = translateConfig.getYoudao();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String translateToChinese(String text, String sourceLanguage) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        if (!isAvailable()) {
            log.warn("Youdao translate service is not available");
            return text;
        }

        try {
            String salt = String.valueOf(System.currentTimeMillis());
            String curtime = String.valueOf(System.currentTimeMillis() / 1000);
            String sign = generateSign(text, salt, curtime);

            String url = buildUrl(text, sourceLanguage, salt, curtime, sign);

            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .addHeader("Content-Type", "application/x-www-form-urlencoded")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String responseBody = response.body().string();
                    return parseTranslationResult(responseBody);
                } else {
                    log.error("Youdao translate failed: {}", response.code());
                    return text;
                }
            }
        } catch (Exception e) {
            log.error("Youdao translate error: {}", e.getMessage());
            return text;
        }
    }

    /**
     * 构建请求URL
     *
     * @param text 待翻译文本
     * @param sourceLanguage 源语言
     * @param salt 随机数
     * @param curtime 当前时间戳
     * @param sign 签名
     * @return 完整URL
     */
    private String buildUrl(String text, String sourceLanguage, String salt, String curtime, String sign) {
        String from = convertLanguageCode(sourceLanguage);
        String input = truncateText(text);
        return String.format("%s?q=%s&from=%s&to=zh-CHS&appKey=%s&salt=%s&curtime=%s&signType=v3&sign=%s",
                config.getUrl(), input, from, config.getAppid(), salt, curtime, sign);
    }

    /**
     * 截断文本(有道API对长文本有限制)
     *
     * @param text 原文本
     * @return 截断后的文本
     */
    private String truncateText(String text) {
        if (text.length() > 200) {
            return text.substring(0, 10) + text.length() + text.substring(text.length() - 10);
        }
        return text;
    }

    /**
     * 生成签名
     *
     * @param text 待翻译文本
     * @param salt 随机数
     * @param curtime 当前时间戳
     * @return 签名
     */
    private String generateSign(String text, String salt, String curtime) {
        try {
            String input = truncateText(text);
            String signStr = config.getAppid() + input + salt + curtime + config.getSecret();
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(signStr.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("Generate sign error: {}", e.getMessage());
            return "";
        }
    }

    /**
     * 解析翻译结果
     *
     * @param responseBody 响应体
     * @return 翻译结果
     */
    private String parseTranslationResult(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode translation = root.path("translation");
            if (translation.isArray() && translation.size() > 0) {
                return translation.get(0).asText();
            }
            return responseBody;
        } catch (Exception e) {
            log.error("Parse translation result error: {}", e.getMessage());
            return responseBody;
        }
    }

    /**
     * 转换语言代码
     *
     * @param languageCode 语言代码
     * @return 有道语言代码
     */
    private String convertLanguageCode(String languageCode) {
        if (languageCode == null) {
            return "en";
        }
        return switch (languageCode.toLowerCase()) {
            case "zh", "zh-cn" -> "zh-CHS";
            case "zh-tw" -> "zh-CHT";
            case "en" -> "en";
            case "ja" -> "ja";
            case "ko" -> "ko";
            case "fr" -> "fr";
            case "de" -> "de";
            case "ru" -> "ru";
            case "es" -> "es";
            case "pt" -> "pt";
            case "it" -> "it";
            case "ar" -> "ar";
            default -> "en";
        };
    }

    @Override
    public String getServiceName() {
        return "youdao";
    }

    @Override
    public boolean isAvailable() {
        return config.getAppid() != null && !config.getAppid().isEmpty()
                && config.getSecret() != null && !config.getSecret().isEmpty()
                && !config.getAppid().startsWith("your_");
    }
}
