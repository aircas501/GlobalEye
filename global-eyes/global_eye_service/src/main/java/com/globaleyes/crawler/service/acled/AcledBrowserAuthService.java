package com.globaleyes.crawler.service.acled;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.LoadState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * ACLED 浏览器自动化Token获取服务
 * 使用Playwright绕过安全验证获取OAuth Token
 *
 * 支持三种模式：
 * 1. 手动配置Token（推荐）
 * 2. 浏览器自动化获取
 * 3. 交互式登录
 *
 * @author ACLED Integration Team
 * @version 1.0.0
 */
@Slf4j
@Service
public class AcledBrowserAuthService {

    private static final String ACLED_BASE_URL = "https://acleddata.com";
    private static final String ACLED_TOKEN_URL = "https://acleddata.com/oauth/token";
    private static final int TOKEN_REFRESH_MARGIN_SECONDS = 300;
    private static final int PAGE_TIMEOUT = 90000;

    private final ObjectMapper objectMapper;
    private final String username;
    private final String password;

    @Value("${acled.api.access-token:}")
    private String configuredToken;

    @Value("${acled.api.use-browser-auth:false}")
    private boolean useBrowserAuth;

    @Value("${acled.api.headless:true}")
    private boolean headless;

    private volatile Playwright playwright;
    private volatile Browser browser;
    private volatile String cachedAccessToken;
    private volatile Instant cachedExpiresAt;
    private final ReentrantLock lock = new ReentrantLock();

    public AcledBrowserAuthService(
            ObjectMapper objectMapper,
            @Value("${acled.api.username:}") String username,
            @Value("${acled.api.password:}") String password) {
        this.objectMapper = objectMapper;
        this.username = username;
        this.password = password;
    }

    /**
     * 获取有效的access_token
     *
     * @return 访问令牌
     */
    public String getAccessToken() {
        if (configuredToken != null && !configuredToken.isEmpty()) {
            log.debug("Using manually configured ACLED token");
            return configuredToken;
        }

        if (!useBrowserAuth) {
            throw new IllegalStateException("ACLED token not configured. Please set 'acled.api.access-token' in application.yml or enable 'acled.api.use-browser-auth'");
        }

        if (username == null || username.isEmpty() || password == null || password.isEmpty()) {
            throw new IllegalStateException("ACLED API credentials not configured");
        }

        if (isTokenValid(cachedAccessToken, cachedExpiresAt)) {
            return cachedAccessToken;
        }

        lock.lock();
        try {
            if (isTokenValid(cachedAccessToken, cachedExpiresAt)) {
                return cachedAccessToken;
            }
            return fetchTokenViaBrowser();
        } finally {
            lock.unlock();
        }
    }

    /**
     * 检查Token是否有效
     *
     * @param token     访问令牌
     * @param expiresAt 过期时间
     * @return 是否有效
     */
    private boolean isTokenValid(String token, Instant expiresAt) {
        if (token == null || expiresAt == null) {
            return false;
        }
        return Instant.now().plusSeconds(TOKEN_REFRESH_MARGIN_SECONDS).isBefore(expiresAt);
    }

