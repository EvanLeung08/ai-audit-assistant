package org.evan.ai.audit.config;

import org.evan.ai.audit.service.copilot.CopilotTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Configuration for the knowledge base and vector store.
 * Loads knowledge base documents into the vector store for RAG retrieval.
 * Knowledge base loading is deferred until user authentication is complete.
 */
@Configuration
public class KnowledgeBaseConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(KnowledgeBaseConfig.class);

    private final ResourceLoader resourceLoader;
    private final AuditProperties auditProperties;
    private final AtomicBoolean knowledgeBaseLoaded = new AtomicBoolean(false);

    public KnowledgeBaseConfig(ResourceLoader resourceLoader, AuditProperties auditProperties) {
        this.resourceLoader = resourceLoader;
        this.auditProperties = auditProperties;
    }

    /**
     * Creates a simple in-memory vector store for storing document embeddings.
     */
    @Bean
    VectorStore vectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }

    /**
     * Service to load knowledge base on demand (after user authentication).
     */
    @Bean
    public KnowledgeBaseLoader knowledgeBaseLoader(VectorStore vectorStore, CopilotTokenService copilotTokenService) {
        return new KnowledgeBaseLoader(vectorStore, copilotTokenService, resourceLoader, auditProperties, knowledgeBaseLoaded);
    }

    /**
     * Helper class to load knowledge base on demand.
     */
    public static class KnowledgeBaseLoader {
        private static final Logger LOGGER = LoggerFactory.getLogger(KnowledgeBaseLoader.class);

        private final VectorStore vectorStore;
        private final CopilotTokenService copilotTokenService;
        private final ResourceLoader resourceLoader;
        private final AuditProperties auditProperties;
        private final AtomicBoolean knowledgeBaseLoaded;

        public KnowledgeBaseLoader(VectorStore vectorStore, CopilotTokenService copilotTokenService,
                                   ResourceLoader resourceLoader, AuditProperties auditProperties,
                                   AtomicBoolean knowledgeBaseLoaded) {
            this.vectorStore = vectorStore;
            this.copilotTokenService = copilotTokenService;
            this.resourceLoader = resourceLoader;
            this.auditProperties = auditProperties;
            this.knowledgeBaseLoaded = knowledgeBaseLoaded;
        }

        /**
         * Ensure the knowledge base is loaded. Call this before using RAG features.
         * This method is idempotent - it will only load once.
         */
        public synchronized void ensureLoaded() {
            if (knowledgeBaseLoaded.get()) {
                LOGGER.debug("Knowledge base already loaded");
                return;
            }

            if (!copilotTokenService.hasOAuthToken()) {
                throw new RuntimeException("请先完成 GitHub Copilot 授权认证");
            }

            LOGGER.info("Starting to load knowledge base documents...");

            TokenTextSplitter splitter = new TokenTextSplitter();
            List<Document> allDocuments = new ArrayList<>();

            List<String> documentPaths = auditProperties.getKnowledgeBase().getDocuments();

            for (String documentPath : documentPaths) {
                try {
                    Resource resource = resourceLoader.getResource(documentPath);
                    if (resource.exists()) {
                        LOGGER.info("Loading document: {}", documentPath);
                        TikaDocumentReader reader = new TikaDocumentReader(resource);
                        List<Document> docs = reader.read();
                        List<Document> splitDocuments = splitter.split(docs);
                        allDocuments.addAll(splitDocuments);
                        LOGGER.info("Loaded {} chunks from {}", splitDocuments.size(), documentPath);
                    } else {
                        LOGGER.warn("Document not found: {}", documentPath);
                    }
                } catch (Exception e) {
                    LOGGER.error("Failed to load document: {}", documentPath, e);
                }
            }

            if (!allDocuments.isEmpty()) {
                vectorStore.accept(allDocuments);
                knowledgeBaseLoaded.set(true);
                LOGGER.info("Successfully loaded {} total document chunks into vector store", allDocuments.size());
            } else {
                LOGGER.warn("No documents were loaded into the knowledge base");
            }
        }

        public boolean isLoaded() {
            return knowledgeBaseLoaded.get();
        }
    }
}
