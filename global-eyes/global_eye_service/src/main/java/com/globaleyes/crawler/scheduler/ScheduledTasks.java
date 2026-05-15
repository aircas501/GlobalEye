package com.globaleyes.crawler.scheduler;

import com.globaleyes.crawler.pojo.entity.SpaceTrackTleData;
import com.globaleyes.crawler.service.ws.WsPushInterfaceCallService;
import com.globaleyes.crawler.service.satellite.SpaceTrackApiCallService;
import com.globaleyes.crawler.service.satellite.SpaceTrackTleDataService;
import com.globaleyes.crawler.pojo.vo.SpaceTrackOmmData;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
@ConditionalOnProperty(prefix = "external.space-track", name = "enable", havingValue = "true", matchIfMissing = true)
public class ScheduledTasks {

    @Resource
    private WsPushInterfaceCallService wsPushInterfaceCallService;

    @Resource
    private SpaceTrackApiCallService spaceTrackApiCallService;

    @Resource
    private SpaceTrackTleDataService spaceTrackTleDataService;

    private final int retryTimes = 3;

    /**
     * 调用SpaceTrack的接口
     */
    @Scheduled(fixedRate = 2, timeUnit = TimeUnit.HOURS)
    public void spaceOrgServerCall() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
        LocalDateTime currTime = LocalDateTime.now().minusHours(8);
        LocalDateTime beforeTwoHours = currTime.minusHours(2);
        String beforeTwoHoursString = beforeTwoHours.format(formatter);
        log.info("开始拉取{}(UTC时间)之后的数据", beforeTwoHoursString);
        if (spaceTrackApiCallService.checkSpaceTrackCookie()) {
            Flux<SpaceTrackOmmData> spaceTrackOmmDataFlux = spaceTrackApiCallService.querySpaceTrack(beforeTwoHoursString, retryTimes);
            // 数据格式转换为TLE数据
            List<SpaceTrackTleData> spaceTrackTleData = spaceTrackOmmDataFlux.toStream().map(SpaceTrackTleData::new).toList();
            if (CollectionUtils.isEmpty(spaceTrackTleData)) {
                log.info("未查询到卫星数据");
                return;
            }
            // 数据库需要新增、缓存需要更新的数据
            List<SpaceTrackTleData> needUpdateData = new ArrayList<>();

            log.info("本次拉取到{}个卫星的数据", spaceTrackTleData.size());
            //更新数据库数据
            spaceTrackTleDataService.saveAll(needUpdateData);
            log.info("{}个星体数据存入数据库成功", needUpdateData.size());
            //更新缓存
            spaceTrackTleDataService.batchUpdateCache(needUpdateData);
            log.info("{}个星体数据缓存更新成功", needUpdateData.size());

        }
    }

}
