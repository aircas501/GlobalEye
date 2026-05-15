package com.globaleyes.crawler.repository.aircraft;

import com.globaleyes.crawler.pojo.entity.opensky.AircraftTrack;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 飞机轨迹Repository
 * 
 * @author RSS News Crawler Team
 * @version 1.0.0
 */
@Repository
public interface AircraftTrackRepository extends JpaRepository<AircraftTrack, Long> {

    /**
     * 根据ICAO24地址查询飞机轨迹，按时间升序排列
     *
     * @param icao24 ICAO 24位地址
     * @return 轨迹点列表
     */
    @Query("SELECT t FROM AircraftTrack t WHERE t.icao24 = :icao24 ORDER BY t.time ASC")
    List<AircraftTrack> findByIcao24OrderByTime(@Param("icao24") String icao24);

    /**
     * 根据ICAO24地址和时间范围查询飞机轨迹
     *
     * @param icao24    ICAO 24位地址
     * @param startTime 开始时间（Unix时间戳，秒）
     * @param endTime   结束时间（Unix时间戳，秒）
     * @return 轨迹点列表，按时间升序排列
     */
    @Query("SELECT t FROM AircraftTrack t WHERE t.icao24 = :icao24 " +
           "AND t.time >= :startTime AND t.time <= :endTime " +
           "ORDER BY t.time ASC")
    List<AircraftTrack> findByIcao24AndTimeRange(
        @Param("icao24") String icao24,
        @Param("startTime") Long startTime,
        @Param("endTime") Long endTime
    );

    /**
     * 根据呼号查询飞机轨迹，按时间升序排列
     *
     * @param callsign 飞机呼号
     * @return 轨迹点列表
     */
    @Query("SELECT t FROM AircraftTrack t WHERE t.callsign = :callsign ORDER BY t.time ASC")
    List<AircraftTrack> findByCallsignOrderByTime(@Param("callsign") String callsign);

    /**
     * 根据呼号和时间范围查询飞机轨迹
     *
     * @param callsign  飞机呼号
     * @param startTime 开始时间（Unix时间戳，秒）
     * @param endTime   结束时间（Unix时间戳，秒）
     * @return 轨迹点列表，按时间升序排列
     */
    @Query("SELECT t FROM AircraftTrack t WHERE t.callsign = :callsign " +
           "AND t.time >= :startTime AND t.time <= :endTime " +
           "ORDER BY t.time ASC")
    List<AircraftTrack> findByCallsignAndTimeRange(
        @Param("callsign") String callsign,
        @Param("startTime") Long startTime,
        @Param("endTime") Long endTime
    );


    /**
     * 删除指定时间之前的轨迹数据
     *
     * @param time 时间阈值（Unix时间戳，秒）
     */
    void deleteByTimeBefore(Long time);
}
