package com.globaleyes.crawler.service.acled;

import com.alibaba.excel.EasyExcel;
import com.globaleyes.crawler.pojo.dto.acled.*;
import com.globaleyes.crawler.pojo.entity.CastForecastData;
import com.globaleyes.crawler.repository.acled.CastForecastRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * CAST冲突预测数据服务
 *
 * @author CAST Data Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CastForecastService {

    private final CastForecastRepository repository;

    /**
     * 导入xlsx文件
     * 每次导入前清空现有数据，采用分批导入优化性能
     *
     * @param file xlsx文件
     * @return 导入结果
     */
    @Transactional
    public ImportResult importFromExcel(MultipartFile file) {
        ImportResult result = new ImportResult();

        try {
            List<CastForecastData> dataList = EasyExcel.read(file.getInputStream())
                    .head(CastForecastData.class)
                    .sheet()
                    .doReadSync();

            if (dataList.isEmpty()) {
                result.setMessage("文件内容为空");
                return result;
            }

            repository.deleteAllData();

            int batchSize = 500;
            int totalSize = dataList.size();
            int importedCount = 0;

            for (int i = 0; i < totalSize; i += batchSize) {
                int endIndex = Math.min(i + batchSize, totalSize);
                List<CastForecastData> batch = dataList.subList(i, endIndex);
                
                repository.saveAll(batch);
                importedCount += batch.size();
                
                log.info("CAST forecast data batch imported: {}/{} records", importedCount, totalSize);
            }

            result.setSuccess(true);
            result.setTotal(totalSize);
            result.setMessage(String.format("导入完成：共%d条数据", totalSize));

            log.info("CAST forecast data imported successfully: {} records", totalSize);

        } catch (IOException e) {
            log.error("Failed to read Excel file: {}", e.getMessage());
            result.setMessage("读取文件失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("Failed to import CAST data: {}", e.getMessage(), e);
            result.setMessage("导入失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 获取全球未来6个月冲突总体趋势
     *
     * @return 趋势数据列表
     */
    public List<CastTrendDTO> getGlobalTrend() {
        List<Object[]> results = repository.findGlobalTrend();
        return results.stream()
                .map(row -> new CastTrendDTO(
                        (String) row[0],
                        ((Number) row[1]).doubleValue(),
                        ((Number) row[2]).doubleValue(),
                        ((Number) row[3]).doubleValue()
                ))
                .collect(Collectors.toList());
    }

    /**
     * 获取风险最高国家TOP10
     *
     * @return 国家风险排名列表
     */
    public List<CastRegionRiskDTO> getTopCountries() {
        List<Object[]> results = repository.findTopCountries();
        List<CastRegionRiskDTO> list = new ArrayList<>();
        int rank = 1;
        int periodCount = repository.findAllPeriods().size();

        for (Object[] row : results) {
            String country = (String) row[0];
            double totalForecast = ((Number) row[1]).doubleValue();
            double avgMonthly = periodCount > 0 ? totalForecast / periodCount : 0;

            list.add(new CastRegionRiskDTO(rank++, country, null, totalForecast, avgMonthly));
        }

        return list;
    }

    /**
     * 获取风险最高省份TOP10
     *
     * @return 省份风险排名列表
     */
    public List<CastRegionRiskDTO> getTopRegions() {
        List<Object[]> results = repository.findTopRegions();
        List<CastRegionRiskDTO> list = new ArrayList<>();
        int rank = 1;
        int periodCount = repository.findAllPeriods().size();

        for (Object[] row : results) {
            String country = (String) row[0];
            String admin1 = (String) row[1];
            double totalForecast = ((Number) row[2]).doubleValue();
            double avgMonthly = periodCount > 0 ? totalForecast / periodCount : 0;

            list.add(new CastRegionRiskDTO(rank++, country, admin1, totalForecast, avgMonthly));
        }

        return list;
    }

    /**
     * 获取最频发暴力类型
     *
     * @return 暴力类型统计列表
     */
    public List<CastViolenceTypeDTO> getViolenceTypes() {
        List<Object[]> results = repository.findViolenceTypes();

        double totalAll = results.stream()
                .mapToDouble(row -> ((Number) row[1]).doubleValue())
                .sum();

        return results.stream()
                .map(row -> {
                    String outcome = (String) row[0];
                    double totalForecast = ((Number) row[1]).doubleValue();
                    double percentage = totalAll > 0 ? (totalForecast / totalAll) * 100 : 0;

                    return new CastViolenceTypeDTO(outcome, totalForecast, Math.round(percentage * 100.0) / 100.0);
                })
                .collect(Collectors.toList());
    }

    /**
     * 获取风险上升最快地区
     *
     * @return 风险上升地区列表
     */
    public List<CastRisingRiskDTO> getRisingRiskRegions() {
        List<Object[]> results = repository.findRisingRiskRegions();
        List<CastRisingRiskDTO> list = new ArrayList<>();
        int rank = 1;

        for (Object[] row : results) {
            String country = (String) row[0];
            String admin1 = (String) row[1];
            double growthRate = row[2] != null ? ((Number) row[2]).doubleValue() : 0;

            String trend = growthRate > 50 ? "急剧上升" : growthRate > 20 ? "快速上升" : "缓慢上升";

            list.add(new CastRisingRiskDTO(rank++, country, admin1, Math.round(growthRate * 100.0) / 100.0, trend));
        }

        return list;
    }

    /**
     * 获取最安全地区
     *
     * @return 最安全地区列表
     */
    public List<CastRegionRiskDTO> getSafestRegions() {
        List<Object[]> results = repository.findSafestRegions();
        List<CastRegionRiskDTO> list = new ArrayList<>();
        int rank = 1;
        int periodCount = repository.findAllPeriods().size();

        for (Object[] row : results) {
            String country = (String) row[0];
            double totalForecast = ((Number) row[1]).doubleValue();
            double avgMonthly = periodCount > 0 ? totalForecast / periodCount : 0;

            list.add(new CastRegionRiskDTO(rank++, country, null, totalForecast, avgMonthly));
        }

        return list;
    }

    /**
     * 获取关键风险窗口期
     *
     * @return 风险窗口期列表
     */
    public List<CastRiskWindowDTO> getRiskWindows() {
        List<Object[]> results = repository.findRiskWindows();

        if (results.isEmpty()) {
            return new ArrayList<>();
        }

        double avgForecast = results.stream()
                .mapToDouble(row -> ((Number) row[1]).doubleValue())
                .average()
                .orElse(0);

        return results.stream()
                .map(row -> {
                    String period = (String) row[0];
                    double expected = ((Number) row[1]).doubleValue();
                    double low = ((Number) row[2]).doubleValue();
                    double high = ((Number) row[3]).doubleValue();

                    double deviation = avgForecast > 0 ? ((expected - avgForecast) / avgForecast) * 100 : 0;

                    String riskLevel;
                    if (deviation > 20) {
                        riskLevel = "极高";
                    } else if (deviation > 10) {
                        riskLevel = "高";
                    } else if (deviation > 0) {
                        riskLevel = "中";
                    } else {
                        riskLevel = "低";
                    }

                    return new CastRiskWindowDTO(
                            period,
                            riskLevel,
                            expected,
                            low,
                            high,
                            Math.round(deviation * 100.0) / 100.0
                    );
                })
                .collect(Collectors.toList());
    }

    /**
     * 获取指定国家的月度趋势
     *
     * @param country 国家名称
     * @return 月度趋势
     */
    public List<CastTrendDTO> getCountryTrend(String country) {
        List<Object[]> results = repository.findCountryTrend(country);
        return results.stream()
                .map(row -> new CastTrendDTO(
                        (String) row[0],
                        ((Number) row[1]).doubleValue(),
                        ((Number) row[2]).doubleValue(),
                        ((Number) row[3]).doubleValue()
                ))
                .collect(Collectors.toList());
    }

    /**
     * 获取指定省份的月度趋势
     *
     * @param country 国家名称
     * @param admin1  省份名称
     * @return 月度趋势
     */
    public List<CastTrendDTO> getRegionTrend(String country, String admin1) {
        List<Object[]> results = repository.findRegionTrend(country, admin1);
        return results.stream()
                .map(row -> new CastTrendDTO(
                        (String) row[0],
                        ((Number) row[1]).doubleValue(),
                        ((Number) row[2]).doubleValue(),
                        ((Number) row[3]).doubleValue()
                ))
                .collect(Collectors.toList());
    }

    /**
     * 获取所有国家列表
     *
     * @return 国家列表
     */
    public List<String> getAllCountries() {
        return repository.findAllCountries();
    }

    /**
     * 获取指定国家的所有省份
     *
     * @param country 国家名称
     * @return 省份列表
     */
    public List<String> getAdmin1ByCountry(String country) {
        return repository.findAdmin1ByCountry(country);
    }

    /**
     * 获取所有预测周期
     *
     * @return 周期列表
     */
    public List<String> getAllPeriods() {
        return repository.findAllPeriods();
    }

    /**
     * 导入结果
     */
    public static class ImportResult {
        private boolean success = false;
        private String message;
        private int total;

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public int getTotal() {
            return total;
        }

        public void setTotal(int total) {
            this.total = total;
        }
    }
}
