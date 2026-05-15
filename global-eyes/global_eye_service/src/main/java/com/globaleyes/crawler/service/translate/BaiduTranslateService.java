package com.globaleyes.crawler.service.translate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.globaleyes.crawler.config.TranslateConfig;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.TimeUnit;

/**
 * 百度翻译服务实现
 * 使用百度翻译API进行文本翻译
 *
 * @author RSS News Crawler Team
 * @version 1.0.0
 */
@Slf4j
@Service("baiduTranslateService")
public class BaiduTranslateService implements TranslateService {

    private final TranslateConfig.BaiduConfig config;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    /**
     * 构造函数注入配置
     *
     * @param translateConfig 翻译配置
     */
    public BaiduTranslateService(TranslateConfig translateConfig) {
        this.config = translateConfig.getBaidu();
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
            log.warn("Baidu translate service is not available");
            return text;
        }

        try {
            String salt = String.valueOf(System.currentTimeMillis());
            String sign = generateSign(text, salt);

            String url = buildUrl(text, sourceLanguage, salt, sign);

            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String responseBody = response.body().string();
                    return parseTranslationResult(responseBody);
                } else {
                    log.error("Baidu translate failed: {}", response.code());
                    return text;
                }
            }
        } catch (Exception e) {
            log.error("Baidu translate error: {}", e.getMessage());
            return text;
        }
    }

    /**
     * 构建请求URL
     *
     * @param text 待翻译文本
     * @param sourceLanguage 源语言
     * @param salt 随机数
     * @param sign 签名
     * @return 完整URL
     */
    private String buildUrl(String text, String sourceLanguage, String salt, String sign) {
        String from = convertLanguageCode(sourceLanguage);
        String encodedText = URLEncoder.encode(text, StandardCharsets.UTF_8);
        return String.format("%s?q=%s&from=%s&to=zh&appid=%s&salt=%s&sign=%s",
                config.getUrl(), encodedText, from, config.getAppid(), salt, sign);
    }

    /**
     * 生成签名
     *
     * @param text 待翻译文本
     * @param salt 随机数
     * @return 签名
     */
    private String generateSign(String text, String salt) {
        try {
            String input = config.getAppid() + text + salt + config.getSecret();
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
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
            JsonNode transResult = root.path("trans_result");
            if (transResult.isArray() && transResult.size() > 0) {
                StringBuilder result = new StringBuilder();
                for (JsonNode item : transResult) {
                    result.append(item.path("dst").asText());
                }
                return result.toString();
            }
            return responseBody;
        } catch (Exception e) {
            log.error("Parse translation result error: {}", e.getMessage());
            return responseBody;
        }
    }

    /**
     * 转换语言代码
     * 百度翻译使用特定的语言代码
     *
     * @param languageCode 语言代码
     * @return 百度语言代码
     */
    private String convertLanguageCode(String languageCode) {
        if (languageCode == null) {
            return "en";
        }
        return switch (languageCode.toLowerCase()) {
            case "zh", "zh-cn", "zh-tw" -> "zh";
            case "en" -> "en";
            case "ja" -> "jp";
            case "ko" -> "kor";
            case "fr" -> "fra";
            case "de" -> "de";
            case "ru" -> "ru";
            case "es" -> "spa";
            case "pt" -> "pt";
            case "it" -> "it";
            case "ar" -> "ara";
            default -> "en";
        };
    }

    @Override
    public String getServiceName() {
        return "baidu";
    }

    @Override
    public boolean isAvailable() {
        return config.getAppid() != null && !config.getAppid().isEmpty()
                && config.getSecret() != null && !config.getSecret().isEmpty()
                && !config.getAppid().startsWith("your_");
    }
}