    /**
     * 通过浏览器自动化获取Token
     *
     * @return 访问令牌
     */
    private String fetchTokenViaBrowser() {
        log.info("Starting browser automation to fetch ACLED token...");

        Page page = null;
        BrowserContext context = null;
        try {
            initPlaywright();

            context = createBrowserContext();
            page = context.newPage();
            page.setDefaultTimeout(PAGE_TIMEOUT);

            String token = fetchTokenByDirectApiCall(page);

            if (token != null && !token.isEmpty()) {
                this.cachedAccessToken = token;
                this.cachedExpiresAt = Instant.now().plusSeconds(86400 - TOKEN_REFRESH_MARGIN_SECONDS);
                log.info("ACLED token obtained successfully via browser automation");
                return token;
            } else {
                throw new RuntimeException("Failed to extract token from browser session");
            }
        } catch (Exception e) {
            log.error("Failed to fetch token via browser automation: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to fetch ACLED token via browser", e);
        } finally {
            if (page != null) {
                try {
                    page.close();
                } catch (Exception ignored) {
                }
            }
            if (context != null) {
                try {
                    context.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    /**
     * 初始化Playwright
     */
    private void initPlaywright() {
        if (playwright == null) {
            playwright = Playwright.create();
        }
        if (browser == null || !browser.isConnected()) {
            browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                    .setHeadless(headless)
                    .setArgs(java.util.List.of(
                            "--no-sandbox",
                            "--disable-setuid-sandbox",
                            "--disable-dev-shm-usage",
                            "--disable-gpu",
                            "--disable-software-rasterizer"
                    )));
        }
    }

    /**
     * 创建浏览器上下文
     *
     * @return 浏览器上下文
     */
    private BrowserContext createBrowserContext() {
        return browser.newContext(new Browser.NewContextOptions()
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .setViewportSize(1920, 1080)
                .setLocale("en-US")
                .setTimezoneId("Asia/Shanghai")
                .setIgnoreHTTPSErrors(true));
    }

    /**
     * 通过浏览器直接调用OAuth API获取Token
     *
     * @param page 页面对象
     * @return 访问令牌
     */
    private String fetchTokenByDirectApiCall(Page page) {
        try {
            log.info("Navigating to ACLED website first to establish session...");
            page.navigate(ACLED_BASE_URL);
            page.waitForLoadState(LoadState.NETWORKIDLE);

            handleCloudflareChallenge(page);

            log.info("Calling OAuth token endpoint via browser JavaScript...");

            String formData = "username=" + username + "&password=" + password + "&grant_type=password&client_id=acled";

            String result = (String) page.evaluate("async (formData) => {" +
                    "  try {" +
                    "    const response = await fetch('" + ACLED_TOKEN_URL + "', {" +
                    "      method: 'POST'," +
                    "      headers: {" +
                    "        'Content-Type': 'application/x-www-form-urlencoded'," +
                    "        'Accept': 'application/json'" +
                    "      }," +
                    "      body: formData" +
                    "    });" +
                    "    const text = await response.text();" +
                    "    return JSON.stringify({status: response.status, body: text});" +
                    "  } catch(e) {" +
                    "    return JSON.stringify({error: e.message});" +
                    "  }" +
                    "}", formData);

            log.info("OAuth response: {}", result);

            if (result != null && !result.contains("\"error\"")) {
                try {
                    TokenResult tokenResult = objectMapper.readValue(result, TokenResult.class);
                    if (tokenResult != null && tokenResult.body != null) {
                        TokenResponse tokenResponse = objectMapper.readValue(tokenResult.body, TokenResponse.class);
                        if (tokenResponse != null && tokenResponse.accessToken != null) {
                            log.info("Token obtained successfully, expires in {} seconds", tokenResponse.expiresIn);
                            return tokenResponse.accessToken;
                        } else if (tokenResult.status == 403) {
                            log.warn("OAuth endpoint returned 403, trying alternative method...");
                            return tryAlternativeLogin(page);
                        }
                    }
                } catch (Exception e) {
                    log.debug("Failed to parse token response: {}", e.getMessage());
                }
            }

            return tryAlternativeLogin(page);

        } catch (Exception e) {
            log.error("Direct API call failed: {}", e.getMessage());
            return tryAlternativeLogin(page);
        }
    }

    /**
     * 处理Cloudflare挑战
     *
     * @param page 页面对象
     */
    private void handleCloudflareChallenge(Page page) {
        try {
            for (int i = 0; i < 3; i++) {
                String content = page.content();
                if (content.contains("Checking your browser") || content.contains("Please Wait") || content.contains("challenge")) {
                    log.info("Detected security challenge, waiting... (attempt {})", i + 1);
                    TimeUnit.SECONDS.sleep(5);
                    page.waitForLoadState(LoadState.NETWORKIDLE);
                } else {
                    break;
                }
            }
        } catch (Exception e) {
            log.debug("Cloudflare handling: {}", e.getMessage());
        }
    }

    /**
     * 尝试替代登录方法
     *
     * @param page 页面对象
     * @return 访问令牌
     */
    private String tryAlternativeLogin(Page page) {
        try {
            log.info("Trying alternative login method...");

            page.navigate(ACLED_BASE_URL + "/auth/realms/acled/protocol/openid-connect/auth?client_id=acled&redirect_uri=https://acleddata.com/&response_type=code");
            page.waitForLoadState(LoadState.NETWORKIDLE);

            handleCloudflareChallenge(page);

            String emailSelector = "input[name='username'], input[name='email'], input[type='email'], #username";
            String passwordSelector = "input[name='password'], input[type='password'], #password";
            String submitSelector = "input[type='submit'], button[type='submit'], #kc-login";

            if (page.isVisible(emailSelector)) {
                log.info("Found login form, filling credentials...");
                page.fill(emailSelector, username);
                TimeUnit.MILLISECONDS.sleep(300);
                page.fill(passwordSelector, password);
                TimeUnit.MILLISECONDS.sleep(300);
                page.click(submitSelector);
                page.waitForLoadState(LoadState.NETWORKIDLE);
                TimeUnit.SECONDS.sleep(2);
            }

            String tokenFromStorage = extractTokenFromStorage(page);
            if (tokenFromStorage != null) {
                return tokenFromStorage;
            }

            return tryFetchTokenAfterLogin(page);

        } catch (Exception e) {
            log.error("Alternative login failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 登录后尝试获取Token
     *
     * @param page 页面对象
     * @return 访问令牌
     */
    private String tryFetchTokenAfterLogin(Page page) {
        try {
            String formData = "username=" + username + "&password=" + password + "&grant_type=password&client_id=acled";

            String result = (String) page.evaluate("async (formData) => {" +
                    "  try {" +
                    "    const response = await fetch('" + ACLED_TOKEN_URL + "', {" +
                    "      method: 'POST'," +
                    "      headers: {'Content-Type': 'application/x-www-form-urlencoded'}," +
                    "      body: formData" +
                    "    });" +
                    "    return await response.text();" +
                    "  } catch(e) { return '{\"error\":\"' + e.message + '\"}'; }" +
                    "}", formData);

            log.debug("Post-login token response: {}", result);

            if (result != null && result.contains("access_token")) {
                TokenResponse tokenResponse = objectMapper.readValue(result, TokenResponse.class);
                if (tokenResponse != null && tokenResponse.accessToken != null) {
                    return tokenResponse.accessToken;
                }
            }
        } catch (Exception e) {
            log.debug("Post-login token fetch failed: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 从存储中提取Token
     *
     * @param page 页面对象
     * @return 访问令牌
     */
    private String extractTokenFromStorage(Page page) {
        try {
            Object localStorageObj = page.evaluate("() => { const items = {}; for(let i=0; i<localStorage.length; i++) { const key = localStorage.key(i); items[key] = localStorage.getItem(key); } return items; }");
            if (localStorageObj != null) {
                String localStorageStr = objectMapper.writeValueAsString(localStorageObj);
                log.debug("LocalStorage: {}", localStorageStr);
                if (localStorageStr.contains("access_token") || localStorageStr.contains("token")) {
                    return parseTokenFromJson(localStorageStr);
                }
            }
        } catch (Exception e) {
            log.debug("Storage extraction failed: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 从JSON中解析Token
     *
     * @param json JSON字符串
     * @return 访问令牌
     */
    private String parseTokenFromJson(String json) {
        try {
            if (json.contains("access_token")) {
                int start = json.indexOf("\"access_token\"");
                if (start > 0) {
                    int valueStart = json.indexOf(":", start) + 2;
                    int valueEnd = json.indexOf("\"", valueStart);
                    if (valueStart > 0 && valueEnd > valueStart) {
                        return json.substring(valueStart, valueEnd);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("JSON parsing failed: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 获取带有Bearer令牌的认证头
     *
     * @return 认证头字符串
     */
    public String getAuthorizationHeader() {
        String token = getAccessToken();
        return token != null ? "Bearer " + token : null;
    }

    /**
     * 销毁资源
     */
    @PreDestroy
    public void destroy() {
        if (browser != null) {
            try {
                browser.close();
            } catch (Exception ignored) {
            }
        }
        if (playwright != null) {
            try {
                playwright.close();
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Token结果
     */
    private static class TokenResult {
        public Integer status;
        public String body;
        public String error;
    }

    /**
     * 令牌响应DTO
     */
    private static class TokenResponse {
        @JsonProperty("access_token")
        String accessToken;

        @JsonProperty("expires_in")
        Integer expiresIn;

        @JsonProperty("token_type")
        String tokenType;

        @JsonProperty("refresh_token")
        String refreshToken;
    }
}
