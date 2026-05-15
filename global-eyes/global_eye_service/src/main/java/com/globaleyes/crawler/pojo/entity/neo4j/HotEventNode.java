package com.globaleyes.crawler.pojo.entity.neo4j;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.data.neo4j.core.schema.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 热点事件节点
 * 存储热点事件信息，由多篇相关新闻聚合而成
 *
 * @author RSS News Crawler Team
 * @version 1.0.0
 */
@Data
@Node("HotEvent")
public class HotEventNode {

    /**
     * Neo4j内部ID
     */
    @Id
    @GeneratedValue
    private Long id;

    /**
     * 事件名称
     */
    @Property
    private String name;

    /**
     * 事件开始日期（关联新闻中最早的发布日期）
     */
    @Property
    private LocalDate startDate;

    /**
     * 影响范围（全球/区域/局部）
     */
    @Property
    private String scope;

    /**
     * 事件描述
     */
    @Property
    private String description;

    /**
     * 创建时间
     */
    @Property
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @Property
    private LocalDateTime updatedAt;

    /**
     * 事件发生地点列表
     */
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Relationship(type = "OCCURS_IN", direction = Relationship.Direction.OUTGOING)
    private List<OccursInRelation> locations = new ArrayList<>();

    /**
     * 包含的新闻文章列表
     */
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Relationship(type = "INCLUDES", direction = Relationship.Direction.OUTGOING)
    private List<IncludesRelation> articles = new ArrayList<>();

    public HotEventNode() {
        this.description = "";
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public HotEventNode(String name, LocalDate startDate, String scope) {
        this.name = name;
        this.startDate = startDate;
        this.scope = scope;
        this.description = "";
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void addLocation(LocationNode location, Boolean isPrimary) {
        OccursInRelation relation = new OccursInRelation(location);
        relation.setIsPrimary(isPrimary != null ? isPrimary : false);
        this.locations.add(relation);
    }

    public void addArticle(NewsArticleNode article, String relevance) {
        IncludesRelation relation = new IncludesRelation(article);
        relation.setRelevance(relevance != null ? relevance : "高");
        this.articles.add(relation);
    }
}
