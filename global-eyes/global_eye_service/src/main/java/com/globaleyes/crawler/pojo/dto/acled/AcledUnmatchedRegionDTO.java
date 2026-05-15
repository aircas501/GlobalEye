package com.globaleyes.crawler.pojo.dto.acled;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AcledUnmatchedRegionDTO {
    private String country;
    private String admin1;
    private Long rowsCount;
    private LocalDate firstWeek;
    private LocalDate lastWeek;
}
