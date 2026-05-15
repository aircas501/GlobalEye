package com.globaleyes.crawler.pojo.dto.acled;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AcledRiskTrendPointDTO {
    private LocalDate week;
    private Double riskScore;
    private Long events;
    private Long fatalities;
}
