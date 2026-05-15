package com.globaleyes.crawler.repository.neo4j;

import com.globaleyes.crawler.pojo.entity.neo4j.HotEventNode;
import com.globaleyes.crawler.pojo.entity.neo4j.NewsArticleNode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 热点事件Repository
 *
 * @author RSS News Crawler Team
 * @version 1.0.0
 */
@Repository
public interface HotEventRepository extends Neo4jRepository<HotEventNode, Long> {

    @Query("MATCH (e:HotEvent) WHERE e.name = $name RETURN e LIMIT 1")
    Optional<HotEventNode> findByName(@Param("name") String name);

    /**
     * 根据热点事件名称查询关联的新闻文章
     *
     * @param eventName 热点事件名称
     * @return 新闻文章列表
     */
    @Query("""
        MATCH (h:HotEvent {name: $eventName})-[:INCLUDES]->(n:NewsArticle)
        RETURN n.id AS id, n.mysqlId AS mysqlId, n.title AS title, 
               n.summary AS summary, n.source AS source, n.url AS url,
               n.publishedAt AS publishedAt, n.topic AS topic,
               n.authorityLevel AS authorityLevel, n.sentiment AS sentiment,
               n.militaryRelevance AS militaryRelevance,
               n.createdAt AS createdAt, n.updatedAt AS updatedAt
        ORDER BY n.createdAt DESC limit 5
        """)
    List<NewsArticleNode> findArticlesByEventName(@Param("eventName") String eventName);

    /**
     * 根据热点事件名称和领域查询关联的新闻文章（模糊匹配）
     *
     * @param eventName 热点事件名称
     * @param topic 领域（如：军事、政治、经济）
     * @return 新闻文章列表
     */
    @Query("""
        MATCH (h:HotEvent {name: $eventName})-[:INCLUDES]->(n:NewsArticle)
        WHERE n.topic CONTAINS $topic
        RETURN n.id AS id, n.mysqlId AS mysqlId, n.title AS title, 
               n.summary AS summary, n.source AS source, n.url AS url,
               n.publishedAt AS publishedAt, n.topic AS topic,
               n.authorityLevel AS authorityLevel, n.sentiment AS sentiment,
               n.militaryRelevance AS militaryRelevance,
               n.createdAt AS createdAt, n.updatedAt AS updatedAt
        ORDER BY n.createdAt DESC 
        LIMIT 5
        """)
    List<NewsArticleNode> findArticlesByEventNameAndTopic(
            @Param("eventName") String eventName, 
            @Param("topic") String topic
    );

    /**
     * 根据热点事件ID查询关联的新闻文章（支持分页）
     *
     * @param eventId 热点事件ID
     * @param pageable 分页参数
     * @return 分页的新闻文章
     */
    @Query(value = """
        MATCH (h:HotEvent {id: $eventId})-[:INCLUDES]->(n:NewsArticle)
        RETURN n
        ORDER BY n.publishedAt DESC
        """,
        countQuery = """
        MATCH (h:HotEvent {id: $eventId})-[:INCLUDES]->(n:NewsArticle)
        RETURN count(n)
        """)
    Page<NewsArticleNode> findArticlesByEventId(@Param("eventId") Long eventId, Pageable pageable);

    /**
     * 根据地区名称查询相关的热点事件
     * 关系: (h:HotEvent)-[:OCCURS_IN]->(l:Location)
     *
     * @param locationName 地区名称
     * @return 热点事件列表
     */
    @Query("""
        MATCH (h:HotEvent)-[:OCCURS_IN]->(l:Location)
        WHERE l.name = $locationName
        RETURN h.id AS id, h.name AS name, h.startDate AS startDate,
               h.scope AS scope, h.description AS description,
               h.createdAt AS createdAt, h.updatedAt AS updatedAt
        ORDER BY h.createdAt DESC limit 10
        """)
    List<HotEventNode> findEventsByLocationName(@Param("locationName") String locationName);

    /**
     * 根据地区名称和领域查询相关的热点事件（模糊匹配）
     * 关系: (h:HotEvent)-[:OCCURS_IN]->(l:Location), (h:HotEvent)-[:INCLUDES]->(n:NewsArticle)
     *
     * @param locationName 地区名称
     * @param topic 领域（如：军事、政治、经济）
     * @return 热点事件列表
     */
    @Query("""
        MATCH (h:HotEvent)-[:OCCURS_IN]->(l:Location),
              (h)-[:INCLUDES]->(n:NewsArticle)
        WHERE l.name = $locationName 
          AND n.topic CONTAINS $topic
        RETURN DISTINCT h.id AS id, 
               h.name AS name, 
               h.startDate AS startDate,
               h.scope AS scope, 
               h.description AS description,
               h.createdAt AS createdAt, 
               h.updatedAt AS updatedAt
        ORDER BY h.createdAt DESC 
        LIMIT 10
        """)
    List<HotEventNode> findEventsByLocationAndTopic(
            @Param("locationName") String locationName, 
            @Param("topic") String topic
    );
}
