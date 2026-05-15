package com.globaleyes.crawler.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport;

/**
 * @version 1.0
 * @classname CorsConfig
 * @description 解决跨域问题,重写父类提供的跨域请求处理的接口
 * @date 2021/2/24 00:06
 * @created by MelodyJerry
 */
//@Configuration
//@EnableWebMvc
public class CorsConfig extends WebMvcConfigurationSupport {

    /*
     * 这里主要为了解决跨域问题,所以重写addCorsMappings方法
     */
    @Override
    protected void addCorsMappings(CorsRegistry registry) {
        //设置允许跨域的路径
        registry.addMapping("/**") //所有的当前站点的请求地址，都支持跨域访问
                //设置允许跨域请求的域名,二选一allowedOrigins或allowedOrigins
                //.allowedOrigins("*") //注意access-control-allow-origin时,allowedOrigins不能为*
                //.allowedOrigins("域名") //如果是localhost则很难配置，因为在跨域请求的时候，外部域的解析可能是localhost、127.0.0.1、主机名
                .allowedOriginPatterns("*") // 所有的外部域都可跨域访问
                //放行哪些原始域(请求方式)
                .allowedMethods("GET","HEAD","POST","PUT","DELETE","OPTIONS")
                //设置允许的请求头
                .allowedHeaders("*")
                //暴露哪些头部信息（因为跨域访问默认不能获取全部头部信息）
                .exposedHeaders("access-control-allow-headers",
                        "access-control-allow-methods",
                        "access-control-allow-origin",
                        "access-control-max-age",
                        "X-Frame-Options")
                //是否允许证书(是否支持跨域用户凭证),不再默认开启
                .allowCredentials(true)
                //跨域允许时间
                .maxAge(3600);
    }

    /*
     * 跨域配置后swagger2可能不能访问，需要增加如下配置
     * 原因: 不能访问的原因的swagger的内置接口被拦截器拦下来了，需要将swagger加到拦截器的排除列表中。
     * @param registry
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("swagger-ui.html")
                .addResourceLocations("classpath:/META-INF/resources/");
        registry.addResourceHandler("doc.html")
                .addResourceLocations("classpath:/META-INF/resources/");
        registry.addResourceHandler("/webjars/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/");
    }
}
