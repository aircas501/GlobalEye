package com.globaleyes.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 全局跨域配置
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {
    
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 添加所有路径的跨域映射
        registry.addMapping("/**")

                // 允许所有来源
                .allowedOrigins("*")

                // 允许所有请求方法
                .allowedMethods("*")

                // 允许所有请求头
                .allowedHeaders("*")

                // 是否允许携带cookie
                .allowCredentials(false)

                // 预检请求缓存时间
                .maxAge(3600);

    }
}
