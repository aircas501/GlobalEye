package com.globaleyes.crawler.controller;

import com.globaleyes.crawler.pojo.dto.satellite.StarSpotCalcRequest;
import com.globaleyes.crawler.pojo.entity.SpaceTrackTleData;
import com.globaleyes.crawler.service.ws.WsPushInterfaceCallService;
import com.globaleyes.crawler.service.satellite.SpaceTrackTleDataService;
import com.globaleyes.crawler.util.StarSpotCalcUtil;
import com.globaleyes.crawler.pojo.vo.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * SpaceTrack TLE 数据控制器
 */
@RestController
@RequestMapping("/api/spacetrack/tle")
@CrossOrigin(origins = "*")
@Tag(name = "空间追踪 TLE 数据", description = "提供 SpaceTrack TLE 数据的增删改查和缓存管理接口")
public class SpaceTrackTleDataController {

    private static final Logger logger = LoggerFactory.getLogger(SpaceTrackTleDataController.class);

    @Resource
    private SpaceTrackTleDataService spaceTrackTleDataService;

    @Resource
    private WsPushInterfaceCallService wsPushInterfaceCallService;

    @PostMapping("/import")
    @Operation(summary = "导入 OMM 数据", description = "从 JSON 格式导入 SpaceTrack OMM 轨道数据")
    public void importJson(@RequestBody List<SpaceTrackOmmData> spaceTrackOmmDataList) {
        spaceTrackTleDataService.importJson(spaceTrackOmmDataList);
    }

    @GetMapping("/star-spot")
    @Operation(summary = "根据两行根数计算卫星某一刻的星下点", description = "根据两行根数计算卫星某一刻的星下点")
    public ResponseEntity<StarSpot> getLatestDataByObjectId(@RequestBody StarSpotCalcRequest request) {
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String format = dateTimeFormatter.format(request.getEpoch());
        StarSpot starSpot = StarSpotCalcUtil.calculateSatellitePosition(request.getEpoch(), request.getLine1(), request.getLine2());
        starSpot.setObjectId(request.getObjectId());
        starSpot.setObjectName(request.getObjectName());
        starSpot.setEpoch(format);
        return ResponseEntity.ok(starSpot);
    }

    /**
     * 模拟卫星异动
     */
    @GetMapping("/simulate-abnormal-move")
    @Operation(summary = "模拟卫星异动", description = "模拟卫星异动")
    public void simulateAbnormalMove() {
        WebSocketMsgPush.StarAbnormalMoveBroadcastMessageRequest abnormalMoveBroadcastMessageRequest = new WebSocketMsgPush.StarAbnormalMoveBroadcastMessageRequest();
        abnormalMoveBroadcastMessageRequest.setObjectId("2025-089N");
        abnormalMoveBroadcastMessageRequest.setObjectName("STARLINK-11693");
        abnormalMoveBroadcastMessageRequest.setTelLine1("1 63763U 25089N   26125.89427625  .00164937  00000-0  12537-2 0  9998");
        abnormalMoveBroadcastMessageRequest.setTelLine2("2 63763  42.9994 307.3103 0001787 306.9448  53.1256 15.71026030 59021");
        wsPushInterfaceCallService.wsNotice(abnormalMoveBroadcastMessageRequest, MsgTypeEnum.satellite, LevelEnum.warn);
    }

    @GetMapping("/time-range")
    @Operation(summary = "根据时间范围查询卫星数据", description = "查询指定时间范围内的所有卫星轨道数据（分页）")
    public Page<SatelliteTelData> getDataByTimeRange(
            @Parameter(description = "开始时间（格式：yyyy-MM-dd'T'HH:mm:ss）", required = true)
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime startTime,

            @Parameter(description = "结束时间（格式：yyyy-MM-dd'T'HH:mm:ss）", required = true)
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime endTime,

            @Parameter(description = "页码（从 0 开始）", example = "0")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "每页数量", example = "20")
            @RequestParam(defaultValue = "20") int size) {

        Page<SpaceTrackTleData> result = spaceTrackTleDataService.getDataByTimeRange(startTime, endTime, page, size);
        return result.map(SatelliteTelData::new);
    }

    @GetMapping("/epoch")
    @Operation(summary = "根据时刻查询卫星数据", description = "查询指定时刻的所有卫星轨道数据")
    public List<SatelliteTelData> getDataByEpoch(
            @Parameter(description = "历元时刻（格式：yyyy-MM-dd'T'HH:mm:ss）", required = true)
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime epoch) {

        List<SpaceTrackTleData> dataList = spaceTrackTleDataService.getDataByEpoch(epoch);
        return dataList.stream().map(SatelliteTelData::new).collect(Collectors.toList());
    }

}
