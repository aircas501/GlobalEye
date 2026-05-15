package com.globaleyes.crawler.repository.acled;

import com.globaleyes.crawler.pojo.entity.acled.AcledAdm1Alias;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AcledAdm1AliasRepository extends JpaRepository<AcledAdm1Alias, Long> {
    Optional<AcledAdm1Alias> findByCountryAndNormalizedAlias(String country, String normalizedAlias);
}
