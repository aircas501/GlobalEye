
package com.globaleyes.crawler.pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 数据导入结果 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImportResult {
    /**
     * 成功导入数量
     */
    private int successCount;

    /**
     * 失败数量
     */
    private int failCount;

    /**
     * 跳过数量（已存在的数据）
     */
    private int skipCount;

    /**
     * 错误信息列表
     */
    private String errorMessage;
}