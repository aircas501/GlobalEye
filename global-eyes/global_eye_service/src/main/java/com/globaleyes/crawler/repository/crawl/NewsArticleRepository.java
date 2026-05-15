package com.globaleyes.crawler.repository.crawl;

import com.globaleyes.crawler.pojo.entity.NewsArticle;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 新闻文章数据访问层接口
 * 提供对新闻文章数据库表的各种查询操作
 *
 * @author RSS News Crawler Team
 * @version 2.0.0
 */
@Repository
public interface NewsArticleRepository extends JpaRepository<NewsArticle, Long> {

    Page<NewsArticle> findAll(Pageable pageable);

    Page<NewsArticle> findBySource(String source, Pageable pageable);

    @Query("SELECT na FROM NewsArticle na WHERE LOWER(na.title) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    Page<NewsArticle> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    @Query("SELECT na.source, COUNT(na) FROM NewsArticle na GROUP BY na.source")
    List<Object[]> countBySourceGroup();

}
