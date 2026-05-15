package com.globaleyes.crawler.pojo.dto.satellite;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "星下点计算请求对象")
public class StarSpotCalcRequest {
    @NotNull(message = "epoch 不能为空")
    @Schema(description = "历元时间", example = "2024-01-01T12:00:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime epoch;

    @Schema(description = "目标 ID", example = "SAT-001")
    private String objectId;

    @Schema(description = "目标名称", example = "卫星一号")
    private String objectName;

    @NotBlank(message = "line1 不能为空")
    @Schema(description = "TLE 第一行数据", example = "1 25544U 99025A 24001.50000000 .00012345 00000-0 12345-0 0 9999", requiredMode = Schema.RequiredMode.REQUIRED)
    private String line1;

    @NotBlank(message = "line2 不能为空")
    @Schema(description = "TLE 第二行数据", example = "2 25544 51.6400 123.4567 0001234 45.6789 314.5678 15.12345678123456", requiredMode = Schema.RequiredMode.REQUIRED)
    private String line2;
}