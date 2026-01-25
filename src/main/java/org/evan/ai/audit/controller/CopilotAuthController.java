package org.evan.ai.audit.controller;

import org.evan.ai.audit.model.CopilotToken;
import org.evan.ai.audit.model.DeviceCodeResponse;
import org.evan.ai.audit.model.DeviceTokenResponse;
import org.evan.ai.audit.service.copilot.CopilotTokenService;
import org.evan.ai.audit.service.copilot.GitHubDeviceAuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * REST Controller for GitHub Copilot device authentication flow.
 *
 * Provides endpoints to:
 * 1. Initiate device flow authentication
 * 2. Poll for authentication status
 * 3. Check current authentication status
 */
@RestController
@RequestMapping("/api/auth")
public class CopilotAuthController {

    private static final Logger LOGGER = LoggerFactory.getLogger(CopilotAuthController.class);

    private final GitHubDeviceAuthService deviceAuthService;
    private final CopilotTokenService copilotTokenService;

    // Store pending device codes (in production, use Redis or similar)
    private final Map<String, DeviceCodeResponse> pendingDeviceCodes = new ConcurrentHashMap<>();

    public CopilotAuthController(GitHubDeviceAuthService deviceAuthService,
                                  CopilotTokenService copilotTokenService) {
        this.deviceAuthService = deviceAuthService;
        this.copilotTokenService = copilotTokenService;
    }

    /**
     * Check current authentication status.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getAuthStatus() {
        Map<String, Object> status = new HashMap<>();

        boolean hasOAuthToken = copilotTokenService.hasOAuthToken();
        status.put("authenticated", hasOAuthToken);

        if (hasOAuthToken) {
            CopilotToken tokenInfo = copilotTokenService.getCachedTokenInfo();
            if (tokenInfo != null) {
                status.put("tokenExpired", tokenInfo.isExpired());
                status.put("expiresAt", tokenInfo.getExpiresAt());
            } else {
                status.put("tokenCached", false);
            }
        }

        return ResponseEntity.ok(status);
    }

    /**
     * Initiate device flow authentication.
     * Returns device code and verification URL for user to authenticate.
     */
    @PostMapping("/device/start")
    public ResponseEntity<Map<String, Object>> startDeviceAuth() {
        LOGGER.info("Starting device authentication flow");

        try {
            DeviceCodeResponse response = deviceAuthService.requestDeviceCode();

            // Store the device code for later polling
            pendingDeviceCodes.put(response.getDeviceCode(), response);

            Map<String, Object> result = new HashMap<>();
            result.put("deviceCode", response.getDeviceCode());
            result.put("userCode", response.getUserCode());
            result.put("verificationUri", response.getVerificationUri());
            result.put("verificationUriComplete", response.getVerificationUriComplete());
            result.put("expiresIn", response.getExpiresIn());
            result.put("interval", response.getInterval());
            result.put("message", "Please visit the verification URL and enter the user code to authenticate.");

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            LOGGER.error("Failed to start device authentication", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to start authentication: " + e.getMessage()));
        }
    }

    /**
     * Poll for authentication completion.
     * Call this endpoint after the user has authorized in their browser.
     */
    @PostMapping("/device/poll")
    public ResponseEntity<Map<String, Object>> pollDeviceAuth(@RequestBody Map<String, String> request) {
        String deviceCode = request.get("deviceCode");

        if (deviceCode == null || deviceCode.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "deviceCode is required"));
        }

        LOGGER.debug("Polling for device authentication: {}", deviceCode);

        try {
            DeviceTokenResponse tokenResponse = deviceAuthService.pollForAccessToken(deviceCode);

            Map<String, Object> result = new HashMap<>();

            if (tokenResponse.getAccessToken() != null) {
                // Success! Store the OAuth token
                copilotTokenService.setGithubOAuthToken(tokenResponse.getAccessToken());
                pendingDeviceCodes.remove(deviceCode);

                // Try to get Copilot token immediately to verify network connectivity
                try {
                    copilotTokenService.getCopilotToken();
                    result.put("success", true);
                    result.put("message", "授权成功！已准备好使用 AI 功能。");
                    LOGGER.info("Device authentication completed successfully, Copilot token obtained");
                } catch (Exception e) {
                    LOGGER.warn("OAuth token obtained but failed to get Copilot token: {}", e.getMessage());
                    result.put("success", true);
                    result.put("warning", true);
                    result.put("message", "GitHub 授权成功，但获取 Copilot Token 失败（可能是网络问题）。将在使用时重试。");
                }

            } else if (tokenResponse.isPending()) {
                result.put("success", false);
                result.put("pending", true);
                result.put("message", "Waiting for user authorization...");

            } else if (tokenResponse.isSlowDown()) {
                result.put("success", false);
                result.put("slowDown", true);
                result.put("message", "Please slow down polling requests");

            } else if (tokenResponse.hasError()) {
                result.put("success", false);
                result.put("error", tokenResponse.getError());
                result.put("errorDescription", tokenResponse.getErrorDescription());
                pendingDeviceCodes.remove(deviceCode);
            }

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            LOGGER.error("Failed to poll for device authentication", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to poll authentication: " + e.getMessage()));
        }
    }

    /**
     * Manually set OAuth token (for testing or when token is obtained externally).
     */
    @PostMapping("/token")
    public ResponseEntity<Map<String, Object>> setToken(@RequestBody Map<String, String> request) {
        String token = request.get("token");

        if (token == null || token.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "token is required"));
        }

        copilotTokenService.setGithubOAuthToken(token);
        LOGGER.info("OAuth token set manually");

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Token set successfully"
        ));
    }

    /**
     * Clear all cached tokens.
     */
    @DeleteMapping("/token")
    public ResponseEntity<Map<String, Object>> clearTokens() {
        copilotTokenService.clearTokens();
        LOGGER.info("Tokens cleared");

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Tokens cleared successfully"
        ));
    }

    /**
     * Test the current token by attempting to refresh it.
     */
    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> testToken() {
        try {
            String token = copilotTokenService.getCopilotToken();

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Token is valid");
            result.put("tokenPrefix", token.substring(0, Math.min(20, token.length())) + "...");

            CopilotToken tokenInfo = copilotTokenService.getCachedTokenInfo();
            if (tokenInfo != null) {
                result.put("expiresAt", tokenInfo.getExpiresAt());
            }

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            LOGGER.error("Token test failed", e);
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }
}
