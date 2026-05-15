package com.globaleyes.crawler.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
public class AppConfig {

    @Value("${opensky.api.base-url:https://opensky-network.org/api}")
    private String openSkyBaseUrl;

    @Value("${opensky.api.connect-timeout:10000}")
    private int connectTimeout;

    @Value("${opensky.api.read-timeout:30000}")
    private int readTimeout;

    @Value("${opensky.api.max-buffer-size:16777216}")
    private int maxBufferSize;

    @Value("${external.space-track.base-url:https://www.space-track.org}")
    private String spaceTrackBaseUrl;

    @Value("${external.abnormal-move.base-url:http://127.0.0.1:8081/}")
    private String abnormalMoveBaseUrl;

    @Value("${external.websocket-broadcast.base-url:http://127.0.0.1:8082}")
    private String websocketBroadcastBaseUrl;

    @Bean
    public WebClient spaceTrackWebClient() {
        return WebClient.builder()
                .baseUrl(spaceTrackBaseUrl)
                .build();
    }

    @Bean
    public WebClient abnormalMoveWebClient() {
        return WebClient.builder()
                .baseUrl(abnormalMoveBaseUrl)
                .build();
    }

    @Bean
    public WebClient webSocketBroadcastClient() {
        return WebClient.builder()
                .baseUrl(websocketBroadcastBaseUrl)
                .build();
    }

    /**
     * 创建RestTemplate Bean，用于HTTP请求
     * 配置连接超时和读取超时
     *
     * @return RestTemplate实例
     */
    @Bean
    public RestTemplate restTemplate() {
        ClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        // 设置连接超时时间（毫秒）
        ((SimpleClientHttpRequestFactory) factory).setConnectTimeout(10000);
        // 设置读取超时时间（毫秒）
        ((SimpleClientHttpRequestFactory) factory).setReadTimeout(30000);
        
        return new RestTemplate(factory);
    }

    /**
     * 创建OpenSky API WebClient实例
     * 配置连接超时、读写超时、日志过滤器、缓冲区大小
     *
     * @return WebClient实例
     */
    @Bean("openSkyWebClient")
    public WebClient openSkyWebClient() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeout)
                .responseTimeout(Duration.ofMillis(readTimeout))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(readTimeout, TimeUnit.MILLISECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(readTimeout, TimeUnit.MILLISECONDS))
                );

        return WebClient.builder()
                .baseUrl(openSkyBaseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(maxBufferSize))
                .filter(logRequest())
                .filter(logResponse())
                .build();
    }

    /**
     * 创建请求日志过滤器
     * 记录请求方法和URL
     *
     * @return 请求日志过滤器
     */
    private ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(request -> {
            log.debug("OpenSky API Request: {} {}", request.method(), request.url());
            return Mono.just(request);
        });
    }

    /**
     * 创建响应日志过滤器
     * 记录响应状态码
     *
     * @return 响应日志过滤器
     */
    private ExchangeFilterFunction logResponse() {
        return ExchangeFilterFunction.ofResponseProcessor(response -> {
            log.debug("OpenSky API Response: status={}", response.statusCode());
            return Mono.just(response);
        });
    }
}

