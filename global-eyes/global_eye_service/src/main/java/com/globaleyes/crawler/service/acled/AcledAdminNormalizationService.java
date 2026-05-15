package com.globaleyes.crawler.service.acled;

import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class AcledAdminNormalizationService {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern PUNCTUATION = Pattern.compile("[\\p{Punct}]");
    private static final Pattern MULTI_SPACE = Pattern.compile("\\s+");
    private static final Pattern SUFFIXES = Pattern.compile(
            "\\b(governorate|province|region|district|muhafazah|state)\\b",
            Pattern.CASE_INSENSITIVE
    );

    public String normalizeCountry(String value) {
        return normalize(value);
    }

    public String normalizeAdmin1(String value) {
        if (value == null) {
            return null;
        }
        String normalized = normalize(value);
        normalized = SUFFIXES.matcher(normalized).replaceAll(" ");
        normalized = MULTI_SPACE.matcher(normalized).replaceAll(" ").trim();
        return normalized;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKD);
        normalized = DIACRITICS.matcher(normalized).replaceAll("");
        normalized = normalized.toLowerCase(Locale.ROOT).trim();
        normalized = PUNCTUATION.matcher(normalized).replaceAll(" ");
        normalized = MULTI_SPACE.matcher(normalized).replaceAll(" ").trim();
        return normalized;
    }
}
