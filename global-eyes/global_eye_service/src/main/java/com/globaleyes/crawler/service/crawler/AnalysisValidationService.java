package com.globaleyes.crawler.service.crawler;

import com.globaleyes.crawler.pojo.dto.crawler.AnalysisValidationResult;
import com.globaleyes.crawler.pojo.dto.crawler.ArticleAnalysisOutputDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 分析结果质量校验服务
 * 校验LLM输出的JSON格式、字段完整性、逻辑一致性
 *
 * @author RSS News Crawler Team
 * @version 1.0.0
 */
@Slf4j
@Service
public class AnalysisValidationService {

    private static final Set<String> VALID_TOPICS = new HashSet<>(Arrays.asList(
            "军事·冲突", "军事·技术", "军事·预算", "军事·演习", "军事·情报", "军事·其他",
            "科技", "财经", "政治", "体育", "娱乐", "社会", "国际", "教育", "健康", "环境", "其他"
    ));

    private static final Set<String> VALID_AUTHORITY_LEVELS = new HashSet<>(Arrays.asList(
            "A", "B", "C", "D", "E"
    ));

    private static final Set<String> VALID_SENTIMENTS = new HashSet<>(Arrays.asList(
            "正面", "负面", "中立", "极端负面"
    ));

    private static final Set<String> VALID_SCOPES = new HashSet<>(Arrays.asList(
            "全球", "区域", "局部", "未知"
    ));

    /**
     * 校验分析结果
     *
     * @param result 分析结果DTO
     * @return 校验结果
     */
    public AnalysisValidationResult validate(ArticleAnalysisOutputDTO result) {
        AnalysisValidationResult validationResult = new AnalysisValidationResult(true, new ArrayList<>(), new ArrayList<>());

        if (result == null) {
            validationResult.addError("分析结果为空");
            return validationResult;
        }

        validateBasicFields(result, validationResult);
        validateHotEvent(result, validationResult);
        return validationResult;
    }

    /**
     * 校验基础字段
     */
    private void validateBasicFields(ArticleAnalysisOutputDTO result, AnalysisValidationResult validationResult) {
        if (result.getSummary() == null || result.getSummary().isEmpty()) {
            validationResult.addError("summary字段为空");
        } else if (result.getSummary().length() < 50) {
            validationResult.addWarning("summary字数过少，建议100-200字");
        } else if (result.getSummary().length() > 500) {
            validationResult.addWarning("summary字数过多，建议100-200字");
        }

        if (!VALID_TOPICS.contains(result.getTopic())) {
            validationResult.addError("topic字段值无效: " + result.getTopic());
        }

        if (!VALID_AUTHORITY_LEVELS.contains(result.getAuthorityLevel())) {
            validationResult.addError("authorityLevel字段值无效: " + result.getAuthorityLevel());
        }

        if (!VALID_SENTIMENTS.contains(result.getSentiment())) {
            validationResult.addError("sentiment字段值无效: " + result.getSentiment());
        }
    }

    /**
     * 校验热点事件
     */
    private void validateHotEvent(ArticleAnalysisOutputDTO result, AnalysisValidationResult validationResult) {
        if (result.getHotEvent() != null) {
            ArticleAnalysisOutputDTO.HotEventDTO hotEvent = result.getHotEvent();
            if (!"无".equals(hotEvent.getName()) && hotEvent.getName() != null && !hotEvent.getName().isEmpty()) {
                if (hotEvent.getStartDate() != null && !"未知".equals(hotEvent.getStartDate())) {
                    if (!isValidDateFormat(hotEvent.getStartDate())) {
                        validationResult.addWarning("hotEvent.startDate格式不正确: " + hotEvent.getStartDate());
                    }
                }
                if (!VALID_SCOPES.contains(hotEvent.getScope())) {
                    validationResult.addWarning("hotEvent.scope值无效: " + hotEvent.getScope());
                }
            }
        }
    }




    /**
     * 校验日期格式
     */
    private boolean isValidDateFormat(String date) {
        if (date == null || "未知".equals(date)) {
            return true;
        }
        return date.matches("\\d{4}-\\d{2}-\\d{2}");
    }
}
