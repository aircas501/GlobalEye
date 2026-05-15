package com.globaleyes.crawler.service.neo4j;

import com.globaleyes.crawler.constant.CommonConstants;
import com.globaleyes.crawler.pojo.dto.crawler.ArticleAnalysisOutputDTO;
import com.globaleyes.crawler.pojo.entity.neo4j.HotEventNode;
import com.globaleyes.crawler.pojo.entity.neo4j.LocationNode;
import com.globaleyes.crawler.pojo.entity.neo4j.NewsArticleNode;
import com.globaleyes.crawler.repository.neo4j.HotEventRepository;
import com.globaleyes.crawler.repository.neo4j.LocationRepository;
import com.globaleyes.crawler.repository.neo4j.NewsArticleNeo4jRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 知识图谱服务
 * 负责将分析结果存入Neo4j
 * 使用Spring Data Neo4j关系属性机制
 *
 * @author RSS News Crawler Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.neo4j.uri")
public class KnowledgeGraphService {

    private final NewsArticleNeo4jRepository articleRepository;
    private final HotEventRepository hotEventRepository;
    private final LocationRepository locationRepository;

    /**
     * 保存分析结果到Neo4j
     *
     * @param mysqlId          MySQL文章ID
     * @param title            文章标题
     * @param source           文章来源
     * @param analysisResult   分析结果
     * @return 创建的文章节点
     */
    @Transactional("neo4jTransactionManager")
    public NewsArticleNode saveAnalysisResult(Long mysqlId, String title, String source,
                                               ArticleAnalysisOutputDTO analysisResult) {
        NewsArticleNode articleNode = createArticleNode(mysqlId, title, source, analysisResult);
        if (analysisResult.getHotEvent() != null && !CommonConstants.NONE.equals(analysisResult.getHotEvent().getName())) {
            createHotEventRelation(analysisResult.getHotEvent(), articleNode, analysisResult);
        }
        articleNode = articleRepository.save(articleNode);

        log.info("Saved analysis result to Neo4j: articleId={}", mysqlId);

        return articleNode;
    }

    private NewsArticleNode createArticleNode(Long mysqlId, String title, String source,
                                              ArticleAnalysisOutputDTO analysisResult) {
        NewsArticleNode node = new NewsArticleNode(mysqlId, title, source);
        
        if (analysisResult != null) {
            node.setSummary(analysisResult.getSummary());
            node.setTopic(analysisResult.getTopic());
            node.setAuthorityLevel(analysisResult.getAuthorityLevel());
            node.setSentiment(analysisResult.getSentiment());
        }
        
        return node;
    }


    /**
     * 创建COVERS、INCLUDES和OCCURS_IN关系
     * 使用Spring Data Neo4j关系属性机制
     * 方向：(NewsArticle)-[:COVERS]->(HotEvent)
     * 方向：(HotEvent)-[:INCLUDES]->(NewsArticle)
     * 方向：(HotEvent)-[:OCCURS_IN]->(Location)
     */
    private void createHotEventRelation(ArticleAnalysisOutputDTO.HotEventDTO hotEventDTO,
                                        NewsArticleNode articleNode,
                                        ArticleAnalysisOutputDTO analysisResult) {
        LocalDate articlePublishedAt = articleNode.getPublishedAt();

        HotEventNode eventNode = hotEventRepository.findByName(hotEventDTO.getName())
                .map(existingEvent -> {
                    if (articlePublishedAt != null) {
                        LocalDate currentStartDate = existingEvent.getStartDate();
                        if (currentStartDate == null || articlePublishedAt.isBefore(currentStartDate)) {
                            existingEvent.setStartDate(articlePublishedAt);
                            log.debug("Updated HotEvent '{}' startDate to {} (earlier article found)",
                                    existingEvent.getName(), articlePublishedAt);
                        }
                    }
                    return existingEvent;
                })
                .orElseGet(() -> {
                    HotEventNode newNode = new HotEventNode();
                    newNode.setName(hotEventDTO.getName());
                    newNode.setDescription("");
                    newNode.setStartDate(articlePublishedAt);
                    newNode.setScope(hotEventDTO.getScope());
                    return hotEventRepository.save(newNode);
                });

        articleNode.setCoverEvent(eventNode, CommonConstants.DEFAULT_RELEVANCE);

        eventNode.addArticle(articleNode, CommonConstants.DEFAULT_RELEVANCE);

        String country = analysisResult.getCountry();
        if (country != null && !country.isEmpty()) {
            final String finalCountry = country;
            LocationNode locationNode = locationRepository.findByName(finalCountry)
                    .orElseGet(() -> {
                        LocationNode newNode = new LocationNode(finalCountry, "country");
                        return locationRepository.save(newNode);
                    });
            if ("country".equals(locationNode.getType())) {
                eventNode.addLocation(locationNode, true);
                log.debug("Created OCCURS_IN relation: HotEvent({}) -[:OCCURS_IN]-> Location({})",
                        eventNode.getName(), locationNode.getName());
            }
        }

        hotEventRepository.save(eventNode);

        log.debug("Created COVERS relation: Article({}) -[:COVERS]-> HotEvent({})",
                articleNode.getMysqlId(), eventNode.getName());
        log.debug("Created INCLUDES relation: HotEvent({}) -[:INCLUDES]-> Article({})",
                eventNode.getName(), articleNode.getMysqlId());
    }
}
