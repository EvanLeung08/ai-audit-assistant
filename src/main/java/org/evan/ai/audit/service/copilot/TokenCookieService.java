package org.evan.ai.audit.service.copilot;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Service for managing token storage in cookies.
 * Provides secure cookie-based persistence for GitHub OAuth and Copilot tokens.
 */
@Service
public class TokenCookieService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TokenCookieService.class);

    private static final String GITHUB_TOKEN_COOKIE = "gh_oauth_token";
    private static final String COPILOT_TOKEN_COOKIE = "copilot_token";
    private static final String COPILOT_TOKEN_EXPIRY_COOKIE = "copilot_token_expiry";

    // Cookie max age: 30 days
    private static final int COOKIE_MAX_AGE = 30 * 24 * 60 * 60;

    /**
     * Save GitHub OAuth token to cookie.
     */
    public void saveGithubToken(String token) {
        if (token == null || token.isEmpty()) {
            return;
        }
        try {
            String encodedToken = Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8));
            setCookie(GITHUB_TOKEN_COOKIE, encodedToken, COOKIE_MAX_AGE);
            LOGGER.debug("GitHub OAuth token saved to cookie");
        } catch (Exception e) {
            LOGGER.warn("Failed to save GitHub token to cookie: {}", e.getMessage());
        }
    }

    /**
     * Get GitHub OAuth token from cookie.
     */
    public String getGithubToken() {
        try {
            String encodedToken = getCookie(GITHUB_TOKEN_COOKIE);
            if (encodedToken != null && !encodedToken.isEmpty()) {
                String token = new String(Base64.getDecoder().decode(encodedToken), StandardCharsets.UTF_8);
                LOGGER.debug("GitHub OAuth token retrieved from cookie");
                return token;
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to get GitHub token from cookie: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Save Copilot token and expiry to cookies.
     */
    public void saveCopilotToken(String token, long expiresAt) {
        if (token == null || token.isEmpty()) {
            return;
        }
        try {
            String encodedToken = Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8));
            setCookie(COPILOT_TOKEN_COOKIE, encodedToken, COOKIE_MAX_AGE);
            setCookie(COPILOT_TOKEN_EXPIRY_COOKIE, String.valueOf(expiresAt), COOKIE_MAX_AGE);
            LOGGER.debug("Copilot token saved to cookie, expires at: {}", expiresAt);
        } catch (Exception e) {
            LOGGER.warn("Failed to save Copilot token to cookie: {}", e.getMessage());
        }
    }

    /**
     * Get Copilot token from cookie.
     */
    public String getCopilotToken() {
        try {
            String encodedToken = getCookie(COPILOT_TOKEN_COOKIE);
            if (encodedToken != null && !encodedToken.isEmpty()) {
                String token = new String(Base64.getDecoder().decode(encodedToken), StandardCharsets.UTF_8);
                LOGGER.debug("Copilot token retrieved from cookie");
                return token;
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to get Copilot token from cookie: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Get Copilot token expiry from cookie.
     */
    public long getCopilotTokenExpiry() {
        try {
            String expiryStr = getCookie(COPILOT_TOKEN_EXPIRY_COOKIE);
            if (expiryStr != null && !expiryStr.isEmpty()) {
                return Long.parseLong(expiryStr);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to get Copilot token expiry from cookie: {}", e.getMessage());
        }
        return 0;
    }

    /**
     * Check if Copilot token in cookie is still valid (not expired).
     */
    public boolean isCopilotTokenValid() {
        long expiry = getCopilotTokenExpiry();
        if (expiry <= 0) {
            return false;
        }
        // Token is valid if it expires more than 5 minutes from now
        return expiry > (System.currentTimeMillis() / 1000) + 300;
    }

    /**
     * Clear all token cookies.
     */
    public void clearAllTokens() {
        try {
            deleteCookie(GITHUB_TOKEN_COOKIE);
            deleteCookie(COPILOT_TOKEN_COOKIE);
            deleteCookie(COPILOT_TOKEN_EXPIRY_COOKIE);
            LOGGER.info("All token cookies cleared");
        } catch (Exception e) {
            LOGGER.warn("Failed to clear token cookies: {}", e.getMessage());
        }
    }

    /**
     * Check if we have a GitHub token in cookie.
     */
    public boolean hasGithubToken() {
        String token = getGithubToken();
        return token != null && !token.isEmpty();
    }

    // ==================== Cookie Helper Methods ====================

    private void setCookie(String name, String value, int maxAge) {
        HttpServletResponse response = getResponse();
        if (response == null) {
            LOGGER.warn("No HTTP response available, cannot set cookie");
            return;
        }

        String encodedValue = URLEncoder.encode(value, StandardCharsets.UTF_8);
        Cookie cookie = new Cookie(name, encodedValue);
        cookie.setPath("/");
        cookie.setMaxAge(maxAge);
        cookie.setHttpOnly(true);
        cookie.setSecure(false); // Set to true in production with HTTPS
        response.addCookie(cookie);
    }

    private String getCookie(String name) {
        HttpServletRequest request = getRequest();
        if (request == null) {
            return null;
        }

        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (name.equals(cookie.getName())) {
                    try {
                        return URLDecoder.decode(cookie.getValue(), StandardCharsets.UTF_8);
                    } catch (Exception e) {
                        return cookie.getValue();
                    }
                }
            }
        }
        return null;
    }

    private void deleteCookie(String name) {
        HttpServletResponse response = getResponse();
        if (response == null) {
            return;
        }

        Cookie cookie = new Cookie(name, "");
        cookie.setPath("/");
        cookie.setMaxAge(0);
        cookie.setHttpOnly(true);
        response.addCookie(cookie);
    }

    private HttpServletRequest getRequest() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attrs != null ? attrs.getRequest() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private HttpServletResponse getResponse() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attrs != null ? attrs.getResponse() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
