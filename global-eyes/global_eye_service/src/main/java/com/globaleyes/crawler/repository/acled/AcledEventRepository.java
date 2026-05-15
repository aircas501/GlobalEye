package com.globaleyes.crawler.repository.acled;

import com.globaleyes.crawler.pojo.entity.acled.AcledEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * ACLED事件Repository
 *
 * @author ACLED Integration Team
 * @version 1.0.0
 */
@Repository
public interface AcledEventRepository extends JpaRepository<AcledEvent, Long> {


    List<AcledEvent> findByEventIdCntyIn(Set<String> eventIdCntySet);

    @Query("SELECT e.eventIdCnty FROM AcledEvent e WHERE e.eventIdCnty IN :eventIds")
    Set<String> findExistingIdsByEventIdCntyIn(@Param("eventIds") List<String> eventIds);

    void deleteByEventIdCntyIn(List<String> eventIdCntyList);

    @Query("SELECT e FROM AcledEvent e WHERE " +
           "(:country IS NULL OR e.country = :country) AND " +
           "(:eventType IS NULL OR e.eventType = :eventType) AND " +
           "(:startDate IS NULL OR e.eventDate >= :startDate) AND " +
           "(:endDate IS NULL OR e.eventDate <= :endDate)")
    Page<AcledEvent> search(@Param("country") String country,
                            @Param("eventType") String eventType,
                            @Param("startDate") LocalDate startDate,
                            @Param("endDate") LocalDate endDate,
                            Pageable pageable);

}
