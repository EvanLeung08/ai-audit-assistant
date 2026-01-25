package org.evan.ai.audit.config;

import org.evan.ai.audit.service.copilot.CopilotTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;

import java.util.UUID;

/**
 * Configuration for GitHub Copilot as OpenAI-compatible API provider.
 */
@Configuration
@ConditionalOnProperty(name = "copilot.auth-mode", havingValue = "oauth", matchIfMissing = true)
public class CopilotOpenAiConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(CopilotOpenAiConfig.class);

    private final CopilotProperties copilotProperties;
    private final CopilotTokenService copilotTokenService;

    public CopilotOpenAiConfig(CopilotProperties copilotProperties, CopilotTokenService copilotTokenService) {
        this.copilotProperties = copilotProperties;
        this.copilotTokenService = copilotTokenService;
        LOGGER.info("Copilot OAuth mode enabled - using dynamic token authentication");
    }

    private RestClient.Builder createCopilotRestClientBuilder() {
        return RestClient.builder()
                .requestInterceptor(copilotHeadersInterceptor())
                .requestInterceptor(copilotAuthInterceptor());
    }


    private ClientHttpRequestInterceptor copilotHeadersInterceptor() {
        return (request, body, execution) -> {
            HttpHeaders headers = request.getHeaders();
            headers.set("x-request-id", UUID.randomUUID().toString());
            headers.set("vscode-sessionid", UUID.randomUUID().toString() + System.currentTimeMillis());
            headers.set("vscode-machineid", getMachineId());
            headers.set("copilot-integration-id", copilotProperties.getApi().getIntegrationId());
            headers.set("openai-organization", copilotProperties.getApi().getOpenaiOrganization());
            headers.set("openai-intent", copilotProperties.getApi().getOpenaiIntent());
            headers.set("client-version", copilotProperties.getApi().getClientVersion());
            headers.set("User-Agent", copilotProperties.getApi().getUserAgent());
            return execution.execute(request, body);
        };
    }

    private ClientHttpRequestInterceptor copilotAuthInterceptor() {
        return (request, body, execution) -> {
            try {
                String token = copilotTokenService.getCopilotToken();
                request.getHeaders().set("Authorization", "Bearer " + token);
            } catch (Exception e) {
                LOGGER.error("Failed to get Copilot token for request", e);
                throw e;
            }
            return execution.execute(request, body);
        };
    }

    private String getMachineId() {
        String machineIdSource = System.getProperty("user.name") +
                System.getProperty("os.name") +
                System.getProperty("user.home");
        return UUID.nameUUIDFromBytes(machineIdSource.getBytes()).toString();
    }

    @Bean
    @Primary
    public OpenAiApi openAiApi() {
        String baseUrl = copilotProperties.getApi().getBaseUrl();
        LOGGER.info("Creating OpenAI API with Copilot base URL: {}", baseUrl);
        return OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey("copilot-dynamic-token")
                .completionsPath("/chat/completions")
                .embeddingsPath("/embeddings")
                .restClientBuilder(createCopilotRestClientBuilder())
                .build();
    }

    @Bean
    @Primary
    public OpenAiChatModel openAiChatModel(OpenAiApi openAiApi) {
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model("gpt-4o")
                .build();
        LOGGER.info("Creating OpenAI Chat Model with Copilot configuration");
        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(options)
                .build();
    }

    @Bean
    @Primary
    public OpenAiEmbeddingModel openAiEmbeddingModel(OpenAiApi openAiApi) {
        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model("copilot-text-embedding-ada-002")
                .build();
        LOGGER.info("Creating OpenAI Embedding Model with Copilot configuration");
        return new OpenAiEmbeddingModel(openAiApi, MetadataMode.EMBED, options);
    }
}
