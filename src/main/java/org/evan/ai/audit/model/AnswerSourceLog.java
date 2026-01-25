package org.evan.ai.audit.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a log entry for an answered question, including source references.
 * Used for tracking which knowledge base documents were used to answer each question.
 */
public class AnswerSourceLog {

    private String id;
    private String question;
    private String answer;
    private List<SourceReference> sourceReferences;
    private LocalDateTime timestamp;
    private String sessionId;

    public AnswerSourceLog() {
        this.sourceReferences = new ArrayList<>();
        this.timestamp = LocalDateTime.now();
    }

    public AnswerSourceLog(String question, String answer) {
        this();
        this.question = question;
        this.answer = answer;
    }

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }

    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }

    public List<SourceReference> getSourceReferences() { return sourceReferences; }
    public void setSourceReferences(List<SourceReference> sourceReferences) { this.sourceReferences = sourceReferences; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public void addSourceReference(SourceReference reference) {
        this.sourceReferences.add(reference);
    }

    /**
     * Represents a reference to a source document used in answering a question.
     */
    public static class SourceReference {
        private String documentId;
        private String fileName;
        private String source;
        private String contentExcerpt;
        private double similarityScore;
        private int lineNumber;
        private LocalDateTime addedAt;

        public SourceReference() {}

        public SourceReference(String documentId, String fileName, String contentExcerpt, double similarityScore) {
            this.documentId = documentId;
            this.fileName = fileName;
            this.contentExcerpt = contentExcerpt;
            this.similarityScore = similarityScore;
        }

        // Getters and setters
        public String getDocumentId() { return documentId; }
        public void setDocumentId(String documentId) { this.documentId = documentId; }

        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }

        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }

        public String getContentExcerpt() { return contentExcerpt; }
        public void setContentExcerpt(String contentExcerpt) { this.contentExcerpt = contentExcerpt; }

        public double getSimilarityScore() { return similarityScore; }
        public void setSimilarityScore(double similarityScore) { this.similarityScore = similarityScore; }

        public int getLineNumber() { return lineNumber; }
        public void setLineNumber(int lineNumber) { this.lineNumber = lineNumber; }

        public LocalDateTime getAddedAt() { return addedAt; }
        public void setAddedAt(LocalDateTime addedAt) { this.addedAt = addedAt; }

        /**
         * Get similarity score as percentage string.
         */
        public String getSimilarityPercentage() {
            return String.format("%.1f%%", similarityScore * 100);
        }
    }
}
