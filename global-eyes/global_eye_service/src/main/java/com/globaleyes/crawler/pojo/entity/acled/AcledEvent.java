package com.globaleyes.crawler.pojo.entity.acled;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * ACLED事件实体类
 *
 * @author ACLED Integration Team
 * @version 1.0.0
 */
@Data
@Entity
@Table(name = "acled_event", indexes = {
    @Index(name = "idx_acled_event_id_cnty", columnList = "event_id_cnty", unique = true),
    @Index(name = "idx_acled_event_date", columnList = "event_date"),
    @Index(name = "idx_acled_country", columnList = "country"),
    @Index(name = "idx_acled_event_type", columnList = "event_type")
})
public class AcledEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonIgnore
    private Long id;

    @JsonProperty("event_id_cnty")
    @Column(name = "event_id_cnty", nullable = false, unique = true, length = 50)
    private String eventIdCnty;

    @JsonProperty("event_date")
    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    @JsonProperty("year")
    @Column(name = "year")
    private Integer year;

    @JsonProperty("time_precision")
    @Column(name = "time_precision")
    private Integer timePrecision;

    @JsonProperty("event_type")
    @Column(name = "event_type", length = 100)
    private String eventType;

    @JsonProperty("sub_event_type")
    @Column(name = "sub_event_type", length = 100)
    private String subEventType;

    @JsonProperty("actor1")
    @Column(name = "actor1", length = 200)
    private String actor1;

    @JsonProperty("actor2")
    @Column(name = "actor2", length = 200)
    private String actor2;

    @JsonProperty("inter1")
    @Column(name = "inter1")
    private Integer inter1;

    @JsonProperty("inter2")
    @Column(name = "inter2")
    private Integer inter2;

    @JsonProperty("interaction")
    @Column(name = "interaction")
    private Integer interaction;

    @JsonProperty("country")
    @Column(name = "country", length = 100)
    private String country;

    @JsonProperty("iso")
    @Column(name = "iso")
    private Integer iso;

    @JsonProperty("region")
    @Column(name = "region", length = 100)
    private String region;

    @JsonProperty("admin1")
    @Column(name = "admin1", length = 100)
    private String admin1;

    @JsonProperty("admin2")
    @Column(name = "admin2", length = 100)
    private String admin2;

    @JsonProperty("admin3")
    @Column(name = "admin3", length = 100)
    private String admin3;

    @JsonProperty("location")
    @Column(name = "location", length = 200)
    private String location;

    @JsonProperty("latitude")
    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    @JsonProperty("longitude")
    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;

    @JsonProperty("geo_precision")
    @Column(name = "geo_precision")
    private Integer geoPrecision;

    @JsonProperty("source")
    @Column(name = "source", length = 500)
    private String source;

    @JsonProperty("source_scale")
    @Column(name = "source_scale", length = 50)
    private String sourceScale;

    @JsonProperty("notes")
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @JsonProperty("fatalities")
    @Column(name = "fatalities")
    private Integer fatalities;

    @JsonProperty("timestamp")
    @Column(name = "timestamp")
    private Long timestamp;

    @JsonIgnore
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @JsonIgnore
    @Column(name = "updated_at")
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
