package com.globaleyes.crawler.service.translate;

/**
 * 翻译服务接口
 * 定义翻译功能的标准接口
 *
 * @author RSS News Crawler Team
 * @version 1.0.0
 */
public interface TranslateService {

    /**
     * 将文本翻译为中文
     *
     * @param text 待翻译的文本
     * @param sourceLanguage 源语言代码(如en, ja, ko等)
     * @return 翻译后的中文文本
     */
    String translateToChinese(String text, String sourceLanguage);

    /**
     * 获取翻译服务名称
     *
     * @return 服务名称(baidu/youdao/ai)
     */
    String getServiceName();

    /**
     * 检查服务是否可用
     *
     * @return true-可用, false-不可用
     */
    boolean isAvailable();
}
