package com.globaleyes.crawler.repository.satellite;

import com.globaleyes.crawler.pojo.entity.SpaceTrackTleData;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SpaceTrack TLE 数据 Repository 接口
 */
@Repository
public interface SpaceTrackTleDataRepository extends JpaRepository<SpaceTrackTleData, Long> {

    @Query(value = "SELECT t1.* FROM star_tle_data t1 INNER JOIN (SELECT object_id, MAX(epoch) AS latest_epoch FROM star_tle_data GROUP BY object_id) t2 ON t1.object_id = t2.object_id AND t1.epoch = t2.latest_epoch", countQuery = "SELECT COUNT(DISTINCT object_id) FROM star_tle_data", nativeQuery = true)
    Page<SpaceTrackTleData> listAllStarLatestData(Pageable pageable);

    @Query("SELECT s FROM SpaceTrackTleData s WHERE s.epoch BETWEEN :startTime AND :endTime ORDER BY s.epoch DESC")
    Page<SpaceTrackTleData> findByEpochBetween(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime, Pageable pageable);

    @Query("SELECT s FROM SpaceTrackTleData s WHERE s.epoch = :epoch ORDER BY s.objectName ASC")
    List<SpaceTrackTleData> findByEpoch(@Param("epoch") LocalDateTime epoch);
}
