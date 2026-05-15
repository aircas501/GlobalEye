package com.globaleyes.crawler.pojo.entity;

import jakarta.persistence.*;
import lombok.Data;

/**
 * 飞机基础信息实体类
 * 存储飞机的基本识别信息，来自OpenSky Network数据
 *
 * @author Aircraft Info Team
 * @version 1.0.0
 */
@Data
@Entity
@Table(name = "aricraft_basic_info", indexes = {
    @Index(name = "idx_icao24", columnList = "icao24", unique = true),
    @Index(name = "idx_country", columnList = "country"),
    @Index(name = "idx_model", columnList = "model"),
    @Index(name = "idx_type_code", columnList = "type_code")
})
public class AircraftBasicInfo {

    /**
     * 主键ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * ICAO 24位地址码（飞机唯一标识符）
     */
    @Column(name = "icao24", nullable = false, length = 100, unique = true)
    private String icao24;

    /**
     * 航空器类别描述
     */
    @Column(name = "category_description", length = 256)
    private String categoryDescription;

    /**
     * 注册国家
     */
    @Column(name = "country", length = 100)
    private String country;

    /**
     * ICAO航空器分类
     */
    @Column(name = "icao_aircraft_class", length = 100)
    private String icaoAircraftClass;

    /**
     * 飞机型号
     */
    @Column(name = "model", length = 100)
    private String model;

    /**
     * 注册号（尾号）
     */
    @Column(name = "registration", length = 100)
    private String registration;

    /**
     * 机型代码
     */
    @Column(name = "type_code", length = 100)
    private String typeCode;
}
