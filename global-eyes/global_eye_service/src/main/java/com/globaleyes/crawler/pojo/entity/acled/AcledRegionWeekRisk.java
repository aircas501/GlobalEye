package com.globaleyes.crawler.pojo.entity.acled;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "acled_region_week_risk", indexes = {
        @Index(name = "idx_acled_risk_week", columnList = "week_start"),
        @Index(name = "idx_acled_risk_country_admin1", columnList = "country,admin1"),
        @Index(name = "idx_acled_risk_boundary", columnList = "boundary_id")
})
public class AcledRegionWeekRisk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    @Column(name = "country", nullable = false, length = 100)
    private String country;

    @Column(name = "admin1", nullable = false, length = 150)
    private String admin1;

    @Column(name = "boundary_id", nullable = false)
    private Long boundaryId;

    @Column(name = "events_total")
    private Long eventsTotal;

    @Column(name = "fatalities_total")
    private Long fatalitiesTotal;

    @Column(name = "population_exposure_max")
    private Long populationExposureMax;

    @Column(name = "risk_score")
    private Double riskScore;

    @Column(name = "events_score")
    private Double eventsScore;

    @Column(name = "fatalities_score")
    private Double fatalitiesScore;

    @Column(name = "risk_wow_delta")
    private Double riskWowDelta;

    @Column(name = "events_wow_delta")
    private Double eventsWowDelta;

    @Column(name = "fatalities_wow_delta")
    private Double fatalitiesWowDelta;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
