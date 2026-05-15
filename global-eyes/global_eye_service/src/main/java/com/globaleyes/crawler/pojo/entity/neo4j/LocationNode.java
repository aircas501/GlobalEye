package com.globaleyes.crawler.pojo.entity.neo4j;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.data.neo4j.core.schema.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 地点节点
 * 存储地理位置信息（国家/区域/基地）
 *
 * @author RSS News Crawler Team
 * @version 1.0.0
 */
@Data
@Node("Location")
public class LocationNode {

    /**
     * Neo4j内部ID
     */
    @Id
    @GeneratedValue
    private Long id;

    /**
     * 地点名称
     */
    @Property
    private String name;

    /**
     * 地点类型（country/region/base等）
     */
    @Property
    private String type;

    /**
     * 经度
     */
    @Property
    private Double longitude;

    /**
     * 纬度
     */
    @Property
    private Double latitude;

    /**
     * 创建时间
     */
    @Property
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @Property
    private LocalDateTime updatedAt;

    /**
     * 发生于该地点的热点事件列表
     */
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Relationship(type = "OCCURS_IN", direction = Relationship.Direction.INCOMING)
    private List<OccursInRelation> hotEvents = new ArrayList<>();

    public LocationNode() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public LocationNode(String name, String type) {
        this.name = name;
        this.type = type;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public LocationNode(String name, String type, Double longitude, Double latitude) {
        this.name = name;
        this.type = type;
        this.longitude = longitude;
        this.latitude = latitude;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
}
