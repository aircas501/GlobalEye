package com.globaleyes.crawler.pojo.dto.acled;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 地区风险排名DTO
 *
 * @author CAST Data Team
 * @version 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CastRegionRiskDTO {

    /**
     * 排名
     */
    private Integer rank;

    /**
     * 国家名称
     */
    private String country;

    /**
     * 省份名称（可选）
     */
    private String admin1;

    /**
     * 总预测事件数
     */
    private Double totalForecast;

    /**
     * 月均预测事件数
     */
    private Double avgMonthlyForecast;
}
