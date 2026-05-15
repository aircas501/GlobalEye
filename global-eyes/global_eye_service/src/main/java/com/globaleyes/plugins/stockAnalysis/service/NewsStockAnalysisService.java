package com.globaleyes.plugins.stockAnalysis.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.globaleyes.plugins.stockAnalysis.tool.StockDataTool;
import com.globaleyes.plugins.stockAnalysis.model.NewsStockAnalysisRequest;
import com.globaleyes.plugins.stockAnalysis.model.NewsStockAnalysisResult;
import com.globaleyes.plugins.stockAnalysis.util.ConfidenceCalculator;
import com.globaleyes.plugins.stockAnalysis.util.StockTradingTimeUtil;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.tool.Toolkit;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 新闻股票分析服务
 */
@Service
public class NewsStockAnalysisService {

    private static final Logger logger = LoggerFactory.getLogger(NewsStockAnalysisService.class);

    @Autowired
    private DashScopeChatModel chatModel;

    @Autowired
    private StockDataTool stockDataTool;

    private ReActAgent newsStockAnalyzer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 初始化新闻股票分析 Agent
     */
    @PostConstruct
    public void initializeAgent() {
        logger.info("初始化新闻股票分析 Agent...");

        String systemPrompt = buildSystemPrompt();
        
        // 创建工具集并注册股票数据工具
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(stockDataTool);
        logger.info("已注册股票数据工具");

        newsStockAnalyzer = ReActAgent.builder()
                .name("NewsStockAnalyzer")
                .sysPrompt(systemPrompt)
                .model(chatModel)
                .toolkit(toolkit)  // 挂载工具
                .build();

        logger.info("新闻股票分析 Agent 初始化完成（带股票数据工具）");
    }

