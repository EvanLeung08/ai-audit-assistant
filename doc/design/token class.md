classDiagram
class CopilotTokenService {
-CopilotProperties properties
-TokenCookieService cookieService
-String githubOAuthToken
-CopilotToken cachedCopilotToken
+isAuthenticated() boolean
+setGithubOAuthToken(String)
+getCopilotToken() String
+clearAuthentication()
-exchangeForCopilotToken() CopilotToken
}

    class TokenCookieService {
        +saveGithubToken(String)
        +getGithubToken() String
        +saveCopilotToken(String, long)
        +getCopilotToken() String
        +isCopilotTokenValid() boolean
        +clearAllTokens()
    }
    
    class GitHubDeviceAuthService {
        -CopilotProperties properties
        -ProxyService proxyService
        +requestDeviceCode() DeviceCodeResponse
        +pollForAccessToken(String) DeviceTokenResponse
    }
    
    class ProxyService {
        -boolean proxyEnabled
        -String proxyUsername
        -String proxyPassword
        +enableProxy(String, String)
        +disableProxy()
        +createRestClientBuilder() RestClient.Builder
    }
    
    class CopilotToken {
        -String token
        -long expiresAt
        +isExpired() boolean
        +getToken() String
        +getExpiresAt() long
    }
    
    CopilotTokenService --> TokenCookieService : persists to
    CopilotTokenService --> CopilotToken : caches
    GitHubDeviceAuthService --> ProxyService : uses
    CopilotTokenService ..> GitHubDeviceAuthService : device flow
