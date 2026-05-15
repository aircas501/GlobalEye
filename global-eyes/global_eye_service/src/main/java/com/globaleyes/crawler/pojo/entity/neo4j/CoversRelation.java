package com.globaleyes.crawler.pojo.entity.neo4j;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.data.neo4j.core.schema.*;

import java.time.LocalDateTime;

@Data
@RelationshipProperties
public class CoversRelation {

    @Id
    @GeneratedValue
    private Long id;

    @Property
    private String relevance;

    @Property
    private LocalDateTime createdAt;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @TargetNode
    private HotEventNode event;

    public CoversRelation() {
        this.createdAt = LocalDateTime.now();
    }

    public CoversRelation(HotEventNode event) {
        this.event = event;
        this.createdAt = LocalDateTime.now();
    }
}
