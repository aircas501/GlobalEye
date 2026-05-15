package com.globaleyes.plugins.stockAnalysis.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * 股票数据工具 - 提供实时股票信息查询
 * 数据源：东方财富 API（主） + 新浪财经（备用）
 */
@Component
public class StockDataTool {

    private static final Logger logger = LoggerFactory.getLogger(StockDataTool.class);
    
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // 东方财富 API 基础 URL
    private static final String EASTMONEY_REALTIME_URL = 
        "http://push2.eastmoney.com/api/qt/stock/get";
    private static final String EASTMONEY_KLINE_URL = 
        "http://push2his.eastmoney.com/api/qt/stock/kline/get";
    
    // 新浪财经 API 基础 URL
    private static final String SINA_REALTIME_URL = 
        "http://hq.sinajs.cn/list=";

    /**
     * 获取股票实时信息
     * 
     * @param stockCode 股票代码（支持多种格式：600519, sh600519, sz000001, hk00700, gb_aapl）
     * @return 股票实时信息 JSON 字符串
     */
    @Tool(name = "get_stock_realtime_info", 
          description = "获取股票实时行情信息，包括当前价格、涨跌幅、成交量、换手率等。" +
                       "输入股票代码（如 600519, AAPL, 00700），返回详细的实时数据。")
    public String getStockRealtimeInfo(String stockCode) {
        logger.info("查询股票实时信息: {}", stockCode);
        
        try {
            // 尝试东方财富 API
            String result = getFromEastMoney(stockCode);
            if (result != null) {
                logger.info("从东方财富获取成功");
                return result;
            }
            
            // 降级到新浪财经
            logger.warn("东方财富失败，尝试新浪财经");
            result = getFromSina(stockCode);
            if (result != null) {
                logger.info("从新浪财经获取成功");
                return result;
            }
            
            return buildErrorResponse("无法获取股票数据，请检查股票代码是否正确");
            
        } catch (Exception e) {
            logger.error("获取股票数据失败", e);
            return buildErrorResponse("获取股票数据异常: " + e.getMessage());
        }
    }

