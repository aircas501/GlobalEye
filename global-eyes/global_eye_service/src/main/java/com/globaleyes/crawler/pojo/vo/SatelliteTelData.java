package com.globaleyes.crawler.pojo.vo;

import com.globaleyes.crawler.pojo.entity.SpaceTrackTleData;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 卫星轨道及星下点数据 VO
 * 用于接收和返回卫星的完整信息，包括 TLE 数据和计算后的星下点位置
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "卫星轨道及星下点数据")
public class SatelliteTelData {

    public SatelliteTelData(SpaceTrackTleData spaceTrackTleData) {
        this.objectName = spaceTrackTleData.getObjectName();
        this.objectId = spaceTrackTleData.getObjectId();
        this.epoch = spaceTrackTleData.getEpoch();
        this.tleLine1 = spaceTrackTleData.getTleLine1();
        this.tleLine2 = spaceTrackTleData.getTleLine2();
        this.latitude = spaceTrackTleData.getStartSpotLat();
        this.longitude = spaceTrackTleData.getStartSpotLon();
    }

    @Schema(description = "卫星名称")
    @JsonProperty("name")
    private String objectName;

    @Schema(description = "NORAD 目录 ID")
    @JsonProperty("norad_id")
    private String noradId;

    @Schema(description = "目标 ID")
    @JsonProperty("object_id")
    private String objectId;

    @Schema(description = "历元时间", example = "2026-02-28T00:00:00.998784")
    @JsonProperty("epoch")
    private LocalDateTime epoch;

    @Schema(description = "TLE 第一行数据")
    @JsonProperty("tle_line1")
    private String tleLine1;

    @Schema(description = "TLE 第二行数据")
    @JsonProperty("tle_line2")
    private String tleLine2;

    @Schema(description = "纬度")
    @JsonProperty("latitude")
    private Double latitude;

    @Schema(description = "经度")
    @JsonProperty("longitude")
    private Double longitude;
}
