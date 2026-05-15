package com.globaleyes.crawler.pojo.dto.crawler;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 新闻内容给你分析
 * 对应LLM输出的JSON结构
 *
 * @author RSS News Crawler Team
 * @version 1.0.0
 */
@Data
public class ArticleAnalysisOutputDTO {

    private String summary;

    private String topic;

    @JsonProperty("authorityLevel")
    private String authorityLevel;

    @JsonProperty("hotEvent")
    private HotEventDTO hotEvent;

    private String sentiment;

    private String country;

    private String region;

    @JsonProperty("publishDate")
    private String publishDate;


    @Data
    public static class HotEventDTO {
        private String name;
        private String startDate;
        private String scope;
    }

}
