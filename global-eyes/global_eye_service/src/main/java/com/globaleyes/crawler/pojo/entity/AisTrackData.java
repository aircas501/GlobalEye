package com.globaleyes.crawler.pojo.entity;

import com.alibaba.excel.annotation.ExcelProperty;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AIS船舶追踪数据实体类
 * 存储船舶AIS广播数据，包括位置、航速、航向等信息
 *
 * @author AIS Data Team
 * @version 1.0.0
 */
@Data
@Entity
@Table(name = "ais_track_data", indexes = {
    @Index(name = "idx_ais_mmsi", columnList = "mmsi"),
    @Index(name = "idx_ais_name", columnList = "name_ais"),
    @Index(name = "idx_ais_navy_type", columnList = "navy_type"),
    @Index(name = "idx_ais_country", columnList = "country"),
    @Index(name = "idx_ais_created_at", columnList = "created_at")
})
public class AisTrackData {

    /**
     * 主键ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 海上移动服务标识（9位数字，船舶唯一识别码）
     */
    @ExcelProperty("mmsi")
    @Column(name = "mmsi", nullable = false, length = 100)
    private String mmsi;

    /**
     * 船舶名称（来自AIS广播的船名）
     */
    @ExcelProperty("name_ais")
    @Column(name = "name_ais", length = 100)
    private String nameAis;

    /**
     * 船舶类型（如货船、客船、油轮等）
     */
    @ExcelProperty("type")
    @Column(name = "type", length = 100)
    private String type;

    /**
     * 船舶细分类型/海军分类（如拖船、渔船、军事船等）
     */
    @ExcelProperty("navy_type")
    @Column(name = "navy_type", length = 100)
    private String navyType;

    /**
     * 船旗国（船舶注册或悬挂国旗的国家）
     */
    @ExcelProperty("country")
    @Column(name = "country", length = 100)
    private String country;

    /**
     * 经度（船舶当前位置的经度坐标，东正西负）
     */
    @ExcelProperty("longitude")
    @Column(name = "longitude")
    private Double longitude;

    /**
     * 纬度（船舶当前位置的纬度坐标，北正南负）
     */
    @ExcelProperty("latitude")
    @Column(name = "latitude")
    private Double latitude;

    /**
     * 航行状态（如航行中、锚泊、靠泊、搁浅等）
     */
    @ExcelProperty("navigational_status")
    @Column(name = "navigational_status", length = 100)
    private String navigationalStatus;

    /**
     * 对地航速（单位通常为节，kn）
     */
    @ExcelProperty("speed_over_ground")
    @Column(name = "speed_over_ground")
    private Double speedOverGround;

    /**
     * 对地航向（船舶实际运动方向，0-359度）
     */
    @ExcelProperty("course_over_ground")
    @Column(name = "course_over_ground")
    private Double courseOverGround;

    /**
     * 记录创建时间（数据库中该行数据的插入时间）
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /**
     * 记录更新时间（该行数据最后修改的时间）
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
