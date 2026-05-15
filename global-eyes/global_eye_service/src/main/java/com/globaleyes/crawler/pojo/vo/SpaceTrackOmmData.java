package com.globaleyes.crawler.pojo.vo;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * CCSDS OMM 轨道数据实体类
 * 对应Space-Track.org API返回的JSON结构
 */
@Data
public class SpaceTrackOmmData {

    /**
     * OMM版本号
     */
    @JsonProperty("CCSDS_OMM_VERS")
    private String ccsdsOmmVers;

    /**
     * 注释信息
     */
    @JsonProperty("COMMENT")
    private String comment;

    /**
     * 创建时间
     */
    @JsonProperty("CREATION_DATE")
    private String creationDate;

    /**
     * 数据来源
     */
    @JsonProperty("ORIGINATOR")
    private String originator;

    /**
     * 目标名称
     */
    @JsonProperty("OBJECT_NAME")
    private String objectName;

    /**
     * 目标ID
     */
    @JsonProperty("OBJECT_ID")
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
     * 平均轨道根数理论
     */
    @JsonProperty("MEAN_ELEMENT_THEORY")
    private String meanElementTheory;

    /**
     * 历元时间
     */
    @JsonProperty("EPOCH")
    private String epoch;

    /**
     * 平均运动
     */
    @JsonProperty("MEAN_MOTION")
    private String meanMotion;

    /**
     * 偏心率
     */
    @JsonProperty("ECCENTRICITY")
    private String eccentricity;

    /**
     * 轨道倾角
     */
    @JsonProperty("INCLINATION")
    private String inclination;

    /**
     * 升交点赤经
     */
    @JsonProperty("RA_OF_ASC_NODE")
    private String raOfAscNode;

    /**
     * 近心点幅角
     */
    @JsonProperty("ARG_OF_PERICENTER")
    private String argOfPericenter;

    /**
     * 平近点角
     */
    @JsonProperty("MEAN_ANOMALY")
    private String meanAnomaly;

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
     * NORAD目录ID
     */
    @JsonProperty("NORAD_CAT_ID")
    private String noradCatId;

    /**
     * 轨道根数编号
     */
    @JsonProperty("ELEMENT_SET_NO")
    private String elementSetNo;

    /**
     * 历元圈数
     */
    @JsonProperty("REV_AT_EPOCH")
    private String revAtEpoch;

    /**
     * BSTAR阻力系数
     */
    @JsonProperty("BSTAR")
    private String bstar;

    /**
     * 平均运动一阶导数
     */
    @JsonProperty("MEAN_MOTION_DOT")
    private String meanMotionDot;

    /**
     * 平均运动二阶导数
     */
    @JsonProperty("MEAN_MOTION_DDOT")
    private String meanMotionDdot;

    /**
     * 半长轴
     */
    @JsonProperty("SEMIMAJOR_AXIS")
    private String semimajorAxis;

    /**
     * 轨道周期
     */
    @JsonProperty("PERIOD")
    private String period;

    /**
     * 远地点
     */
    @JsonProperty("APOAPSIS")
    private String apoapsis;

    /**
     * 近地点
     */
    @JsonProperty("PERIAPSIS")
    private String periapsis;

    /**
     * 目标类型
     */
    @JsonProperty("OBJECT_TYPE")
    private String objectType;

    /**
     * 雷达散射截面大小
     */
    @JsonProperty("RCS_SIZE")
    private String rcsSize;

    /**
     * 国家代码
     */
    @JsonProperty("COUNTRY_CODE")
    private String countryCode;

    /**
     * 发射日期
     */
    @JsonProperty("LAUNCH_DATE")
    private String launchDate;

    /**
     * 发射地点
     */
    @JsonProperty("SITE")
    private String site;

    /**
     * 衰减日期（可为null）
     */
    @JsonProperty("DECAY_DATE")
    private String decayDate;

    /**
     * 文件编号
     */
    @JsonProperty("FILE")
    private String file;

    /**
     * GP编号
     */
    @JsonProperty("GP_ID")
    private String gpId;

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
}