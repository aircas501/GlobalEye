package com.globaleyes.crawler.scheduler;

import com.globaleyes.crawler.service.opensky.OpenSkyDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * OpenSky数据同步定时任务
 * 每5分钟同步一次飞机实时数据
 * 
 * @author RSS News Crawler Team
 * @version 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenSkySyncScheduler {

    private final OpenSkyDataService openSkyDataService;

    @Value("${opensky.sync.enabled:true}")
    private boolean syncEnabled;

    @Value("${opensky.sync.retention-days:30}")
    private int retentionDays;

    /**
     * 定时同步飞机状态数据
     * 默认每5分钟执行一次
     */
    @Scheduled(fixedRateString = "${opensky.sync.interval:1200000}", initialDelay = 10000)
    public void syncStates() {
        if (!syncEnabled) {
            log.debug("OpenSky sync is disabled");
            return;
        }

        log.info("Starting OpenSky states sync task");
        long startTime = System.currentTimeMillis();

        try {
            int count = openSkyDataService.syncAllStates();
            long duration = System.currentTimeMillis() - startTime;
            log.info("OpenSky sync completed: {} records synced in {}ms", count, duration);
        } catch (Exception e) {
            log.error("OpenSky sync task failed", e);
        }
    }

    /**
     * 定时清理过期数据
     * 默认每天凌晨3点执行
     */
    @Scheduled(cron = "${opensky.sync.cleanup-cron:0 0 3 * * ?}")
    public void cleanupOldData() {
        if (!syncEnabled) {
            return;
        }

        log.info("Starting OpenSky data cleanup task");

        try {
            openSkyDataService.cleanupOldData(retentionDays);
            log.info("OpenSky data cleanup completed");
        } catch (Exception e) {
            log.error("OpenSky data cleanup task failed", e);
        }
    }
}
