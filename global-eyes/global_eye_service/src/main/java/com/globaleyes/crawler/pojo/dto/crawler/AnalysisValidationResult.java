package com.globaleyes.crawler.pojo.dto.crawler;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 分析结果校验结果
 *
 * @author RSS News Crawler Team
 * @version 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisValidationResult {

    private boolean valid;

    private List<String> errors;

    private List<String> warnings;

    public static AnalysisValidationResult success() {
        return new AnalysisValidationResult(true, new ArrayList<>(), new ArrayList<>());
    }

    public static AnalysisValidationResult failure(List<String> errors) {
        return new AnalysisValidationResult(false, errors, new ArrayList<>());
    }

    public void addError(String error) {
        if (errors == null) {
            errors = new ArrayList<>();
        }
        errors.add(error);
        this.valid = false;
    }

    public void addWarning(String warning) {
        if (warnings == null) {
            warnings = new ArrayList<>();
        }
        warnings.add(warning);
    }
}
