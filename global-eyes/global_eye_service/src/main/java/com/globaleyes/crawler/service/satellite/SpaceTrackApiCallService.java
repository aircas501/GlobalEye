package com.globaleyes.crawler.service.satellite;

import com.globaleyes.crawler.pojo.vo.SpaceTrackOmmData;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class SpaceTrackApiCallService {
    private final Logger log = LoggerFactory.getLogger(SpaceTrackApiCallService.class);
    @Value("${space-track.userName}")
    private String userName;

    @Value("${space-track.passwd}")
    private String passwd;

    @Resource(name = "spaceTrackWebClient")
    private WebClient spaceTrackWebClient;

    private final AtomicReference<String> spaceTrackSessionCookie = new AtomicReference<>();

    public static final String SPACE_TRACK_MIDDLE_URL = "/basicspacedata/query/class/gp/EPOCH/>%s/%s";
    public static final String SPACE_TRACK_URL_SUFFIX = "/orderby/EPOCH%20ASC/format/json";
    public static final String SPACE_TRACK_LOGIN_URI = "/ajaxauth/login";

    /**
     * 查询近两小时的两行根数信息
     *
     * @param startTime 开始时间
     * @return 两行根数
     */
    public Flux<SpaceTrackOmmData> querySpaceTrack(String startTime, final int retryTimes) {
        String queryUri = String.format(SPACE_TRACK_MIDDLE_URL, startTime, SPACE_TRACK_URL_SUFFIX);
        log.info("查询数据的URI为:{}", queryUri);

        return spaceTrackWebClient.get()
                .uri(queryUri)
                .cookies(cookies -> {
                    String allCookie = spaceTrackSessionCookie.get();
                    String[] split = allCookie.split(";");
                    for (String cookie : split) {
                        String[] kv = cookie.strip().split("=");
                        cookies.add(kv[0], kv[1]);
                    }
                })
                .accept(MediaType.APPLICATION_JSON)
                .exchangeToFlux(response -> {
                    if (response.statusCode().is2xxSuccessful()) {
                        return response.bodyToFlux(SpaceTrackOmmData.class);
                    } else if (response.statusCode().is4xxClientError()) {
                        checkSpaceTrackCookie();
                        if (retryTimes > 0) {
                            return querySpaceTrack(startTime, retryTimes - 1);
                        } else {
                            log.error("登录失败无法获取cookie");
                            return Flux.empty();
                        }
                    } else {
                        log.error("获取最新的两行根数失败，失败原因:{}", response);
                        return Flux.empty();
                    }
                });
    }


    /**
     * 登录space-track获取cookie
     *
     * @return cookie
     */
    public boolean checkSpaceTrackCookie() {
        if (StringUtils.isEmpty(spaceTrackSessionCookie.get())) {
            log.info("cookie已经过期，重新获取cookie");
            String cookieValue = spaceTrackLogin(userName, passwd);
            if (StringUtils.isEmpty(spaceTrackSessionCookie.get()) && StringUtils.isNotEmpty(cookieValue)) {
                spaceTrackSessionCookie.set(cookieValue);
                return true;
            } else {
                log.info("cookie获取失败，终止定时任务执行，请定位原因");
                return false;
            }
        }
        return true;

    }

    /**
     * 登录space-track获取cookie
     *
     * @param username 用户名
     * @param password 密码
     * @return cookie
     */
    private String spaceTrackLogin(String username, String password) {
        String spacetrackCsrfCookie = spaceTrackWebClient.get()
                .exchangeToMono(response -> {
                    HttpHeaders headers = response.headers().asHttpHeaders();
                    String spacetrackCsrfCookieStr = headers.getFirst(HttpHeaders.SET_COOKIE);
                    log.info("获取到Csrf的Cookie信息为:{}", spacetrackCsrfCookieStr);
                    return Mono.justOrEmpty(Objects.requireNonNull(spacetrackCsrfCookieStr).split(";")[0]);
                }).block();
        if (StringUtils.isAnyBlank(spacetrackCsrfCookie)) {
            log.error("获取spacetrackCsrfCookie失败");
            return null;
        }
        log.info("获取spacecraftCsrfCookie成功:{}", spacetrackCsrfCookie);
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("identity", username);
        formData.add("password", password);
        String chocolate = spaceTrackWebClient.post()
                .uri(SPACE_TRACK_LOGIN_URI)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue(formData)
                .exchangeToMono(response -> {
                    HttpHeaders headers = response.headers().asHttpHeaders();
                    log.info("获取到的登录Cookie信息为:{}", headers.getFirst(HttpHeaders.SET_COOKIE));
                    return Mono.justOrEmpty(Objects.requireNonNull(headers.getFirst(HttpHeaders.SET_COOKIE)).split(";")[0]);
                }).block();
        if (StringUtils.isAnyBlank(chocolate)) {
            log.error("获取chocolatechip失败");
            return null;
        }
        log.info("获取chocolates成功:{}", chocolate);
        String cookies = spacetrackCsrfCookie + "; " + chocolate;
        log.info("获取cookie成功:{}", cookies);
        return chocolate;
    }

}
