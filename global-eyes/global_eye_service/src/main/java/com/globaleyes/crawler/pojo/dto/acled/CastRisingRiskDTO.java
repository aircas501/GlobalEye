package com.globaleyes.crawler.pojo.dto.acled;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 风险上升地区DTO
 *
 * @author CAST Data Team
 * @version 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CastRisingRiskDTO {

    /**
     * 排名
     */
    private Integer rank;

    /**
     * 国家名称
     */
    private String country;

    /**
     * 省份名称
     */
    private String admin1;

    /**
     * 增长率（百分比）
     */
    private Double growthRate;

    /**
     * 趋势描述
     */
    private String trend;
}
