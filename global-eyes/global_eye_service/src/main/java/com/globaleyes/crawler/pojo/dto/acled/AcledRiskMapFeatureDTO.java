package com.globaleyes.crawler.pojo.dto.acled;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.util.Map;

@Data
public class AcledRiskMapFeatureDTO {
    private Long boundaryId;
    private String country;
    private String admin1;
    private Double value;
    private Double riskScore;
    private Long eventsTotal;
    private Long fatalitiesTotal;
    private Long populationExposure;
    private Double wowDelta;
    private Map<String, Double> centroid;
    private JsonNode geometry;
}
