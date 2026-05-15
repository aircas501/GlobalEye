package com.globaleyes.crawler.service.acled;

import com.alibaba.excel.EasyExcel;
import com.globaleyes.crawler.pojo.dto.acled.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.globaleyes.crawler.pojo.entity.acled.AcledAdm1Alias;
import com.globaleyes.crawler.pojo.entity.acled.AcledAdm1Boundary;
import com.globaleyes.crawler.pojo.entity.acled.AcledAggregatedRaw;
import com.globaleyes.crawler.pojo.entity.acled.AcledRegionWeekRisk;
import com.globaleyes.crawler.repository.acled.AcledAdm1AliasRepository;
import com.globaleyes.crawler.repository.acled.AcledAdm1BoundaryRepository;
import com.globaleyes.crawler.repository.acled.AcledAggregatedRawRepository;
import com.globaleyes.crawler.repository.acled.AcledRegionWeekRiskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AcledAggregatedRiskService {

    private static final String REGION_MIDDLE_EAST = "Middle East";
    private static final List<String> NAME_PROPERTY_CANDIDATES = List.of(
            "shapeName", "adm1_name", "ADM1_EN", "NAME_1", "name", "admin1", "shapeName_1"
    );
    private static final List<String> CODE_PROPERTY_CANDIDATES = List.of(
            "shapeID", "shapeISO", "ADM1_PCODE", "HASC_1", "adm1_code", "code"
    );
    private static final List<String> COUNTRY_PROPERTY_CANDIDATES = List.of(
            "shapeGroup", "country", "NAME_0", "admin0", "COUNTRY"
    );
    private static final List<String> LAT_PROPERTY_CANDIDATES = List.of("shapeLat", "lat", "latitude");
    private static final List<String> LNG_PROPERTY_CANDIDATES = List.of("shapeLon", "lon", "lng", "longitude");
    private static final List<DateTimeFormatter> DATE_PATTERNS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("d-MMMM-yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd-MMMM-yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("M/d/yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d/M/yyyy", Locale.ENGLISH)
    );
    private static final List<String> LEGEND_COLORS = List.of("#fff5eb", "#fdd0a2", "#f16913", "#d94801", "#8c2d04");

    private final AcledAggregatedRawRepository rawRepository;
    private final AcledAdm1BoundaryRepository boundaryRepository;
    private final AcledAdm1AliasRepository aliasRepository;
    private final AcledRegionWeekRiskRepository riskRepository;
    private final AcledAdminNormalizationService normalizationService;
    private final ObjectMapper objectMapper;

    @Transactional
    public AcledImportResultDTO importAggregatedExcel(MultipartFile file, boolean replaceExisting) {
        AcledImportResultDTO result = new AcledImportResultDTO();

        try {
            List<AcledAggregatedImportRow> rows = EasyExcel.read(file.getInputStream())
                    .head(AcledAggregatedImportRow.class)
                    .autoTrim(true)
                    .sheet()
                    .doReadSync();

            if (rows == null || rows.isEmpty()) {
                result.setMessage("File contains no data");
                return result;
            }

            String batchId = UUID.randomUUID().toString();
            List<AcledAggregatedRaw> entities = new ArrayList<>();
            List<LocalDate> importedWeeks = new ArrayList<>();
            int skipped = 0;
            int matched = 0;

            for (AcledAggregatedImportRow row : rows) {
                if (row == null) {
                    skipped++;
                    continue;
                }

                LocalDate week = parseWeek(row.getWeek());
                String region = trimToNull(row.getRegion());
                String country = trimToNull(row.getCountry());
                String admin1 = trimToNull(row.getAdmin1());

                if (week == null || country == null || admin1 == null) {
                    skipped++;
                    continue;
                }
                if (region != null && !REGION_MIDDLE_EAST.equalsIgnoreCase(region)) {
                    skipped++;
                    continue;
                }

                AcledAggregatedRaw entity = new AcledAggregatedRaw();
                entity.setBatchId(batchId);
                entity.setSourceFileName(file.getOriginalFilename());
                entity.setWeekStart(week);
                entity.setRegion(REGION_MIDDLE_EAST);
                entity.setCountry(country);
                entity.setAdmin1(admin1);
                entity.setNormalizedAdmin1(normalizationService.normalizeAdmin1(admin1));
                entity.setEventType(trimToNull(row.getEventType()));
                entity.setSubEventType(trimToNull(row.getSubEventType()));
                entity.setDisorderType(trimToNull(row.getDisorderType()));
                entity.setEvents(parseInteger(row.getEvents()));
                entity.setFatalities(parseInteger(row.getFatalities()));
                entity.setPopulationExposure(parseLong(row.getPopulationExposure()));
                entity.setSourceLocationId(trimToNull(row.getId()));
                entity.setCentroidLatitude(parseDouble(row.getCentroidLatitude()));
                entity.setCentroidLongitude(parseDouble(row.getCentroidLongitude()));

                Long boundaryId = resolveBoundaryId(country, admin1);
                entity.setBoundaryId(boundaryId);
                if (boundaryId != null) {
                    matched++;
                }

                entities.add(entity);
                importedWeeks.add(week);
            }

            if (replaceExisting && !importedWeeks.isEmpty()) {
                rawRepository.deleteByRegionAndWeekStartIn(REGION_MIDDLE_EAST, importedWeeks.stream().distinct().toList());
            }

            saveInBatches(entities, 500, rawRepository::saveAll);
            rebuildRiskAggregates();

            result.setSuccess(true);
            result.setBatchId(batchId);
            result.setTotal(rows.size());
            result.setInserted(entities.size());
            result.setSkipped(skipped);
            result.setMatched(matched);
            result.setUnmatched(Math.max(entities.size() - matched, 0));
            result.setMessage("Aggregated ACLED data imported");
            return result;
        } catch (IOException e) {
            log.error("Failed to read aggregated ACLED excel", e);
            result.setMessage("Failed to read Excel file: " + e.getMessage());
            return result;
        } catch (Exception e) {
            log.error("Failed to import aggregated ACLED data", e);
            result.setMessage("Failed to import aggregated data: " + e.getMessage());
            return result;
        }
    }

    @Transactional
    public AcledImportResultDTO importBoundaryGeoJson(MultipartFile file,
                                                      String country,
                                                      String boundarySource,
                                                      boolean replaceExisting) {
        AcledImportResultDTO result = new AcledImportResultDTO();

        try {
            JsonNode root = objectMapper.readTree(file.getInputStream());
            JsonNode features = root.path("features");
            if (!features.isArray() || features.isEmpty()) {
                result.setMessage("Boundary file does not contain GeoJSON features");
                return result;
            }

            List<AcledAdm1Boundary> boundaries = new ArrayList<>();
            int skipped = 0;

            for (JsonNode feature : features) {
                JsonNode properties = feature.path("properties");
                JsonNode geometry = feature.path("geometry");
                if (geometry.isMissingNode() || geometry.isNull()) {
                    skipped++;
                    continue;
                }

                String resolvedCountry = trimToNull(country);
                if (resolvedCountry == null) {
                    resolvedCountry = firstText(properties, COUNTRY_PROPERTY_CANDIDATES);
                }

                String adm1Name = firstText(properties, NAME_PROPERTY_CANDIDATES);
                if (resolvedCountry == null || adm1Name == null) {
                    skipped++;
                    continue;
                }

                double[] bbox = readOrComputeBbox(feature, geometry);
                Double centroidLat = firstDouble(properties, LAT_PROPERTY_CANDIDATES);
                Double centroidLng = firstDouble(properties, LNG_PROPERTY_CANDIDATES);
                if (centroidLat == null || centroidLng == null) {
                    centroidLat = bbox[1] + ((bbox[3] - bbox[1]) / 2.0);
                    centroidLng = bbox[0] + ((bbox[2] - bbox[0]) / 2.0);
                }

                AcledAdm1Boundary boundary = new AcledAdm1Boundary();
                boundary.setCountry(resolvedCountry);
                boundary.setBoundarySource(trimToNull(boundarySource) != null ? boundarySource : "geojson-upload");
                boundary.setBoundaryAdm1Name(adm1Name);
                boundary.setBoundaryAdm1Code(firstText(properties, CODE_PROPERTY_CANDIDATES));
                boundary.setNormalizedKey(normalizationService.normalizeAdmin1(adm1Name));
                boundary.setGeometryGeojson(objectMapper.writeValueAsString(geometry));
                boundary.setBboxJson(objectMapper.writeValueAsString(bbox));
                boundary.setCentroidLatitude(centroidLat);
                boundary.setCentroidLongitude(centroidLng);
                boundaries.add(boundary);
            }

            if (replaceExisting && country != null) {
                boundaryRepository.deleteByCountry(country);
            }

            saveInBatches(boundaries, 200, boundaryRepository::saveAll);

            if (country != null) {
                relinkRawBoundaries(country);
            } else {
                boundaries.stream()
                        .map(AcledAdm1Boundary::getCountry)
                        .filter(Objects::nonNull)
                        .distinct()
                        .forEach(this::relinkRawBoundaries);
            }

            rebuildRiskAggregates();

            result.setSuccess(true);
            result.setTotal(features.size());
            result.setInserted(boundaries.size());
            result.setSkipped(skipped);
            result.setMatched(boundaries.size());
            result.setUnmatched(0);
            result.setMessage("ADM1 boundaries imported");
            return result;
        } catch (IOException e) {
            log.error("Failed to read boundary GeoJSON", e);
            result.setMessage("Failed to read GeoJSON file: " + e.getMessage());
            return result;
        } catch (Exception e) {
            log.error("Failed to import ADM1 boundaries", e);
            result.setMessage("Failed to import boundaries: " + e.getMessage());
            return result;
        }
    }

    @Transactional
    public AcledAdm1Alias upsertAlias(AcledAdm1AliasRequest request) {
        if (request.getCountry() == null || request.getAliasName() == null || request.getBoundaryId() == null) {
            throw new IllegalArgumentException("country, aliasName and boundaryId are required");
        }

        AcledAdm1Boundary boundary = boundaryRepository.findById(request.getBoundaryId())
                .orElseThrow(() -> new IllegalArgumentException("boundaryId does not exist"));

        AcledAdm1Alias alias = aliasRepository.findByCountryAndNormalizedAlias(
                        request.getCountry(),
                        normalizationService.normalizeAdmin1(request.getAliasName())
                )
                .orElseGet(AcledAdm1Alias::new);

        alias.setCountry(request.getCountry());
        alias.setAliasName(request.getAliasName());
        alias.setNormalizedAlias(normalizationService.normalizeAdmin1(request.getAliasName()));
        alias.setBoundaryId(boundary.getId());
        alias.setMatchType(trimToNull(request.getMatchType()) != null ? request.getMatchType() : "manual");
        alias.setRemark(trimToNull(request.getRemark()));

        AcledAdm1Alias saved = aliasRepository.save(alias);
        relinkRawBoundaries(request.getCountry());
        rebuildRiskAggregates();
        return saved;
    }

    @Transactional
    public Map<String, Object> rebuildRiskAggregates() {
        List<AcledRegionMetricDTO> rows = rawRepository.findAll().stream()
                .filter(item -> item.getBoundaryId() != null)
                .collect(Collectors.groupingBy(
                        item -> new RegionWeekKey(item.getWeekStart(), item.getCountry(), item.getAdmin1(), item.getBoundaryId()),
                        Collectors.collectingAndThen(Collectors.toList(), this::toMetric)
                ))
                .values()
                .stream()
                .sorted(Comparator.comparing(AcledRegionMetricDTO::getWeekStart)
                        .thenComparing(AcledRegionMetricDTO::getCountry)
                        .thenComparing(AcledRegionMetricDTO::getAdmin1))
                .toList();

        riskRepository.deleteAllInBatch();

        if (rows.isEmpty()) {
            return Map.of("records", 0, "message", "No matched region rows available");
        }

        double maxLogEvents = rows.stream()
                .map(AcledRegionMetricDTO::getEventsTotal)
                .filter(Objects::nonNull)
                .mapToDouble(value -> Math.log1p(value))
                .max()
                .orElse(0.0);
        double maxLogFatalities = rows.stream()
                .map(AcledRegionMetricDTO::getFatalitiesTotal)
                .filter(Objects::nonNull)
                .mapToDouble(value -> Math.log1p(value))
                .max()
                .orElse(0.0);

        Map<RegionIdentityKey, List<AcledRegionMetricDTO>> grouped = rows.stream()
                .collect(Collectors.groupingBy(item -> new RegionIdentityKey(item.getCountry(), item.getAdmin1(), item.getBoundaryId())));

        List<AcledRegionWeekRisk> risks = new ArrayList<>();
        for (List<AcledRegionMetricDTO> series : grouped.values()) {
            series.sort(Comparator.comparing(AcledRegionMetricDTO::getWeekStart));
            AcledRegionWeekRisk previous = null;
            for (AcledRegionMetricDTO item : series) {
                AcledRegionWeekRisk risk = new AcledRegionWeekRisk();
                risk.setWeekStart(item.getWeekStart());
                risk.setCountry(item.getCountry());
                risk.setAdmin1(item.getAdmin1());
                risk.setBoundaryId(item.getBoundaryId());
                risk.setEventsTotal(nullSafe(item.getEventsTotal()));
                risk.setFatalitiesTotal(nullSafe(item.getFatalitiesTotal()));
                risk.setPopulationExposureMax(item.getPopulationExposureMax());

                double eventsScore = normalizeLogValue(item.getEventsTotal(), maxLogEvents);
                double fatalitiesScore = normalizeLogValue(item.getFatalitiesTotal(), maxLogFatalities);
                double riskScore = (eventsScore * 0.6) + (fatalitiesScore * 0.4);

                risk.setEventsScore(eventsScore);
                risk.setFatalitiesScore(fatalitiesScore);
                risk.setRiskScore(riskScore);

                if (previous != null) {
                    risk.setEventsWowDelta((double) (risk.getEventsTotal() - previous.getEventsTotal()));
                    risk.setFatalitiesWowDelta((double) (risk.getFatalitiesTotal() - previous.getFatalitiesTotal()));
                    risk.setRiskWowDelta(riskScore - previous.getRiskScore());
                } else {
                    risk.setEventsWowDelta((double) risk.getEventsTotal());
                    risk.setFatalitiesWowDelta((double) risk.getFatalitiesTotal());
                    risk.setRiskWowDelta(riskScore);
                }

                risks.add(risk);
                previous = risk;
            }
        }

        saveInBatches(risks, 500, riskRepository::saveAll);
        return Map.of("records", risks.size(), "message", "Risk aggregates rebuilt");
    }

    public AcledRiskMapResponseDTO getRiskMap(LocalDate week,
                                              String country,
                                              String eventType,
                                              String subEventType,
                                              String disorderType,
                                              String metric) {
        String resolvedMetric = normalizeMetric(metric);
        List<AcledRegionMetricDTO> currentRows = rawRepository.aggregateMapByWeek(week, country, eventType, subEventType, disorderType);
        List<AcledRegionMetricDTO> previousRows = rawRepository.aggregateMapByWeek(week.minusWeeks(1), country, eventType, subEventType, disorderType);

        Map<Long, AcledRegionMetricDTO> previousMap = previousRows.stream()
                .filter(item -> item.getBoundaryId() != null)
                .collect(Collectors.toMap(AcledRegionMetricDTO::getBoundaryId, item -> item, (left, right) -> left));

        Map<Long, AcledAdm1Boundary> boundaryMap = boundaryRepository.findAllById(
                currentRows.stream().map(AcledRegionMetricDTO::getBoundaryId).filter(Objects::nonNull).distinct().toList()
        ).stream().collect(Collectors.toMap(AcledAdm1Boundary::getId, item -> item));

        double[] maxima = readNormalizationMaxima();
        List<AcledRiskMapFeatureDTO> features = new ArrayList<>();
        List<Double> values = new ArrayList<>();

        for (AcledRegionMetricDTO row : currentRows) {
            AcledAdm1Boundary boundary = boundaryMap.get(row.getBoundaryId());
            if (boundary == null) {
                continue;
            }

            double riskScore = calculateRiskScore(row, maxima[0], maxima[1]);
            Double value = resolveMetricValue(resolvedMetric, row, riskScore);

            AcledRiskMapFeatureDTO feature = new AcledRiskMapFeatureDTO();
            feature.setBoundaryId(row.getBoundaryId());
            feature.setCountry(row.getCountry());
            feature.setAdmin1(row.getAdmin1());
            feature.setValue(value);
            feature.setRiskScore(riskScore);
            feature.setEventsTotal(nullSafe(row.getEventsTotal()));
            feature.setFatalitiesTotal(nullSafe(row.getFatalitiesTotal()));
            feature.setPopulationExposure(row.getPopulationExposureMax());
            feature.setCentroid(Map.of(
                    "lat", boundary.getCentroidLatitude() != null ? boundary.getCentroidLatitude() : defaultDouble(row.getCentroidLatitude()),
                    "lng", boundary.getCentroidLongitude() != null ? boundary.getCentroidLongitude() : defaultDouble(row.getCentroidLongitude())
            ));
            feature.setGeometry(readJson(boundary.getGeometryGeojson()));

            AcledRegionMetricDTO previous = previousMap.get(row.getBoundaryId());
            double previousRisk = previous != null ? calculateRiskScore(previous, maxima[0], maxima[1]) : 0.0;
            double previousValue = previous != null ? resolveMetricValue(resolvedMetric, previous, previousRisk) : 0.0;
            feature.setWowDelta(value - previousValue);

            features.add(feature);
            values.add(value);
        }

        AcledRiskMapResponseDTO response = new AcledRiskMapResponseDTO();
        response.setWeek(week);
        response.setMetric(resolvedMetric);
        response.setMode("quantile");
        response.setLegend(buildLegend(values));
        response.setFeatures(features);
        return response;
    }

    public AcledRiskTrendResponseDTO getRiskTrend(String country,
                                                  String admin1,
                                                  Integer weeks,
                                                  String eventType,
                                                  String subEventType,
                                                  String disorderType) {
        int resolvedWeeks = weeks != null && weeks > 0 ? weeks : 12;
        List<AcledRegionMetricDTO> allPoints = rawRepository.aggregateTrend(country, admin1, eventType, subEventType, disorderType);
        if (allPoints.size() > resolvedWeeks) {
            allPoints = allPoints.subList(allPoints.size() - resolvedWeeks, allPoints.size());
        }

        double[] maxima = readNormalizationMaxima();
        List<AcledRiskTrendPointDTO> series = allPoints.stream()
                .map(item -> new AcledRiskTrendPointDTO(
                        item.getWeekStart(),
                        calculateRiskScore(item, maxima[0], maxima[1]),
                        nullSafe(item.getEventsTotal()),
                        nullSafe(item.getFatalitiesTotal())
                ))
                .toList();

        AcledRiskTrendResponseDTO response = new AcledRiskTrendResponseDTO();
        response.setCountry(country);
        response.setAdmin1(admin1);
        response.setWeeks(resolvedWeeks);
        response.setSeries(series);
        return response;
    }

    public AcledRiskDetailResponseDTO getRiskDetail(LocalDate week,
                                                    String country,
                                                    String admin1,
                                                    String eventType,
                                                    String subEventType,
                                                    String disorderType) {
        List<AcledRegionMetricDTO> summaryRows = rawRepository.aggregateDetailSummary(week, country, admin1, eventType, subEventType, disorderType);
        AcledRegionMetricDTO summaryRow = summaryRows.isEmpty() ? new AcledRegionMetricDTO(week, country, admin1, null, 0L, 0L, 0L, null, null) : summaryRows.get(0);
        double[] maxima = readNormalizationMaxima();

        AcledRiskSummaryDTO summary = new AcledRiskSummaryDTO();
        summary.setRiskScore(calculateRiskScore(summaryRow, maxima[0], maxima[1]));
        summary.setEventsTotal(nullSafe(summaryRow.getEventsTotal()));
        summary.setFatalitiesTotal(nullSafe(summaryRow.getFatalitiesTotal()));
        summary.setPopulationExposure(summaryRow.getPopulationExposureMax());

        AcledRiskDetailResponseDTO response = new AcledRiskDetailResponseDTO();
        response.setCountry(country);
        response.setAdmin1(admin1);
        response.setWeek(week);
        response.setSummary(summary);
        response.setEventTypeBreakdown(filterEmptyBreakdowns(rawRepository.breakdownByEventType(week, country, admin1, eventType, subEventType, disorderType)));
        response.setSubEventTypeBreakdown(filterEmptyBreakdowns(rawRepository.breakdownBySubEventType(week, country, admin1, eventType, subEventType, disorderType)));
        response.setDisorderTypeBreakdown(filterEmptyBreakdowns(rawRepository.breakdownByDisorderType(week, country, admin1, eventType, subEventType, disorderType)));
        return response;
    }

    public AcledRiskFiltersDTO getFilters() {
        AcledRiskFiltersDTO response = new AcledRiskFiltersDTO();
        response.setCountries(rawRepository.findDistinctCountries());
        response.setEventTypes(rawRepository.findDistinctEventTypes());
        response.setSubEventTypes(rawRepository.findDistinctSubEventTypes());
        response.setDisorderTypes(rawRepository.findDistinctDisorderTypes());
        response.setWeeks(rawRepository.findDistinctWeeksDesc());
        return response;
    }

    public List<AcledUnmatchedRegionDTO> getUnmatchedRegions() {
        return rawRepository.findUnmatchedRegions();
    }

    private void relinkRawBoundaries(String country) {
        List<AcledAggregatedRaw> rows = rawRepository.findByCountry(country);
        for (AcledAggregatedRaw row : rows) {
            row.setBoundaryId(resolveBoundaryId(row.getCountry(), row.getAdmin1()));
            row.setNormalizedAdmin1(normalizationService.normalizeAdmin1(row.getAdmin1()));
        }
        saveInBatches(rows, 500, rawRepository::saveAll);
    }

    private Long resolveBoundaryId(String country, String admin1) {
        if (country == null || admin1 == null) {
            return null;
        }
        String normalizedCountry = country.trim();
        String normalizedAdmin1 = normalizationService.normalizeAdmin1(admin1);

        return aliasRepository.findByCountryAndNormalizedAlias(normalizedCountry, normalizedAdmin1)
                .map(AcledAdm1Alias::getBoundaryId)
                .or(() -> boundaryRepository.findFirstByCountryAndNormalizedKey(normalizedCountry, normalizedAdmin1)
                        .map(AcledAdm1Boundary::getId))
                .orElse(null);
    }

    private AcledRegionMetricDTO toMetric(List<AcledAggregatedRaw> rows) {
        AcledAggregatedRaw first = rows.get(0);
        long eventsTotal = rows.stream().map(AcledAggregatedRaw::getEvents).filter(Objects::nonNull).mapToLong(Integer::longValue).sum();
        long fatalitiesTotal = rows.stream().map(AcledAggregatedRaw::getFatalities).filter(Objects::nonNull).mapToLong(Integer::longValue).sum();
        long populationExposureMax = rows.stream().map(AcledAggregatedRaw::getPopulationExposure).filter(Objects::nonNull).mapToLong(Long::longValue).max().orElse(0L);
        Double centroidLat = rows.stream().map(AcledAggregatedRaw::getCentroidLatitude).filter(Objects::nonNull).findFirst().orElse(null);
        Double centroidLng = rows.stream().map(AcledAggregatedRaw::getCentroidLongitude).filter(Objects::nonNull).findFirst().orElse(null);
        return new AcledRegionMetricDTO(
                first.getWeekStart(),
                first.getCountry(),
                first.getAdmin1(),
                first.getBoundaryId(),
                eventsTotal,
                fatalitiesTotal,
                populationExposureMax,
                centroidLat,
                centroidLng
        );
    }

    private List<AcledBreakdownItemDTO> filterEmptyBreakdowns(List<AcledBreakdownItemDTO> items) {
        return items.stream()
                .filter(item -> item.getName() != null && !item.getName().isBlank())
                .toList();
    }

    private JsonNode readJson(String value) {
        try {
            return value == null ? null : objectMapper.readTree(value);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse stored GeoJSON", e);
        }
    }

    private List<AcledLegendBucketDTO> buildLegend(List<Double> values) {
        if (values.isEmpty()) {
            return List.of();
        }
        List<Double> sorted = values.stream().sorted().toList();
        List<AcledLegendBucketDTO> legend = new ArrayList<>();
        for (int i = 0; i < LEGEND_COLORS.size(); i++) {
            int startIndex = (int) Math.floor((double) i * sorted.size() / LEGEND_COLORS.size());
            int endIndex = (int) Math.min(sorted.size() - 1, Math.ceil((double) (i + 1) * sorted.size() / LEGEND_COLORS.size()) - 1);
            legend.add(new AcledLegendBucketDTO(
                    sorted.get(startIndex),
                    sorted.get(Math.max(startIndex, endIndex)),
                    LEGEND_COLORS.get(i)
            ));
        }
        return legend;
    }

    private double[] readNormalizationMaxima() {
        Object[] raw = riskRepository.findGlobalMaxima();
        long maxEvents = raw != null && raw.length > 0 && raw[0] instanceof Number number ? number.longValue() : 0L;
        long maxFatalities = raw != null && raw.length > 1 && raw[1] instanceof Number number ? number.longValue() : 0L;
        return new double[]{Math.log1p(maxEvents), Math.log1p(maxFatalities)};
    }

    private double calculateRiskScore(AcledRegionMetricDTO row, double maxLogEvents, double maxLogFatalities) {
        double eventsScore = normalizeLogValue(row.getEventsTotal(), maxLogEvents);
        double fatalitiesScore = normalizeLogValue(row.getFatalitiesTotal(), maxLogFatalities);
        return (eventsScore * 0.6) + (fatalitiesScore * 0.4);
    }

    private double normalizeLogValue(Long value, double maxLogValue) {
        if (value == null || maxLogValue <= 0) {
            return 0.0;
        }
        return Math.log1p(value) / maxLogValue;
    }

    private Double resolveMetricValue(String metric, AcledRegionMetricDTO row, double riskScore) {
        return switch (metric) {
            case "events" -> (double) nullSafe(row.getEventsTotal());
            case "fatalities" -> (double) nullSafe(row.getFatalitiesTotal());
            default -> riskScore;
        };
    }

    private String normalizeMetric(String metric) {
        if (metric == null || metric.isBlank()) {
            return "risk";
        }
        String normalized = metric.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "events", "fatalities", "risk" -> normalized;
            default -> throw new IllegalArgumentException("Unsupported metric: " + metric);
        };
    }

    private LocalDate parseWeek(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }

        if (trimmed.matches("\\d+(\\.0+)?")) {
            double excelSerial = Double.parseDouble(trimmed);
            return LocalDate.of(1899, 12, 30).plusDays((long) excelSerial);
        }

        for (DateTimeFormatter formatter : DATE_PATTERNS) {
            try {
                return LocalDate.parse(trimmed, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }

        if (trimmed.length() >= 10) {
            try {
                return LocalDate.parse(trimmed.substring(0, 10), DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }

    private Integer parseInteger(String value) {
        Long longValue = parseLong(value);
        return longValue == null ? null : longValue.intValue();
    }

    private Long parseLong(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        try {
            return new BigDecimal(trimmed.replace(",", "")).longValue();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Double parseDouble(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        try {
            return Double.parseDouble(trimmed.replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String firstText(JsonNode properties, Collection<String> candidates) {
        for (String key : candidates) {
            JsonNode node = properties.get(key);
            if (node != null && !node.isNull()) {
                String text = trimToNull(node.asText());
                if (text != null) {
                    return text;
                }
            }
        }
        return null;
    }

    private Double firstDouble(JsonNode properties, Collection<String> candidates) {
        for (String key : candidates) {
            JsonNode node = properties.get(key);
            if (node != null && node.isNumber()) {
                return node.asDouble();
            }
            if (node != null && !node.isNull()) {
                Double parsed = parseDouble(node.asText());
                if (parsed != null) {
                    return parsed;
                }
            }
        }
        return null;
    }

    private double[] readOrComputeBbox(JsonNode feature, JsonNode geometry) {
        JsonNode bboxNode = feature.get("bbox");
        if (bboxNode != null && bboxNode.isArray() && bboxNode.size() >= 4) {
            return new double[]{
                    bboxNode.get(0).asDouble(),
                    bboxNode.get(1).asDouble(),
                    bboxNode.get(2).asDouble(),
                    bboxNode.get(3).asDouble()
            };
        }

        double[] bbox = new double[]{Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        walkCoordinates(geometry.path("coordinates"), bbox);
        return bbox;
    }

    private void walkCoordinates(JsonNode coordinates, double[] bbox) {
        if (coordinates == null || coordinates.isMissingNode() || coordinates.isNull()) {
            return;
        }
        if (coordinates.isArray() && coordinates.size() >= 2 && coordinates.get(0).isNumber() && coordinates.get(1).isNumber()) {
            double lng = coordinates.get(0).asDouble();
            double lat = coordinates.get(1).asDouble();
            bbox[0] = Math.min(bbox[0], lng);
            bbox[1] = Math.min(bbox[1], lat);
            bbox[2] = Math.max(bbox[2], lng);
            bbox[3] = Math.max(bbox[3], lat);
            return;
        }
        for (JsonNode node : coordinates) {
            walkCoordinates(node, bbox);
        }
    }

    private double defaultDouble(Double value) {
        return value == null ? 0.0 : value;
    }

    private long nullSafe(Long value) {
        return value == null ? 0L : value;
    }

    private <T> void saveInBatches(List<T> items, int batchSize, java.util.function.Consumer<List<T>> consumer) {
        if (items == null || items.isEmpty()) {
            return;
        }
        for (int i = 0; i < items.size(); i += batchSize) {
            int end = Math.min(i + batchSize, items.size());
            consumer.accept(items.subList(i, end));
        }
    }

    private record RegionWeekKey(LocalDate weekStart, String country, String admin1, Long boundaryId) {
    }

    private record RegionIdentityKey(String country, String admin1, Long boundaryId) {
    }
}
