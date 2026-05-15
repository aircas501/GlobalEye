package com.globaleyes.websocket.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI (Swagger) 配置
 */
@Configuration
public class OpenApiConfig {
    
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("WebSocket 服务平台 API")
                        .version("1.0.0")
                        .description("基于 Spring Boot 4.0.3 和 WebSocket 的实时消息推送平台\n\n" +
                                "## 功能特性\n" +
                                "- 按消息类型建立 WebSocket 连接（卫星、船、飞机、车辆、人员、设备、系统）\n" +
                                "- 消息等级分类（INFO、WARN、ERROR）\n" +
                                "- 实时会话管理和统计\n" +
                                "- 支持广播、组播、单播\n" +
                                "- CORS 跨域支持")
                        .contact(new Contact()
                                .name("WebSocket Platform Team")
                                .email("support@websocket.com"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")));
    }
}
