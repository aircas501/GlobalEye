package com.globaleyes.crawler.pojo.entity.opensky;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 飞机轨迹点实体
 * 存储OpenSky API获取的飞机状态数据，每个状态点即为一个轨迹点
 * 
 * @author RSS News Crawler Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "aircraft_track", indexes = {
    @Index(name = "idx_icao24_time", columnList = "icao24, time"),
    @Index(name = "idx_callsign", columnList = "callsign"),
    @Index(name = "idx_time", columnList = "time"),
    @Index(name = "idx_origin_country", columnList = "originCountry")
})
public class AircraftTrack {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * ICAO 24位地址，飞机唯一标识符
     * 格式：6位十六进制字符串，如 "3c6b42"
     */
    @Column(name = "icao24", length = 6, nullable = false)
    private String icao24;

    /**
     * 飞机呼号
     * 格式：最多8位字符，如 "CPA121"
     */
    @Column(name = "callsign", length = 8)
    private String callsign;

    /**
     * 飞机注册国
     */
    @Column(name = "origin_country", length = 100)
    private String originCountry;

    /**
     * 位置更新时间戳（Unix时间戳，秒）
     */
    @Column(name = "time_position")
    private Long timePosition;

    /**
     * 最后联系时间戳（Unix时间戳，秒）
     */
    @Column(name = "last_contact")
    private Long lastContact;

    /**
     * 经度
     * 范围：-180到180
     */
    @Column(name = "longitude")
    private Double longitude;

    /**
     * 纬度
     * 范围：-90到90
     */
    @Column(name = "latitude")
    private Double latitude;

    /**
     * 气压高度（米）
     */
    @Column(name = "baro_altitude")
    private Double baroAltitude;

    /**
     * 是否在地面
     */
    @Column(name = "on_ground")
    private Boolean onGround;

    /**
     * 地速（米/秒）
     */
    @Column(name = "velocity")
    private Double velocity;

    /**
     * 真航向（度）
     * 范围：0到360
     */
    @Column(name = "true_track")
    private Double trueTrack;

    /**
     * 垂直速率（米/秒）
     */
    @Column(name = "vertical_rate")
    private Double verticalRate;

    /**
     * 几何高度（米）
     */
    @Column(name = "geo_altitude")
    private Double geoAltitude;

    /**
     * 应答机代码
     * 格式：4位八进制字符串，如 "7000"
     */
    @Column(name = "squawk", length = 4)
    private String squawk;

    /**
     * 特殊目的标识
     */
    @Column(name = "spi")
    private Boolean spi;

    /**
     * 位置来源
     * 0=未定义, 1=ADS-B, 2=ASTERIX, 3=MLAT, 4=FLARM
     */
    @Column(name = "position_source")
    private Integer positionSource;

    /**
     * 数据同步时间戳（Unix时间戳，秒）
     * 对应OpenSky API返回的time字段
     */
    @Column(name = "time", nullable = false)
    private Long time;

    /**
     * 记录创建时间
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "category")
    private Integer category;
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
