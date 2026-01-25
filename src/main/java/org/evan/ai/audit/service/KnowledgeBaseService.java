package org.evan.ai.audit.service;

import org.evan.ai.audit.model.AnswerSourceLog;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * Service for managing the knowledge base - uploading documents, building embeddings,
 * and tracking answer sources.
 */
public interface KnowledgeBaseService {

    /**
     * Upload and process a document into the knowledge base.
     * The document will be read, split into chunks, and embedded.
     *
     * @param file The document file to upload
     * @return Number of chunks created from the document
     */
    int uploadDocument(MultipartFile file);

    /**
     * Get list of documents in the knowledge base.
     *
     * @return List of document info maps containing fileName, chunkCount, addedAt
     */
    List<Map<String, Object>> getDocumentList();

    /**
     * Delete a document from the knowledge base by file name.
     *
     * @param fileName The name of the file to delete
     * @return Number of chunks deleted
     */
    int deleteDocument(String fileName);

    /**
     * Get the total number of document chunks in the knowledge base.
     */
    int getTotalChunkCount();

    /**
     * Check if the knowledge base has any documents.
     */
    boolean hasDocuments();

    /**
     * Clear all documents from the knowledge base.
     */
    void clearKnowledgeBase();

    /**
     * Record an answer with its source references.
     *
     * @param log The answer source log to record
     */
    void recordAnswerLog(AnswerSourceLog log);

    /**
     * Get recent answer logs for display.
     *
     * @param limit Maximum number of logs to return
     * @return List of recent answer logs
     */
    List<AnswerSourceLog> getRecentAnswerLogs(int limit);

    /**
     * Clear all answer logs.
     */
    void clearAnswerLogs();

    /**
     * Get answer logs for a specific session.
     *
     * @param sessionId The session ID
     * @return List of answer logs for the session
     */
    List<AnswerSourceLog> getAnswerLogsBySession(String sessionId);
}
