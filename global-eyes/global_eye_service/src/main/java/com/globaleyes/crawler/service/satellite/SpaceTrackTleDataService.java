package com.globaleyes.crawler.service.satellite;

import com.globaleyes.crawler.pojo.entity.SpaceTrackTleData;
import com.globaleyes.crawler.repository.satellite.SpaceTrackTleDataRepository;
import com.globaleyes.crawler.service.ws.WsPushInterfaceCallService;
import com.globaleyes.crawler.util.StarSpotCalcUtil;
import com.globaleyes.crawler.pojo.vo.LevelEnum;
import com.globaleyes.crawler.pojo.vo.MsgTypeEnum;
import com.globaleyes.crawler.pojo.vo.SpaceTrackOmmData;
import com.globaleyes.crawler.pojo.vo.StarSpot;
import com.google.common.collect.Lists;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * SpaceTrack TLE 数据服务类
 * 包含内存缓存功能，缓存每个 objectId 的最新 epoch 数据
 */
@Service
public class SpaceTrackTleDataService {

    private static final Logger logger = LoggerFactory.getLogger(SpaceTrackTleDataService.class);

    @Resource
    private SpaceTrackTleDataRepository spaceTrackTleDataRepository;

    @Resource
    private WsPushInterfaceCallService wsPushInterfaceCallService;

    private final ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();

    /**
     * 内存缓存：存储每个 objectId 的最新 TLE 数据
     * 使用 ConcurrentHashMap 保证线程安全
     */
    private final Map<String, SpaceTrackTleData> latestEpochCache = new ConcurrentHashMap<>();

    private final String realStar = "2025-089N;2025-048F;2025-048M;2025-039E;2025-039D;2025-043D;2025-043C;2025-057B;2025-027D;2025-019F;2024-249B;1996-072A;2005-042A;2021-032A";
    @PostConstruct
    public void init() {
        Page<SpaceTrackTleData> result = spaceTrackTleDataRepository.listAllStarLatestData(PageRequest.of(0, 100));
        List<SpaceTrackTleData> spaceTrackTleData = result.getContent();
        latestEpochCache.putAll(spaceTrackTleData.stream().collect(Collectors.toMap(SpaceTrackTleData::getObjectId, v -> v, (v1, v2) -> v1)));
        if (result.getTotalPages() > 1) {
            for (int i = 1; i < result.getTotalPages(); i++) {
                spaceTrackTleData = spaceTrackTleDataRepository.listAllStarLatestData(PageRequest.of(i, 100)).getContent();
                for (SpaceTrackTleData spaceTrackTleDatum : spaceTrackTleData) {
                    latestEpochCache.put(spaceTrackTleDatum.getObjectId(), spaceTrackTleDatum);
                }
            }
        }
        scheduler.setPoolSize(5);
        scheduler.initialize();
        scheduler.scheduleWithFixedDelay(() -> {
            LocalDateTime currentDateTime = LocalDateTime.now();
            DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
            // 转成北京时
            LocalDateTime currBjDateTime = currentDateTime.plusHours(8);
            String format = dateTimeFormatter.format(currBjDateTime);
            String[] split = realStar.split(";");
            List<StarSpot> starSpots = new ArrayList<>();
            for (String starId : split) {
                SpaceTrackTleData tleData = latestEpochCache.get(starId);
                if (tleData != null) {
                    StarSpot calc = StarSpotCalcUtil.calculateSatellitePosition(LocalDateTime.now(), tleData.getTleLine1(), tleData.getTleLine2());
                    // logger.info("{}的{}时刻行下点计算成功，计算结果为: lon:{},lat:{}", tleData.getObjectName(), format, calc.getLongitude(), calc.getLatitude());
                    StarSpot starSpot = new StarSpot(tleData,format,calc.getLatitude(), calc.getLongitude());
                    starSpot.setAltitude(calc.getAltitude());
                    starSpots.add(starSpot);
                }
            }
            // 推送数据
            wsPushInterfaceCallService.wsNotice(starSpots, MsgTypeEnum.satellite, LevelEnum.info);
        }, Duration.of(10, TimeUnit.SECONDS.toChronoUnit()));
    }

    /**
     * 通过卫星ID查询卫星信息
     *
     * @param objectId 卫星ID
     * @return 卫星数据
     */
    public SpaceTrackTleData getLatestDataByObjectId(String objectId) {
        return latestEpochCache.get(objectId);
    }

    /**
     * 更新缓存中卫星的最新数据
     *
     * @param spaceTrackTleData 最新数据
     * @return 数据是否更新
     */
    public boolean updateLatestData(SpaceTrackTleData spaceTrackTleData) {
        SpaceTrackTleData spaceTrackTleCacheData = latestEpochCache.get(spaceTrackTleData.getObjectId());
        if (spaceTrackTleCacheData == null || spaceTrackTleCacheData.getEpoch().isBefore(spaceTrackTleData.getEpoch())) {
            latestEpochCache.put(spaceTrackTleData.getObjectId(), spaceTrackTleData);
            return true;
        }
        return false;
    }

    /**
     * 批量更新更新缓存中卫星的最新数据
     *
     * @param spaceTrackTleDataList 最新数据
     * @return 数据是否更新
     */
    @Async
    public void batchUpdateCache(List<SpaceTrackTleData> spaceTrackTleDataList) {
        for (SpaceTrackTleData spaceTrackTleData : spaceTrackTleDataList) {
            this.updateLatestData(spaceTrackTleData);
        }
    }


    /**
     * 导入历史数据
     *
     * @param spaceTrackOmmDataList
     */
    public void importJson(List<SpaceTrackOmmData> spaceTrackOmmDataList) {
        List<List<SpaceTrackOmmData>> partition = Lists.partition(spaceTrackOmmDataList, 1000);
        for (List<SpaceTrackOmmData> spaceTrackOmmData : partition) {
            this.saveAll(spaceTrackOmmData.stream().map(SpaceTrackTleData::new).toList());
        }
    }

    /**
     * 保存所有的卫星数据
     *
     * @param tleDataList 卫星数据
     */
    @Async
    public void saveAll(List<SpaceTrackTleData> tleDataList) {
        List<List<SpaceTrackTleData>> partition = Lists.partition(tleDataList, 1000);
        for (List<SpaceTrackTleData> spaceTrackTleData : partition) {
            spaceTrackTleDataRepository.saveAll(spaceTrackTleData);
        }
    }

    /**
     * 根据时间范围查询卫星数据
     *
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @param page      页码
     * @param size      每页数量
     * @return 分页查询结果
     */
    public Page<SpaceTrackTleData> getDataByTimeRange(LocalDateTime startTime, LocalDateTime endTime, int page, int size) {
        return spaceTrackTleDataRepository.findByEpochBetween(startTime, endTime, PageRequest.of(page, size));
    }

    /**
     * 根据指定时刻查询卫星数据
     *
     * @param epoch 历元时刻
     * @return 该时刻的所有卫星数据
     */
    public List<SpaceTrackTleData> getDataByEpoch(LocalDateTime epoch) {
        return spaceTrackTleDataRepository.findByEpoch(epoch);
    }


}