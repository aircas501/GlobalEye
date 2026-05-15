package com.globaleyes.crawler.pojo.dto.acled;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 暴力类型统计DTO
 *
 * @author CAST Data Team
 * @version 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CastViolenceTypeDTO {

    /**
     * 暴力类型
     */
    private String outcome;

    /**
     * 总预测事件数
     */
    private Double totalForecast;

    /**
     * 占比百分比
     */
    private Double percentage;
}
