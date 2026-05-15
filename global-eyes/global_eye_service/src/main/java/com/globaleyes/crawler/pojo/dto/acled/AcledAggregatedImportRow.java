package com.globaleyes.crawler.pojo.dto.acled;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

@Data
public class AcledAggregatedImportRow {

    @ExcelProperty("WEEK")
    private String week;

    @ExcelProperty("REGION")
    private String region;

    @ExcelProperty("COUNTRY")
    private String country;

    @ExcelProperty("ADMIN1")
    private String admin1;

    @ExcelProperty("EVENT_TYPE")
    private String eventType;

    @ExcelProperty("SUB_EVENT_TYPE")
    private String subEventType;

    @ExcelProperty("EVENTS")
    private String events;

    @ExcelProperty("FATALITIES")
    private String fatalities;

    @ExcelProperty("POPULATION_EXPOSURE")
    private String populationExposure;

    @ExcelProperty("DISORDER_TYPE")
    private String disorderType;

    @ExcelProperty("ID")
    private String id;

    @ExcelProperty("CENTROID_LATITUDE")
    private String centroidLatitude;

    @ExcelProperty("CENTROID_LONGITUDE")
    private String centroidLongitude;
}
