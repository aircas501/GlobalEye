package com.globaleyes.crawler.repository.acled;

import com.globaleyes.crawler.pojo.entity.CastForecastData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * CAST冲突预测数据Repository
 *
 * @author CAST Data Team
 * @version 1.0.0
 */
@Repository
public interface CastForecastRepository extends JpaRepository<CastForecastData, Long> {

    /**
     * 清空所有数据（每次导入前执行）
     */
    @Modifying
    @Query("DELETE FROM CastForecastData")
    void deleteAllData();

    /**
     * 查询所有预测周期
     *
     * @return 周期列表
     */
    @Query("SELECT DISTINCT c.period FROM CastForecastData c ORDER BY c.period")
    List<String> findAllPeriods();


    /**
     * 全球趋势查询（按周期汇总）
     * 查询level='global'的数据
     *
     * @return 全球趋势数据
     */
    @Query("SELECT c.period, SUM(c.expectedForecast), SUM(c.lowForecast), SUM(c.highForecast) " +
           "FROM CastForecastData c WHERE c.level = 'global' " +
           "GROUP BY c.period ORDER BY c.period")
    List<Object[]> findGlobalTrend();

    /**
     * 风险最高国家TOP10
     * 查询level='country'的数据，按国家汇总
     *
     * @return 国家风险排名
     */
    @Query(value = "SELECT c.country, SUM(c.expected_forecast) as total_forecast " +
           "FROM cast_forecast_data c WHERE c.level = 'country' " +
           "GROUP BY c.country ORDER BY total_forecast DESC LIMIT 10",
           nativeQuery = true)
    List<Object[]> findTopCountries();

    /**
     * 风险最高省份TOP10
     * 查询level='admin1'的数据，按省份汇总
     *
     * @return 省份风险排名
     */
    @Query(value = "SELECT c.country, c.admin1, SUM(c.expected_forecast) as total_forecast " +
           "FROM cast_forecast_data c WHERE c.level = 'admin1' " +
           "GROUP BY c.country, c.admin1 ORDER BY total_forecast DESC LIMIT 10",
           nativeQuery = true)
    List<Object[]> findTopRegions();

    /**
     * 暴力类型统计
     * 查询level包含'outcome'的数据
     *
     * @return 暴力类型统计
     */
    @Query(value = "SELECT c.outcome, SUM(c.expected_forecast) as total_forecast " +
           "FROM cast_forecast_data c WHERE c.level LIKE '%outcome%' AND c.outcome IS NOT NULL " +
           "GROUP BY c.outcome ORDER BY total_forecast DESC",
           nativeQuery = true)
    List<Object[]> findViolenceTypes();

    /**
     * 风险上升最快地区
     * 计算相邻月份的增长率
     *
     * @return 风险上升地区
     */
    @Query(value = "SELECT t1.country, t1.admin1, " +
           "(SUM(t1.expected_forecast) - SUM(t2.expected_forecast)) / NULLIF(SUM(t2.expected_forecast), 0) * 100 as growth_rate " +
           "FROM cast_forecast_data t1 " +
           "JOIN cast_forecast_data t2 ON t1.country = t2.country AND t1.admin1 = t2.admin1 " +
           "AND t1.level = t2.level AND t1.period > t2.period " +
           "WHERE t1.level = 'admin1' AND t2.level = 'admin1' " +
           "GROUP BY t1.country, t1.admin1 " +
           "HAVING growth_rate > 0 " +
           "ORDER BY growth_rate DESC LIMIT 10",
           nativeQuery = true)
    List<Object[]> findRisingRiskRegions();

    /**
     * 最安全地区
     * 查询level='country'的数据，按国家汇总，升序排列
     *
     * @return 最安全地区
     */
    @Query(value = "SELECT c.country, SUM(c.expected_forecast) as total_forecast " +
           "FROM cast_forecast_data c WHERE c.level = 'country' " +
           "GROUP BY c.country ORDER BY total_forecast ASC LIMIT 10",
           nativeQuery = true)
    List<Object[]> findSafestRegions();

    /**
     * 关键风险窗口期
     * 查询level='global'的数据，识别高风险月份
     *
     * @return 风险窗口期
     */
    @Query("SELECT c.period, SUM(c.expectedForecast), SUM(c.lowForecast), SUM(c.highForecast) " +
           "FROM CastForecastData c WHERE c.level = 'global' " +
           "GROUP BY c.period ORDER BY SUM(c.expectedForecast) DESC")
    List<Object[]> findRiskWindows();

    /**
     * 国家月度趋势
     *
     * @param country 国家名称
     * @return 月度趋势
     */
    @Query("SELECT c.period, SUM(c.expectedForecast), SUM(c.lowForecast), SUM(c.highForecast) " +
           "FROM CastForecastData c WHERE c.level = 'country' AND c.country = :country " +
           "GROUP BY c.period ORDER BY c.period")
    List<Object[]> findCountryTrend(@Param("country") String country);

    /**
     * 省份月度趋势
     *
     * @param country 国家名称
     * @param admin1  省份名称
     * @return 月度趋势
     */
    @Query("SELECT c.period, SUM(c.expectedForecast), SUM(c.lowForecast), SUM(c.highForecast) " +
           "FROM CastForecastData c WHERE c.level = 'admin1' AND c.country = :country AND c.admin1 = :admin1 " +
           "GROUP BY c.period ORDER BY c.period")
    List<Object[]> findRegionTrend(@Param("country") String country, @Param("admin1") String admin1);

    /**
     * 查询指定国家的所有省份
     *
     * @param country 国家名称
     * @return 省份列表
     */
    @Query("SELECT DISTINCT c.admin1 FROM CastForecastData c WHERE c.country = :country AND c.admin1 IS NOT NULL ORDER BY c.admin1")
    List<String> findAdmin1ByCountry(@Param("country") String country);

    /**
     * 查询所有国家
     *
     * @return 国家列表
     */
    @Query("SELECT DISTINCT c.country FROM CastForecastData c WHERE c.country IS NOT NULL ORDER BY c.country")
    List<String> findAllCountries();
}
