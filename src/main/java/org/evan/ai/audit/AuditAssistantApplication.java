package org.evan.ai.audit;

import org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * AI Audit Assistant Application
 *
 * This application allows users to upload Word documents containing audit questions,
 * automatically answers them using RAG (Retrieval-Augmented Generation) from a knowledge base,
 * and returns the completed document.
 */
@SpringBootApplication(exclude = {
        OpenAiChatAutoConfiguration.class,
        OpenAiEmbeddingAutoConfiguration.class
})
@ConfigurationPropertiesScan
public class AuditAssistantApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuditAssistantApplication.class, args);
    }
}
