package com.globaleyes.crawler.repository.acled;

import com.globaleyes.crawler.pojo.dto.acled.AcledBreakdownItemDTO;
import com.globaleyes.crawler.pojo.dto.acled.AcledRegionMetricDTO;
import com.globaleyes.crawler.pojo.dto.acled.AcledUnmatchedRegionDTO;
import com.globaleyes.crawler.pojo.entity.acled.AcledAggregatedRaw;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

@Repository
public interface AcledAggregatedRawRepository extends JpaRepository<AcledAggregatedRaw, Long> {

    @Modifying
    void deleteByRegionAndWeekStartIn(String region, Collection<LocalDate> weekStarts);

    List<AcledAggregatedRaw> findByCountry(String country);

    @Query("""
            select new com.globaleyes.crawler.pojo.dto.acled.AcledRegionMetricDTO(
                r.weekStart,
                r.country,
                r.admin1,
                r.boundaryId,
                coalesce(sum(r.events), 0),
                coalesce(sum(r.fatalities), 0),
                coalesce(max(r.populationExposure), 0),
                max(r.centroidLatitude),
                max(r.centroidLongitude)
            )
            from AcledAggregatedRaw r
            where r.weekStart = :week
              and r.boundaryId is not null
              and (:country is null or r.country = :country)
              and (:eventType is null or r.eventType = :eventType)
              and (:subEventType is null or r.subEventType = :subEventType)
              and (:disorderType is null or r.disorderType = :disorderType)
            group by r.weekStart, r.country, r.admin1, r.boundaryId
            order by r.country, r.admin1
            """)
    List<AcledRegionMetricDTO> aggregateMapByWeek(@Param("week") LocalDate week,
                                                  @Param("country") String country,
                                                  @Param("eventType") String eventType,
                                                  @Param("subEventType") String subEventType,
                                                  @Param("disorderType") String disorderType);

    @Query("""
            select new  com.globaleyes.crawler.pojo.dto.acled.AcledRegionMetricDTO(
                r.weekStart,
                r.country,
                r.admin1,
                r.boundaryId,
                coalesce(sum(r.events), 0),
                coalesce(sum(r.fatalities), 0),
                coalesce(max(r.populationExposure), 0),
                max(r.centroidLatitude),
                max(r.centroidLongitude)
            )
            from AcledAggregatedRaw r
            where r.boundaryId is not null
              and r.country = :country
              and r.admin1 = :admin1
              and (:eventType is null or r.eventType = :eventType)
              and (:subEventType is null or r.subEventType = :subEventType)
              and (:disorderType is null or r.disorderType = :disorderType)
            group by r.weekStart, r.country, r.admin1, r.boundaryId
            order by r.weekStart asc
            """)
    List<AcledRegionMetricDTO> aggregateTrend(@Param("country") String country,
                                              @Param("admin1") String admin1,
                                              @Param("eventType") String eventType,
                                              @Param("subEventType") String subEventType,
                                              @Param("disorderType") String disorderType);

    @Query("""
            select new  com.globaleyes.crawler.pojo.dto.acled.AcledRegionMetricDTO(
                r.weekStart,
                r.country,
                r.admin1,
                r.boundaryId,
                coalesce(sum(r.events), 0),
                coalesce(sum(r.fatalities), 0),
                coalesce(max(r.populationExposure), 0),
                max(r.centroidLatitude),
                max(r.centroidLongitude)
            )
            from AcledAggregatedRaw r
            where r.weekStart = :week
              and r.country = :country
              and r.admin1 = :admin1
              and (:eventType is null or r.eventType = :eventType)
              and (:subEventType is null or r.subEventType = :subEventType)
              and (:disorderType is null or r.disorderType = :disorderType)
            group by r.weekStart, r.country, r.admin1, r.boundaryId
            """)
    List<AcledRegionMetricDTO> aggregateDetailSummary(@Param("week") LocalDate week,
                                                      @Param("country") String country,
                                                      @Param("admin1") String admin1,
                                                      @Param("eventType") String eventType,
                                                      @Param("subEventType") String subEventType,
                                                      @Param("disorderType") String disorderType);

