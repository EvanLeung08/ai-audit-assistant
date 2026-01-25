package org.evan.ai.audit.service.copilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.evan.ai.audit.config.CopilotProperties;
import org.evan.ai.audit.model.DeviceCodeResponse;
import org.evan.ai.audit.model.DeviceTokenResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Service for GitHub Device Flow OAuth authentication.
 * Implements the device authorization grant flow as per RFC 8628.
 *
 * @see <a href="https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/authorizing-oauth-apps#device-flow">GitHub Device Flow</a>
 */
@Service
public class GitHubDeviceAuthService {

    private static final Logger LOGGER = LoggerFactory.getLogger(GitHubDeviceAuthService.class);

    private static final String GITHUB_DEVICE_CODE_URL = "https://github.com/login/device/code";
    private static final String GITHUB_ACCESS_TOKEN_URL = "https://github.com/login/oauth/access_token";
    private static final String DEFAULT_SCOPE = "read:user";

    private final CopilotProperties copilotProperties;
    private final ProxyService proxyService;
    private final ObjectMapper objectMapper;

    public GitHubDeviceAuthService(CopilotProperties copilotProperties, ObjectMapper objectMapper,
                                   ProxyService proxyService) {
        this.copilotProperties = copilotProperties;
        this.objectMapper = objectMapper;
        this.proxyService = proxyService;
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
     * Initiates the device flow by requesting a device code from GitHub.
     *
     * @return DeviceCodeResponse containing the device code and user code
     */
    public DeviceCodeResponse requestDeviceCode() {
        LOGGER.info("Requesting device code from GitHub...");

        String clientId = copilotProperties.getGithub().getClientId();

        String requestBody = "client_id=" + clientId + "&scope=" + DEFAULT_SCOPE;

        String response = createRestClient().post()
                .uri(GITHUB_DEVICE_CODE_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(requestBody)
                .retrieve()
                .body(String.class);

        try {
            DeviceCodeResponse deviceCodeResponse = objectMapper.readValue(response, DeviceCodeResponse.class);
            LOGGER.info("Device code received. User code: {}, Verification URL: {}",
                    deviceCodeResponse.getUserCode(),
                    deviceCodeResponse.getVerificationUri());
            return deviceCodeResponse;
        } catch (Exception e) {
            LOGGER.error("Failed to parse device code response: {}", response, e);
            throw new RuntimeException("Failed to request device code", e);
        }
    }

    /**
     * Polls GitHub for the access token after the user has authorized the device.
     *
     * @param deviceCode The device code from the initial request
     * @return DeviceTokenResponse containing the access token or error
     */
    public DeviceTokenResponse pollForAccessToken(String deviceCode) {
        LOGGER.debug("Polling for access token...");

        String clientId = copilotProperties.getGithub().getClientId();

        String requestBody = "client_id=" + clientId +
                "&device_code=" + deviceCode +
                "&grant_type=urn:ietf:params:oauth:grant-type:device_code";

        String response = createRestClient().post()
                .uri(GITHUB_ACCESS_TOKEN_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(requestBody)
                .retrieve()
                .body(String.class);

        try {
            DeviceTokenResponse tokenResponse = objectMapper.readValue(response, DeviceTokenResponse.class);

            if (tokenResponse.getAccessToken() != null) {
                LOGGER.info("Access token received successfully");
            } else if (tokenResponse.isPending()) {
                LOGGER.debug("Authorization pending, user has not yet authorized");
            } else if (tokenResponse.hasError()) {
                LOGGER.warn("Token request error: {}", tokenResponse.getError());
            }

            return tokenResponse;
        } catch (Exception e) {
            LOGGER.error("Failed to parse token response: {}", response, e);
            throw new RuntimeException("Failed to poll for access token", e);
        }
    }

    /**
     * Waits for the user to authorize the device and returns the access token.
     * This method blocks until authorization is complete or times out.
     *
     * @param deviceCodeResponse The response from requestDeviceCode()
     * @param maxWaitSeconds Maximum time to wait for authorization
     * @return The OAuth access token
     * @throws RuntimeException if authorization fails or times out
     */
    public String waitForAuthorization(DeviceCodeResponse deviceCodeResponse, int maxWaitSeconds) {
        LOGGER.info("Waiting for user authorization...");
        LOGGER.info("Please visit {} and enter code: {}",
                deviceCodeResponse.getVerificationUri(),
                deviceCodeResponse.getUserCode());

        int interval = Math.max(deviceCodeResponse.getInterval(), 5);
        int elapsed = 0;

        while (elapsed < maxWaitSeconds) {
            try {
                Thread.sleep(interval * 1000L);
                elapsed += interval;

                DeviceTokenResponse tokenResponse = pollForAccessToken(deviceCodeResponse.getDeviceCode());

                if (tokenResponse.getAccessToken() != null) {
                    LOGGER.info("Authorization successful!");
                    return tokenResponse.getAccessToken();
                }

                if (tokenResponse.isSlowDown()) {
                    interval += 5;
                    LOGGER.debug("Slowing down polling interval to {} seconds", interval);
                } else if (!tokenResponse.isPending() && tokenResponse.hasError()) {
                    throw new RuntimeException("Authorization failed: " + tokenResponse.getError() +
                            " - " + tokenResponse.getErrorDescription());
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Authorization interrupted", e);
            }
        }

        throw new RuntimeException("Authorization timed out after " + maxWaitSeconds + " seconds");
    }
}
