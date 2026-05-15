package com.globaleyes.crawler.repository.aircraft;

import com.globaleyes.crawler.pojo.entity.AircraftBasicInfo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 飞机基础信息Repository
 *
 * @author Aircraft Info Team
 * @version 1.0.0
 */
@Repository
public interface AircraftBasicInfoRepository extends JpaRepository<AircraftBasicInfo, Long> {

    /**
     * 根据icao24查询
     */
    Optional<AircraftBasicInfo> findByIcao24(String icao24);



    /**
     * 模糊查询
     */
    @Query("SELECT a FROM AircraftBasicInfo a WHERE " +
           "(:icao24 IS NULL OR a.icao24 LIKE %:icao24%) AND " +
           "(:country IS NULL OR a.country LIKE %:country%) AND " +
           "(:model IS NULL OR a.model LIKE %:model%) AND " +
           "(:typeCode IS NULL OR a.typeCode LIKE %:typeCode%)")
    Page<AircraftBasicInfo> search(@Param("icao24") String icao24,
                                   @Param("country") String country,
                                   @Param("model") String model,
                                   @Param("typeCode") String typeCode,
                                   Pageable pageable);

    /**
     * 根据icao24查询model
     */
    @Query("SELECT a.model FROM AircraftBasicInfo a WHERE a.icao24 = :icao24")
    Optional<String> findModelByIcao24(@Param("icao24") String icao24);
}