    /**
     * 从东方财富获取数据
     */
    private String getFromEastMoney(String stockCode) {
        try {
            // 转换股票代码格式
            String secid = convertToSecid(stockCode);
            
            String url = String.format("%s?secid=%s&fields=f43,f44,f45,f46,f47,f48,f49,f50,f51,f52,f53,f54,f55,f56,f57,f58,f60,f61,f62,f170,f171,f172,f173,f174,f175,f176,f177,f178,f179,f180,f181,f182,f183,f184,f185,f186,f187,f188,f189,f190,f191,f192,f193,f194,f195,f196,f197,f198,f199,f200",
                    EASTMONEY_REALTIME_URL, secid);
            
            String response = restTemplate.getForObject(url, String.class);
            
            if (response == null || response.isEmpty()) {
                return null;
            }
            
            JsonNode rootNode = objectMapper.readTree(response);
            JsonNode dataNode = rootNode.path("data");
            
            if (dataNode.isMissingNode() || dataNode.isNull()) {
                return null;
            }
            
            // 解析关键数据
            Map<String, Object> result = new HashMap<>();
            result.put("stockCode", stockCode);
            result.put("stockName", dataNode.path("f57").asText());
            result.put("currentPrice", dataNode.path("f43").asDouble() / 100.0);
            result.put("changePercent", dataNode.path("f170").asDouble() / 100.0);
            result.put("changeAmount", dataNode.path("f169").asDouble() / 100.0);
            result.put("volume", dataNode.path("f47").asLong());
            result.put("turnoverRate", dataNode.path("f171").asDouble() / 100.0);
            result.put("peRatio", dataNode.path("f183").asDouble());
            result.put("marketCap", dataNode.path("f173").asLong());
            result.put("highPrice", dataNode.path("f44").asDouble() / 100.0);
            result.put("lowPrice", dataNode.path("f45").asDouble() / 100.0);
            result.put("openPrice", dataNode.path("f46").asDouble() / 100.0);
            result.put("previousClose", dataNode.path("f60").asDouble() / 100.0);
            
            return objectMapper.writeValueAsString(result);
            
        } catch (Exception e) {
            logger.warn("东方财富 API 调用失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从新浪财经获取数据
     */
    private String getFromSina(String stockCode) {
        try {
            String sinaCode = convertToSinaCode(stockCode);
            String url = SINA_REALTIME_URL + sinaCode;
            
            String response = restTemplate.getForObject(url, String.class);
            
            if (response == null || response.isEmpty()) {
                return null;
            }
            
            // 新浪财经返回格式：var hq_str_sh600519="贵州茅台,1800.000,..."
            String[] parts = response.split("=");
            if (parts.length < 2) {
                return null;
            }
            
            String data = parts[1].trim().replaceAll("\"", "").replaceAll(";", "");
            String[] fields = data.split(",");
            
            if (fields.length < 30) {
                return null;
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("stockCode", stockCode);
            result.put("stockName", fields[0]);
            result.put("currentPrice", Double.parseDouble(fields[3]));
            result.put("previousClose", Double.parseDouble(fields[2]));
            result.put("openPrice", Double.parseDouble(fields[1]));
            result.put("highPrice", Double.parseDouble(fields[4]));
            result.put("lowPrice", Double.parseDouble(fields[5]));
            result.put("volume", Long.parseLong(fields[8]));
            result.put("amount", Double.parseDouble(fields[9]));
            
            double changePercent = ((Double.parseDouble(fields[3]) - Double.parseDouble(fields[2])) 
                    / Double.parseDouble(fields[2])) * 100;
            result.put("changePercent", Math.round(changePercent * 100.0) / 100.0);
            
            return objectMapper.writeValueAsString(result);
            
        } catch (Exception e) {
            logger.warn("新浪财经 API 调用失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 获取股票历史K线数据
     * 
     * @param stockCode 股票代码
     * @param days 天数（默认30天）
     * @return K线数据 JSON 字符串
     */
    @Tool(name = "get_stock_historical_data",
          description = "获取股票历史K线数据，包括每日的开盘价、收盘价、最高价、最低价、成交量。" +
                       "用于分析股票近期走势。")
    public String getHistoricalData(String stockCode, Integer days) {
        logger.info("查询股票历史数据: {}, 天数: {}", stockCode, days);
        
        if (days == null || days <= 0) {
            days = 30;
        }
        
        try {
            String secid = convertToSecid(stockCode);
            
            String url = String.format("%s?secid=%s&fields1=f1,f2,f3,f4,f5,f6&fields2=f51,f52,f53,f54,f55,f56,f57,f58&klt=101&fqt=1&lmt=%d",
                    EASTMONEY_KLINE_URL, secid, days);
            
            String response = restTemplate.getForObject(url, String.class);
            
            if (response == null || response.isEmpty()) {
                return buildErrorResponse("无法获取历史数据");
            }
            
            JsonNode rootNode = objectMapper.readTree(response);
            JsonNode klinesNode = rootNode.path("data").path("klines");
            
            if (!klinesNode.isArray()) {
                return buildErrorResponse("历史数据格式错误");
            }
            
            // 解析K线数据
            // 格式：日期,开盘,收盘,最高,最低,成交量,成交额,振幅,涨跌幅,涨跌额,换手率
            return objectMapper.writeValueAsString(klinesNode);
            
        } catch (Exception e) {
            logger.error("获取历史数据失败", e);
            return buildErrorResponse("获取历史数据异常: " + e.getMessage());
        }
    }

    /**
     * 获取行业板块信息
     * 
     * @param stockCode 股票代码
     * @return 行业信息 JSON 字符串
     */
    @Tool(name = "get_industry_info",
          description = "获取股票所属的行业板块信息，包括行业名称、行业整体涨跌幅、板块热度等。")
    public String getIndustryInfo(String stockCode) {
        logger.info("查询行业信息: {}", stockCode);
        
        // TODO: 实现行业信息查询
        // 这里可以调用东方财富的行业接口
        
        Map<String, Object> result = new HashMap<>();
        result.put("stockCode", stockCode);
        result.put("industry", "待实现");
        result.put("industryChangePercent", 0.0);
        result.put("message", "行业信息查询功能开发中");
        
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            return buildErrorResponse("获取行业信息失败");
        }
    }

    // ========== 辅助方法 ==========

    /**
     * 转换为东方财富的 secid 格式
     * 1.XXXXXX - 上海
     * 0.XXXXXX - 深圳
     * 116.HKXXXX - 港股
     * 105.XXXX - 美股
     */
    private String convertToSecid(String stockCode) {
        stockCode = stockCode.toUpperCase().trim();
        
        // A股 - 上海
        if (stockCode.matches("^60\\d{4}$") || stockCode.startsWith("SH")) {
            String code = stockCode.replaceAll("[^0-9]", "");
            return "1." + code;
        }
        
        // A股 - 深圳
        if (stockCode.matches("^(00|30)\\d{4}$") || stockCode.startsWith("SZ")) {
            String code = stockCode.replaceAll("[^0-9]", "");
            return "0." + code;
        }
        
        // 港股
        if (stockCode.matches("^\\d{4,5}$") && stockCode.startsWith("HK")) {
            String code = stockCode.replaceAll("[^0-9]", "");
            return "116." + code;
        }
        
        // 美股
        if (stockCode.matches("^[A-Z]{1,5}$")) {
            return "105." + stockCode;
        }
        
        // 默认尝试上海
        if (stockCode.matches("^\\d{6}$")) {
            return "1." + stockCode;
        }
        
        return "1." + stockCode;
    }

    /**
     * 转换为新浪财经代码格式
     */
    private String convertToSinaCode(String stockCode) {
        stockCode = stockCode.toUpperCase().trim();
        
        // A股 - 上海
        if (stockCode.matches("^60\\d{4}$") || stockCode.startsWith("SH")) {
            String code = stockCode.replaceAll("[^0-9]", "");
            return "sh" + code;
        }
        
        // A股 - 深圳
        if (stockCode.matches("^(00|30)\\d{4}$") || stockCode.startsWith("SZ")) {
            String code = stockCode.replaceAll("[^0-9]", "");
            return "sz" + code;
        }
        
        // 港股
        if (stockCode.startsWith("HK")) {
            String code = stockCode.replaceAll("[^0-9]", "");
            return "rt_hk" + code;
        }
        
        // 美股
        if (stockCode.matches("^[A-Z]{1,5}$")) {
            return "gb_" + stockCode.toLowerCase();
        }
        
        // 默认
        return "sh" + stockCode;
    }

    /**
     * 构建错误响应
     */
    private String buildErrorResponse(String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("error", true);
        error.put("message", message);
        
        try {
            return objectMapper.writeValueAsString(error);
        } catch (Exception e) {
            return "{\"error\":true,\"message\":\"" + message + "\"}";
        }
    }
}
