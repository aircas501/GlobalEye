package com.globaleyes.crawler.service.neo4j;

import com.globaleyes.crawler.pojo.dto.crawler.ArticleAnalysisOutputDTO;
import com.globaleyes.crawler.pojo.entity.neo4j.NewsArticleNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Neo4j 异步存储服务
 * 使用独立事务，避免影响主事务
 *
 * @author RSS News Crawler Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Neo4jStorageService {

    private final KnowledgeGraphService knowledgeGraphService;

    /**
     * 异步保存分析结果到 Neo4j
     * 使用 REQUIRES_NEW 创建独立事务，失败不影响主事务
     *
     * @param mysqlId           MySQL 文章 ID
     * @param title             文章标题
     * @param source            文章来源
     * @param analysisResult    分析结果
     * @return 是否成功
     */
    @Transactional(value = "neo4jTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public boolean saveToNeo4j(Long mysqlId, String title, String source,
                                ArticleAnalysisOutputDTO analysisResult) {
        try {
            if (knowledgeGraphService == null) {
                log.warn("Neo4j service not available, skipping knowledge graph storage");
                return false;
            }

            NewsArticleNode node = knowledgeGraphService.saveAnalysisResult(
                    mysqlId, title, source, analysisResult);

            log.info("Saved analysis result to Neo4j: articleId={}, node={}", mysqlId, node.getId());
            return true;

        } catch (Exception e) {
            log.error("Failed to save to Neo4j for article {}: {}", title, e.getMessage());
            return false;
        }
    }
}
