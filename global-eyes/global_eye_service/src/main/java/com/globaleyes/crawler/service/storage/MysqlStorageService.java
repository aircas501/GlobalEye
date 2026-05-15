package com.globaleyes.crawler.service.storage;

import com.globaleyes.crawler.pojo.entity.NewsArticle;
import com.globaleyes.crawler.repository.crawl.NewsArticleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * MySQL存储服务实现
 * 将新闻文章存储到MySQL数据库
 *
 * @author RSS News Crawler Team
 * @version 1.0.0
 */
@Slf4j
@Service("mysqlStorageService")
public class MysqlStorageService implements NewsStorageService {

    private final NewsArticleRepository newsArticleRepository;

    /**
     * 构造函数注入Repository
     *
     * @param newsArticleRepository 新闻文章Repository
     */
    public MysqlStorageService(NewsArticleRepository newsArticleRepository) {
        this.newsArticleRepository = newsArticleRepository;
    }

    @Override
    @Transactional
    public boolean save(NewsArticle article) {
        if (article == null) {
            return false;
        }

        try {
            newsArticleRepository.save(article);
            log.debug("Saved article to MySQL: {}", article.getTitle());
            return true;
        } catch (Exception e) {
            log.error("Failed to save article to MySQL: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String getStorageType() {
        return "mysql";
    }

    @Override
    public boolean isAvailable() {
        return newsArticleRepository != null;
    }
}
