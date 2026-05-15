
package com.globaleyes.crawler.pojo.dto.satellite;

import lombok.Data;

/**
 * 卫星目录数据查询请求 DTO
 */
@Data
public class SatelliteCatalogQueryRequest {

    /**
     * 目标 ID
     */
    private String objectId;

    /**
     * 目标名称（支持模糊查询）
     */
    private String objectName;

    /**
     * 国家
     */
    private String country;

    /**
     * 卫星名称（支持模糊查询）
     */
    private String satname;

    /**
     * 目标类型
     */
    private String objectType;
}