    @Query("""
            select new  com.globaleyes.crawler.pojo.dto.acled.AcledBreakdownItemDTO(
                r.eventType,
                coalesce(sum(r.events), 0),
                coalesce(sum(r.fatalities), 0)
            )
            from AcledAggregatedRaw r
            where r.weekStart = :week
              and r.country = :country
              and r.admin1 = :admin1
              and (:eventType is null or r.eventType = :eventType)
              and (:subEventType is null or r.subEventType = :subEventType)
              and (:disorderType is null or r.disorderType = :disorderType)
            group by r.eventType
            order by coalesce(sum(r.events), 0) desc
            """)
    List<AcledBreakdownItemDTO> breakdownByEventType(@Param("week") LocalDate week,
                                                     @Param("country") String country,
                                                     @Param("admin1") String admin1,
                                                     @Param("eventType") String eventType,
                                                     @Param("subEventType") String subEventType,
                                                     @Param("disorderType") String disorderType);

    @Query("""
            select new  com.globaleyes.crawler.pojo.dto.acled.AcledBreakdownItemDTO(
                r.subEventType,
                coalesce(sum(r.events), 0),
                coalesce(sum(r.fatalities), 0)
            )
            from AcledAggregatedRaw r
            where r.weekStart = :week
              and r.country = :country
              and r.admin1 = :admin1
              and (:eventType is null or r.eventType = :eventType)
              and (:subEventType is null or r.subEventType = :subEventType)
              and (:disorderType is null or r.disorderType = :disorderType)
            group by r.subEventType
            order by coalesce(sum(r.events), 0) desc
            """)
    List<AcledBreakdownItemDTO> breakdownBySubEventType(@Param("week") LocalDate week,
                                                        @Param("country") String country,
                                                        @Param("admin1") String admin1,
                                                        @Param("eventType") String eventType,
                                                        @Param("subEventType") String subEventType,
                                                        @Param("disorderType") String disorderType);

    @Query("""
            select new com.globaleyes.crawler.pojo.dto.acled.AcledBreakdownItemDTO(
                r.disorderType,
                coalesce(sum(r.events), 0),
                coalesce(sum(r.fatalities), 0)
            )
            from AcledAggregatedRaw r
            where r.weekStart = :week
              and r.country = :country
              and r.admin1 = :admin1
              and (:eventType is null or r.eventType = :eventType)
              and (:subEventType is null or r.subEventType = :subEventType)
              and (:disorderType is null or r.disorderType = :disorderType)
            group by r.disorderType
            order by coalesce(sum(r.events), 0) desc
            """)
    List<AcledBreakdownItemDTO> breakdownByDisorderType(@Param("week") LocalDate week,
                                                        @Param("country") String country,
                                                        @Param("admin1") String admin1,
                                                        @Param("eventType") String eventType,
                                                        @Param("subEventType") String subEventType,
                                                        @Param("disorderType") String disorderType);

    @Query("""
            select distinct r.country
            from AcledAggregatedRaw r
            where r.region = 'Middle East'
            order by r.country
            """)
    List<String> findDistinctCountries();

    @Query("""
            select distinct r.eventType
            from AcledAggregatedRaw r
            where r.eventType is not null and r.eventType <> ''
            order by r.eventType
            """)
    List<String> findDistinctEventTypes();

    @Query("""
            select distinct r.subEventType
            from AcledAggregatedRaw r
            where r.subEventType is not null and r.subEventType <> ''
            order by r.subEventType
            """)
    List<String> findDistinctSubEventTypes();

    @Query("""
            select distinct r.disorderType
            from AcledAggregatedRaw r
            where r.disorderType is not null and r.disorderType <> ''
            order by r.disorderType
            """)
    List<String> findDistinctDisorderTypes();

    @Query("""
            select distinct r.weekStart
            from AcledAggregatedRaw r
            where r.region = 'Middle East'
            order by r.weekStart desc
            """)
    List<LocalDate> findDistinctWeeksDesc();

    @Query("""
            select new com.globaleyes.crawler.pojo.dto.acled.AcledUnmatchedRegionDTO(
                r.country,
                r.admin1,
                count(r.id),
                min(r.weekStart),
                max(r.weekStart)
            )
            from AcledAggregatedRaw r
            where r.boundaryId is null
            group by r.country, r.admin1
            order by count(r.id) desc, r.country asc, r.admin1 asc
            """)
    List<AcledUnmatchedRegionDTO> findUnmatchedRegions();
}
