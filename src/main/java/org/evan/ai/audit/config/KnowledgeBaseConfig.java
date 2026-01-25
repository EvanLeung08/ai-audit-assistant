package org.evan.ai.audit.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for the knowledge base and vector store.
 * Loads knowledge base documents into the vector store for RAG retrieval.
 */
@Configuration
public class KnowledgeBaseConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(KnowledgeBaseConfig.class);

    private final ResourceLoader resourceLoader;
    private final AuditProperties auditProperties;

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
     * Application runner that loads all knowledge base documents into the vector store.
     */
    @Bean
    ApplicationRunner loadKnowledgeBase(VectorStore vectorStore) {
        return args -> {
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
                LOGGER.info("Successfully loaded {} total document chunks into vector store", allDocuments.size());
            } else {
                LOGGER.warn("No documents were loaded into the knowledge base");
            }
        };
    }
}
