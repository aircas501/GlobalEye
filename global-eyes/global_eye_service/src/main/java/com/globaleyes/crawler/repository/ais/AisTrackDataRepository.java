package com.globaleyes.crawler.repository.ais;

import com.globaleyes.crawler.pojo.entity.AisTrackData;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * AIS船舶追踪数据Repository
 *
 * @author AIS Data Team
 * @version 1.0.0
 */
@Repository
public interface AisTrackDataRepository extends JpaRepository<AisTrackData, Long> {

    /**
     * 根据MMSI查询
     *
     * @param mmsi 海上移动服务标识
     * @return 船舶数据
     */
    Optional<AisTrackData> findByMmsi(String mmsi);


    /**
     * 分页查询（支持name_ais模糊搜索，navy_type、country精确查询，更新时间范围查询）
     *
     * @param nameAis   船舶名称（模糊查询）
     * @param navyType  船舶细分类型（精确查询）
     * @param country   船旗国（精确查询）
     * @param startTime 更新时间开始范围
     * @param endTime   更新时间结束范围
     * @param pageable  分页参数
     * @return 分页结果
     */
    @Query("SELECT a FROM AisTrackData a WHERE " +
           "(:nameAis IS NULL OR a.nameAis LIKE %:nameAis%) AND " +
           "(:navyType IS NULL OR a.navyType = :navyType) AND " +
           "(:country IS NULL OR a.country = :country) AND " +
           "(:startTime IS NULL OR a.updatedAt >= :startTime) AND " +
           "(:endTime IS NULL OR a.updatedAt <= :endTime)")
    Page<AisTrackData> search(@Param("nameAis") String nameAis,
                              @Param("navyType") String navyType,
                              @Param("country") String country,
                              @Param("startTime") LocalDateTime startTime,
                              @Param("endTime") LocalDateTime endTime,
                              Pageable pageable);

    /**
     * 查询所有船舶的最新数据（按MMSI分组，取每组中创建时间最新的记录）
     * 支持按船舶细分类型和船旗国筛选
     *
     * @param navyType  船舶细分类型（精确查询）
     * @param country   船旗国（精确查询）
     * @param pageable  分页参数
     * @return 分页结果
     */
    @Query(value = "SELECT a.* FROM ais_track_data a " +
           "INNER JOIN (" +
           "  SELECT mmsi, MAX(created_at) as max_created_at " +
           "  FROM ais_track_data " +
           "  WHERE (:navyType IS NULL OR navy_type = :navyType) " +
           "  AND (:country IS NULL OR country = :country) " +
           "  GROUP BY mmsi" +
           ") latest ON a.mmsi = latest.mmsi AND a.created_at = latest.max_created_at " +
           "WHERE (:navyType IS NULL OR a.navy_type = :navyType) " +
           "AND (:country IS NULL OR a.country = :country) " +
           "ORDER BY a.created_at DESC",
           nativeQuery = true)
    Page<AisTrackData> findLatestByMmsi(@Param("navyType") String navyType,
                                        @Param("country") String country,
                                        Pageable pageable);

    /**
     * 查询所有船舶类型列表
     *
     * @return 船舶类型列表
     */
    @Query("SELECT DISTINCT a.navyType FROM AisTrackData a WHERE a.navyType IS NOT NULL ORDER BY a.navyType")
    List<String> findAllNavyTypes();

    /**
     * 查询所有国家列表
     *
     * @return 国家列表
     */
    @Query("SELECT DISTINCT a.country FROM AisTrackData a WHERE a.country IS NOT NULL ORDER BY a.country")
    List<String> findAllCountries();

    /**
     * 按精确更新时间查询
     *
     * @param updatedAt 更新时间
     * @param pageable  分页参数
     * @return 分页结果
     */
    Page<AisTrackData> findByUpdatedAt(LocalDateTime updatedAt, Pageable pageable);

    /**
     * 按更新时间范围查询
     *
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @param pageable  分页参数
     * @return 分页结果
     */
    Page<AisTrackData> findByUpdatedAtBetween(LocalDateTime startTime, LocalDateTime endTime, Pageable pageable);
}
