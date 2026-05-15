package com.globaleyes.crawler.pojo.vo;

import com.globaleyes.crawler.pojo.entity.SpaceTrackTleData;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StarSpot {

    public StarSpot(Double latitude, Double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public StarSpot(Double latitude, Double longitude, Double  altitude) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.altitude = altitude;
    }

    public StarSpot(SpaceTrackTleData trackTleData,String epoch, Double latitude, Double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.objectName = trackTleData.getObjectName();
        this.objectId = trackTleData.getObjectId();
        this.epoch = epoch;
        this.objectType = trackTleData.getObjectType();
        this.telLine1 = trackTleData.getTleLine1();
        this.telLine2 = trackTleData.getTleLine2();


    }
    /**
     * 卫星名称
     */
    private String objectName;

    /**
     * 卫星ID
     */
    private String objectId;

    /**
     * 星历时间
     */
    private String epoch;
    /**
     * 纬度
     */
    private Double latitude;
    /**
     * 经度
     */
    private Double longitude;

    /**
     * 高度
     */
    private Double altitude;

    /**
     * 卫星类型
     */
    private String objectType;

    private String telLine1;

    private String telLine2;

}
