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
@Table(name = "acled_adm1_alias", indexes = {
        @Index(name = "idx_acled_alias_country", columnList = "country"),
        @Index(name = "idx_acled_alias_norm", columnList = "country,normalized_alias")
})
public class AcledAdm1Alias {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "country", nullable = false, length = 100)
    private String country;

    @Column(name = "alias_name", nullable = false, length = 160)
    private String aliasName;

    @Column(name = "normalized_alias", nullable = false, length = 180)
    private String normalizedAlias;

    @Column(name = "boundary_id", nullable = false)
    private Long boundaryId;

    @Column(name = "match_type", length = 50)
    private String matchType;

    @Column(name = "remark", length = 255)
    private String remark;

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
