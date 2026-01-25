package org.evan.ai.audit.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response from GitHub Device Flow token request.
 */
public class DeviceTokenResponse {

    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("token_type")
    private String tokenType;

    @JsonProperty("scope")
    private String scope;

    @JsonProperty("error")
    private String error;

    @JsonProperty("error_description")
    private String errorDescription;

    public DeviceTokenResponse() {
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getErrorDescription() {
        return errorDescription;
    }

    public void setErrorDescription(String errorDescription) {
        this.errorDescription = errorDescription;
    }

    public boolean hasError() {
        return error != null && !error.isEmpty();
    }

    public boolean isPending() {
        return "authorization_pending".equals(error);
    }

    public boolean isSlowDown() {
        return "slow_down".equals(error);
    }

    @Override
    public String toString() {
        return "DeviceTokenResponse{" +
                "accessToken='" + (accessToken != null ? accessToken.substring(0, Math.min(10, accessToken.length())) + "..." : "null") + '\'' +
                ", tokenType='" + tokenType + '\'' +
                ", scope='" + scope + '\'' +
                ", error='" + error + '\'' +
                '}';
    }
}
