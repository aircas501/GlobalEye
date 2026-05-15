package com.globaleyes.crawler.service.ws;

import com.globaleyes.crawler.pojo.vo.LevelEnum;
import com.globaleyes.crawler.pojo.vo.MsgTypeEnum;
import jakarta.annotation.Resource;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class WsPushInterfaceCallService {
    @Resource(name = "webSocketBroadcastClient")
    private WebClient webSocketBroadcastClient;

    private static final String BROADCAST_URI = "/api/ws/send/{type}";

    /**
     * 卫星异动通知
     * @param requests 卫星ID
     */
    @Async
    public <T> void wsNotice(Object requests, MsgTypeEnum msgType, LevelEnum level) {
        if (requests != null) {
            try {
                // 发送请求并获取响应字符串（或反序列化为对象）
                WsPushInterfaceResponse response = webSocketBroadcastClient.post()
                        .uri(uriBuilder -> uriBuilder
                                .path(BROADCAST_URI)
                                .queryParam("level", level.name())
                                .build(msgType))   // 路径变量替换
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(requests)
                        .retrieve()
                        .onStatus(status -> status.isError(), clientResponse -> {
                            // 处理错误响应，记录详细错误信息
                            return clientResponse.bodyToMono(String.class)
                                    .flatMap(errorBody -> {
                                        log.error("WebSocket broadcast failed with status {}: {}", 
                                                clientResponse.statusCode(), errorBody);
                                        return Mono.error(new RuntimeException(
                                                "WebSocket broadcast failed: " + clientResponse.statusCode() + " - " + errorBody));
                                    });
                        })
                        .bodyToMono(WsPushInterfaceResponse.class)
                        .block();
                
                if (response != null) {
                    log.info("WebSocket broadcast success: messageType={}, receiverCount={}", 
                            response.getMessageType(), response.getReceiverCount());
                }
            } catch (Exception e) {
                // 捕获所有异常，避免影响主流程
                log.error("Failed to send abnormal move notice for msgType={}, level={}: {}", 
                        msgType, level, e.getMessage(), e);
            }
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WsPushInterfaceResponse {
        private String messageType;
        private boolean success;
        private String messageLevel;
        private int receiverCount;
        private String message;
    }


}
