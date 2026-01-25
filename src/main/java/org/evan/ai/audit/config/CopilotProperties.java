package org.evan.ai.audit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Configuration properties for GitHub Copilot authentication.
 */
@ConfigurationProperties(prefix = "copilot")
public class CopilotProperties {

    /**
     * Authentication mode: "oauth" for GitHub Copilot OAuth flow, "env" for environment variable
     */
    private String authMode = "oauth";

    private GitHub github = new GitHub();

    private Api api = new Api();

    public String getAuthMode() {
        return authMode;
    }

    public void setAuthMode(String authMode) {
        this.authMode = authMode;
    }

    public GitHub getGithub() {
        return github;
    }

    public void setGithub(GitHub github) {
        this.github = github;
    }

    public Api getApi() {
        return api;
    }

    public void setApi(Api api) {
        this.api = api;
    }

    /**
     * GitHub OAuth configuration.
     */
    public static class GitHub {
        /**
         * GitHub OAuth App Client ID (for device flow authentication)
         */
        private String clientId = "Iv1.b507a08c87ecfe98";

        /**
         * GitHub OAuth token (if already authenticated)
         */
        private String oauthToken;

        /**
         * Paths to search for local GitHub Copilot config files
         */
        private List<String> configPaths = List.of(
                "${user.home}/.config/github-copilot/hosts.json",
                "${user.home}/.config/github-copilot/apps.json"
        );

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getOauthToken() {
            return oauthToken;
        }

        public void setOauthToken(String oauthToken) {
            this.oauthToken = oauthToken;
        }

        public List<String> getConfigPaths() {
            return configPaths;
        }

        public void setConfigPaths(List<String> configPaths) {
            this.configPaths = configPaths;
        }
    }

    /**
     * Copilot API configuration.
     */
    public static class Api {
        /**
         * Base URL for GitHub Copilot API
         */
        private String baseUrl = "https://api.githubcopilot.com";

        /**
         * GitHub API URL for token exchange
         */
        private String tokenUrl = "https://api.github.com/copilot_internal/v2/token";

        /**
         * Copilot integration ID header value
         */
        private String integrationId = "vscode-chat";

        /**
         * OpenAI organization header value
         */
        private String openaiOrganization = "github-copilot";

        /**
         * OpenAI intent header value
         */
        private String openaiIntent = "conversation-panel";

        /**
         * User agent for API requests
         */
        private String userAgent = "evan-ai-audit-assistant/1.0.0";

        /**
         * Client version header value
         */
        private String clientVersion = "1.0.0";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getTokenUrl() {
            return tokenUrl;
        }

        public void setTokenUrl(String tokenUrl) {
            this.tokenUrl = tokenUrl;
        }

        public String getIntegrationId() {
            return integrationId;
        }

        public void setIntegrationId(String integrationId) {
            this.integrationId = integrationId;
        }

        public String getOpenaiOrganization() {
            return openaiOrganization;
        }

        public void setOpenaiOrganization(String openaiOrganization) {
            this.openaiOrganization = openaiOrganization;
        }

        public String getOpenaiIntent() {
            return openaiIntent;
        }

        public void setOpenaiIntent(String openaiIntent) {
            this.openaiIntent = openaiIntent;
        }

        public String getUserAgent() {
            return userAgent;
        }

        public void setUserAgent(String userAgent) {
            this.userAgent = userAgent;
        }

        public String getClientVersion() {
            return clientVersion;
        }

        public void setClientVersion(String clientVersion) {
            this.clientVersion = clientVersion;
        }
    }
}
