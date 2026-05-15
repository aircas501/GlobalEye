package com.globaleyes.crawler.pojo.dto.acled;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AcledRegionMetricDTO {
    private LocalDate weekStart;
    private String country;
    private String admin1;
    private Long boundaryId;
    private Long eventsTotal;
    private Long fatalitiesTotal;
    private Long populationExposureMax;
    private Double centroidLatitude;
    private Double centroidLongitude;
}
