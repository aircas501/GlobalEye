package com.globaleyes.crawler.pojo.dto.acled;

import lombok.Data;

@Data
public class AcledRiskSummaryDTO {
    private Double riskScore;
    private Long eventsTotal;
    private Long fatalitiesTotal;
    private Long populationExposure;
}
