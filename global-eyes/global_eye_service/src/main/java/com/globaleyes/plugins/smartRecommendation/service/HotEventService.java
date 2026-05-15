package com.globaleyes.plugins.smartRecommendation.service;

import com.globaleyes.crawler.pojo.entity.neo4j.HotEventNode;
import com.globaleyes.crawler.pojo.entity.neo4j.NewsArticleNode;
import com.globaleyes.crawler.repository.neo4j.HotEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 热点事件服务
 * 提供热点事件查询及关联文章的业务逻辑
 *
 * @author RSS News Crawler Team
 * @version 1.0.0
 */
@Slf4j
@Service
public class HotEventService {

    private final HotEventRepository hotEventRepository;

    public HotEventService(HotEventRepository hotEventRepository) {
        this.hotEventRepository = hotEventRepository;
    }

    /**
     * 根据事件名称获取关联的新闻文章
     *
     * @param eventName 热点事件名称
     * @return 新闻文章列表
     */
    public List<NewsArticleNode> getArticlesByEventName(String eventName) {
        log.info("查询热点事件[{}]的关联文章", eventName);
        
        // 先检查事件是否存在
        Optional<HotEventNode> eventOpt = hotEventRepository.findByName(eventName);
        if (eventOpt.isEmpty()) {
            log.warn("热点事件不存在: [{}]", eventName);
            // 尝试模糊查询
            List<HotEventNode> allEvents = hotEventRepository.findAll();
            log.info("数据库中所有热点事件: {}", allEvents.stream().map(HotEventNode::getName).toList());
            return List.of();
        }
        
        log.info("找到热点事件: ID={}, Name={}", eventOpt.get().getId(), eventOpt.get().getName());
        
        List<NewsArticleNode> articles = hotEventRepository.findArticlesByEventName(eventName);
        log.info("查询到 {} 篇关联文章", articles.size());
        
        if (articles.isEmpty()) {
            log.warn("事件[{}]存在但没有关联文章，请检查 INCLUDES 关系是否建立", eventName);
        }
        
        return articles;
    }

    /**
     * 根据事件名称和领域获取关联的新闻文章（模糊匹配）
     *
     * @param eventName 热点事件名称
     * @param topic 领域（如：军事、政治、经济）
     * @return 新闻文章列表
     */
    public List<NewsArticleNode> getArticlesByEventNameAndTopic(String eventName, String topic) {
        log.info("查询热点事件[{}]、领域[{}]的关联文章", eventName, topic);
        
        // 先检查事件是否存在
        Optional<HotEventNode> eventOpt = hotEventRepository.findByName(eventName);
        if (eventOpt.isEmpty()) {
            log.warn("热点事件不存在: [{}]", eventName);
            return List.of();
        }
        
        log.info("找到热点事件: ID={}, Name={}", eventOpt.get().getId(), eventOpt.get().getName());
        
        List<NewsArticleNode> articles = hotEventRepository.findArticlesByEventNameAndTopic(eventName, topic);
        log.info("查询到 {} 篇关联文章", articles.size());
        
        if (articles.isEmpty()) {
            log.warn("事件[{}]存在但没有领域[{}]的关联文章", eventName, topic);
        }
        
        return articles;
    }

    /**
     * 根据事件ID获取关联的新闻文章（分页）
     *
     * @param eventId 热点事件ID
     * @param page    页码（从0开始）
     * @param size    每页数量
     * @return 分页的新闻文章
     */
    public Page<NewsArticleNode> getArticlesByEventId(Long eventId, int page, int size) {
        log.info("查询热点事件ID[{}]的关联文章，页码: {}, 每页: {}", eventId, page, size);
        Page<NewsArticleNode> articles = hotEventRepository.findArticlesByEventId(
                eventId, 
                PageRequest.of(page, size)
        );
        log.info("查询到 {} 篇关联文章，总页数: {}", articles.getTotalElements(), articles.getTotalPages());
        return articles;
    }

    /**
     * 获取热点事件及其关联的新闻文章（完整信息）
     *
     * @param eventName 热点事件名称
     * @return 包含事件信息和文章列表的Map
     */
    public Map<String, Object> getEventWithArticles(String eventName) {
        log.info("获取热点事件[{}]的详细信息及关联文章", eventName);

        // 1. 查询热点事件
        Optional<HotEventNode> eventOpt = hotEventRepository.findByName(eventName);
        
        if (eventOpt.isEmpty()) {
            log.warn("热点事件不存在: {}", eventName);
            return null;
        }

        HotEventNode event = eventOpt.get();

        // 2. 查询关联文章
        List<NewsArticleNode> articles = getArticlesByEventName(eventName);

        // 3. 组装返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("event", event);
        result.put("articles", articles);
        result.put("articleCount", articles.size());

        log.info("热点事件[{}]查询完成，关联文章数: {}", eventName, articles.size());
        return result;
    }

    /**
     * 获取热点事件详情及关联文章（分页版本）
     *
     * @param eventName 热点事件名称
     * @param page      页码
     * @param size      每页数量
     * @return 包含事件信息和分页文章列表的Map
     */
    public Map<String, Object> getEventWithArticlesPaged(String eventName, int page, int size) {
        log.info("获取热点事件[{}]的详细信息及关联文章（分页），页码: {}, 每页: {}", eventName, page, size);

        // 1. 查询热点事件
        Optional<HotEventNode> eventOpt = hotEventRepository.findByName(eventName);
        
        if (eventOpt.isEmpty()) {
            log.warn("热点事件不存在: {}", eventName);
            return null;
        }

        HotEventNode event = eventOpt.get();

        // 2. 查询关联文章（分页）
        Page<NewsArticleNode> articlesPage = hotEventRepository.findArticlesByEventId(
                event.getId(), 
                PageRequest.of(page, size)
        );

        // 3. 组装返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("event", event);
        result.put("articles", articlesPage.getContent());
        result.put("totalCount", articlesPage.getTotalElements());
        result.put("totalPages", articlesPage.getTotalPages());
        result.put("currentPage", page);
        result.put("pageSize", size);

        log.info("热点事件[{}]查询完成，文章总数: {}, 总页数: {}", 
                eventName, articlesPage.getTotalElements(), articlesPage.getTotalPages());
        return result;
    }

    /**
     * 获取所有热点事件（调试用）
     *
     * @return 热点事件列表
     */
    public List<HotEventNode> getAllEvents() {
        log.info("查询所有热点事件");
        List<HotEventNode> events = hotEventRepository.findAll();
        log.info("查询到 {} 个热点事件", events.size());
        return events;
    }

    /**
     * 根据地区名称查询相关的热点事件
     *
     * @param locationName 地区名称
     * @return 热点事件列表
     */
    public List<HotEventNode> getEventsByLocationName(String locationName) {
        log.info("查询地区[{}]相关的热点事件", locationName);
        List<HotEventNode> events = hotEventRepository.findEventsByLocationName(locationName);
        log.info("查询到 {} 个热点事件", events.size());
        return events;
    }

    /**
     * 根据地区名称和领域查询相关的热点事件
     *
     * @param locationName 地区名称
     * @param topic 领域（如：军事、政治、经济）
     * @return 热点事件列表
     */
    public List<HotEventNode> getEventsByLocationAndTopic(String locationName, String topic) {
        log.info("查询地区[{}]、领域[{}]相关的热点事件", locationName, topic);
        List<HotEventNode> events = hotEventRepository.findEventsByLocationAndTopic(locationName, topic);
        log.info("查询到 {} 个热点事件", events.size());
        return events;
    }
}
