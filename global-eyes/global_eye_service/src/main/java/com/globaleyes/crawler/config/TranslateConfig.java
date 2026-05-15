package com.globaleyes.crawler.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 翻译服务配置类
 * 读取application.yml中的translate配置
 *
 * @author RSS News Crawler Team
 * @version 1.0.0
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "translate")
public class TranslateConfig {

    /**
     * 翻译类型(baidu/youdao/ai)
     */
    private String type = "ai";

    /**
     * 百度翻译配置
     */
    private BaiduConfig baidu = new BaiduConfig();

    /**
     * 有道翻译配置
     */
    private YoudaoConfig youdao = new YoudaoConfig();

    /**
     * AI翻译配置
     */
    private AiConfig ai = new AiConfig();

    /**
     * 百度翻译配置
     */
    @Data
    public static class BaiduConfig {
        /**
         * 应用ID
         */
        private String appid;

        /**
         * 密钥
         */
        private String secret;

        /**
         * API地址
         */
        private String url = "https://fanyi-api.baidu.com/api/trans/vip/translate";
    }

    /**
     * 有道翻译配置
     */
    @Data
    public static class YoudaoConfig {
        /**
         * 应用ID
         */
        private String appid;

        /**
         * 密钥
         */
        private String secret;

        /**
         * API地址
         */
        private String url = "https://openapi.youdao.com/api";
    }

    /**
     * AI翻译配置
     */
    @Data
    public static class AiConfig {
        /**
         * 是否启用
         */
        private boolean enabled = true;
    }
}
