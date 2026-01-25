package org.evan.ai.audit.agent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared context between agents during document processing.
 * Contains the document data, extracted questions, generated answers, and metadata.
 */
public class AgentContext {

    private final String sessionId;
    private final Map<String, Object> data;
    private final long startTime;

    public AgentContext() {
        this.sessionId = UUID.randomUUID().toString();
        this.data = new ConcurrentHashMap<>();
        this.startTime = System.currentTimeMillis();
    }

    public String getSessionId() {
        return sessionId;
    }

    public long getElapsedTime() {
        return System.currentTimeMillis() - startTime;
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) data.get(key);
    }

    public void set(String key, Object value) {
        data.put(key, value);
    }

    public boolean has(String key) {
        return data.containsKey(key);
    }

    public void remove(String key) {
        data.remove(key);
    }

    // Common context keys
    public static final String KEY_DOCUMENT_BYTES = "documentBytes";
    public static final String KEY_DOCUMENT_TYPE = "documentType";
    public static final String KEY_FILE_NAME = "fileName";
    public static final String KEY_ANALYSIS_RESULT = "analysisResult";
    public static final String KEY_QUESTIONS = "questions";
    public static final String KEY_ANSWERED_QUESTIONS = "answeredQuestions";
    public static final String KEY_OUTPUT_DOCUMENT = "outputDocument";
    public static final String KEY_SKIP_EXISTING = "skipExisting";
    public static final String KEY_ERROR = "error";
}
