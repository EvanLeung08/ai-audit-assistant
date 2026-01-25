package org.evan.ai.audit.service.copilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.evan.ai.audit.config.CopilotProperties;
import org.evan.ai.audit.model.CopilotToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Service for managing GitHub Copilot API tokens.
 * Handles token retrieval from GitHub API, caching, and automatic refresh.
 *
 * The token flow is:
 * 1. User authenticates via GitHub Device Flow in browser
 * 2. GitHub OAuth Token is stored
 * 3. Exchange for Copilot Token (via /copilot_internal/v2/token)
 * 4. Use Copilot Token as Bearer token for API calls
 */
@Service
public class CopilotTokenService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CopilotTokenService.class);

    private final CopilotProperties copilotProperties;
    private final ObjectMapper objectMapper;
    private final ProxyService proxyService;
    private final ReentrantLock tokenLock = new ReentrantLock();

    private volatile String githubOAuthToken;
    private volatile CopilotToken cachedCopilotToken;

    public CopilotTokenService(CopilotProperties copilotProperties, ObjectMapper objectMapper,
                               ProxyService proxyService) {
        this.copilotProperties = copilotProperties;
        this.objectMapper = objectMapper;
        this.proxyService = proxyService;

        LOGGER.info("CopilotTokenService initialized - waiting for user authentication");
    }

    /**
     * Create a RestClient with current proxy configuration.
     */
    private RestClient createRestClient() {
        return proxyService.createRestClientBuilder()
                .defaultHeader("Accept", "application/json")
                .build();
    }

    /**
     * Set the GitHub OAuth token (from device flow authentication).
     */
    public void setGithubOAuthToken(String token) {
        tokenLock.lock();
        try {
            this.githubOAuthToken = token;
            this.cachedCopilotToken = null; // Clear cached Copilot token to force refresh
            LOGGER.info("GitHub OAuth token set from user authentication");
        } finally {
            tokenLock.unlock();
        }
    }

    /**
     * Check if we have a valid GitHub OAuth token.
     */
    public boolean hasOAuthToken() {
        return githubOAuthToken != null && !githubOAuthToken.isEmpty();
    }

    /**
     * Check if the user is authenticated and ready to use the API.
     */
    public boolean isAuthenticated() {
        if (!hasOAuthToken()) {
            return false;
        }
        // Try to get a valid Copilot token
        try {
            getCopilotToken();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get the current Copilot API token, refreshing if necessary.
     * This is the token used as Bearer token for API calls.
     *
     * @return The Copilot API token
     * @throws RuntimeException if no OAuth token is available or token exchange fails
     */
    public String getCopilotToken() {
        tokenLock.lock();
        try {
            // Check if we have a valid cached token
            if (cachedCopilotToken != null && !cachedCopilotToken.isExpired()) {
                LOGGER.debug("Using cached Copilot token");
                return cachedCopilotToken.getToken();
            }

            // Need to refresh the token
            if (githubOAuthToken == null || githubOAuthToken.isEmpty()) {
                throw new RuntimeException("Not authorized: Please complete GitHub Copilot authorization first");
            }

            LOGGER.info("Refreshing Copilot token...");
            cachedCopilotToken = exchangeForCopilotToken(githubOAuthToken);
            LOGGER.info("Copilot token refreshed, expires at: {}", cachedCopilotToken.getExpiresAt());

            return cachedCopilotToken.getToken();
        } finally {
            tokenLock.unlock();
        }
    }

    /**
     * Exchange GitHub OAuth token for Copilot API token.
     */
    private CopilotToken exchangeForCopilotToken(String oauthToken) {
        String tokenUrl = copilotProperties.getApi().getTokenUrl();
        String userAgent = copilotProperties.getApi().getUserAgent();

        try {
            String response = createRestClient().get()
                    .uri(tokenUrl)
                    .header("Authorization", "token " + oauthToken)
                    .header("User-Agent", userAgent)
                    .retrieve()
                    .body(String.class);

            CopilotToken copilotToken = objectMapper.readValue(response, CopilotToken.class);

            if (copilotToken.getToken() == null || copilotToken.getToken().isEmpty()) {
                throw new RuntimeException("Unable to obtain Copilot Token. Please ensure your GitHub account has an active GitHub Copilot subscription");
            }

            return copilotToken;
        } catch (Exception e) {
            LOGGER.error("Failed to exchange OAuth token for Copilot token", e);
            String errorMsg = e.getMessage();
            if (errorMsg != null && (errorMsg.contains("UnknownHostException") ||
                    errorMsg.contains("Failed to resolve") ||
                    errorMsg.contains("Network is unreachable") ||
                    errorMsg.contains("Connection refused") ||
                    errorMsg.contains("Connection timed out"))) {
                throw new RuntimeException("Network connection failed: Unable to access GitHub API. Please check your network connection or proxy settings.", e);
            }
            throw new RuntimeException("Failed to obtain Copilot Token: " + errorMsg, e);
        }
    }

    /**
     * Get the cached token info (for debugging/status).
     */
    public CopilotToken getCachedTokenInfo() {
        return cachedCopilotToken;
    }

    /**
     * Clear all cached tokens (logout).
     */
    public void clearTokens() {
        tokenLock.lock();
        try {
            this.githubOAuthToken = null;
            this.cachedCopilotToken = null;
            LOGGER.info("User logged out, tokens cleared");
        } finally {
            tokenLock.unlock();
        }
    }
}
