package com.globaleyes.crawler.pojo.entity;

import com.alibaba.excel.annotation.ExcelIgnore;
import com.alibaba.excel.annotation.ExcelProperty;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * CAST冲突预测数据实体类
 * 存储ACLED CAST报告的冲突预测数据
 *
 * @author CAST Data Team
 * @version 1.0.0
 */
@Data
@Entity
@Table(name = "cast_forecast_data", indexes = {
    @Index(name = "idx_cast_level", columnList = "level"),
    @Index(name = "idx_cast_country", columnList = "country"),
    @Index(name = "idx_cast_admin1", columnList = "admin1"),
    @Index(name = "idx_cast_outcome", columnList = "outcome"),
    @Index(name = "idx_cast_period", columnList = "period")
})
public class CastForecastData {

    /**
     * 主键ID
     */
    @ExcelIgnore
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 地理层级
     * global: 全球汇总预测
     * country: 国家级预测
     * admin1: 一级行政区预测
     * global outcome: 全球层级的特定暴力类型预测
     * country outcome: 国家层级的特定暴力类型预测
     * admin1 outcome: 行政区层级的特定暴力类型预测
     */
    @ExcelProperty("level")
    @Column(name = "level", length = 50)
    private String level;

    /**
     * 国家名称
     */
    @ExcelProperty("country")
    @Column(name = "country", length = 100)
    private String country;

    /**
     * 省份/一级行政区
     */
    @ExcelProperty("admin1")
    @Column(name = "admin1", length = 100)
    private String admin1;

    /**
     * 暴力事件类型
     * Battles: 战斗
     * Explosions/remote violence: 爆炸/远程暴力
     * Violence against civilians: 针对平民的暴力
     * Organized violence: 有组织暴力（上述三类的总和）
     */
    @ExcelProperty("outcome")
    @Column(name = "outcome", length = 100)
    private String outcome;

    /**
     * 预测周期（格式：YYYY-MM）
     */
    @ExcelProperty("period")
    @Column(name = "period", length = 20)
    private String period;

    /**
     * 预期事件数量预测值
     */
    @ExcelProperty("expected_forecast")
    @Column(name = "expected_forecast")
    private Double expectedForecast;

    /**
     * 预测下限
     */
    @ExcelProperty("low_forecast")
    @Column(name = "low_forecast")
    private Double lowForecast;

    /**
     * 预测上限
     */
    @ExcelProperty("high_forecast")
    @Column(name = "high_forecast")
    private Double highForecast;

    /**
     * 记录创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
