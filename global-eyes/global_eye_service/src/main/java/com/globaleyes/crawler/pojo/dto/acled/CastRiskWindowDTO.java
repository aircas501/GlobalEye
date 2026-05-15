package com.globaleyes.crawler.pojo.dto.acled;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 风险窗口期DTO
 *
 * @author CAST Data Team
 * @version 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CastRiskWindowDTO {

    /**
     * 预测周期
     */
    private String period;

    /**
     * 风险等级
     */
    private String riskLevel;

    /**
     * 预期事件数量
     */
    private Double expectedForecast;

    /**
     * 预测下限
     */
    private Double lowForecast;

    /**
     * 预测上限
     */
    private Double highForecast;

    /**
     * 与平均值的偏差百分比
     */
    private Double deviationPercentage;
}
