package com.globaleyes.crawler.pojo.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 卫星目录数据实体类
 * 对应 Space-Track.org API 返回的 JSON 结构
 */
@Data
@Entity
@Table(name = "satellite_catalog_data")
@NoArgsConstructor
public class SatelliteCatalogData {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 国际标识符
     */
    @JsonProperty("INTLDES")
    @Column(name = "intldes", length = 20)
    private String intldes;

    /**
     * NORAD 目录 ID
     */
    @JsonProperty("NORAD_CAT_ID")
    @Column(name = "norad_cat_id")
    private Integer noradCatId;

    /**
     * 目标类型
     */
    @JsonProperty("OBJECT_TYPE")
    @Column(name = "object_type", length = 50)
    private String objectType;

    /**
     * 卫星名称
     */
    @JsonProperty("SATNAME")
    @Column(name = "satname", length = 100)
    private String satname;

    /**
     * 国家
     */
    @JsonProperty("COUNTRY")
    @Column(name = "country", length = 50)
    private String country;

    /**
     * 发射日期
     */
    @JsonProperty("LAUNCH")
    @Column(name = "launch")
    private String launch;

    /**
     * 发射地点
     */
    @JsonProperty("SITE")
    @Column(name = "site", length = 20)
    private String site;

    /**
     * 衰变日期
     */
    @JsonProperty("DECAY")
    @Column(name = "decay")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private String decay;

    /**
     * 轨道周期（分钟）
     */
    @JsonProperty("PERIOD")
    @Column(name = "period")
    private Double period;

    /**
     * 轨道倾角（度）
     */
    @JsonProperty("INCLINATION")
    @Column(name = "inclination")
    private Double inclination;

    /**
     * 远地点高度（公里）
     */
    @JsonProperty("APOGEE")
    @Column(name = "apogee")
    private Integer apogee;

    /**
     * 近地点高度（公里）
     */
    @JsonProperty("PERIGEE")
    @Column(name = "perigee")
    private Integer perigee;

    /**
     * 备注
     */
    @JsonProperty("COMMENT")
    @Column(name = "comment", length = 500)
    private String comment;

    /**
     * 备注代码
     */
    @JsonProperty("COMMENTCODE")
    @Column(name = "commentcode", length = 10)
    private String commentcode;

    /**
     * RCS 值
     */
    @JsonProperty("RCSVALUE")
    @Column(name = "rcsvalue")
    private Integer rcsvalue;

    /**
     * RCS 大小
     */
    @JsonProperty("RCS_SIZE")
    @Column(name = "rcs_size", length = 20)
    private String rcsSize;

    /**
     * 文件号
     */
    @JsonProperty("FILE")
    @Column(name = "file", length = 10)
    private String file;

    /**
     * 发射年份
     */
    @JsonProperty("LAUNCH_YEAR")
    @Column(name = "launch_year", length = 10)
    private String launchYear;

    /**
     * 发射编号
     */
    @JsonProperty("LAUNCH_NUM")
    @Column(name = "launch_num", length = 10)
    private String launchNum;

    /**
     * 发射碎片
     */
    @JsonProperty("LAUNCH_PIECE")
    @Column(name = "launch_piece", length = 10)
    private String launchPiece;

    /**
     * 当前状态
     */
    @JsonProperty("CURRENT")
    @Column(name = "current", length = 1)
    private String current;

    /**
     * 目标名称
     */
    @JsonProperty("OBJECT_NAME")
    @Column(name = "object_name", length = 100)
    private String objectName;

    /**
     * 目标 ID
     */
    @JsonProperty("OBJECT_ID")
    @Column(name = "object_id", length = 20)
    private String objectId;

    /**
     * 目标编号
     */
    @JsonProperty("OBJECT_NUMBER")
    @Column(name = "object_number", length = 20)
    private String objectNumber;
}
