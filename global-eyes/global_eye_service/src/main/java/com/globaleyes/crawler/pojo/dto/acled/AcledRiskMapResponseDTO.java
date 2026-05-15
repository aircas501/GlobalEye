package com.globaleyes.crawler.pojo.dto.acled;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class AcledRiskMapResponseDTO {
    private LocalDate week;
    private String metric;
    private String mode;
    private List<AcledLegendBucketDTO> legend;
    private List<AcledRiskMapFeatureDTO> features;
}
