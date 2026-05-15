package com.globaleyes.crawler.controller;

import com.globaleyes.crawler.pojo.entity.neo4j.LocationNode;
import com.globaleyes.crawler.service.neo4j.LocationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 地点控制器
 * 提供地点相关的REST API
 *
 * @author RSS News Crawler Team
 * @version 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/locations")
@Tag(name = "地点管理", description = "地点信息查询接口")
public class LocationController {

    private final LocationService locationService;

    public LocationController(LocationService locationService) {
        this.locationService = locationService;
    }

    /**
     * 查询所有国家
     *
     * @return 国家列表
     */
    @GetMapping("/countries")
    @Operation(
            summary = "查询所有国家/地区",
            description = "获取Neo4j中所有type='country'的地点节点，按名称升序排列"
    )
    public ResponseEntity<Map<String, Object>> getAllCountries() {
        Map<String, Object> result = new HashMap<>();

        try {
            log.info("收到查询所有国家的请求");

            List<LocationNode> countries = locationService.getAllCountries();

            result.put("success", true);
            result.put("countries", countries);
            result.put("totalCount", countries.size());
            result.put("message", "查询成功");

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("查询所有国家失败", e);
            
            result.put("success", false);
            result.put("error", "查询失败: " + e.getMessage());

            return ResponseEntity.status(500).body(result);
        }
    }
}
