package com.globaleyes.crawler.service.opensky;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * OpenSky API Token管理器
 * 负责获取和刷新访问令牌
 * 支持单机模式和分布式模式（Redis存储+Redisson锁）
 * 
 * @author RSS News Crawler Team
 * @version 1.0.0
 */
@Slf4j
@Service
public class OpenSkyTokenManager {

    private static final String REDIS_TOKEN_KEY = "opensky:token:access_token";
    private static final String REDIS_EXPIRES_KEY = "opensky:token:expires_at";
    private static final String REDIS_LOCK_KEY = "opensky:token:lock";

    @Value("${opensky.auth.token-url:https://auth.opensky-network.org/auth/realms/opensky-network/protocol/openid-connect/token}")
    private String tokenUrl;

    @Value("${opensky.auth.client-id:}")
    private String clientId;

    @Value("${opensky.auth.client-secret:}")
    private String clientSecret;

    @Value("${opensky.auth.token-refresh-margin:30}")
    private long tokenRefreshMargin;

    @Value("${opensky.auth.distributed-mode:false}")
    private boolean distributedMode;

    private final WebClient webClient;
    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;

    private volatile String localAccessToken;
    private volatile Instant localExpiresAt;
    private final ReentrantLock localLock = new ReentrantLock();

    public OpenSkyTokenManager(WebClient.Builder webClientBuilder,
                               RedissonClient redissonClient,
                               ObjectMapper objectMapper) {
        this.webClient = webClientBuilder.build();
        this.redissonClient = redissonClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 获取有效的访问令牌
     * 如果令牌不存在或即将过期，自动刷新
     * 支持单机和分布式两种模式
     *
     * @return 有效的访问令牌，未配置认证信息时返回null
     */
    public String getToken() {
        if (clientId == null || clientId.isEmpty() || clientSecret == null || clientSecret.isEmpty()) {
            log.debug("OpenSky client credentials not configured, using unauthenticated access");
            return null;
        }

        if (distributedMode) {
            return getDistributedToken();
        } else {
            return getLocalToken();
        }
    }

    /**
     * 获取本地模式下的Token
     * 使用双重检查锁定确保线程安全
     *
     * @return 有效的访问令牌
     */
    private String getLocalToken() {
        if (isTokenValid(localAccessToken, localExpiresAt)) {
            return localAccessToken;
        }

        localLock.lock();
        try {
            if (isTokenValid(localAccessToken, localExpiresAt)) {
                return localAccessToken;
            }
            refreshTokenLocal();
            return localAccessToken;
        } finally {
            localLock.unlock();
        }
    }

    /**
     * 获取分布式模式下的Token
     * 使用Redisson分布式锁确保多实例安全
     *
     * @return 有效的访问令牌
     */
    private String getDistributedToken() {
        RBucket<String> tokenBucket = redissonClient.getBucket(REDIS_TOKEN_KEY);
        RBucket<String> expiresBucket = redissonClient.getBucket(REDIS_EXPIRES_KEY);
        
        String token = tokenBucket.get();
        String expiresAtStr = expiresBucket.get();
        
        Instant expiresAt = expiresAtStr != null ? Instant.parse(expiresAtStr) : null;
        
        if (isTokenValid(token, expiresAt)) {
            return token;
        }

        RLock lock = redissonClient.getLock(REDIS_LOCK_KEY);
        
        try {
            boolean acquired = lock.tryLock(10, 30, TimeUnit.SECONDS);
            
            if (acquired) {
                try {
                    token = tokenBucket.get();
                    expiresAtStr = expiresBucket.get();
                    expiresAt = expiresAtStr != null ? Instant.parse(expiresAtStr) : null;
                    
                    if (isTokenValid(token, expiresAt)) {
                        return token;
                    }
                    
                    refreshTokenDistributed();
                    return tokenBucket.get();
                } finally {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                }
            } else {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return getDistributedToken();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while waiting for distributed lock", e);
            return refreshTokenWithFallback();
        } catch (Exception e) {
            log.error("Failed to acquire distributed lock for token refresh", e);
            return refreshTokenWithFallback();
        }
    }

    /**
     * 检查Token是否有效
     *
     * @param token     访问令牌
     * @param expiresAt 过期时间
     * @return true如果Token有效且未即将过期
     */
    private boolean isTokenValid(String token, Instant expiresAt) {
        if (token == null || expiresAt == null) {
            return false;
        }
        return Instant.now().plusSeconds(tokenRefreshMargin).isBefore(expiresAt);
    }

    /**
     * 本地模式刷新Token
     */
    private void refreshTokenLocal() {
        TokenData tokenData = fetchNewToken();
        if (tokenData != null) {
            this.localAccessToken = tokenData.accessToken;
            this.localExpiresAt = tokenData.expiresAt;
            log.info("OpenSky token refreshed (local mode), expires at: {}", tokenData.expiresAt);
        }
    }

    /**
     * 分布式模式刷新Token
     */
    private void refreshTokenDistributed() {
        TokenData tokenData = fetchNewToken();
        if (tokenData != null) {
            long ttlSeconds = Instant.now().until(tokenData.expiresAt, java.time.temporal.ChronoUnit.SECONDS);
            
            RBucket<String> tokenBucket = redissonClient.getBucket(REDIS_TOKEN_KEY);
            RBucket<String> expiresBucket = redissonClient.getBucket(REDIS_EXPIRES_KEY);
            
            tokenBucket.set(tokenData.accessToken, ttlSeconds, TimeUnit.SECONDS);
            expiresBucket.set(tokenData.expiresAt.toString(), ttlSeconds, TimeUnit.SECONDS);
            
            log.info("OpenSky token refreshed (distributed mode), expires at: {}", tokenData.expiresAt);
        }
    }

    /**
     * 分布式锁获取失败时的降级刷新策略
     *
     * @return Token
     */
    private String refreshTokenWithFallback() {
        try {
            TokenData tokenData = fetchNewToken();
            if (tokenData != null) {
                return tokenData.accessToken;
            }
        } catch (Exception e) {
            log.error("Fallback token refresh failed", e);
        }
        return null;
    }

    /**
     * 从OpenSky认证服务器获取新令牌
     *
     * @return Token数据，失败返回null
     */
    private TokenData fetchNewToken() {
        try {
            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("grant_type", "client_credentials");
            formData.add("client_id", clientId);
            formData.add("client_secret", clientSecret);

            String response = webClient.post()
                .uri(tokenUrl)
                .bodyValue(formData)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .retrieve()
                .bodyToMono(String.class)
                .block();

            if (response == null || response.isEmpty()) {
                log.error("Empty response from token endpoint");
                return null;
            }

            TokenResponse tokenResponse = objectMapper.readValue(response, TokenResponse.class);
            
            if (tokenResponse != null && tokenResponse.getAccessToken() != null) {
                String accessToken = tokenResponse.getAccessToken();
                long expiresIn = tokenResponse.getExpiresIn() != null ? tokenResponse.getExpiresIn() : 1800;
                Instant expiresAt = Instant.now().plusSeconds(expiresIn - tokenRefreshMargin);
                
                return new TokenData(accessToken, expiresAt);
            }
            
            return null;
        } catch (Exception e) {
            log.error("Failed to fetch new token from OpenSky", e);
            throw new RuntimeException("Failed to refresh OpenSky token", e);
        }
    }

    /**
     * 获取带有Bearer令牌的认证头
     *
     * @return 认证头字符串，未配置认证时返回null
     */
    public String getAuthorizationHeader() {
        String token = getToken();
        return token != null ? "Bearer " + token : null;
    }

    /**
     * Token数据内部类
     */
    private static class TokenData {
        final String accessToken;
        final Instant expiresAt;

        TokenData(String accessToken, Instant expiresAt) {
            this.accessToken = accessToken;
            this.expiresAt = expiresAt;
        }
    }

    /**
     * 令牌响应DTO
     * 对应OpenSky OAuth2返回的JSON格式
     */
    private static class TokenResponse {
        @com.fasterxml.jackson.annotation.JsonProperty("access_token")
        private String accessToken;

        @com.fasterxml.jackson.annotation.JsonProperty("expires_in")
        private Long expiresIn;

        @com.fasterxml.jackson.annotation.JsonProperty("refresh_expires_in")
        private Long refreshExpiresIn;

        @com.fasterxml.jackson.annotation.JsonProperty("token_type")
        private String tokenType;

        @com.fasterxml.jackson.annotation.JsonProperty("not-before-policy")
        private Long notBeforePolicy;

        private String scope;

        public String getAccessToken() {
            return accessToken;
        }

        public void setAccessToken(String accessToken) {
            this.accessToken = accessToken;
        }

        public Long getExpiresIn() {
            return expiresIn;
        }

        public void setExpiresIn(Long expiresIn) {
            this.expiresIn = expiresIn;
        }

        public Long getRefreshExpiresIn() {
            return refreshExpiresIn;
        }

        public void setRefreshExpiresIn(Long refreshExpiresIn) {
            this.refreshExpiresIn = refreshExpiresIn;
        }

        public String getTokenType() {
            return tokenType;
        }

        public void setTokenType(String tokenType) {
            this.tokenType = tokenType;
        }

        public Long getNotBeforePolicy() {
            return notBeforePolicy;
        }

        public void setNotBeforePolicy(Long notBeforePolicy) {
            this.notBeforePolicy = notBeforePolicy;
        }

        public String getScope() {
            return scope;
        }

        public void setScope(String scope) {
            this.scope = scope;
        }
    }
}
