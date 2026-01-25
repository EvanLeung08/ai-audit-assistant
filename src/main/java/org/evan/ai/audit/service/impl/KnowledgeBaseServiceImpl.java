package org.evan.ai.audit.service.impl;

import org.evan.ai.audit.config.AuditProperties;
import org.evan.ai.audit.model.AnswerSourceLog;
import org.evan.ai.audit.service.KnowledgeBaseService;
import org.evan.ai.audit.vectorstore.PersistentVectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.Collectors;

/**
 * Implementation of KnowledgeBaseService for managing documents and answer logs.
 */
@Service
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    private static final Logger LOGGER = LoggerFactory.getLogger(KnowledgeBaseServiceImpl.class);
    private static final int MAX_ANSWER_LOGS = 1000;

    private final VectorStore vectorStore;
    private final AuditProperties auditProperties;
    private final Deque<AnswerSourceLog> answerLogs;

    public KnowledgeBaseServiceImpl(VectorStore vectorStore, AuditProperties auditProperties) {
        this.vectorStore = vectorStore;
        this.auditProperties = auditProperties;
        this.answerLogs = new ConcurrentLinkedDeque<>();
    }

    @Override
    public int uploadDocument(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is required");
        }

        String fileName = file.getOriginalFilename();
        LOGGER.info("Processing uploaded document: {}", fileName);

        try {
            // Save the original file to uploads directory
            Path uploadsDir = Paths.get(auditProperties.getKnowledgeBase().getUploadsDir());
            Files.createDirectories(uploadsDir);

            // Generate unique filename to avoid conflicts
            String uniqueFileName = generateUniqueFileName(uploadsDir, fileName);
            Path filePath = uploadsDir.resolve(uniqueFileName);

            // Copy file to uploads directory
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("Saved original document to: {}", filePath);

            // Use Tika to read the document from saved file (fixes stream reset issue)
            FileSystemResource resource = new FileSystemResource(filePath.toFile());
            TikaDocumentReader reader = new TikaDocumentReader(resource);
            List<Document> documents = reader.read();

            // Split documents into chunks
            TokenTextSplitter splitter = new TokenTextSplitter();
            List<Document> chunks = splitter.split(documents);

            // Add metadata to each chunk
            List<Document> enrichedChunks = new ArrayList<>();
            int lineNumber = 1;
            for (Document chunk : chunks) {
                Map<String, Object> metadata = new HashMap<>(chunk.getMetadata());
                metadata.put("fileName", uniqueFileName);
                metadata.put("originalFileName", fileName);
                metadata.put("source", filePath.toString());
                metadata.put("lineNumber", lineNumber);
                metadata.put("uploadedAt", LocalDateTime.now().toString());

                Document enrichedChunk = Document.builder()
                        .id(UUID.randomUUID().toString())
                        .text(chunk.getText())
                        .metadata(metadata)
                        .build();
                enrichedChunks.add(enrichedChunk);
                lineNumber++;
            }

            // Add to vector store
            vectorStore.add(enrichedChunks);

            LOGGER.info("Successfully processed {} chunks from document: {}", enrichedChunks.size(), uniqueFileName);
            return enrichedChunks.size();

        } catch (IOException e) {
            LOGGER.error("Failed to process document: {}", fileName, e);
            throw new RuntimeException("Failed to process document: " + e.getMessage(), e);
        }
    }

    /**
     * Generate a unique filename to avoid conflicts.
     */
    private String generateUniqueFileName(Path directory, String originalName) {
        Path targetPath = directory.resolve(originalName);
        if (!Files.exists(targetPath)) {
            return originalName;
        }

        // Extract name and extension
        String name = originalName;
        String extension = "";
        int dotIndex = originalName.lastIndexOf('.');
        if (dotIndex > 0) {
            name = originalName.substring(0, dotIndex);
            extension = originalName.substring(dotIndex);
        }

        // Find unique name with counter
        int counter = 1;
        while (Files.exists(targetPath)) {
            String newName = name + "_" + counter + extension;
            targetPath = directory.resolve(newName);
            counter++;
        }

        return targetPath.getFileName().toString();
    }

    @Override
    public List<Map<String, Object>> getDocumentList() {
        if (!(vectorStore instanceof PersistentVectorStore persistentStore)) {
            LOGGER.warn("VectorStore is not PersistentVectorStore, cannot list documents");
            return Collections.emptyList();
        }

        // Group documents by file name
        Map<String, List<PersistentVectorStore.StoredDocument>> byFileName = persistentStore.getAllDocuments()
                .stream()
                .filter(doc -> doc.getFileName() != null && !doc.getFileName().isEmpty())
                .collect(Collectors.groupingBy(PersistentVectorStore.StoredDocument::getFileName));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, List<PersistentVectorStore.StoredDocument>> entry : byFileName.entrySet()) {
            Map<String, Object> docInfo = new HashMap<>();
            docInfo.put("fileName", entry.getKey());
            docInfo.put("chunkCount", entry.getValue().size());

            // Get the earliest addedAt time
            LocalDateTime addedAt = entry.getValue().stream()
                    .map(PersistentVectorStore.StoredDocument::getAddedAt)
                    .filter(Objects::nonNull)
                    .min(LocalDateTime::compareTo)
                    .orElse(null);
            docInfo.put("addedAt", addedAt != null ? addedAt.toString() : null);

            result.add(docInfo);
        }

        // Sort by addedAt descending (newest first)
        result.sort((a, b) -> {
            String aTime = (String) a.get("addedAt");
            String bTime = (String) b.get("addedAt");
            if (aTime == null && bTime == null) return 0;
            if (aTime == null) return 1;
            if (bTime == null) return -1;
            return bTime.compareTo(aTime);
        });

        return result;
    }

    @Override
    public int deleteDocument(String fileName) {
        if (!(vectorStore instanceof PersistentVectorStore persistentStore)) {
            LOGGER.warn("VectorStore is not PersistentVectorStore, cannot delete documents");
            return 0;
        }

        int deletedChunks = persistentStore.deleteBySource(fileName);

        // Also delete the original file from uploads directory
        try {
            Path uploadsDir = Paths.get(auditProperties.getKnowledgeBase().getUploadsDir());
            Path filePath = uploadsDir.resolve(fileName);
            if (Files.exists(filePath)) {
                Files.delete(filePath);
                LOGGER.info("Deleted original file: {}", filePath);
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to delete original file: {}", fileName, e);
        }

        return deletedChunks;
    }

    @Override
    public int getTotalChunkCount() {
        if (vectorStore instanceof PersistentVectorStore persistentStore) {
            return persistentStore.getDocumentCount();
        }
        return 0;
    }

    @Override
    public boolean hasDocuments() {
        if (vectorStore instanceof PersistentVectorStore persistentStore) {
            return !persistentStore.isEmpty();
        }
        return false;
    }

    @Override
    public void clearKnowledgeBase() {
        if (vectorStore instanceof PersistentVectorStore persistentStore) {
            persistentStore.clear();
            LOGGER.info("Knowledge base cleared");
        }

        // Also clear all uploaded files
        try {
            Path uploadsDir = Paths.get(auditProperties.getKnowledgeBase().getUploadsDir());
            if (Files.exists(uploadsDir)) {
                Files.walk(uploadsDir)
                        .filter(Files::isRegularFile)
                        .forEach(file -> {
                            try {
                                Files.delete(file);
                                LOGGER.debug("Deleted file: {}", file);
                            } catch (IOException e) {
                                LOGGER.warn("Failed to delete file: {}", file, e);
                            }
                        });
                LOGGER.info("Cleared all uploaded files from: {}", uploadsDir);
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to clear uploads directory", e);
        }
    }

    @Override
    public void recordAnswerLog(AnswerSourceLog log) {
        if (log.getId() == null) {
            log.setId(UUID.randomUUID().toString());
        }
        if (log.getTimestamp() == null) {
            log.setTimestamp(LocalDateTime.now());
        }

        // Add to front of deque
        answerLogs.addFirst(log);

        // Trim if too many
        while (answerLogs.size() > MAX_ANSWER_LOGS) {
            answerLogs.pollLast();
        }

        LOGGER.debug("Recorded answer log with {} source references", log.getSourceReferences().size());
    }

    @Override
    public List<AnswerSourceLog> getRecentAnswerLogs(int limit) {
        return answerLogs.stream()
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    public void clearAnswerLogs() {
        answerLogs.clear();
        LOGGER.info("Answer logs cleared");
    }

    @Override
    public List<AnswerSourceLog> getAnswerLogsBySession(String sessionId) {
        return answerLogs.stream()
                .filter(log -> sessionId.equals(log.getSessionId()))
                .collect(Collectors.toList());
    }
}
