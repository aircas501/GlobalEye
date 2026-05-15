package com.globaleyes.crawler.constant;

public final class ProcessingStatus {

    private ProcessingStatus() {
    }

    public static final String PENDING = "PENDING";
    public static final String PROCESSED = "PROCESSED";
    public static final String PROCESSING = "PROCESSING";
    public static final String FAILED = "FAILED";
    public static final String ANALYSIS_FAILED = "ANALYSIS_FAILED";
    public static final String VALIDATION_FAILED = "VALIDATION_FAILED";
    public static final String ANALYSIS_ERROR = "ANALYSIS_ERROR";

    public static final String RUNNING = "RUNNING";
    public static final String SUCCESS = "SUCCESS";
}
