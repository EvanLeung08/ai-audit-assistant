package org.evan.ai.audit.model;

import java.util.List;

/**
 * Represents the result of processing an audit document.
 */
public record AuditProcessResult(
    String originalFileName,
    String processedFileName,
    int totalQuestions,
    int answeredQuestions,
    int skippedQuestions,
    List<AuditQuestion> questions,
    long processingTimeMs,
    String status,
    String errorMessage
) {
    /**
     * Creates a successful result.
     */
    public static AuditProcessResult success(
            String originalFileName,
            String processedFileName,
            List<AuditQuestion> questions,
            long processingTimeMs) {

        int answered = (int) questions.stream()
                .filter(q -> q.generatedAnswer() != null && !q.generatedAnswer().isEmpty())
                .count();
        int skipped = questions.size() - answered;

        return new AuditProcessResult(
                originalFileName,
                processedFileName,
                questions.size(),
                answered,
                skipped,
                questions,
                processingTimeMs,
                "SUCCESS",
                null
        );
    }

    /**
     * Creates a failure result.
     */
    public static AuditProcessResult failure(String originalFileName, String errorMessage) {
        return new AuditProcessResult(
                originalFileName,
                null,
                0,
                0,
                0,
                List.of(),
                0,
                "FAILED",
                errorMessage
        );
    }

    /**
     * Checks if the processing was successful.
     */
    public boolean isSuccess() {
        return "SUCCESS".equals(status);
    }
}
