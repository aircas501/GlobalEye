package com.globaleyes.crawler.repository.acled;

import com.globaleyes.crawler.pojo.entity.acled.AcledRegionWeekRisk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface AcledRegionWeekRiskRepository extends JpaRepository<AcledRegionWeekRisk, Long> {

    @Query("""
            select coalesce(max(r.eventsTotal), 0), coalesce(max(r.fatalitiesTotal), 0)
            from AcledRegionWeekRisk r
            """)
    Object[] findGlobalMaxima();
}
