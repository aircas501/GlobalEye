package com.globaleyes.crawler.service.crawler;

import com.globaleyes.crawler.pojo.entity.NewsArticle;
import com.globaleyes.crawler.repository.crawl.NewsArticleRepository;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 文章内容解析服务
 * 使用AI提取地名并标注坐标信息
 *
 * @author RSS News Crawler Team
 * @version 1.0.0
 */
@Slf4j
@Service
public class ArticleContentParseService {

    private final NewsArticleRepository newsArticleRepository;
    private final ChatClient chatClient;

    public ArticleContentParseService(NewsArticleRepository newsArticleRepository, ChatClient chatClient) {
        this.newsArticleRepository = newsArticleRepository;
        this.chatClient = chatClient;
    }

    /**
     * 根据文章ID解析内容，提取地名并标注坐标
     *
     * @param id 文章ID
     * @return 解析后的文章内容
     */
    public String parseArticleContent(Long id) {
        log.info("开始解析文章，ID: {}", id);

        // 1. 从数据库查询文章
        Optional<NewsArticle> articleOpt = newsArticleRepository.findById(id);

        if (articleOpt.isEmpty()) {
            log.warn("文章不存在，ID: {}", id);
            throw new RuntimeException("文章不存在，ID: " + id);
        }

        NewsArticle article = articleOpt.get();
        String originalContent = article.getOriginalContent();

        if (originalContent == null || originalContent.isEmpty()) {
            log.warn("文章内容为空，ID: {}", id);
            return "";
        }

        log.info("文章标题: {}, 内容长度: {}", article.getTitle(), originalContent.length());

        // 2. 调用大模型提取地名和坐标
        try {
            String parsedContent = extractAndAnnotateLocations(originalContent);
            log.info("文章解析完成，ID: {}", id);
            return parsedContent;
        } catch (Exception e) {
            log.error("文章解析失败，ID: {}, 错误: {}", id, e.getMessage(), e);
            throw new RuntimeException("文章解析失败: " + e.getMessage());
        }
    }

    /**
     * 提取地名并标注坐标
     *
     * @param content 原始文章内容
     * @return 标注后的文章内容
     */
    private String extractAndAnnotateLocations(String content) {
        // 构建 Prompt
        String prompt = buildLocationExtractionPrompt(content);

        // 调用 ChatClient
        LocationExtractionResult result = chatClient.prompt()
                .user(prompt)
                .call()
                .entity(LocationExtractionResult.class);

        if (result == null || result.getLocations() == null || result.getLocations().isEmpty()) {
            log.info("未提取到地名，返回原文");
            return content;
        }

        log.info("提取到 {} 个地名", result.getLocations().size());

        // 3. 将地名替换为带坐标标签的格式
        String annotatedContent = annotateLocations(content, result.getLocations());

        return annotatedContent;
    }

    /**
     * 构建地名提取的 Prompt
     *
     * @param content 文章内容
     * @return Prompt 字符串
     */
    private String buildLocationExtractionPrompt(String content) {
        return String.format("""
                请分析以下新闻文章，提取其中所有的地名（包括国家、城市、地区、海域、基地等），并为每个地名提供经纬度坐标。
                
                要求：
                1. 提取所有明确的地理位置名称
                2. 为每个地名提供准确的经纬度坐标（纬度在前，经度在后）
                3. 如果地名是常见地点，使用标准坐标；如果不确定，给出大致坐标
                4. 只输出JSON格式，不要包含其他说明文字
                
                输出格式示例：
                {
                    "locations": [
                        {"name": "北京", "latitude": 39.9042, "longitude": 116.4074},
                        {"name": "南海", "latitude": 12.0, "longitude": 115.0},
                        {"name": "华盛顿", "latitude": 38.9072, "longitude": -77.0369}
                    ]
                }
                
                文章内容：
                %s
                """, content);
    }

    /**
     * 在文章内容中标注地名坐标
     *
     * @param content   原始内容
     * @param locations 地名列表
     * @return 标注后的内容
     */
    private String annotateLocations(String content, List<LocationInfo> locations) {
        String annotatedContent = content;

        // 按地名长度降序排序，避免短地名先替换导致长地名无法匹配
        locations.sort((a, b) -> b.getName().length() - a.getName().length());

        for (LocationInfo location : locations) {
            String name = location.getName();
            Double latitude = location.getLatitude();
            Double longitude = location.getLongitude();

            if (name == null || name.isEmpty() || latitude == null || longitude == null) {
                continue;
            }

            // 构建标签：<target="经度,纬度">地名</target>
            String tag = String.format("<target=\"%.4f,%.4f\">%s</target>", longitude, latitude, name);

            // 直接替换地名（简单方式）
            // 注意：如果地名可能重复出现，所有出现位置都会被替换
            annotatedContent = annotatedContent.replace(name, tag);
        }

        return annotatedContent;
    }

    /**
     * 地名信息内部类
     */
    @Data
    public static class LocationInfo {
        private String name;
        private Double latitude;
        private Double longitude;
    }

    /**
     * 地名提取结果内部类
     */
    @Data
    public static class LocationExtractionResult {
        private List<LocationInfo> locations;
    }
}