    /**
     * 构建系统提示词（优化版 - 包含 Few-Shot 示例和思维链）
     */
    private String buildSystemPrompt() {
        return "你是资深的金融分析师和新闻解读专家，擅长从各类新闻中分析对股票市场的影响。\n\n" +
                "【分析流程 - 请按以下步骤思考】\n" +
                "第一步：新闻分类与实体提取\n" +
                "  - 判断新闻领域（科技/政策/社会/经济/其他）\n" +
                "  - 识别关键实体（公司名称、行业、人物、地点、事件类型）\n" +
                "  - 提取股票代码或公司名称\n\n" +
                "第二步：关联性分析\n" +
                "  - 直接关联：新闻明确提到的上市公司\n" +
                "  - 间接关联：行业影响、产业链传导、政策受益/受损\n" +
                "  - 地域关联：特定地区的企业\n" +
                "  - 情绪关联：影响市场整体情绪的事件\n\n" +
                "第三步：影响评估\n" +
                "  - 情感倾向：利好/利空/中性\n" +
                "  - 影响强度：重大/中等/轻微\n" +
                "  - 持续时间：短期(1-3天)/中期(1-2周)/长期(1月+)\n" +
                "  - 考虑当前市场环境（牛市/熊市/震荡）\n\n" +
                "第四步：置信度评估\n" +
                "  - 新闻清晰度：是否明确提到具体公司\n" +
                "  - 信息完整性：是否有足够的数据支撑判断\n" +
                "  - 历史可参考性：是否有类似案例\n" +
                "  - 时效性：新闻发布时间距今多久\n\n" +
                "第五步：风险提示\n" +
                "  - 指出不确定性因素\n" +
                "  - 说明分析的局限性\n" +
                "  - 提醒用户独立判断\n\n" +
                "【Few-Shot 示例 - 请参考以下格式】\n\n" +
                "示例 1：科技产品发布\n" +
                "输入：苹果发布新款iPhone，搭载自研AI芯片，性能提升50%\n" +
                "输出：\n" +
                "{\n" +
                "  \"newsInfo\": {\n" +
                "    \"title\": \"苹果发布新款iPhone\",\n" +
                "    \"summary\": \"苹果发布搭载AI芯片的新款iPhone\",\n" +
                "    \"source\": \"科技媒体\"\n" +
                "  },\n" +
                "  \"affectedStocks\": [\n" +
                "    {\n" +
                "      \"stockCode\": \"AAPL\",\n" +
                "      \"stockName\": \"苹果公司\",\n" +
                "      \"market\": \"美股\",\n" +
                "      \"relationType\": \"DIRECT_MENTION\",\n" +
                "      \"relevanceScore\": 0.95,\n" +
                "      \"impactDirection\": \"POSITIVE\",\n" +
                "      \"impactSeverity\": \"HIGH\",\n" +
                "      \"predictedChangePercent\": 3.5,\n" +
                "      \"duration\": \"SHORT_TERM\",\n" +
                "      \"confidence\": 0.85,\n" +
                "      \"reasoning\": \"新产品发布通常提振股价。AI芯片是重大创新，可能带动销量增长。当前估值合理，有上涨空间。\",\n" +
                "      \"keyFactors\": [\"产品创新\", \"市场预期\", \"技术领先\", \"估值合理\"]\n" +
                "    },\n" +
                "    {\n" +
                "      \"stockCode\": \"TSM\",\n" +
                "      \"stockName\": \"台积电\",\n" +
                "      \"market\": \"美股\",\n" +
                "      \"relationType\": \"SUPPLY_CHAIN\",\n" +
                "      \"relevanceScore\": 0.7,\n" +
                "      \"impactDirection\": \"POSITIVE\",\n" +
                "      \"impactSeverity\": \"MODERATE\",\n" +
                "      \"predictedChangePercent\": 1.5,\n" +
                "      \"duration\": \"MEDIUM_TERM\",\n" +
                "      \"confidence\": 0.7,\n" +
                "      \"reasoning\": \"作为苹果芯片代工厂，将受益于新品量产订单增加。\",\n" +
                "      \"keyFactors\": [\"供应链关系\", \"订单增长\"]\n" +
                "    }\n" +
                "  ],\n" +
                "  \"marketImpact\": {\n" +
                "    \"marketSentiment\": \"SLIGHTLY_POSITIVE\",\n" +
                "    \"affectedSectors\": [\"科技\", \"消费电子\", \"半导体\"],\n" +
                "    \"systemicRisk\": false,\n" +
                "    \"focusPoints\": [\"关注供应链企业\", \"观察市场反应\"]\n" +
                "  },\n" +
                "  \"overallConfidence\": 0.8,\n" +
                "  \"analysisSummary\": \"该新闻对苹果及产业链股票有积极影响，建议关注相关概念股\"\n" +
                "}\n\n" +
                "示例 2：政策类新闻\n" +
                "输入：央行宣布降准0.5个百分点，释放流动性约1万亿元\n" +
                "输出：\n" +
                "{\n" +
                "  \"affectedStocks\": [\n" +
                "    {\n" +
                "      \"stockCode\": \"601398.SH\",\n" +
                "      \"stockName\": \"工商银行\",\n" +
                "      \"market\": \"A股\",\n" +
                "      \"relationType\": \"POLICY_IMPACT\",\n" +
                "      \"relevanceScore\": 0.8,\n" +
                "      \"impactDirection\": \"POSITIVE\",\n" +
                "      \"impactSeverity\": \"MODERATE\",\n" +
                "      \"predictedChangePercent\": 2.0,\n" +
                "      \"duration\": \"SHORT_TERM\",\n" +
                "      \"confidence\": 0.75,\n" +
                "      \"reasoning\": \"降准释放流动性，银行可贷资金增加，利好银行业务。但需注意息差收窄风险。\",\n" +
                "      \"keyFactors\": [\"流动性增加\", \"信贷扩张\", \"息差压力\"]\n" +
                "    }\n" +
                "  ],\n" +
                "  \"marketImpact\": {\n" +
                "    \"marketSentiment\": \"POSITIVE\",\n" +
                "    \"affectedSectors\": [\"银行\", \"房地产\", \"基建\"],\n" +
                "    \"systemicRisk\": false,\n" +
                "    \"focusPoints\": [\"关注金融板块\", \"观察资金流向\"]\n" +
                "  },\n" +
                "  \"overallConfidence\": 0.75,\n" +
                "  \"analysisSummary\": \"降准政策整体利好市场，特别是金融和地产板块\"\n" +
                "}\n\n" +
                "【输出格式要求 - 必须严格遵守】\n" +
                "你必须输出严格的 JSON 格式，不要包含任何其他文字。\n" +
                "所有字段名必须使用英文，值可以使用中文。\n" +
                "如果某个字段没有信息，使用 null 或空数组 []。\n\n" +
                "【重要提醒】\n" +
                "1. reasoning 字段必须有详细的分析逻辑，至少 50 字\n" +
                "2. keyFactors 必须是字符串数组，列出 2-5 个关键因素\n" +
                "3. confidence 范围 0-1，根据分析依据的充分程度给出\n" +
                "4. predictedChangePercent 为百分比数值（正数上涨，负数下跌）\n" +
                "5. 如果没有明确的股票关联，affectedStocks 返回空数组 []，不要编造\n" +
                "6. 如果新闻与股市完全无关，也返回空数组，并在 analysisSummary 中说明原因";
    }

