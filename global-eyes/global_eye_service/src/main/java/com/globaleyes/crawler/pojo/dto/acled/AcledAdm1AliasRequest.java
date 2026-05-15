package com.globaleyes.crawler.pojo.dto.acled;

import lombok.Data;

@Data
public class AcledAdm1AliasRequest {
    private String country;
    private String aliasName;
    private Long boundaryId;
    private String matchType;
    private String remark;
}
