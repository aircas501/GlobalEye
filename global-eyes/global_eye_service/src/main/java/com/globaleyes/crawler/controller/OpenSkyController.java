package com.globaleyes.crawler.controller;

import com.globaleyes.crawler.pojo.entity.opensky.AircraftTrack;
import com.globaleyes.crawler.service.opensky.OpenSkyDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenSky飞机数据API控制器
 * 
 * @author RSS News Crawler Team
 * @version 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/opensky")
@RequiredArgsConstructor
@Tag(name = "OpenSky飞机数据", description = "飞机实时数据和历史轨迹查询接口")
public class OpenSkyController {

    private final OpenSkyDataService openSkyDataService;

    /**
     * 手动触发数据同步
     *
     * @return 同步结果，包含成功状态和同步数量
     */
    @PostMapping("/sync")
    @Operation(summary = "手动触发数据同步", description = "手动触发从OpenSky同步飞机实时数据")
    public ResponseEntity<Map<String, Object>> triggerSync() {
        log.info("Manual sync triggered");
        
        try {
            int count = openSkyDataService.syncAllStates();
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("syncedCount", count);
            result.put("message", "Successfully synced " + count + " aircraft tracks");
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Manual sync failed", e);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "Sync failed: " + e.getMessage());
            
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * 根据ICAO24地址查询飞机历史轨迹
     *
     * @param icao24    ICAO 24位地址
     * @param startTime 开始时间（Unix时间戳，秒），可选
     * @param endTime   结束时间（Unix时间戳，秒），可选
     * @return 轨迹点列表
     */
    @GetMapping("/aircraft/{icao24}/history")
    @Operation(summary = "查询飞机历史轨迹", description = "根据ICAO24地址查询飞机的历史轨迹数据")
    public ResponseEntity<List<AircraftTrack>> getAircraftHistory(
            @Parameter(description = "ICAO24地址") @PathVariable String icao24,
            @Parameter(description = "开始时间(Unix时间戳，秒)") @RequestParam(required = false) Long startTime,
            @Parameter(description = "结束时间(Unix时间戳，秒)") @RequestParam(required = false) Long endTime) {
        
        List<AircraftTrack> history = openSkyDataService.getAircraftHistory(icao24, startTime, endTime);
        return ResponseEntity.ok(history);
    }

    /**
     * 根据呼号查询飞机历史轨迹
     *
     * @param callsign  飞机呼号
     * @param startTime 开始时间（Unix时间戳，秒），可选
     * @param endTime   结束时间（Unix时间戳，秒），可选
     * @return 轨迹点列表
     */
    @GetMapping("/aircraft/callsign/{callsign}/history")
    @Operation(summary = "按呼号查询历史轨迹", description = "根据呼号查询飞机的历史轨迹数据")
    public ResponseEntity<List<AircraftTrack>> getAircraftHistoryByCallsign(
            @Parameter(description = "呼号") @PathVariable String callsign,
            @Parameter(description = "开始时间(Unix时间戳，秒)") @RequestParam(required = false) Long startTime,
            @Parameter(description = "结束时间(Unix时间戳，秒)") @RequestParam(required = false) Long endTime) {
        
        List<AircraftTrack> history = openSkyDataService.getAircraftHistoryByCallsign(callsign, startTime, endTime);
        return ResponseEntity.ok(history);
    }

    /**
     * 获取GeoJSON格式的飞机轨迹
     *
     * @param icao24    ICAO 24位地址
     * @param startTime 开始时间（Unix时间戳，秒），可选
     * @param endTime   结束时间（Unix时间戳，秒），可选
     * @return GeoJSON格式的轨迹数据
     */
    @GetMapping("/aircraft/{icao24}/track/geojson")
    @Operation(summary = "获取GeoJSON格式轨迹", description = "将飞机轨迹转换为GeoJSON格式返回")
    public ResponseEntity<Map<String, Object>> getAircraftTrackGeoJson(
            @Parameter(description = "ICAO24地址") @PathVariable String icao24,
            @Parameter(description = "开始时间(Unix时间戳，秒)") @RequestParam(required = false) Long startTime,
            @Parameter(description = "结束时间(Unix时间戳，秒)") @RequestParam(required = false) Long endTime) {
        
        List<AircraftTrack> track = openSkyDataService.getAircraftHistory(icao24, startTime, endTime);
        
        Map<String, Object> geoJson = buildTrackGeoJson(icao24, track);
        return ResponseEntity.ok(geoJson);
    }

    /**
     * 清理过期历史数据
     *
     * @param retentionDays 数据保留天数
     * @return 清理结果
     */
    @DeleteMapping("/cleanup")
    @Operation(summary = "清理历史数据", description = "清理超过保留期的历史数据")
    public ResponseEntity<Map<String, Object>> cleanupOldData(
            @Parameter(description = "保留天数") @RequestParam(defaultValue = "30") int retentionDays) {
        
        log.info("Manual cleanup triggered with retention days: {}", retentionDays);
        
        try {
            openSkyDataService.cleanupOldData(retentionDays);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Data cleanup completed for data older than " + retentionDays + " days");
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Data cleanup failed", e);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "Cleanup failed: " + e.getMessage());
            
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * 构建GeoJSON格式的轨迹数据
     *
     * @param icao24 ICAO 24位地址
     * @param track  轨迹点列表
     * @return GeoJSON格式的轨迹数据
     */
    private Map<String, Object> buildTrackGeoJson(String icao24, List<AircraftTrack> track) {
        Map<String, Object> geoJson = new HashMap<>();
        geoJson.put("type", "FeatureCollection");

        Map<String, Object> feature = new HashMap<>();
        feature.put("type", "Feature");
        
        Map<String, Object> properties = new HashMap<>();
        properties.put("icao24", icao24);
        if (!track.isEmpty()) {
            properties.put("callsign", track.get(0).getCallsign());
            properties.put("originCountry", track.get(0).getOriginCountry());
            properties.put("pointCount", track.size());
        }
        feature.put("properties", properties);

        Map<String, Object> geometry = new HashMap<>();
        geometry.put("type", "LineString");
        
        List<List<Double>> lineCoords = track.stream()
            .filter(p -> p.getLongitude() != null && p.getLatitude() != null)
            .map(p -> List.of(p.getLongitude(), p.getLatitude()))
            .toList();
        geometry.put("coordinates", lineCoords);
        
        feature.put("geometry", geometry);
        
        geoJson.put("features", List.of(feature));
        
        List<Map<String, Object>> points = track.stream()
            .filter(p -> p.getLongitude() != null && p.getLatitude() != null)
            .map(p -> {
                Map<String, Object> point = new HashMap<>();
                point.put("time", p.getTime());
                point.put("lon", p.getLongitude());
                point.put("lat", p.getLatitude());
                point.put("baroAltitude", p.getBaroAltitude());
                point.put("geoAltitude", p.getGeoAltitude());
                point.put("velocity", p.getVelocity());
                point.put("trueTrack", p.getTrueTrack());
                point.put("verticalRate", p.getVerticalRate());
                point.put("onGround", p.getOnGround());
                return point;
            })
            .toList();
        geoJson.put("points", points);
        
        return geoJson;
    }
}
