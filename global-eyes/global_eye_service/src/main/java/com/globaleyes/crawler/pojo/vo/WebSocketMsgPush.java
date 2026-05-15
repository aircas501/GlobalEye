package com.globaleyes.crawler.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class WebSocketMsgPush<T> {
    private T data;

    /**
     * 星体异常移动通知
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class  StarAbnormalMoveBroadcastMessageRequest {
        private String objectId;
        private String objectName;

        private String telLine1;

        private String telLine2;
    }
}


