package com.globaleyes.crawler.pojo.dto.acled;

import lombok.Data;

import java.util.List;

@Data
public class AcledRiskTrendResponseDTO {
    private String country;
    private String admin1;
    private Integer weeks;
    private List<AcledRiskTrendPointDTO> series;
}
