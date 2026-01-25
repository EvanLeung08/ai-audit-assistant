sequenceDiagram
autonumber
participant User as 👤 User
participant UI as 🖥️ Web UI
participant Auth as 🔐 CopilotAuthController
participant Token as 🎫 CopilotTokenService
participant Cookie as 🍪 TokenCookieService
participant Device as 📱 GitHubDeviceAuthService
participant GH as 🐙 GitHub OAuth API
participant Copilot as ✨ Copilot Token API

    rect rgb(227, 242, 253)
        Note over User,Cookie: Page Load - Check Auth Status
        User->>UI: Open application
        UI->>Auth: GET /api/auth/status
        Auth->>Token: isAuthenticated()
        Token->>Cookie: getGithubToken()
        alt Has token in cookie
            Cookie-->>Token: OAuth token
            Token-->>Auth: true
            Auth-->>UI: {authenticated: true}
            UI-->>User: Show "Authorized" ✓
        else No token
            Cookie-->>Token: null
            Token-->>Auth: false
            Auth-->>UI: {authenticated: false}
            UI-->>User: Show "Authorize" button
        end
    end

    rect rgb(255, 243, 224)
        Note over User,GH: Device Flow Authorization
        User->>UI: Click "Authorize with GitHub"
        UI->>Auth: POST /api/auth/device/start
        Auth->>Device: requestDeviceCode()
        Device->>GH: POST /login/device/code
        GH-->>Device: {device_code, user_code, verification_uri}
        Device-->>Auth: DeviceCodeResponse
        Auth-->>UI: {userCode, verificationUri}
        UI-->>User: Display code: XXXX-XXXX<br/>Open github.com/login/device
        User->>GH: Enter code & authorize
    end

    rect rgb(232, 234, 246)
        Note over UI,GH: Polling for Token
        loop Every 5 seconds
            UI->>Auth: POST /api/auth/device/poll
            Auth->>Device: pollForAccessToken(deviceCode)
            Device->>GH: POST /login/oauth/access_token
            alt Authorization complete
                GH-->>Device: {access_token}
                Device-->>Auth: Token received
                Auth->>Token: setGithubOAuthToken(token)
                Token->>Cookie: saveGithubToken(token)
                Note over Cookie: HTTP-only cookie<br/>30 days expiry
                Auth-->>UI: {success: true}
                UI-->>User: 🎉 Authorization successful!
            else Still pending
                GH-->>Device: {error: "authorization_pending"}
                Device-->>Auth: Pending
                Auth-->>UI: {pending: true}
                Note over UI: Continue polling...
            end
        end
    end

    rect rgb(200, 230, 201)
        Note over Token,Copilot: API Call with Auto Refresh
        Token->>Token: getCopilotToken()
        alt Valid token in cache
            Token-->>Token: Return cached token
        else Token expired or missing
            Token->>Copilot: GET /copilot_internal/v2/token<br/>Authorization: token {oauth_token}
            Copilot-->>Token: {token, expires_at}
            Token->>Cookie: saveCopilotToken(token, expiresAt)
            Token-->>Token: Return new token
        end
    end
