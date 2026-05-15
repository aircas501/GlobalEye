package com.globaleyes.crawler.pojo.dto.acled;

import lombok.Data;

@Data
public class AcledImportResultDTO {
    private boolean success;
    private String message;
    private String batchId;
    private int total;
    private int inserted;
    private int skipped;
    private int matched;
    private int unmatched;
}
