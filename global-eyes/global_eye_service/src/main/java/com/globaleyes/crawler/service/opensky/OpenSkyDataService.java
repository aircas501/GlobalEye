package com.globaleyes.crawler.service.opensky;

import com.globaleyes.crawler.pojo.entity.opensky.AircraftTrack;
import com.globaleyes.crawler.repository.aircraft.AircraftTrackRepository;
import com.globaleyes.crawler.service.ws.WsPushInterfaceCallService;
import com.globaleyes.crawler.service.aircraft.AircraftModelCacheService;
import com.globaleyes.crawler.pojo.vo.AircraftTrackPushData;
import com.globaleyes.crawler.pojo.vo.LevelEnum;
import com.globaleyes.crawler.pojo.vo.MsgTypeEnum;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * OpenSky数据同步服务
 *
 * @author RSS News Crawler Team
 * @version 1.0.0
 */
@Slf4j
@Service
public class OpenSkyDataService {

    private final WebClient openSkyWebClient;
    private final AircraftTrackRepository trackRepository;
    private final ObjectMapper objectMapper;
    private final OpenSkyTokenManager tokenManager;
    private final AircraftModelCacheService aircraftModelCacheService;
    private final WsPushInterfaceCallService wsPushInterfaceCallService;

    public OpenSkyDataService(@Qualifier("openSkyWebClient") WebClient openSkyWebClient,
                              AircraftTrackRepository trackRepository,
                              ObjectMapper objectMapper,
                              OpenSkyTokenManager tokenManager,
                              AircraftModelCacheService aircraftModelCacheService,
                              WsPushInterfaceCallService wsPushInterfaceCallService) {
        this.openSkyWebClient = openSkyWebClient;
        this.trackRepository = trackRepository;
        this.objectMapper = objectMapper;
        this.tokenManager = tokenManager;
        this.aircraftModelCacheService = aircraftModelCacheService;
        this.wsPushInterfaceCallService = wsPushInterfaceCallService;
    }


    /**
     * 同步所有飞机状态数据
     * 调用OpenSky API /states/all接口获取所有飞机的实时状态
     *
     * @return 同步的轨迹点数量
     * @throws RuntimeException 同步失败时抛出异常
     */
    @Transactional
    public int syncAllStates() {
        log.info("Starting to sync all states from OpenSky API: /states/all");

        try {
            String authHeader = tokenManager.getAuthorizationHeader();
            // 做中东地区数据的过滤
            double lamin = 12.0;  // 南
            double lamax = 42.0;  // 北
            double lomin = 25.0;  // 西
            double lomax = 75.0;  // 东
            String response;
            if (authHeader != null) {
                response = openSkyWebClient.get()
                        .uri("/states/all")
                        .attribute("lamin", lamin)
                        .attribute("lamax", lamax)
                        .attribute("lomin", lomin)
                        .attribute("lomax", lomax)
                        .header("Authorization", authHeader)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();
            } else {
                response = openSkyWebClient.get()
                        .uri("/states/all")
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();
            }

            if (response == null || response.isEmpty()) {
                log.warn("Empty response from OpenSky API");
                return 0;
            }

            JsonNode root = objectMapper.readTree(response);
            Long time = root.has("time") ? root.get("time").asLong() : System.currentTimeMillis() / 1000;
            JsonNode states = root.get("states");

            if (states == null || !states.isArray()) {
                log.warn("No states array in response");
                return 0;
            }

            List<AircraftTrack> tracks = new ArrayList<>();

            for (JsonNode state : states) {
                if (!state.isArray()) {
                    continue;
                }

                try {
                    AircraftTrack track = parseStateVector(state, time);
                    if (track != null && track.getIcao24() != null) {
                        tracks.add(track);
                    }
                    if (tracks.size() == 100) {
                        break;
                    }

                } catch (Exception e) {
                    log.warn("Failed to parse state: {}", state, e);
                }
            }

            if (!tracks.isEmpty()) {
                pushAircraftTrack(tracks);
                trackRepository.saveAll(tracks);
                log.info("Saved {} aircraft tracks to database", tracks.size());

            }

            return tracks.size();
        } catch (Exception e) {
            log.error("Failed to sync states from OpenSky", e);
            throw new RuntimeException("Failed to sync states", e);
        }
    }

    private void pushAircraftTrack(List<AircraftTrack> aircraftTracks) {
        aircraftTracks.stream()
                .map(aircraftTrack ->
                        AircraftTrackPushData.builder()
                                .icao24(aircraftTrack.getIcao24())
                                .timePosition(aircraftTrack.getTimePosition() == null ? null :timestampToLocalDateTime(aircraftTrack.getTimePosition()))
                                .longitude(aircraftTrack.getLongitude())
                                .latitude(aircraftTrack.getLatitude())
                                .velocity(aircraftTrack.getVelocity())
                                .trueTrack(aircraftTrack.getTrueTrack())
                                .geoAltitude(aircraftTrack.getGeoAltitude())
                                .model(this.aircraftModelCacheService.getModel(aircraftTrack.getIcao24()).orElse(""))
                                .build()
                ).forEach(aircraftTrackPushData -> this.wsPushInterfaceCallService.wsNotice(aircraftTrackPushData, MsgTypeEnum.aircraft, LevelEnum.info));
    }


    /**
     * 事件戳转LocalDateTime
     *
     * @param timestamp 时间戳
     * @return localDateTime
     */
    private LocalDateTime timestampToLocalDateTime(long timestamp) {
        Instant instant = Instant.ofEpochSecond(timestamp);
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }

