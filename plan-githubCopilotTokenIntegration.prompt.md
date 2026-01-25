# Plan: Integrate GitHub Copilot OAuth Token Flow into Spring Boot for Dynamic API Key

This plan ports the `github-copilot-api` Go implementation to Spring Boot + Spring AI. The core idea is to implement GitHub Device Flow OAuth, dynamically obtain Copilot API tokens (equivalent to OpenAI API keys), and programmatically inject them into Spring AI's `OpenAiApi` configuration without hardcoding in `application.yml`.

## Steps

1. **Create GitHub Copilot authentication configuration** in [config/](src/main/java/org/evan/ai/audit/config/)
   - Add `CopilotProperties.java` for configuration properties (clientId, baseUrl, token paths)
   - Update [application.yml](src/main/resources/application.yml) with `copilot.github.client-id` and related settings

2. **Implement GitHub Device Flow OAuth service** in new [service/copilot/](src/main/java/org/evan/ai/audit/service/) package
   - Create `GitHubDeviceAuthService.java` porting the logic from [device_auth.go](sample/github-copilot-api/device_auth.go)
   - Implement device code request, user verification URL display, and access token polling

3. **Implement Copilot Token Management service**
   - Create `CopilotTokenService.java` porting logic from [auth.go](sample/github-copilot-api/auth.go)
   - Handle token retrieval from `https://api.github.com/copilot_internal/v2/token`
   - Implement token caching with expiry-based refresh (checking `expires_at`)
   - Support loading OAuth token from local config files (`~/.config/github-copilot/hosts.json`)

4. **Create custom Spring AI OpenAI client configuration**
   - Create `CopilotOpenAiConfig.java` to programmatically build `OpenAiApi` and `OpenAiChatModel`/`OpenAiEmbeddingModel` beans
   - Override default Spring AI auto-configuration with dynamic token injection using `CopilotTokenService`
   - Add required HTTP headers (`copilot-integration-id`, `openai-organization`, etc.) via custom `RestClient` interceptor

5. **Add REST endpoint for device authentication flow**
   - Create `CopilotAuthController.java` to expose `/api/auth/device` endpoints
   - Provide endpoints: `GET /device-code` (initiate flow), `POST /device-token` (complete flow)
   - Return device code and verification URL for user to authenticate via browser

6. **Update existing configuration to support both modes**
   - Modify [application.yml](src/main/resources/application.yml) to support `copilot.auth-mode: oauth|env` switching
   - Ensure existing `OPENAI_API_KEY` environment variable mode continues to work as fallback

## Further Considerations

1. **Token persistence strategy?** Option A: In-memory only (token lost on restart) / Option B: File-based persistence (like Go version reads from hosts.json) / Option C: Encrypted database storage — Recommend Option B for simplicity
2. **HTTP client for token refresh?** Use Spring's `WebClient` (reactive) or `RestClient` (blocking) — Recommend `RestClient` since project already uses synchronous patterns
3. **Auto-refresh mechanism?** Implement background scheduled task to refresh token before expiry, or lazy refresh on each request — Recommend lazy refresh for simplicity
