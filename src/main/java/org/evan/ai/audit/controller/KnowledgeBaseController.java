package org.evan.ai.audit.controller;

import org.evan.ai.audit.model.AnswerSourceLog;
import org.evan.ai.audit.service.KnowledgeBaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for managing the knowledge base.
 * Provides endpoints for uploading documents, listing documents, and viewing answer logs.
 */
@RestController
@RequestMapping("/api/knowledge-base")
public class KnowledgeBaseController {

    private static final Logger LOGGER = LoggerFactory.getLogger(KnowledgeBaseController.class);

    private final KnowledgeBaseService knowledgeBaseService;

    public KnowledgeBaseController(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    /**
     * Upload a document to the knowledge base.
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadDocument(@RequestParam("file") MultipartFile file) {
        LOGGER.info("Received document upload request: {}", file.getOriginalFilename());

        try {
            int chunkCount = knowledgeBaseService.uploadDocument(file);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("fileName", file.getOriginalFilename());
            result.put("chunkCount", chunkCount);
            result.put("message", "Document processed successfully with " + chunkCount + " chunks");

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            LOGGER.error("Failed to upload document: {}", file.getOriginalFilename(), e);
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * Get list of documents in the knowledge base.
     */
    @GetMapping("/documents")
    public ResponseEntity<Map<String, Object>> getDocuments() {
        List<Map<String, Object>> documents = knowledgeBaseService.getDocumentList();
        int totalChunks = knowledgeBaseService.getTotalChunkCount();

        Map<String, Object> result = new HashMap<>();
        result.put("documents", documents);
        result.put("totalChunks", totalChunks);
        result.put("documentCount", documents.size());

        return ResponseEntity.ok(result);
    }

    /**
     * Delete a document from the knowledge base.
     */
    @DeleteMapping("/documents/{fileName}")
    public ResponseEntity<Map<String, Object>> deleteDocument(@PathVariable String fileName) {
        LOGGER.info("Deleting document: {}", fileName);

        try {
            int deletedCount = knowledgeBaseService.deleteDocument(fileName);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("deletedChunks", deletedCount);
            result.put("message", "Deleted " + deletedCount + " chunks from " + fileName);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            LOGGER.error("Failed to delete document: {}", fileName, e);
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * Clear all documents from the knowledge base.
     */
    @DeleteMapping("/documents")
    public ResponseEntity<Map<String, Object>> clearKnowledgeBase() {
        LOGGER.info("Clearing knowledge base");

        try {
            knowledgeBaseService.clearKnowledgeBase();

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Knowledge base cleared successfully"
            ));
        } catch (Exception e) {
            LOGGER.error("Failed to clear knowledge base", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * Get knowledge base status.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("hasDocuments", knowledgeBaseService.hasDocuments());
        status.put("totalChunks", knowledgeBaseService.getTotalChunkCount());
        status.put("documentCount", knowledgeBaseService.getDocumentList().size());

        return ResponseEntity.ok(status);
    }

    /**
     * Get recent answer logs with source references.
     */
    @GetMapping("/logs")
    public ResponseEntity<Map<String, Object>> getAnswerLogs(
            @RequestParam(defaultValue = "50") int limit) {

        List<AnswerSourceLog> logs = knowledgeBaseService.getRecentAnswerLogs(limit);

        Map<String, Object> result = new HashMap<>();
        result.put("logs", logs);
        result.put("count", logs.size());

        return ResponseEntity.ok(result);
    }

    /**
     * Get answer logs for a specific session.
     */
    @GetMapping("/logs/session/{sessionId}")
    public ResponseEntity<Map<String, Object>> getAnswerLogsBySession(@PathVariable String sessionId) {
        List<AnswerSourceLog> logs = knowledgeBaseService.getAnswerLogsBySession(sessionId);

        Map<String, Object> result = new HashMap<>();
        result.put("logs", logs);
        result.put("count", logs.size());
        result.put("sessionId", sessionId);

        return ResponseEntity.ok(result);
    }

    /**
     * Clear all answer logs.
     */
    @DeleteMapping("/logs")
    public ResponseEntity<Map<String, Object>> clearAnswerLogs() {
        knowledgeBaseService.clearAnswerLogs();

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Answer logs cleared successfully"
        ));
    }
}
