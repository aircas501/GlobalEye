package com.globaleyes.crawler.pojo.dto.acled;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class AcledRiskFiltersDTO {
    private List<String> countries;
    private List<String> eventTypes;
    private List<String> subEventTypes;
    private List<String> disorderTypes;
    private List<LocalDate> weeks;
}
