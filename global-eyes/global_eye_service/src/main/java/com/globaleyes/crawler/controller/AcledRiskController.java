package com.globaleyes.crawler.controller;

import com.globaleyes.crawler.pojo.dto.acled.*;
import com.globaleyes.crawler.pojo.entity.acled.AcledAdm1Alias;
import com.globaleyes.crawler.service.acled.AcledAggregatedRiskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/acled/risk")
@RequiredArgsConstructor
@Tag(name = "ACLED Risk Map", description = "Middle East ADM1 risk map data import and query APIs")
public class AcledRiskController {

    private final AcledAggregatedRiskService riskService;

    @PostMapping("/import/aggregated")
    @Operation(summary = "Import ACLED aggregated weekly data")
    public ResponseEntity<AcledImportResultDTO> importAggregated(
            @Parameter(description = "ACLED aggregated xlsx file") @RequestParam("file") MultipartFile file,
            @Parameter(description = "Replace existing rows for imported weeks") @RequestParam(defaultValue = "true") boolean replaceExisting) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(errorResult("File is empty"));
        }
        return ResponseEntity.ok(riskService.importAggregatedExcel(file, replaceExisting));
    }

    @PostMapping("/import/boundary")
    @Operation(summary = "Import ADM1 boundary GeoJSON")
    public ResponseEntity<AcledImportResultDTO> importBoundary(
            @Parameter(description = "ADM1 boundary GeoJSON file") @RequestParam("file") MultipartFile file,
            @Parameter(description = "Country name, required when GeoJSON lacks country property") @RequestParam(required = false) String country,
            @Parameter(description = "Boundary source label") @RequestParam(required = false) String boundarySource,
            @Parameter(description = "Replace existing boundaries for the country") @RequestParam(defaultValue = "true") boolean replaceExisting) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(errorResult("File is empty"));
        }
        return ResponseEntity.ok(riskService.importBoundaryGeoJson(file, country, boundarySource, replaceExisting));
    }

    @PostMapping("/alias")
    @Operation(summary = "Create or update an ADM1 alias mapping")
    public ResponseEntity<AcledAdm1Alias> upsertAlias(@RequestBody AcledAdm1AliasRequest request) {
        return ResponseEntity.ok(riskService.upsertAlias(request));
    }

    @PostMapping("/rebuild")
    @Operation(summary = "Rebuild weekly risk aggregates")
    public ResponseEntity<Map<String, Object>> rebuild() {
        return ResponseEntity.ok(riskService.rebuildRiskAggregates());
    }

    @GetMapping("/map")
    @Operation(summary = "Get ADM1 weekly risk map")
    public ResponseEntity<AcledRiskMapResponseDTO> getRiskMap(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String subEventType,
            @RequestParam(required = false) String disorderType,
            @RequestParam(defaultValue = "risk") String metric) {
        return ResponseEntity.ok(riskService.getRiskMap(week, country, eventType, subEventType, disorderType, metric));
    }

    @GetMapping("/trend")
    @Operation(summary = "Get risk trend for one ADM1 region")
    public ResponseEntity<AcledRiskTrendResponseDTO> getRiskTrend(
            @RequestParam String country,
            @RequestParam String admin1,
            @RequestParam(defaultValue = "12") Integer weeks,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String subEventType,
            @RequestParam(required = false) String disorderType) {
        return ResponseEntity.ok(riskService.getRiskTrend(country, admin1, weeks, eventType, subEventType, disorderType));
    }

    @GetMapping("/detail")
    @Operation(summary = "Get weekly risk detail for one ADM1 region")
    public ResponseEntity<AcledRiskDetailResponseDTO> getRiskDetail(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week,
            @RequestParam String country,
            @RequestParam String admin1,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String subEventType,
            @RequestParam(required = false) String disorderType) {
        return ResponseEntity.ok(riskService.getRiskDetail(week, country, admin1, eventType, subEventType, disorderType));
    }

    @GetMapping("/filters")
    @Operation(summary = "Get available filter options")
    public ResponseEntity<AcledRiskFiltersDTO> getFilters() {
        return ResponseEntity.ok(riskService.getFilters());
    }

    @GetMapping("/unmatched")
    @Operation(summary = "List ADM1 rows that are not matched to boundaries")
    public ResponseEntity<List<AcledUnmatchedRegionDTO>> getUnmatched() {
        return ResponseEntity.ok(riskService.getUnmatchedRegions());
    }

    private AcledImportResultDTO errorResult(String message) {
        AcledImportResultDTO result = new AcledImportResultDTO();
        result.setSuccess(false);
        result.setMessage(message);
        return result;
    }
}
