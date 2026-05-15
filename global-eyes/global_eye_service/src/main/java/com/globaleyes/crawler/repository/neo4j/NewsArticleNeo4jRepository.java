package com.globaleyes.crawler.repository.neo4j;

import com.globaleyes.crawler.pojo.entity.neo4j.NewsArticleNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.stereotype.Repository;

/**
 * 新闻文章Neo4j Repository
 *
 * @author RSS News Crawler Team
 * @version 1.0.0
 */
@Repository
public interface NewsArticleNeo4jRepository extends Neo4jRepository<NewsArticleNode, Long> {

}
