package com.globaleyes.crawler.pojo.entity.neo4j;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.data.neo4j.core.schema.*;

import java.time.LocalDateTime;

@Data
@RelationshipProperties
public class LocatedInRelation {

    @Id
    @GeneratedValue
    private Long id;

    @Property
    private Boolean isPrimary;

    @Property
    private LocalDateTime createdAt;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @TargetNode
    private LocationNode location;

    public LocatedInRelation() {
        this.createdAt = LocalDateTime.now();
    }

    public LocatedInRelation(LocationNode location) {
        this.location = location;
        this.createdAt = LocalDateTime.now();
    }
}
