package com.globaleyes.crawler.pojo.entity;
import com.globaleyes.crawler.util.StarSpotCalcUtil;
import com.globaleyes.crawler.pojo.vo.SpaceTrackOmmData;
import com.globaleyes.crawler.pojo.vo.StarSpot;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * CCSDS OMM 轨道数据实体类
 * 对应Space-Track.org API返回的JSON结构
 */
@Data
@Slf4j
@Entity
@Table(name = "star_tle_data")
@NoArgsConstructor
public class SpaceTrackTleData {
    private static final DateTimeFormatter epochTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS");
    private static final DateTimeFormatter baseTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    public SpaceTrackTleData(SpaceTrackOmmData spaceTrackOmmData) {
        this.creationDate = LocalDateTime.from(baseTimeFormatter.parse(spaceTrackOmmData.getCreationDate()));
        this.objectName = spaceTrackOmmData.getObjectName();
        this.objectId = spaceTrackOmmData.getObjectId();
        this.centerName = spaceTrackOmmData.getCenterName();
        this.refFrame = spaceTrackOmmData.getRefFrame();
        this.timeSystem = spaceTrackOmmData.getTimeSystem();
        this.epoch = LocalDateTime.from(epochTimeFormatter.parse(spaceTrackOmmData.getEpoch()));
        this.ephemerisType = spaceTrackOmmData.getEphemerisType();
        this.classificationType = spaceTrackOmmData.getClassificationType();
        this.countryCode = spaceTrackOmmData.getCountryCode();
        this.tleLine0 = spaceTrackOmmData.getTleLine0();
        this.tleLine1 = spaceTrackOmmData.getTleLine1();
        this.tleLine2 = spaceTrackOmmData.getTleLine2();
        this.objectType = spaceTrackOmmData.getObjectType();
        try {
            StarSpot calc = StarSpotCalcUtil.calculateSatellitePosition(this.epoch, this.tleLine1, this.tleLine2);
            this.startSpotLat = calc.getLatitude();
            this.startSpotLon = calc.getLongitude();
        } catch (RuntimeException e) {
            log.info("卫星{}的星下点获取失败，失败原因:{}", this.objectName, e.getMessage());
        }

    }
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 创建时间
     */
    private LocalDateTime creationDate;

    /**
     * 目标名称
     */
    private String objectName;

    /**
     * 目标ID
     */
    private String objectId;

    /**
     * 中心天体
     */
    @JsonProperty("CENTER_NAME")
    private String centerName;

    /**
     * 参考坐标系
     */
    @JsonProperty("REF_FRAME")
    private String refFrame;

    /**
     * 时间系统
     */
    @JsonProperty("TIME_SYSTEM")
    private String timeSystem;


    /**
     * 历元时间
     */
    @JsonProperty("EPOCH")
    private LocalDateTime epoch;

    /**
     * 星历类型
     */
    @JsonProperty("EPHEMERIS_TYPE")
    private String ephemerisType;

    /**
     * 分类类型
     */
    @JsonProperty("CLASSIFICATION_TYPE")
    private String classificationType;

    /**
     * 国家代码
     */
    @JsonProperty("COUNTRY_CODE")
    private String countryCode;

    /**
     * TLE第一行标题
     */
    @JsonProperty("TLE_LINE0")
    private String tleLine0;

    /**
     * TLE第一行数据
     */
    @JsonProperty("TLE_LINE1")
    private String tleLine1;

    /**
     * TLE第二行数据
     */
    @JsonProperty("TLE_LINE2")
    private String tleLine2;

    /**
     * 目标类型 PAYLOAD为有效载荷，即在轨卫星
     */
    @JsonProperty("OBJECT_TYPE")
    private String objectType;

    /**
     * 星下点经度
     */
    private Double startSpotLat;

    /**
     * 星下点纬度
     */
    private Double startSpotLon;

}