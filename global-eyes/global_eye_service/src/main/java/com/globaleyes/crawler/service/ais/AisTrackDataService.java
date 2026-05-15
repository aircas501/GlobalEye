package com.globaleyes.crawler.service.ais;

import com.alibaba.excel.EasyExcel;
import com.globaleyes.crawler.pojo.entity.AisTrackData;
import com.globaleyes.crawler.repository.ais.AisTrackDataRepository;
import com.globaleyes.crawler.service.ws.WsPushInterfaceCallService;
import com.globaleyes.crawler.pojo.vo.LevelEnum;
import com.globaleyes.crawler.pojo.vo.MsgTypeEnum;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * AIS船舶追踪数据服务
 *
 * @author AIS Data Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AisTrackDataService {

    private final AisTrackDataRepository repository;

    private final WsPushInterfaceCallService wsPushInterfaceCallService;

    private final ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();

    @PostConstruct
    public void init() {
        scheduler.setPoolSize(5);
        scheduler.initialize();

        scheduler.scheduleWithFixedDelay(()-> {
            PageRequest pageable = PageRequest.of(0, 500, Sort.unsorted());
            Page<AisTrackData> latestData = findLatestData(null, null, pageable);
            // 由于没法实时获取数据，智能通过调用查询最新数据接口时触发推送AIS数据
            for (AisTrackData latestDatum : latestData.getContent()) {
                wsPushInterfaceCallService.wsNotice(latestDatum, MsgTypeEnum.ship, LevelEnum.info);
            }

        }, Duration.of(20, TimeUnit.SECONDS.toChronoUnit()));

    }


    /**
     * 分页查询（支持name_ais模糊搜索，navy_type、country精确查询，更新时间范围查询）
     *
     * @param nameAis   船舶名称（模糊查询）
     * @param navyType  船舶细分类型（精确查询）
     * @param country   船旗国（精确查询）
     * @param startTime 更新时间开始范围
     * @param endTime   更新时间结束范围
     * @param pageable  分页参数
     * @return 分页结果
     */
    public Page<AisTrackData> search(String nameAis, String navyType, String country,
                                      LocalDateTime startTime, LocalDateTime endTime, Pageable pageable) {
        return repository.search(nameAis, navyType, country, startTime, endTime, pageable);
    }

    /**
     * 查询所有船舶的最新数据（按MMSI分组，取每组中创建时间最新的记录）
     * 支持按船舶细分类型和船旗国筛选
     *
     * @param navyType 船舶细分类型（精确查询）
     * @param country  船旗国（精确查询）
     * @param pageable 分页参数
     * @return 分页结果
     */
    public Page<AisTrackData> findLatestData(String navyType, String country, Pageable pageable) {
        return repository.findLatestByMmsi(navyType, country, pageable);
    }

    /**
     * 根据ID查询
     *
     * @param id 主键ID
     * @return 船舶数据
     */
    public Optional<AisTrackData> findById(Long id) {
        return repository.findById(id);
    }

    /**
     * 根据MMSI查询
     *
     * @param mmsi 海上移动服务标识
     * @return 船舶数据
     */
    public Optional<AisTrackData> findByMmsi(String mmsi) {
        return repository.findByMmsi(mmsi);
    }

    /**
     * 查询所有船舶类型列表
     *
     * @return 船舶类型列表
     */
    public List<String> findAllNavyTypes() {
        return repository.findAllNavyTypes();
    }

    /**
     * 查询所有国家列表
     *
     * @return 国家列表
     */
    public List<String> findAllCountries() {
        return repository.findAllCountries();
    }

    /**
     * 按精确更新时间查询
     *
     * @param updatedAt 更新时间
     * @param pageable  分页参数
     * @return 分页结果
     */
    public Page<AisTrackData> findByUpdatedAt(LocalDateTime updatedAt, Pageable pageable) {
        return repository.findByUpdatedAt(updatedAt, pageable);
    }

    /**
     * 按更新时间范围查询
     *
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @param pageable  分页参数
     * @return 分页结果
     */
    public Page<AisTrackData> findByUpdatedAtBetween(LocalDateTime startTime, LocalDateTime endTime, Pageable pageable) {
        return repository.findByUpdatedAtBetween(startTime, endTime, pageable);
    }

    /**
     * 导入xlsx文件
     * 采用分批导入优化性能
     *
     * @param file xlsx文件
     * @return 导入结果
     */
    @Transactional
    public ImportResult importFromExcel(MultipartFile file) {
        ImportResult result = new ImportResult();

        try {
            List<AisTrackData> dataList = EasyExcel.read(file.getInputStream())
                    .head(AisTrackData.class)
                    .sheet()
                    .doReadSync();

            if (dataList.isEmpty()) {
                result.setMessage("文件内容为空");
                return result;
            }

            result.setTotal(dataList.size());

            List<AisTrackData> toInsert = new ArrayList<>();

            for (AisTrackData data : dataList) {
                if (data.getMmsi() == null || data.getMmsi().isEmpty()) {
                    result.incrementSkipped();
                    continue;
                }

                data.setId(null);
                toInsert.add(data);
            }

            if (!toInsert.isEmpty()) {
                int batchSize = 500;
                int totalSize = toInsert.size();
                int importedCount = 0;

                for (int i = 0; i < totalSize; i += batchSize) {
                    int endIndex = Math.min(i + batchSize, totalSize);
                    List<AisTrackData> batch = toInsert.subList(i, endIndex);
                    
                    repository.saveAll(batch);
                    importedCount += batch.size();
                    
                    log.info("AIS data batch imported: {}/{} records", importedCount, totalSize);
                }

                result.setInserted(totalSize);
            }

            result.setSuccess(true);
            result.setMessage(String.format("导入完成：总计%d条，新增%d条，跳过%d条",
                    result.getTotal(), result.getInserted(), result.getSkipped()));

            log.info("AIS data import completed: {}", result.getMessage());

        } catch (IOException e) {
            log.error("Failed to read Excel file: {}", e.getMessage());
            result.setMessage("读取文件失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("Failed to import AIS data: {}", e.getMessage());
            result.setMessage("导入失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 更新实体属性
     *
     * @param existing 现有实体
     * @param data     新数据
     */
    private void updateEntity(AisTrackData existing, AisTrackData data) {
        if (data.getNameAis() != null) {
            existing.setNameAis(data.getNameAis());
        }
        if (data.getType() != null) {
            existing.setType(data.getType());
        }
        if (data.getNavyType() != null) {
            existing.setNavyType(data.getNavyType());
        }
        if (data.getCountry() != null) {
            existing.setCountry(data.getCountry());
        }
        if (data.getLongitude() != null) {
            existing.setLongitude(data.getLongitude());
        }
        if (data.getLatitude() != null) {
            existing.setLatitude(data.getLatitude());
        }
        if (data.getNavigationalStatus() != null) {
            existing.setNavigationalStatus(data.getNavigationalStatus());
        }
        if (data.getSpeedOverGround() != null) {
            existing.setSpeedOverGround(data.getSpeedOverGround());
        }
        if (data.getCourseOverGround() != null) {
            existing.setCourseOverGround(data.getCourseOverGround());
        }
    }

    /**
     * 保存单条数据
     *
     * @param data 船舶数据
     * @return 保存后的数据
     */
    @Transactional
    public AisTrackData save(AisTrackData data) {
        AisTrackData saved = repository.save(data);
        log.info("AIS data saved: mmsi={}", saved.getMmsi());
        return saved;
    }

    /**
     * 删除数据
     *
     * @param id 主键ID
     * @return 是否删除成功
     */
    @Transactional
    public boolean deleteById(Long id) {
        return repository.findById(id).map(data -> {
            repository.delete(data);
            log.info("AIS data deleted: id={}, mmsi={}", id, data.getMmsi());
            return true;
        }).orElse(false);
    }

    /**
     * 导入结果
     */
    @Getter
    @Setter
    public static class ImportResult {
        private boolean success = false;
        private String message;
        private int total;
        private int inserted;
        private int updated;
        private int skipped;

        public void incrementSkipped() {
            this.skipped++;
        }
    }
}
