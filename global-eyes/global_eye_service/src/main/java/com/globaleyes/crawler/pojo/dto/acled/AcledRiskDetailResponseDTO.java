package com.globaleyes.crawler.pojo.dto.acled;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class AcledRiskDetailResponseDTO {
    private String country;
    private String admin1;
    private LocalDate week;
    private AcledRiskSummaryDTO summary;
    private List<AcledBreakdownItemDTO> eventTypeBreakdown;
    private List<AcledBreakdownItemDTO> subEventTypeBreakdown;
    private List<AcledBreakdownItemDTO> disorderTypeBreakdown;
}
