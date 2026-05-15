package com.globaleyes.crawler.pojo.entity.acled;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "acled_agg_raw", indexes = {
        @Index(name = "idx_acled_agg_week", columnList = "week_start"),
        @Index(name = "idx_acled_agg_country_admin1", columnList = "country,admin1"),
        @Index(name = "idx_acled_agg_boundary", columnList = "boundary_id"),
        @Index(name = "idx_acled_agg_filters", columnList = "event_type,sub_event_type,disorder_type")
})
public class AcledAggregatedRaw {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_id", nullable = false, length = 64)
    private String batchId;

    @Column(name = "source_file_name", length = 255)
    private String sourceFileName;

    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    @Column(name = "region", length = 100)
    private String region;

    @Column(name = "country", nullable = false, length = 100)
    private String country;

    @Column(name = "admin1", nullable = false, length = 150)
    private String admin1;

    @Column(name = "normalized_admin1", length = 160)
    private String normalizedAdmin1;

    @Column(name = "event_type", length = 100)
    private String eventType;

    @Column(name = "sub_event_type", length = 100)
    private String subEventType;

    @Column(name = "disorder_type", length = 100)
    private String disorderType;

    @Column(name = "events")
    private Integer events;

    @Column(name = "fatalities")
    private Integer fatalities;

    @Column(name = "population_exposure")
    private Long populationExposure;

    @Column(name = "source_location_id", length = 100)
    private String sourceLocationId;

    @Column(name = "centroid_latitude")
    private Double centroidLatitude;

    @Column(name = "centroid_longitude")
    private Double centroidLongitude;

    @Column(name = "boundary_id")
    private Long boundaryId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
