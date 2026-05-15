package com.globaleyes.crawler.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AircraftTrackPushData {

    /**
     * ICAO 24位地址，飞机唯一标识符
     */
    private String icao24;

    /**
     * 位置更新时间
     */
    private LocalDateTime timePosition;

    /**
     * 经度
     */
    private Double longitude;

    /**
     * 纬度
     */
    private Double latitude;

    /**
     * 地速
     */
    private Double velocity;

    /**
     * 真航向
     */
    private Double trueTrack;

    /**
     * 几何高度（米）
     */
    private Double geoAltitude;

    /**
     * 机型
     */
    private String model;

}
