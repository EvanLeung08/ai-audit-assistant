package org.evan.ai.audit.vectorstore;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A persistent VectorStore implementation that stores document embeddings in a JSON file.
 * Supports tracking document source, upload time, and other metadata.
 */
public class PersistentVectorStore implements VectorStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(PersistentVectorStore.class);

    private final EmbeddingModel embeddingModel;
    private final Path storagePath;
    private final ObjectMapper objectMapper;
    private final Map<String, StoredDocument> documents;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public PersistentVectorStore(EmbeddingModel embeddingModel, Path storagePath) {
        this.embeddingModel = embeddingModel;
        this.storagePath = storagePath;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.documents = new ConcurrentHashMap<>();

        // Load existing data from file
        loadFromFile();
    }

    @Override
    public void add(List<Document> documentList) {
        lock.writeLock().lock();
        try {
            for (Document doc : documentList) {
                // Generate embedding
                float[] embedding = embeddingModel.embed(doc);

                // Create stored document with metadata
                StoredDocument storedDoc = new StoredDocument();
                storedDoc.setId(doc.getId() != null ? doc.getId() : UUID.randomUUID().toString());
                storedDoc.setContent(doc.getText());
                storedDoc.setEmbedding(embedding);
                storedDoc.setMetadata(new HashMap<>(doc.getMetadata()));
                storedDoc.setAddedAt(LocalDateTime.now());

                // Extract source information from metadata
                if (doc.getMetadata().containsKey("source")) {
                    storedDoc.setSource(doc.getMetadata().get("source").toString());
                }
                if (doc.getMetadata().containsKey("fileName")) {
                    storedDoc.setFileName(doc.getMetadata().get("fileName").toString());
                }
                if (doc.getMetadata().containsKey("lineNumber")) {
                    Object lineNum = doc.getMetadata().get("lineNumber");
                    if (lineNum instanceof Number) {
                        storedDoc.setLineNumber(((Number) lineNum).intValue());
                    } else {
                        storedDoc.setLineNumber(Integer.parseInt(lineNum.toString()));
                    }
                }

                documents.put(storedDoc.getId(), storedDoc);
                LOGGER.debug("Added document: {} from source: {}", storedDoc.getId(), storedDoc.getSource());
            }

            // Persist to file
            saveToFile();
            LOGGER.info("Added {} documents to persistent vector store", documentList.size());
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void delete(List<String> ids) {
        lock.writeLock().lock();
        try {
            for (String id : ids) {
                documents.remove(id);
            }
            saveToFile();
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void delete(org.springframework.ai.vectorstore.filter.Filter.Expression filterExpression) {
        // Filter expression based deletion is not supported in this simple implementation
        // This would require parsing the filter expression and matching against document metadata
        LOGGER.warn("Filter expression based deletion is not supported, ignoring: {}", filterExpression);
    }

    @Override
    public List<Document> similaritySearch(SearchRequest request) {
        lock.readLock().lock();
        try {
            if (documents.isEmpty()) {
                return Collections.emptyList();
            }

            // Generate embedding for the query
            float[] queryEmbedding = embeddingModel.embed(request.getQuery());

            // Calculate similarity for all documents
            List<ScoredDocument> scoredDocs = new ArrayList<>();
            for (StoredDocument storedDoc : documents.values()) {
                double similarity = cosineSimilarity(queryEmbedding, storedDoc.getEmbedding());
                if (similarity >= request.getSimilarityThreshold()) {
                    scoredDocs.add(new ScoredDocument(storedDoc, similarity));
                }
            }

            // Sort by similarity (descending) and take top K
            scoredDocs.sort((a, b) -> Double.compare(b.score, a.score));

            int topK = request.getTopK();
            List<Document> results = new ArrayList<>();
            for (int i = 0; i < Math.min(topK, scoredDocs.size()); i++) {
                ScoredDocument scored = scoredDocs.get(i);
                StoredDocument storedDoc = scored.document;

                // Create Document with enriched metadata
                Map<String, Object> metadata = new HashMap<>(storedDoc.getMetadata());
                metadata.put("similarity_score", scored.score);
                metadata.put("source", storedDoc.getSource());
                metadata.put("fileName", storedDoc.getFileName());
                metadata.put("lineNumber", storedDoc.getLineNumber());
                metadata.put("addedAt", storedDoc.getAddedAt() != null ? storedDoc.getAddedAt().toString() : null);

                Document doc = Document.builder()
                        .id(storedDoc.getId())
                        .text(storedDoc.getContent())
                        .metadata(metadata)
                        .build();
                results.add(doc);
            }

            LOGGER.debug("Similarity search returned {} results for query: {}",
                    results.size(),
                    request.getQuery().substring(0, Math.min(50, request.getQuery().length())));

            return results;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Calculate cosine similarity between two vectors.
     */
    private double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("Vectors must have the same length");
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        if (normA == 0 || normB == 0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * Save all documents to JSON file.
     */
    private void saveToFile() {
        try {
            // Ensure parent directory exists
            Files.createDirectories(storagePath.getParent());

            VectorStoreData data = new VectorStoreData();
            data.setDocuments(new ArrayList<>(documents.values()));
            data.setLastUpdated(LocalDateTime.now());
            data.setTotalDocuments(documents.size());

            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(storagePath.toFile(), data);

            LOGGER.debug("Saved {} documents to {}", documents.size(), storagePath);
        } catch (IOException e) {
            LOGGER.error("Failed to save vector store to file", e);
            throw new RuntimeException("Failed to save vector store", e);
        }
    }

    /**
     * Load documents from JSON file.
     */
    private void loadFromFile() {
        File file = storagePath.toFile();
        if (!file.exists()) {
            LOGGER.info("Vector store file does not exist, starting with empty store: {}", storagePath);
            return;
        }

        try {
            VectorStoreData data = objectMapper.readValue(file, VectorStoreData.class);
            if (data.getDocuments() != null) {
                for (StoredDocument doc : data.getDocuments()) {
                    documents.put(doc.getId(), doc);
                }
            }
            LOGGER.info("Loaded {} documents from vector store file", documents.size());
        } catch (IOException e) {
            LOGGER.error("Failed to load vector store from file", e);
            throw new RuntimeException("Failed to load vector store", e);
        }
    }

    /**
     * Get all stored documents (for management UI).
     */
    public List<StoredDocument> getAllDocuments() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(documents.values());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get document count.
     */
    public int getDocumentCount() {
        return documents.size();
    }

    /**
     * Check if store is empty.
     */
    public boolean isEmpty() {
        return documents.isEmpty();
    }

    /**
     * Clear all documents.
     */
    public void clear() {
        lock.writeLock().lock();
        try {
            documents.clear();
            saveToFile();
            LOGGER.info("Cleared all documents from vector store");
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Delete documents by source file name.
     */
    public int deleteBySource(String source) {
        lock.writeLock().lock();
        try {
            List<String> toRemove = new ArrayList<>();
            for (StoredDocument doc : documents.values()) {
                if (source.equals(doc.getSource()) || source.equals(doc.getFileName())) {
                    toRemove.add(doc.getId());
                }
            }
            for (String id : toRemove) {
                documents.remove(id);
            }
            if (!toRemove.isEmpty()) {
                saveToFile();
            }
            LOGGER.info("Deleted {} documents from source: {}", toRemove.size(), source);
            return toRemove.size();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Get unique source file names.
     */
    public Set<String> getSourceFiles() {
        lock.readLock().lock();
        try {
            Set<String> sources = new HashSet<>();
            for (StoredDocument doc : documents.values()) {
                if (doc.getFileName() != null && !doc.getFileName().isEmpty()) {
                    sources.add(doc.getFileName());
                } else if (doc.getSource() != null && !doc.getSource().isEmpty()) {
                    sources.add(doc.getSource());
                }
            }
            return sources;
        } finally {
            lock.readLock().unlock();
        }
    }

    // Helper classes
    private record ScoredDocument(StoredDocument document, double score) {}

    /**
     * Stored document with embedding and metadata.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StoredDocument {
        private String id;
        private String content;
        private float[] embedding;
        private Map<String, Object> metadata = new HashMap<>();
        private String source;
        private String fileName;
        private int lineNumber;
        private LocalDateTime addedAt;

        // Getters and setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }

        public float[] getEmbedding() { return embedding; }
        public void setEmbedding(float[] embedding) { this.embedding = embedding; }

        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }

        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }

        public int getLineNumber() { return lineNumber; }
        public void setLineNumber(int lineNumber) { this.lineNumber = lineNumber; }

        public LocalDateTime getAddedAt() { return addedAt; }
        public void setAddedAt(LocalDateTime addedAt) { this.addedAt = addedAt; }
    }

    /**
     * Container for vector store data in JSON file.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class VectorStoreData {
        private List<StoredDocument> documents;
        private LocalDateTime lastUpdated;
        private int totalDocuments;

        public List<StoredDocument> getDocuments() { return documents; }
        public void setDocuments(List<StoredDocument> documents) { this.documents = documents; }

        public LocalDateTime getLastUpdated() { return lastUpdated; }
        public void setLastUpdated(LocalDateTime lastUpdated) { this.lastUpdated = lastUpdated; }

        public int getTotalDocuments() { return totalDocuments; }
        public void setTotalDocuments(int totalDocuments) { this.totalDocuments = totalDocuments; }
    }
}
