package org.evan.ai.audit.config;

import org.evan.ai.audit.service.copilot.CopilotTokenService;
import org.evan.ai.audit.vectorstore.PersistentVectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
     * Creates a persistent vector store that stores embeddings in a JSON file.
     * The storage path is configured via audit.knowledge-base.storage-path property.
     */
    @Bean
    VectorStore vectorStore(EmbeddingModel embeddingModel) {
        String storagePath = auditProperties.getKnowledgeBase().getStoragePath();
        Path path = Paths.get(storagePath);
        LOGGER.info("Initializing PersistentVectorStore with storage path: {}", path.toAbsolutePath());
        return new PersistentVectorStore(embeddingModel, path);
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
     * Now supports both legacy classpath documents and user-uploaded documents.
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
         * For PersistentVectorStore, documents are already loaded from the JSON file.
         * Legacy documents from configuration are only loaded if the store is empty.
         */
        public synchronized void ensureLoaded() {
            if (knowledgeBaseLoaded.get()) {
                LOGGER.debug("Knowledge base already loaded");
                return;
            }

            if (!copilotTokenService.isAuthenticated()) {
                throw new RuntimeException("Not authorized: Please complete GitHub Copilot authorization first");
            }

            // Check if PersistentVectorStore already has documents
            if (vectorStore instanceof PersistentVectorStore persistentStore) {
                if (!persistentStore.isEmpty()) {
                    LOGGER.info("Knowledge base loaded from persistent storage with {} documents",
                            persistentStore.getDocumentCount());
                    knowledgeBaseLoaded.set(true);
                    return;
                }
            }

            // Load legacy documents from configuration if store is empty
            LOGGER.info("Loading legacy knowledge base documents from configuration...");
            loadLegacyDocuments();
        }

        /**
         * Load legacy documents from the configuration file.
         * These are the hardcoded documents in application.yml.
         */
        private void loadLegacyDocuments() {
            TokenTextSplitter splitter = new TokenTextSplitter();
            List<Document> allDocuments = new ArrayList<>();

            List<String> documentPaths = auditProperties.getKnowledgeBase().getDocuments();

            if (documentPaths == null || documentPaths.isEmpty()) {
                LOGGER.info("No legacy documents configured in application.yml");
                knowledgeBaseLoaded.set(true);
                return;
            }

            for (String documentPath : documentPaths) {
                try {
                    Resource resource = resourceLoader.getResource(documentPath);
                    if (resource.exists()) {
                        LOGGER.info("Loading legacy document: {}", documentPath);
                        TikaDocumentReader reader = new TikaDocumentReader(resource);
                        List<Document> docs = reader.read();
                        List<Document> splitDocuments = splitter.split(docs);

                        // Enrich with metadata
                        String fileName = extractFileName(documentPath);
                        int lineNumber = 1;
                        for (Document splitDoc : splitDocuments) {
                            Map<String, Object> metadata = new HashMap<>(splitDoc.getMetadata());
                            metadata.put("fileName", fileName);
                            metadata.put("source", documentPath);
                            metadata.put("lineNumber", lineNumber++);
                            metadata.put("type", "legacy");

                            Document enrichedDoc = Document.builder()
                                    .id(splitDoc.getId())
                                    .text(splitDoc.getText())
                                    .metadata(metadata)
                                    .build();
                            allDocuments.add(enrichedDoc);
                        }
                        LOGGER.info("Loaded {} chunks from {}", splitDocuments.size(), documentPath);
                    } else {
                        LOGGER.warn("Legacy document not found: {}", documentPath);
                    }
                } catch (Exception e) {
                    LOGGER.error("Failed to load legacy document: {}", documentPath, e);
                }
            }

            if (!allDocuments.isEmpty()) {
                vectorStore.add(allDocuments);
                LOGGER.info("Successfully loaded {} total legacy document chunks into vector store", allDocuments.size());
            } else {
                LOGGER.warn("No legacy documents were loaded into the knowledge base");
            }

            knowledgeBaseLoaded.set(true);
        }

        private String extractFileName(String path) {
            int lastSlash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
            return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
        }

        public boolean isLoaded() {
            return knowledgeBaseLoaded.get();
        }

        /**
         * Check if the knowledge base has any documents.
         */
        public boolean hasDocuments() {
            if (vectorStore instanceof PersistentVectorStore persistentStore) {
                return !persistentStore.isEmpty();
            }
            return knowledgeBaseLoaded.get();
        }
    }
}
