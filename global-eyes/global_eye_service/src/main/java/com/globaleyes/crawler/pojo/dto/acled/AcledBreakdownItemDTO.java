package com.globaleyes.crawler.pojo.dto.acled;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AcledBreakdownItemDTO {
    private String name;
    private Long eventsTotal;
    private Long fatalitiesTotal;
}
