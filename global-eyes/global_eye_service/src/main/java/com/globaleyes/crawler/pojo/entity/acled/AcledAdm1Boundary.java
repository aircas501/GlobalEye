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

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "acled_adm1_boundary", indexes = {
        @Index(name = "idx_acled_boundary_country", columnList = "country"),
        @Index(name = "idx_acled_boundary_norm", columnList = "country,normalized_key")
})
public class AcledAdm1Boundary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "country", nullable = false, length = 100)
    private String country;

    @Column(name = "boundary_source", length = 100)
    private String boundarySource;

    @Column(name = "boundary_adm1_name", nullable = false, length = 160)
    private String boundaryAdm1Name;

    @Column(name = "boundary_adm1_code", length = 120)
    private String boundaryAdm1Code;

    @Column(name = "normalized_key", nullable = false, length = 180)
    private String normalizedKey;

    @Column(name = "geometry_geojson", columnDefinition = "LONGTEXT")
    private String geometryGeojson;

    @Column(name = "bbox_json", length = 255)
    private String bboxJson;

    @Column(name = "centroid_latitude")
    private Double centroidLatitude;

    @Column(name = "centroid_longitude")
    private Double centroidLongitude;

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
