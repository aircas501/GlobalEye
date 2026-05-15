package com.globaleyes.crawler.service.neo4j;

import com.globaleyes.crawler.pojo.entity.neo4j.LocationNode;
import com.globaleyes.crawler.repository.neo4j.LocationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 地点服务
 * 提供地点相关的业务逻辑
 *
 * @author RSS News Crawler Team
 * @version 1.0.0
 */
@Slf4j
@Service
public class LocationService {

    private final LocationRepository locationRepository;

    public LocationService(LocationRepository locationRepository) {
        this.locationRepository = locationRepository;
    }

    /**
     * 查询所有国家类型的地点
     *
     * @return 国家地点列表
     */
    public List<LocationNode> getAllCountries() {
        log.info("查询所有国家类型的地点");
        List<LocationNode> countries = locationRepository.findAllCountries();
        log.info("查询到 {} 个国家", countries.size());
        return countries;
    }
}
