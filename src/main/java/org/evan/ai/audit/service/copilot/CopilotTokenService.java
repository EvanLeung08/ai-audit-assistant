package org.evan.ai.audit.service.copilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.evan.ai.audit.config.CopilotProperties;
import org.evan.ai.audit.model.CopilotToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Service for managing GitHub Copilot API tokens.
 * Handles token retrieval from GitHub API, caching, automatic refresh, and cookie-based persistence.
 *
 * The token flow is:
 * 1. User authenticates via GitHub Device Flow in browser
 * 2. GitHub OAuth Token is stored in memory and cookies
 * 3. Exchange for Copilot Token (via /copilot_internal/v2/token)
 * 4. Use Copilot Token as Bearer token for API calls
 * 5. Auto-refresh Copilot token when expired using stored OAuth token
 */
@Service
public class CopilotTokenService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CopilotTokenService.class);

    private final CopilotProperties copilotProperties;
    private final ObjectMapper objectMapper;
    private final TokenCookieService tokenCookieService;
    private final ReentrantLock tokenLock = new ReentrantLock();

    // In-memory cache (will be restored from cookies if empty)
    private volatile String githubOAuthToken;
    private volatile CopilotToken cachedCopilotToken;

    // Proxy settings (set by user at runtime)
    private volatile boolean proxyEnabled = false;
    private volatile String proxyUsername;
    private volatile String proxyPassword;

    public CopilotTokenService(CopilotProperties copilotProperties,
                               ObjectMapper objectMapper,
                               TokenCookieService tokenCookieService) {
        this.copilotProperties = copilotProperties;
        this.objectMapper = objectMapper;
        this.tokenCookieService = tokenCookieService;
    }

    /**
     * Configure proxy settings at runtime.
     */
    public void configureProxy(boolean enabled, String username, String password) {
        this.proxyEnabled = enabled;
        this.proxyUsername = username;
        this.proxyPassword = password;
        LOGGER.info("Proxy configuration updated: enabled={}", enabled);
    }

    /**
     * Check if proxy is enabled.
     */
    public boolean isProxyEnabled() {
        return proxyEnabled;
    }

    /**
     * Set the GitHub OAuth token obtained from device flow.
     * Stores in both memory and cookie.
     */
    public void setGithubOAuthToken(String token) {
        this.githubOAuthToken = token;
        this.cachedCopilotToken = null; // Invalidate cached copilot token
        tokenCookieService.saveGithubToken(token);
        LOGGER.info("GitHub OAuth token set and saved to cookie");
    }

    /**
     * Get the GitHub OAuth token.
     * First checks memory, then falls back to cookie.
     */
    public String getGithubOAuthToken() {
        if (githubOAuthToken != null && !githubOAuthToken.isEmpty()) {
            return githubOAuthToken;
        }
        // Try to restore from cookie
        String cookieToken = tokenCookieService.getGithubToken();
        if (cookieToken != null && !cookieToken.isEmpty()) {
            this.githubOAuthToken = cookieToken;
            LOGGER.info("GitHub OAuth token restored from cookie");
            return cookieToken;
        }
        return null;
    }

    /**
     * Check if user is authenticated.
     * Checks both memory and cookies.
     */
    public boolean isAuthenticated() {
        // First check memory
        if (githubOAuthToken != null && !githubOAuthToken.isEmpty()) {
            return true;
        }
        // Try to restore from cookie
        String cookieToken = tokenCookieService.getGithubToken();
        if (cookieToken != null && !cookieToken.isEmpty()) {
            this.githubOAuthToken = cookieToken;
            LOGGER.info("Session restored from cookie");
            return true;
        }
        return false;
    }

    /**
     * Clear authentication from both memory and cookies.
     */
    public void clearAuthentication() {
        this.githubOAuthToken = null;
        this.cachedCopilotToken = null;
        tokenCookieService.clearAllTokens();
        LOGGER.info("Authentication cleared (memory and cookies)");
    }

    /**
     * Get a valid Copilot token, refreshing if necessary.
     * Implements automatic token refresh with fallback logic.
     */
    public String getCopilotToken() {
        // Ensure we have OAuth token (from memory or cookie)
        if (!isAuthenticated()) {
            throw new RuntimeException("Not authenticated. Please complete GitHub device flow first.");
        }

        tokenLock.lock();
        try {
            // First, try to use cached token in memory
            if (cachedCopilotToken != null && !cachedCopilotToken.isExpired()) {
                return cachedCopilotToken.getToken();
            }

            // Try to restore from cookie if memory cache is empty
            if (cachedCopilotToken == null && tokenCookieService.isCopilotTokenValid()) {
                String cookieToken = tokenCookieService.getCopilotToken();
                long cookieExpiry = tokenCookieService.getCopilotTokenExpiry();
                if (cookieToken != null && !cookieToken.isEmpty()) {
                    cachedCopilotToken = new CopilotToken(cookieToken, cookieExpiry);
                    LOGGER.info("Copilot token restored from cookie");
                    return cachedCopilotToken.getToken();
                }
            }

            // Need to refresh/exchange for new Copilot token
            LOGGER.info("Copilot token expired or not found, refreshing...");
            try {
                cachedCopilotToken = exchangeForCopilotToken();
                // Save to cookie
                tokenCookieService.saveCopilotToken(
                        cachedCopilotToken.getToken(),
                        cachedCopilotToken.getExpiresAt()
                );
                return cachedCopilotToken.getToken();
            } catch (Exception e) {
                // If exchange fails, the OAuth token might be invalid
                LOGGER.warn("Failed to refresh Copilot token: {}. OAuth token may be invalid.", e.getMessage());

                // Clear cached tokens and try once more with fresh state
                this.cachedCopilotToken = null;

                // Check if this is an auth error - if so, we need re-authorization
                if (isAuthError(e)) {
                    LOGGER.error("GitHub OAuth token is invalid. User needs to re-authorize.");
                    clearAuthentication();
                    throw new RuntimeException("Session expired. Please re-authorize with GitHub Copilot.");
                }

                throw e;
            }
        } finally {
            tokenLock.unlock();
        }
    }

    /**
     * Check if exception indicates an authentication error.
     */
    private boolean isAuthError(Exception e) {
        String message = e.getMessage();
        if (message == null) return false;
        return message.contains("401") ||
               message.contains("Unauthorized") ||
               message.contains("invalid") ||
               message.contains("expired") ||
               message.contains("Bad credentials");
    }

    /**
     * Exchange GitHub OAuth token for Copilot API token.
     */
    private CopilotToken exchangeForCopilotToken() {
        LOGGER.info("Exchanging GitHub OAuth token for Copilot token...");

        try {
            RestClient client = createRestClient();

            String response = client.get()
                    .uri("https://api.github.com/copilot_internal/v2/token")
                    .header("Authorization", "token " + githubOAuthToken)
                    .header("Accept", "application/json")
                    .header("User-Agent", "GitHubCopilotChat/0.8.0")
                    .header("Editor-Version", "vscode/1.85.0")
                    .header("Editor-Plugin-Version", "copilot-chat/0.11.1")
                    .retrieve()
                    .onStatus(status -> status.value() == HttpStatus.UNAUTHORIZED.value(), (req, res) -> {
                        throw new RuntimeException("401 Unauthorized - GitHub OAuth token is invalid or expired.");
                    })
                    .body(String.class);

            @SuppressWarnings("unchecked")
            Map<String, Object> tokenData = objectMapper.readValue(response, Map.class);
            String token = (String) tokenData.get("token");
            Number expiresAt = (Number) tokenData.get("expires_at");

            if (token == null || token.isEmpty()) {
                throw new RuntimeException("No token in response");
            }

            long expiry = expiresAt != null ? expiresAt.longValue() : System.currentTimeMillis() / 1000 + 1800;
            LOGGER.info("Copilot token obtained, expires at: {}", expiry);

            return new CopilotToken(token, expiry);
        } catch (Exception e) {
            LOGGER.error("Failed to exchange for Copilot token", e);
            throw new RuntimeException("Failed to get Copilot token: " + e.getMessage(), e);
        }
    }

    /**
     * Create RestClient with optional proxy support.
     */
    private RestClient createRestClient() {
        RestClient.Builder builder = RestClient.builder();

        if (proxyEnabled && copilotProperties.getProxy() != null) {
            String proxyHost = copilotProperties.getProxy().getHost();
            int proxyPort = copilotProperties.getProxy().getPort();

            if (proxyHost != null && !proxyHost.isEmpty()) {
                HttpClient.Builder httpClientBuilder = HttpClient.newBuilder()
                        .proxy(ProxySelector.of(new InetSocketAddress(proxyHost, proxyPort)));

                if (proxyUsername != null && !proxyUsername.isEmpty()) {
                    httpClientBuilder.authenticator(new Authenticator() {
                        @Override
                        protected PasswordAuthentication getPasswordAuthentication() {
                            return new PasswordAuthentication(proxyUsername,
                                    proxyPassword != null ? proxyPassword.toCharArray() : new char[0]);
                        }
                    });
                }

                LOGGER.debug("Using proxy: {}:{}", proxyHost, proxyPort);
            }
        }

        return builder.build();
    }

    /**
     * Initiate GitHub Device Flow authentication.
     */
    public DeviceCodeResponse initiateDeviceFlow() {
        LOGGER.info("Initiating GitHub Device Flow...");

        try {
            RestClient client = createRestClient();

            String response = client.post()
                    .uri("https://github.com/login/device/code")
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .body(Map.of(
                            "client_id", copilotProperties.getGithub().getClientId(),
                            "scope", "read:user"
                    ))
                    .retrieve()
                    .body(String.class);

            @SuppressWarnings("unchecked")
            Map<String, Object> data = objectMapper.readValue(response, Map.class);

            return new DeviceCodeResponse(
                    (String) data.get("device_code"),
                    (String) data.get("user_code"),
                    (String) data.get("verification_uri"),
                    ((Number) data.get("expires_in")).intValue(),
                    ((Number) data.get("interval")).intValue()
            );
        } catch (Exception e) {
            LOGGER.error("Failed to initiate device flow", e);
            throw new RuntimeException("Failed to initiate device flow: " + e.getMessage(), e);
        }
    }

    /**
     * Poll for device flow completion.
     */
    public PollResult pollDeviceFlow(String deviceCode) {
        try {
            RestClient client = createRestClient();

            String response = client.post()
                    .uri("https://github.com/login/oauth/access_token")
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .body(Map.of(
                            "client_id", copilotProperties.getGithub().getClientId(),
                            "device_code", deviceCode,
                            "grant_type", "urn:ietf:params:oauth:grant-type:device_code"
                    ))
                    .retrieve()
                    .body(String.class);

            @SuppressWarnings("unchecked")
            Map<String, Object> data = objectMapper.readValue(response, Map.class);

            if (data.containsKey("access_token")) {
                String token = (String) data.get("access_token");
                setGithubOAuthToken(token);
                return new PollResult(true, "success", token);
            }

            String error = (String) data.get("error");
            if ("authorization_pending".equals(error)) {
                return new PollResult(false, "pending", null);
            } else if ("slow_down".equals(error)) {
                return new PollResult(false, "slow_down", null);
            } else if ("expired_token".equals(error)) {
                return new PollResult(false, "expired", null);
            } else if ("access_denied".equals(error)) {
                return new PollResult(false, "denied", null);
            }

            return new PollResult(false, error != null ? error : "unknown", null);
        } catch (Exception e) {
            LOGGER.error("Failed to poll device flow", e);
            return new PollResult(false, "error: " + e.getMessage(), null);
        }
    }

    /**
     * Device code response from GitHub.
     */
    public record DeviceCodeResponse(
            String deviceCode,
            String userCode,
            String verificationUri,
            int expiresIn,
            int interval
    ) {}

    /**
     * Poll result for device flow.
     */
    public record PollResult(
            boolean success,
            String status,
            String token
    ) {}
}
