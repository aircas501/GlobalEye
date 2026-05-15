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
 * 新闻文章节点
 * 存储新闻文章信息，与MySQL中的news_article表关联
 *
 * @author RSS News Crawler Team
 * @version 1.0.0
 */
@Data
@Node("NewsArticle")
public class NewsArticleNode {

    /**
     * Neo4j内部ID
     */
    @Id
    @GeneratedValue
    private Long id;

    /**
     * MySQL中的文章ID（外键关联）
     */
    @Property
    private Long mysqlId;

    /**
     * 文章标题
     */
    @Property
    private String title;

    /**
     * 文章摘要
     */
    @Property
    private String summary;

    /**
     * 新闻来源
     */
    @Property
    private String source;

    /**
     * 文章URL
     */
    @Property
    private String url;

    /**
     * 发布日期
     */
    @Property
    private LocalDate publishedAt;

    /**
     * 主题分类
     */
    @Property
    private String topic;

    /**
     * 权威等级（A/B/C/D/E）
     */
    @Property
    private String authorityLevel;

    /**
     * 情感倾向（正面/负面/中性）
     */
    @Property
    private String sentiment;

    /**
     * 军事相关性（高/中/低）
     */
    @Property
    private String militaryRelevance;

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
     * 覆盖的热点事件
     */
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Relationship(type = "COVERS", direction = Relationship.Direction.OUTGOING)
    private CoversRelation covers;

    /**
     * 关联的地点列表
     */
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Relationship(type = "LOCATED_IN", direction = Relationship.Direction.OUTGOING)
    private List<LocatedInRelation> locations = new ArrayList<>();

    public NewsArticleNode() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public NewsArticleNode(Long mysqlId, String title, String source) {
        this.mysqlId = mysqlId;
        this.title = title;
        this.source = source;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void setCoverEvent(HotEventNode event, String relevance) {
        CoversRelation relation = new CoversRelation(event);
        relation.setRelevance(relevance);
        this.covers = relation;
    }
}
