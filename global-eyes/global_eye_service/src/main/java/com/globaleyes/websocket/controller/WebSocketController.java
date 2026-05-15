package com.globaleyes.websocket.controller;

import com.globaleyes.websocket.enums.MessageLevel;
import com.globaleyes.websocket.service.MessageSenderService;
import com.globaleyes.websocket.session.SessionManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * WebSocket 管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/ws")
@RequiredArgsConstructor
@Tag(name = "WebSocket 管理", description = "WebSocket 会话管理和消息推送接口")
public class WebSocketController {
    
    private final MessageSenderService messageSenderService;
    private final SessionManager sessionManager;
    
    /**
     * 发送消息到指定数据类型
     */
    @PostMapping("/send/{dataType}")
    @Operation(summary = "发送消息到指定类型", description = "向特定数据类型的所有订阅者发送消息")
    public ResponseEntity<Map<String, Object>> sendMessage(
            @Parameter(description = "数据类型（任意字符串）", required = true)
            @PathVariable String dataType,
            @Parameter(description = "消息等级 (info/warn/error)", required = true)
            @RequestParam String level,
            @Parameter(description = "消息内容 (JSON 格式)", required = true)
            @RequestBody Object content) {
        
        Map<String, Object> response = new HashMap<>();
        try {
            MessageLevel messageLevel = MessageLevel.fromCode(level);
            messageSenderService.sendMessage(dataType, messageLevel, content);
            
            response.put("success", true);
            response.put("message", "消息已发送");
            response.put("dataType", dataType);
            response.put("messageLevel", messageLevel.getDescription());
            response.put("subscriberCount", sessionManager.getSubscriberCountByType(dataType));
        } catch (Exception e) {
            log.error("发送消息失败", e);
            response.put("success", false);
            response.put("error", "发送失败：" + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 广播消息
     */
    @PostMapping("/broadcast")
    @Operation(summary = "广播消息", description = "向所有会话广播消息（不区分类型）")
    public ResponseEntity<Map<String, Object>> broadcast(
            @Parameter(description = "消息等级 (info/warn/error)", required = true)
            @RequestParam String level,
            @Parameter(description = "消息内容 (JSON 格式)", required = true)
            @RequestBody Object content) {
        
        Map<String, Object> response = new HashMap<>();
        try {
            MessageLevel messageLevel = MessageLevel.fromCode(level);
            messageSenderService.broadcastMessage(messageLevel, content);
            
            response.put("success", true);
            response.put("message", "广播消息已发送");
            response.put("totalReceivers", sessionManager.getTotalSessionCount());
        } catch (Exception e) {
            log.error("广播消息失败", e);
            response.put("success", false);
            response.put("error", "广播失败：" + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 发送消息到指定会话
     */
    @PostMapping("/send/session/{sessionId}")
    @Operation(summary = "发送消息到指定会话", description = "向特定的 WebSocket 会话发送消息")
    public ResponseEntity<Map<String, Object>> sendToSession(
            @Parameter(description = "会话 ID", required = true)
            @PathVariable String sessionId,
            @Parameter(description = "消息等级 (info/warn/error)", required = true)
            @RequestParam String level,
            @Parameter(description = "消息内容 (JSON 格式)", required = true)
            @RequestBody Object content) {
        
        Map<String, Object> response = new HashMap<>();
        try {
            messageSenderService.sendToSession(sessionId, MessageLevel.fromCode(level), content);
            
            response.put("success", true);
            response.put("message", "消息已发送到会话");
            response.put("sessionId", sessionId);
        } catch (Exception e) {
            log.error("发送消息到会话失败", e);
            response.put("success", false);
            response.put("error", "发送失败：" + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 获取会话统计信息
     */
    @GetMapping("/stats")
    @Operation(summary = "获取会话统计", description = "获取所有数据类型的订阅统计")
    public ResponseEntity<Map<String, Object>> getStatistics() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("totalSessions", sessionManager.getTotalSessionCount());
        response.put("subscriptions", sessionManager.getSubscriptionStatistics());
        response.put("dataTypes", sessionManager.getAllDataTypes());
        return ResponseEntity.ok(response);
    }
    
    /**
     * 健康检查
     */
    @GetMapping("/health")
    @Operation(summary = "健康检查", description = "检查服务运行状态和活跃会话数")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("timestamp", java.time.LocalDateTime.now().format(
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")));
        response.put("activeSessions", sessionManager.getTotalSessionCount());
        return ResponseEntity.ok(response);
    }
}
