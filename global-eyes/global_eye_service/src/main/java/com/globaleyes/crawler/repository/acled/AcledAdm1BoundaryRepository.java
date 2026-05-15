package com.globaleyes.crawler.repository.acled;

import com.globaleyes.crawler.pojo.entity.acled.AcledAdm1Boundary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AcledAdm1BoundaryRepository extends JpaRepository<AcledAdm1Boundary, Long> {
    Optional<AcledAdm1Boundary> findFirstByCountryAndNormalizedKey(String country, String normalizedKey);

    @Modifying
    void deleteByCountry(String country);
}
