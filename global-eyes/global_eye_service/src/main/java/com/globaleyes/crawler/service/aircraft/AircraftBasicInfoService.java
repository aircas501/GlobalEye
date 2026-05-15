package com.globaleyes.crawler.service.aircraft;

import com.globaleyes.crawler.pojo.entity.AircraftBasicInfo;
import com.globaleyes.crawler.repository.aircraft.AircraftBasicInfoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 飞机基础信息服务
 *
 * @author Aircraft Info Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AircraftBasicInfoService {

    private final AircraftBasicInfoRepository repository;
    private final AircraftModelCacheService cacheService;

    public Page<AircraftBasicInfo> search(String icao24, String country, String model, String typeCode, Pageable pageable) {
        return repository.search(icao24, country, model, typeCode, pageable);
    }

    public Optional<AircraftBasicInfo> findById(Long id) {
        return repository.findById(id);
    }

    public Optional<AircraftBasicInfo> findByIcao24(String icao24) {
        return repository.findByIcao24(icao24);
    }

    public Optional<String> getModelByIcao24(String icao24) {
        return cacheService.getModel(icao24);
    }

    @Transactional
    public AircraftBasicInfo save(AircraftBasicInfo info) {
        AircraftBasicInfo saved = repository.save(info);
        cacheService.put(saved.getIcao24(), saved.getModel());
        log.info("Aircraft info saved: icao24={}, model={}", saved.getIcao24(), saved.getModel());
        return saved;
    }

    @Transactional
    public Optional<AircraftBasicInfo> update(Long id, AircraftBasicInfo info) {
        return repository.findById(id).map(existing -> {
            String oldIcao24 = existing.getIcao24();
            
            existing.setIcao24(info.getIcao24());
            existing.setCategoryDescription(info.getCategoryDescription());
            existing.setCountry(info.getCountry());
            existing.setIcaoAircraftClass(info.getIcaoAircraftClass());
            existing.setModel(info.getModel());
            existing.setRegistration(info.getRegistration());
            existing.setTypeCode(info.getTypeCode());
            
            AircraftBasicInfo saved = repository.save(existing);
            
            if (!oldIcao24.equals(saved.getIcao24())) {
                cacheService.evict(oldIcao24);
            }
            cacheService.evict(saved.getIcao24());
            
            log.info("Aircraft info updated: id={}, icao24={}", saved.getId(), saved.getIcao24());
            return saved;
        });
    }

    @Transactional
    public boolean deleteById(Long id) {
        return repository.findById(id).map(info -> {
            cacheService.evict(info.getIcao24());
            repository.delete(info);
            log.info("Aircraft info deleted: id={}, icao24={}", id, info.getIcao24());
            return true;
        }).orElse(false);
    }
}
