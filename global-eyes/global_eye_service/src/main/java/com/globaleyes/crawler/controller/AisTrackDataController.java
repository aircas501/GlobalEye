package com.globaleyes.crawler.controller;

import com.globaleyes.crawler.pojo.entity.AisTrackData;
import com.globaleyes.crawler.service.ais.AisTrackDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AIS船舶追踪数据控制器
 *
 * @author AIS Data Team
 * @version 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/ais/track")
@Tag(name = "AIS船舶追踪数据管理", description = "AIS船舶追踪数据的导入和查询接口")
@RequiredArgsConstructor
public class AisTrackDataController {

    private final AisTrackDataService service;

    /**
     * 分页查询（支持name_ais模糊搜索，navy_type、country精确查询，更新时间范围查询）
     */
    @GetMapping
    @Operation(summary = "分页查询", description = "支持name_ais模糊搜索，navy_type、country精确查询，更新时间范围查询")
    public ResponseEntity<Page<AisTrackData>> search(
            @Parameter(description = "船舶名称（模糊查询）") @RequestParam(required = false) String nameAis,
            @Parameter(description = "船舶细分类型（精确查询）") @RequestParam(required = false) String navyType,
            @Parameter(description = "船旗国（精确查询）") @RequestParam(required = false) String country,
            @Parameter(description = "更新时间开始范围（格式：yyyy-MM-ddTHH:mm:ss）") 
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @Parameter(description = "更新时间结束范围（格式：yyyy-MM-ddTHH:mm:ss）") 
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @Parameter(description = "页码") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<AisTrackData> result = service.search(nameAis, navyType, country, startTime, endTime, pageable);
        return ResponseEntity.ok(result);
    }

    /**
     * 分页查询所有船舶的最新数据
     */
    @GetMapping("/latest")
    @Operation(summary = "查询最新数据", description = "分页查询所有船舶的最新数据（按MMSI分组，取每组中创建时间最新的记录），支持按船舶细分类型和船旗国筛选")
    public ResponseEntity<Page<AisTrackData>> findLatest(
            @Parameter(description = "船舶细分类型（精确查询）") @RequestParam(required = false) String navyType,
            @Parameter(description = "船旗国（精确查询）") @RequestParam(required = false) String country,
            @Parameter(description = "页码") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.unsorted());
        Page<AisTrackData> result = service.findLatestData(navyType, country, pageable);
        return ResponseEntity.ok(result);
    }

    /**
     * 根据ID查询
     */
    @GetMapping("/{id}")
    @Operation(summary = "根据ID查询", description = "根据ID查询船舶追踪数据")
    public ResponseEntity<AisTrackData> findById(
            @Parameter(description = "ID") @PathVariable Long id) {
        return service.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 根据MMSI查询
     */
    @GetMapping("/mmsi/{mmsi}")
    @Operation(summary = "根据MMSI查询", description = "根据海上移动服务标识查询船舶追踪数据")
    public ResponseEntity<AisTrackData> findByMmsi(
            @Parameter(description = "MMSI") @PathVariable String mmsi) {
        return service.findByMmsi(mmsi)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 查询所有船舶类型列表
     */
    @GetMapping("/navy-types")
    @Operation(summary = "查询船舶类型列表", description = "查询所有船舶细分类型列表")
    public ResponseEntity<List<String>> findAllNavyTypes() {
        return ResponseEntity.ok(service.findAllNavyTypes());
    }

    /**
     * 查询所有国家列表
     */
    @GetMapping("/countries")
    @Operation(summary = "查询国家列表", description = "查询所有船旗国列表")
    public ResponseEntity<List<String>> findAllCountries() {
        return ResponseEntity.ok(service.findAllCountries());
    }

    /**
     * 按精确更新时间查询
     */
    @GetMapping("/by-updated-at")
    @Operation(summary = "按精确更新时间查询", description = "查询指定更新时间的船舶追踪数据")
    public ResponseEntity<Page<AisTrackData>> findByUpdatedAt(
            @Parameter(description = "更新时间（格式：yyyy-MM-ddTHH:mm:ss）") 
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime updatedAt,
            @Parameter(description = "页码") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("updatedAt").descending());
        Page<AisTrackData> result = service.findByUpdatedAt(updatedAt, pageable);
        return ResponseEntity.ok(result);
    }

    /**
     * 导入xlsx文件
     */
    @PostMapping("/import")
    @Operation(summary = "导入xlsx文件", description = "导入xlsx文件，表头名称需与数据库字段名称一致")
    public ResponseEntity<Map<String, Object>> importFromExcel(
            @Parameter(description = "xlsx文件") @RequestParam("file") MultipartFile file) {
        Map<String, Object> result = new HashMap<>();

        if (file.isEmpty()) {
            result.put("success", false);
            result.put("message", "文件为空");
            return ResponseEntity.badRequest().body(result);
        }

        String filename = file.getOriginalFilename();
        if (filename == null || (!filename.endsWith(".xlsx") && !filename.endsWith(".xls"))) {
            result.put("success", false);
            result.put("message", "文件格式不支持，仅支持xlsx和xls格式");
            return ResponseEntity.badRequest().body(result);
        }

        AisTrackDataService.ImportResult importResult = service.importFromExcel(file);
        result.put("success", importResult.isSuccess());
        result.put("message", importResult.getMessage());
        result.put("total", importResult.getTotal());
        result.put("inserted", importResult.getInserted());
        result.put("updated", importResult.getUpdated());
        result.put("skipped", importResult.getSkipped());

        return ResponseEntity.ok(result);
    }

    /**
     * 新增单条数据
     */
    @PostMapping
    @Operation(summary = "新增", description = "新增船舶追踪数据")
    public ResponseEntity<Map<String, Object>> save(@RequestBody AisTrackData data) {
        Map<String, Object> result = new HashMap<>();

        if (data.getMmsi() == null || data.getMmsi().isEmpty()) {
            result.put("success", false);
            result.put("message", "MMSI不能为空");
            return ResponseEntity.badRequest().body(result);
        }

        if (service.findByMmsi(data.getMmsi()).isPresent()) {
            result.put("success", false);
            result.put("message", "MMSI已存在: " + data.getMmsi());
            return ResponseEntity.badRequest().body(result);
        }

        AisTrackData saved = service.save(data);
        result.put("success", true);
        result.put("data", saved);
        return ResponseEntity.ok(result);
    }

    /**
     * 删除数据
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除", description = "删除船舶追踪数据")
    public ResponseEntity<Map<String, Object>> delete(
            @Parameter(description = "ID") @PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();

        if (service.deleteById(id)) {
            result.put("success", true);
            result.put("message", "删除成功");
            return ResponseEntity.ok(result);
        } else {
            result.put("success", false);
            result.put("message", "数据不存在");
            return ResponseEntity.notFound().build();
        }
    }
}
