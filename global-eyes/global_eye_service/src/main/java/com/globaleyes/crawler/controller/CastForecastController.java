package com.globaleyes.crawler.controller;

import com.globaleyes.crawler.pojo.dto.acled.*;
import com.globaleyes.crawler.service.acled.CastForecastService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CAST冲突预测数据控制器
 *
 * @author CAST Data Team
 * @version 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/cast/forecast")
@Tag(name = "CAST冲突预测分析", description = "ACLED CAST报告冲突预测分析接口")
@RequiredArgsConstructor
public class CastForecastController {

    private final CastForecastService service;

    /**
     * 导入xlsx文件
     */
    @PostMapping("/import")
    @Operation(summary = "导入xlsx文件", description = "导入CAST预测数据xlsx文件，每次导入会清空现有数据")
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

        CastForecastService.ImportResult importResult = service.importFromExcel(file);
        result.put("success", importResult.isSuccess());
        result.put("message", importResult.getMessage());
        result.put("total", importResult.getTotal());

        return ResponseEntity.ok(result);
    }

    /**
     * 全球未来6个月冲突总体趋势
     */
    @GetMapping("/trend")
    @Operation(summary = "全球冲突趋势", description = "获取全球未来6个月冲突总体趋势")
    public ResponseEntity<List<CastTrendDTO>> getGlobalTrend() {
        return ResponseEntity.ok(service.getGlobalTrend());
    }

    /**
     * 风险最高国家TOP10
     */
    @GetMapping("/top-countries")
    @Operation(summary = "高风险国家TOP10", description = "获取风险最高的10个国家")
    public ResponseEntity<List<CastRegionRiskDTO>> getTopCountries() {
        return ResponseEntity.ok(service.getTopCountries());
    }

    /**
     * 风险最高省份TOP10
     */
    @GetMapping("/top-regions")
    @Operation(summary = "高风险省份TOP10", description = "获取风险最高的10个省份/行政区")
    public ResponseEntity<List<CastRegionRiskDTO>> getTopRegions() {
        return ResponseEntity.ok(service.getTopRegions());
    }

    /**
     * 最频发暴力类型
     */
    @GetMapping("/violence-types")
    @Operation(summary = "暴力类型统计", description = "获取各暴力类型的预测统计")
    public ResponseEntity<List<CastViolenceTypeDTO>> getViolenceTypes() {
        return ResponseEntity.ok(service.getViolenceTypes());
    }

    /**
     * 风险上升最快地区
     */
    @GetMapping("/rising-risk")
    @Operation(summary = "风险上升地区", description = "获取风险上升最快的地区")
    public ResponseEntity<List<CastRisingRiskDTO>> getRisingRiskRegions() {
        return ResponseEntity.ok(service.getRisingRiskRegions());
    }

    /**
     * 最安全地区
     */
    @GetMapping("/safest-regions")
    @Operation(summary = "最安全地区TOP10", description = "获取预测事件最少的10个国家")
    public ResponseEntity<List<CastRegionRiskDTO>> getSafestRegions() {
        return ResponseEntity.ok(service.getSafestRegions());
    }

    /**
     * 关键风险窗口期
     */
    @GetMapping("/risk-windows")
    @Operation(summary = "关键风险窗口期", description = "识别预测值显著偏高的月份")
    public ResponseEntity<List<CastRiskWindowDTO>> getRiskWindows() {
        return ResponseEntity.ok(service.getRiskWindows());
    }

    /**
     * 国家月度趋势
     */
    @GetMapping("/country/{country}/trend")
    @Operation(summary = "国家月度趋势", description = "获取指定国家的月度冲突趋势")
    public ResponseEntity<List<CastTrendDTO>> getCountryTrend(
            @Parameter(description = "国家名称") @PathVariable String country) {
        return ResponseEntity.ok(service.getCountryTrend(country));
    }

    /**
     * 省份月度趋势
     */
    @GetMapping("/region/{country}/{admin1}/trend")
    @Operation(summary = "省份月度趋势", description = "获取指定省份的月度冲突趋势")
    public ResponseEntity<List<CastTrendDTO>> getRegionTrend(
            @Parameter(description = "国家名称") @PathVariable String country,
            @Parameter(description = "省份名称") @PathVariable String admin1) {
        return ResponseEntity.ok(service.getRegionTrend(country, admin1));
    }

    /**
     * 获取所有国家列表
     */
    @GetMapping("/countries")
    @Operation(summary = "国家列表", description = "获取所有国家名称列表")
    public ResponseEntity<List<String>> getAllCountries() {
        return ResponseEntity.ok(service.getAllCountries());
    }

    /**
     * 获取指定国家的省份列表
     */
    @GetMapping("/countries/{country}/admin1")
    @Operation(summary = "省份列表", description = "获取指定国家的所有省份")
    public ResponseEntity<List<String>> getAdmin1ByCountry(
            @Parameter(description = "国家名称") @PathVariable String country) {
        return ResponseEntity.ok(service.getAdmin1ByCountry(country));
    }

    /**
     * 获取所有预测周期
     */
    @GetMapping("/periods")
    @Operation(summary = "预测周期列表", description = "获取所有预测周期")
    public ResponseEntity<List<String>> getAllPeriods() {
        return ResponseEntity.ok(service.getAllPeriods());
    }

    /**
     * 综合分析报告
     */
    @GetMapping("/report")
    @Operation(summary = "综合分析报告", description = "获取包含所有分析结果的综合报告")
    public ResponseEntity<Map<String, Object>> getFullReport() {
        Map<String, Object> report = new HashMap<>();

        report.put("globalTrend", service.getGlobalTrend());
        report.put("topCountries", service.getTopCountries());
        report.put("topRegions", service.getTopRegions());
        report.put("violenceTypes", service.getViolenceTypes());
        report.put("risingRiskRegions", service.getRisingRiskRegions());
        report.put("safestRegions", service.getSafestRegions());
        report.put("riskWindows", service.getRiskWindows());
        report.put("periods", service.getAllPeriods());

        return ResponseEntity.ok(report);
    }
}
