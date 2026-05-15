package com.globaleyes.crawler.repository.crawl;

import com.globaleyes.crawler.pojo.entity.RssSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * RSS源数据访问层接口
 * 提供对RSS源数据库表的各种查询操作
 *
 * @author RSS News Crawler Team
 * @version 1.0.0
 */
@Repository
public interface RssSourceRepository extends JpaRepository<RssSource, Long> {

    /**
     * 检查URL是否存在
     *
     * @param url RSS订阅URL
     * @return true-存在, false-不存在
     */
    boolean existsByUrl(String url);

    /**
     * 查询所有启用的RSS源
     *
     * @return 启用的RSS源列表
     */
    List<RssSource> findByActiveTrue();

    /**
     * 分页查询启用的RSS源
     *
     * @param pageable 分页参数
     * @return 分页后的RSS源列表
     */
    Page<RssSource> findByActiveTrue(Pageable pageable);

    /**
     * 更新RSS源失败信息
     *
     * @param id RSS源ID
     * @param failReason 失败原因
     */
    @Modifying
    @Query("UPDATE RssSource rs SET rs.failCount = rs.failCount + 1, rs.lastFailAt = :failTime, " +
           "rs.lastFailReason = :failReason, rs.updatedAt = :failTime WHERE rs.id = :id")
    void updateFailInfo(@Param("id") Long id, @Param("failTime") LocalDateTime failTime,
                        @Param("failReason") String failReason);

    /**
     * 重置RSS源失败计数
     *
     * @param id RSS源ID
     */
    @Modifying
    @Query("UPDATE RssSource rs SET rs.failCount = 0, rs.lastFetchedAt = :fetchTime, " +
           "rs.totalArticles = rs.totalArticles + :articleCount, rs.updatedAt = :fetchTime WHERE rs.id = :id")
    void resetFailCount(@Param("id") Long id, @Param("fetchTime") LocalDateTime fetchTime,
                        @Param("articleCount") long articleCount);

    /**
     * 禁用RSS源
     *
     * @param id RSS源ID
     */
    @Modifying
    @Query("UPDATE RssSource rs SET rs.active = false, rs.updatedAt = :updateTime WHERE rs.id = :id")
    void deactivateSource(@Param("id") Long id, @Param("updateTime") LocalDateTime updateTime);

    /**
     * 统计启用的RSS源数量
     *
     * @return 启用的RSS源数量
     */
    long countByActiveTrue();

    /**
     * 按国家统计RSS源数量
     *
     * @return 包含国家名称和对应数量的列表
     */
    @Query("SELECT rs.country, COUNT(rs) FROM RssSource rs WHERE rs.active = true GROUP BY rs.country")
    List<Object[]> countByCountryGroup();

}
