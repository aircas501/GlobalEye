package com.globaleyes.crawler.service.translate;

import com.globaleyes.crawler.config.TranslateConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 翻译服务工厂
 * 根据配置选择合适的翻译服务
 *
 * @author RSS News Crawler Team
 * @version 1.0.0
 */
@Slf4j
@Service
public class TranslateServiceFactory {

    private final TranslateConfig translateConfig;
    private final Map<String, TranslateService> services;

    /**
     * 构造函数注入所有翻译服务
     *
     * @param translateConfig 翻译配置
     * @param baiduTranslateService 百度翻译服务
     * @param youdaoTranslateService 有道翻译服务
     * @param aiTranslateService AI翻译服务
     */
    public TranslateServiceFactory(TranslateConfig translateConfig,
                                   BaiduTranslateService baiduTranslateService,
                                   YoudaoTranslateService youdaoTranslateService,
                                   AiTranslateService aiTranslateService) {
        this.translateConfig = translateConfig;
        this.services = new ConcurrentHashMap<>();
        this.services.put("baidu", baiduTranslateService);
        this.services.put("youdao", youdaoTranslateService);
        this.services.put("ai", aiTranslateService);
    }

    /**
     * 获取当前配置的翻译服务
     *
     * @return 翻译服务实例
     */
    public TranslateService getTranslateService() {
        String type = translateConfig.getType();
        return getTranslateService(type);
    }

    /**
     * 获取指定类型的翻译服务
     *
     * @param type 服务类型(baidu/youdao/ai)
     * @return 翻译服务实例
     */
    public TranslateService getTranslateService(String type) {
        TranslateService service = services.get(type);
        if (service != null && service.isAvailable()) {
            return service;
        }

        log.warn("Translate service '{}' is not available, trying fallback", type);

        for (Map.Entry<String, TranslateService> entry : services.entrySet()) {
            if (entry.getValue().isAvailable()) {
                log.info("Using fallback translate service: {}", entry.getKey());
                return entry.getValue();
            }
        }

        log.error("No available translate service found");
        return new NoOpTranslateService();
    }

    /**
     * 空操作翻译服务(当所有服务都不可用时使用)
     */
    private static class NoOpTranslateService implements TranslateService {
        @Override
        public String translateToChinese(String text, String sourceLanguage) {
            return text;
        }

        @Override
        public String getServiceName() {
            return "noop";
        }

        @Override
        public boolean isAvailable() {
            return true;
        }
    }
}
