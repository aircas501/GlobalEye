package com.globaleyes.crawler.pojo.dto.acled;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 冲突趋势数据DTO
 *
 * @author CAST Data Team
 * @version 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CastTrendDTO {

    /**
     * 预测周期（格式：YYYY-MM）
     */
    private String period;

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
}