    /**
     * 分析新闻对股票的影响
     */
    public NewsStockAnalysisResult analyze(NewsStockAnalysisRequest request) {
        logger.info("收到新闻股票分析请求，标题: {}, 发布时间: {}", 
                request.getTitle(), request.getPublishTime());

        try {
            // 1. 构建用户消息（包含时间上下文）
            String userMessage = buildUserMessage(request);

            // 2. 调用 Agent 分析
            Msg response = newsStockAnalyzer.call(
                    Msg.builder()
                            .textContent(userMessage)
                            .role(MsgRole.USER)
                            .build()
            ).block();

            if (response == null) {
                logger.error("Agent 返回为空");
                throw new RuntimeException("分析服务暂时不可用");
            }

            String content = response.getTextContent();
            logger.debug("Agent 原始响应: {}", content);

            // 3. 解析 JSON 结果
            NewsStockAnalysisResult result = parseAnalysisResult(content, request);
            
            // 4. 补充时间分析信息
            enrichTimeAnalysis(result, request.getPublishTime());
            
            // 5. 计算并更新置信度
            double calculatedConfidence = calculateAndUpdateConfidence(result, request, content);
            logger.info("置信度评估: Agent给出={}, 计算值={}, 采用={}",
                    result.getOverallConfidence(), calculatedConfidence,
                    Math.max(result.getOverallConfidence(), calculatedConfidence));
            
            // 6. 记录解析结果统计
            int stockCount = result.getAffectedStocks() != null ? result.getAffectedStocks().size() : 0;
            logger.info("新闻股票分析完成，识别到 {} 只受影响股票，总体置信度: {}", 
                    stockCount, result.getOverallConfidence());
            
            if (stockCount > 0) {
                logger.info("第一只股票: code={}, name={}, reasoning={}",
                        result.getAffectedStocks().get(0).getStockCode(),
                        result.getAffectedStocks().get(0).getStockName(),
                        result.getAffectedStocks().get(0).getReasoning());
            } else {
                // 没有识别到股票，设置友好提示
                logger.info("未识别到相关股票，设置提示信息");
                result.setAnalysisSummary(
                    "⚠️ 无法分析\n\n" +
                    "根据提供的新闻内容，未能识别出与股票市场相关的信息。\n\n" +
                    "可能的原因：\n" +
                    "1. 新闻内容与股票市场关联度较低\n" +
                    "2. 未提及具体的上市公司或行业\n" +
                    "3. 事件对股市影响不明确\n\n" +
                    "建议：\n" +
                    "• 尝试提供包含具体公司名、行业或经济政策的新闻\n" +
                    "• 确保新闻内容完整且信息丰富\n" +
                    "• 关注财经类、政策类、科技类等与股市相关的新闻"
                );
                result.setOverallConfidence(0.0);
            }

            return result;

        } catch (Exception e) {
            logger.error("新闻股票分析失败", e);
            throw new RuntimeException("分析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 构建用户消息
     */
    private String buildUserMessage(NewsStockAnalysisRequest request) {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        StringBuilder message = new StringBuilder();
        message.append("【时间上下文】\n");
        message.append("- 新闻发布时间：").append(request.getPublishTime().format(formatter)).append("\n");
        message.append("- 当前时间：").append(now.format(formatter)).append("\n");
        message.append("- 距今：").append(StockTradingTimeUtil.getTimeSincePublished(request.getPublishTime())).append("\n");
        message.append("- 是否交易时段：").append(StockTradingTimeUtil.isTradingHours(request.getPublishTime()) ? "是" : "否").append("\n\n");

        message.append("【新闻内容】\n");
        message.append("标题：").append(request.getTitle()).append("\n");
        message.append("内容：").append(request.getContent()).append("\n");
        if (request.getSource() != null && !request.getSource().isEmpty()) {
            message.append("来源：").append(request.getSource()).append("\n");
        }
        if (request.getUrl() != null && !request.getUrl().isEmpty()) {
            message.append("链接：").append(request.getUrl()).append("\n");
        }

        message.append("\n【分析要求】\n");
        message.append("请根据上述新闻，分析对股票市场的影响，并以 JSON 格式输出结果。\n");
        message.append("JSON 结构应包含：newsInfo, timeAnalysis, affectedStocks, marketImpact, overallConfidence, analysisSummary\n");

        return message.toString();
    }

    /**
     * 解析分析结果
     */
    private NewsStockAnalysisResult parseAnalysisResult(String content, NewsStockAnalysisRequest request) {
        try {
            // 从 Agent 返回的文本中提取 JSON
            String jsonContent = extractJsonFromText(content);
            
            if (jsonContent == null || jsonContent.isEmpty()) {
                logger.warn("未能从 Agent 响应中提取 JSON，使用降级方案");
                logger.debug("Agent 原始响应: {}", content);
                return createFallbackResult(request);
            }
            
            logger.info("成功提取 JSON，长度: {} 字符", jsonContent.length());
            logger.debug("提取的 JSON 内容: {}", jsonContent);
            
            // 解析 JSON
            JsonNode rootNode = objectMapper.readTree(jsonContent);
            logger.info("JSON 解析成功，根节点字段: {}", rootNode.fieldNames());
            
            // 构建结果对象
            NewsStockAnalysisResult result = NewsStockAnalysisResult.builder()
                    .newsInfo(parseNewsInfo(rootNode, request))
                    .timeAnalysis(NewsStockAnalysisResult.TimeAnalysis.builder().build())
                    .affectedStocks(parseAffectedStocks(rootNode))
                    .marketImpact(parseMarketImpact(rootNode))
                    .overallConfidence(parseDoubleField(rootNode, "overallConfidence", 0.5))
                    .analysisSummary(parseStringField(rootNode, "analysisSummary", "分析完成"))
                    .analysisTime(LocalDateTime.now())
                    .build();
            
            return result;
            
        } catch (Exception e) {
            logger.error("JSON 解析失败，使用降级方案。错误: {}", e.getMessage());
            logger.debug("异常堆栈", e);
            logger.debug("Agent 原始响应: {}", content);
            return createFallbackResult(request);
        }
    }
    
    /**
     * 从文本中提取 JSON
     */
    private String extractJsonFromText(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        
        // 尝试直接解析
        if (text.trim().startsWith("{")) {
            return text.trim();
        }
        
        // 查找 JSON 对象
        int startIdx = text.indexOf("{");
        int endIdx = text.lastIndexOf("}");
        
        if (startIdx != -1 && endIdx != -1 && endIdx > startIdx) {
            return text.substring(startIdx, endIdx + 1);
        }
        
        return null;
    }
    
    /**
     * 解析新闻信息
     */
    private NewsStockAnalysisResult.NewsInfo parseNewsInfo(JsonNode rootNode, NewsStockAnalysisRequest request) {
        JsonNode newsInfoNode = rootNode.get("newsInfo");
        
        if (newsInfoNode != null) {
            return NewsStockAnalysisResult.NewsInfo.builder()
                    .title(parseStringField(newsInfoNode, "title", request.getTitle()))
                    .summary(parseStringField(newsInfoNode, "summary", request.getContent().substring(0, Math.min(100, request.getContent().length()))))
                    .source(parseStringField(newsInfoNode, "source", request.getSource()))
                    .publishTime(request.getPublishTime())
                    .url(parseStringField(newsInfoNode, "url", request.getUrl()))
                    .build();
        }
        
        // 降级：使用请求数据
        return NewsStockAnalysisResult.NewsInfo.builder()
                .title(request.getTitle())
                .summary(request.getContent().substring(0, Math.min(100, request.getContent().length())))
                .source(request.getSource())
                .publishTime(request.getPublishTime())
                .url(request.getUrl())
                .build();
    }
    
    /**
     * 解析受影响股票列表
     */
    private List<NewsStockAnalysisResult.StockImpact> parseAffectedStocks(JsonNode rootNode) {
        List<NewsStockAnalysisResult.StockImpact> stocks = new ArrayList<>();
        
        JsonNode stocksNode = rootNode.get("affectedStocks");
        if (stocksNode == null) {
            logger.warn("JSON 中未找到 affectedStocks 字段");
            return stocks;
        }
        
        if (!stocksNode.isArray()) {
            logger.warn("affectedStocks 不是数组类型，实际类型: {}", stocksNode.getNodeType());
            return stocks;
        }
        
        logger.info("找到 affectedStocks 数组，包含 {} 个元素", stocksNode.size());
        
        int index = 0;
        for (JsonNode stockNode : stocksNode) {
            try {
                logger.debug("解析第 {} 只股票，字段: {}", index + 1, stockNode.fieldNames());
                
                String stockCode = parseStringField(stockNode, "stockCode", "");
                String stockName = parseStringField(stockNode, "stockName", "");
                String reasoning = parseStringField(stockNode, "reasoning", "");
                List<String> keyFactors = parseStringArrayField(stockNode, "keyFactors");
                
                logger.debug("股票 {}: code={}, name={}, reasoning长度={}, keyFactors数量={}",
                        index + 1, stockCode, stockName, reasoning.length(), keyFactors.size());
                
                NewsStockAnalysisResult.StockImpact stock = NewsStockAnalysisResult.StockImpact.builder()
                        .stockCode(stockCode)
                        .stockName(stockName)
                        .market(parseStringField(stockNode, "market", "A股"))
                        .relationType(parseStringField(stockNode, "relationType", "INDIRECT"))
                        .relevanceScore(parseDoubleField(stockNode, "relevanceScore", 0.5))
                        .impactDirection(parseStringField(stockNode, "impactDirection", "NEUTRAL"))
                        .impactSeverity(parseStringField(stockNode, "impactSeverity", "MODERATE"))
                        .predictedChangePercent(parseDoubleField(stockNode, "predictedChangePercent", 0.0))
                        .duration(parseStringField(stockNode, "duration", "SHORT_TERM"))
                        .confidence(parseDoubleField(stockNode, "confidence", 0.5))
                        .reasoning(reasoning)
                        .keyFactors(keyFactors)
                        .build();
                
                stocks.add(stock);
                logger.info("成功解析第 {} 只股票: {} ({})", index + 1, stockName, stockCode);
                index++;
            } catch (Exception e) {
                logger.warn("解析第 {} 只股票失败: {}", index + 1, e.getMessage());
            }
        }
        
        return stocks;
    }
    
    /**
     * 解析市场影响
     */
    private NewsStockAnalysisResult.MarketImpact parseMarketImpact(JsonNode rootNode) {
        JsonNode marketNode = rootNode.get("marketImpact");
        
        if (marketNode != null) {
            return NewsStockAnalysisResult.MarketImpact.builder()
                    .marketSentiment(parseStringField(marketNode, "marketSentiment", "NEUTRAL"))
                    .affectedSectors(parseStringArrayField(marketNode, "affectedSectors"))
                    .systemicRisk(parseBooleanField(marketNode, "systemicRisk", false))
                    .focusPoints(parseStringArrayField(marketNode, "focusPoints"))
                    .build();
        }
        
        return NewsStockAnalysisResult.MarketImpact.builder()
                .marketSentiment("NEUTRAL")
                .affectedSectors(new ArrayList<>())
                .systemicRisk(false)
                .focusPoints(new ArrayList<>())
                .build();
    }
    
    /**
     * 创建降级结果（当解析失败时）
     */
    private NewsStockAnalysisResult createFallbackResult(NewsStockAnalysisRequest request) {
        logger.info("使用降级结果");
        
        return NewsStockAnalysisResult.builder()
                .newsInfo(NewsStockAnalysisResult.NewsInfo.builder()
                        .title(request.getTitle())
                        .summary(request.getContent().substring(0, Math.min(100, request.getContent().length())))
                        .source(request.getSource())
                        .publishTime(request.getPublishTime())
                        .url(request.getUrl())
                        .build())
                .timeAnalysis(NewsStockAnalysisResult.TimeAnalysis.builder().build())
                .affectedStocks(new ArrayList<>())
                .marketImpact(NewsStockAnalysisResult.MarketImpact.builder()
                        .marketSentiment("NEUTRAL")
                        .affectedSectors(new ArrayList<>())
                        .systemicRisk(false)
                        .focusPoints(new ArrayList<>())
                        .build())
                .overallConfidence(0.3)
                .analysisSummary("⚠️ 分析结果解析失败，请检查新闻内容或稍后重试。")
                .analysisTime(LocalDateTime.now())
                .build();
    }
    
    // ========== 辅助方法 ==========
    
    private String parseStringField(JsonNode node, String fieldName, String defaultValue) {
        JsonNode field = node.get(fieldName);
        return field != null && !field.isNull() ? field.asText() : defaultValue;
    }
    
    private double parseDoubleField(JsonNode node, String fieldName, double defaultValue) {
        JsonNode field = node.get(fieldName);
        return field != null && !field.isNull() ? field.asDouble() : defaultValue;
    }
    
    private boolean parseBooleanField(JsonNode node, String fieldName, boolean defaultValue) {
        JsonNode field = node.get(fieldName);
        return field != null && !field.isNull() ? field.asBoolean() : defaultValue;
    }
    
    private List<String> parseStringArrayField(JsonNode node, String fieldName) {
        List<String> list = new ArrayList<>();
        JsonNode field = node.get(fieldName);
        
        if (field != null && field.isArray()) {
            for (JsonNode item : field) {
                list.add(item.asText());
            }
        }
        
        return list;
    }

    /**
     * 补充时间分析信息
     */
    private void enrichTimeAnalysis(NewsStockAnalysisResult result, LocalDateTime publishTime) {
        NewsStockAnalysisResult.TimeAnalysis timeAnalysis = result.getTimeAnalysis();
        if (timeAnalysis == null) {
            timeAnalysis = NewsStockAnalysisResult.TimeAnalysis.builder().build();
            result.setTimeAnalysis(timeAnalysis);
        }

        timeAnalysis.setPublishTime(publishTime);
        timeAnalysis.setTimeSincePublished(StockTradingTimeUtil.getTimeSincePublished(publishTime));
        timeAnalysis.setDuringTradingHours(StockTradingTimeUtil.isTradingHours(publishTime));
        timeAnalysis.setTimelinessLevel(StockTradingTimeUtil.getTimelinessLevel(publishTime));
        timeAnalysis.setFreshnessScore(StockTradingTimeUtil.calculateFreshnessScore(publishTime));
        timeAnalysis.setTimeDecayFactor(StockTradingTimeUtil.calculateTimeDecay(publishTime));
        timeAnalysis.setRecommendedWatchPeriod(StockTradingTimeUtil.getRecommendedWatchPeriod(publishTime));
    }

    /**
     * 计算并更新置信度
     */
    private double calculateAndUpdateConfidence(NewsStockAnalysisResult result, 
                                                  NewsStockAnalysisRequest request,
                                                  String agentResponse) {
        // 1. 检查是否有明确的实体
        boolean hasClearEntities = ConfidenceCalculator.hasClearEntities(request.getContent());
        
        // 2. 是否有历史案例（当前版本暂不支持，设为 false）
        boolean hasHistoricalCases = false;
        
        // 3. 是否有实时数据（当前版本未集成工具，设为 false）
        boolean hasRealtimeData = false;
        
        // 4. 评估新闻质量
        double newsQuality = ConfidenceCalculator.evaluateNewsQuality(
                request.getTitle(), request.getContent());
        
        // 5. 计算置信度
        double calculatedConfidence = ConfidenceCalculator.calculateConfidence(
                request, hasClearEntities, hasHistoricalCases, hasRealtimeData, newsQuality);
        
        // 6. 取 Agent 给出和计算值的较大者（更乐观的估计）
        double finalConfidence = Math.max(result.getOverallConfidence(), calculatedConfidence);
        result.setOverallConfidence(finalConfidence);
        
        // 7. 在分析摘要中添加置信度说明
        ConfidenceCalculator.ConfidenceLevel level = ConfidenceCalculator.getConfidenceLevel(finalConfidence);
        String confidenceNote = String.format("\n\n【置信度说明】%s %s - %s",
                level.getIcon(), level.getLabel(), level.getDescription());
        
        if (result.getAnalysisSummary() != null) {
            result.setAnalysisSummary(result.getAnalysisSummary() + confidenceNote);
        }
        
        return calculatedConfidence;
    }
}
