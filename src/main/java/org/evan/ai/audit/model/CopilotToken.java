package org.evan.ai.audit.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a GitHub Copilot API token with expiration.
 */
public class CopilotToken {

    @JsonProperty("token")
    private String token;

    @JsonProperty("expires_at")
    private long expiresAt;

    public CopilotToken() {
    }

    public CopilotToken(String token, long expiresAt) {
        this.token = token;
        this.expiresAt = expiresAt;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(long expiresAt) {
        this.expiresAt = expiresAt;
    }

    /**
     * Check if the token is expired or about to expire (within 5 minutes).
     */
    public boolean isExpired() {
        // Add 5 minute buffer before actual expiration
        return expiresAt <= (System.currentTimeMillis() / 1000) + 300;
    }

    @Override
    public String toString() {
        return "CopilotToken{" +
                "token='" + (token != null ? token.substring(0, Math.min(10, token.length())) + "..." : "null") + '\'' +
                ", expiresAt=" + expiresAt +
                ", expired=" + isExpired() +
                '}';
    }
}