    /**
     * 解析状态向量数据
     *
     * @param state 状态向量JSON数组
     * @param time  数据同步时间戳
     * @return 飞机轨迹对象，解析失败返回null
     */
    private AircraftTrack parseStateVector(JsonNode state, Long time) {
        if (state.size() < 17) {
            return null;
        }
        Integer category = getInteger(state, 17);
//        if (category != null && category <= 9) {
//            return AircraftTrack.builder()
//                    .icao24(getString(state, 0))
//                    .callsign(getString(state, 1))
//                    .originCountry(getString(state, 2))
//                    .timePosition(getLong(state, 3))
//                    .lastContact(getLong(state, 4))
//                    .longitude(getDouble(state, 5))
//                    .latitude(getDouble(state, 6))
//                    .baroAltitude(getDouble(state, 7))
//                    .onGround(getBoolean(state, 8))
//                    .velocity(getDouble(state, 9))
//                    .trueTrack(getDouble(state, 10))
//                    .verticalRate(getDouble(state, 11))
//                    .geoAltitude(getDouble(state, 13))
//                    .squawk(getString(state, 14))
//                    .spi(getBoolean(state, 15))
//                    .positionSource(getInteger(state, 16))
//                    .category(getInteger(state, 17))
//                    .time(time)
//                    .build();
//        }
        return AircraftTrack.builder()
                .icao24(getString(state, 0))
                .callsign(getString(state, 1))
                .originCountry(getString(state, 2))
                .timePosition(getLong(state, 3))
                .lastContact(getLong(state, 4))
                .longitude(getDouble(state, 5))
                .latitude(getDouble(state, 6))
                .baroAltitude(getDouble(state, 7))
                .onGround(getBoolean(state, 8))
                .velocity(getDouble(state, 9))
                .trueTrack(getDouble(state, 10))
                .verticalRate(getDouble(state, 11))
                .geoAltitude(getDouble(state, 13))
                .squawk(getString(state, 14))
                .spi(getBoolean(state, 15))
                .positionSource(getInteger(state, 16))
                .category(getInteger(state, 17))
                .time(time)
                .build();
    }

    /**
     * 从JSON数组中获取字符串值
     *
     * @param state JSON数组节点
     * @param index 索引位置
     * @return 字符串值，不存在或为null时返回null
     */
    private String getString(JsonNode state, int index) {
        if (index >= state.size() || state.get(index).isNull()) {
            return null;
        }
        String value = state.get(index).asText();
        return value != null ? value.trim() : null;
    }

    /**
     * 从JSON数组中获取Long值
     *
     * @param state JSON数组节点
     * @param index 索引位置
     * @return Long值，不存在或为null时返回null
     */
    private Long getLong(JsonNode state, int index) {
        if (index >= state.size() || state.get(index).isNull()) {
            return null;
        }
        return state.get(index).asLong();
    }

    /**
     * 从JSON数组中获取Double值
     *
     * @param state JSON数组节点
     * @param index 索引位置
     * @return Double值，不存在或为null时返回null
     */
    private Double getDouble(JsonNode state, int index) {
        if (index >= state.size() || state.get(index).isNull()) {
            return null;
        }
        return state.get(index).asDouble();
    }

    /**
     * 从JSON数组中获取Boolean值
     *
     * @param state JSON数组节点
     * @param index 索引位置
     * @return Boolean值，不存在或为null时返回null
     */
    private Boolean getBoolean(JsonNode state, int index) {
        if (index >= state.size() || state.get(index).isNull()) {
            return null;
        }
        return state.get(index).asBoolean();
    }

    /**
     * 从JSON数组中获取Integer值
     *
     * @param state JSON数组节点
     * @param index 索引位置
     * @return Integer值，不存在或为null时返回null
     */
    private Integer getInteger(JsonNode state, int index) {
        if (index >= state.size() || state.get(index).isNull()) {
            return null;
        }
        return state.get(index).asInt();
    }

    /**
     * 根据ICAO24地址查询飞机历史轨迹
     *
     * @param icao24    ICAO 24位地址
     * @param startTime 开始时间（Unix时间戳，秒），可选
     * @param endTime   结束时间（Unix时间戳，秒），可选
     * @return 轨迹点列表，按时间升序排列
     */
    public List<AircraftTrack> getAircraftHistory(String icao24, Long startTime, Long endTime) {
        if (startTime != null && endTime != null) {
            return trackRepository.findByIcao24AndTimeRange(icao24, startTime, endTime);
        }
        return trackRepository.findByIcao24OrderByTime(icao24);
    }

    /**
     * 根据呼号查询飞机历史轨迹
     *
     * @param callsign  飞机呼号
     * @param startTime 开始时间（Unix时间戳，秒），可选
     * @param endTime   结束时间（Unix时间戳，秒），可选
     * @return 轨迹点列表，按时间升序排列
     */
    public List<AircraftTrack> getAircraftHistoryByCallsign(String callsign, Long startTime, Long endTime) {
        if (startTime != null && endTime != null) {
            return trackRepository.findByCallsignAndTimeRange(callsign, startTime, endTime);
        }
        return trackRepository.findByCallsignOrderByTime(callsign);
    }

    /**
     * 清理过期历史数据
     *
     * @param retentionDays 数据保留天数
     */
    @Transactional
    public void cleanupOldData(int retentionDays) {
        long cutoffTime = System.currentTimeMillis() / 1000 - (retentionDays * 24L * 60 * 60);
        log.info("Cleaning up data older than {} days (timestamp: {})", retentionDays, cutoffTime);
        trackRepository.deleteByTimeBefore(cutoffTime);
        log.info("Data cleanup completed");
    }
}
