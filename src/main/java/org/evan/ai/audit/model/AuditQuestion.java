package org.evan.ai.audit.model;

/**
 * Represents a question-answer pair extracted from an audit document.
 */
public record AuditQuestion(
    int index,
    String question,
    String originalAnswer,
    String generatedAnswer,
    int rowIndex,      // For table-based documents
    int cellIndex      // For table-based documents
) {
    /**
     * Creates a new AuditQuestion with only question text (answer not yet generated).
     */
    public static AuditQuestion of(int index, String question, int rowIndex, int cellIndex) {
        return new AuditQuestion(index, question, null, null, rowIndex, cellIndex);
    }

    /**
     * Creates a new AuditQuestion with the original answer from the document.
     */
    public static AuditQuestion withOriginalAnswer(int index, String question, String originalAnswer, int rowIndex, int cellIndex) {
        return new AuditQuestion(index, question, originalAnswer, null, rowIndex, cellIndex);
    }

    /**
     * Returns a new AuditQuestion with the generated answer set.
     */
    public AuditQuestion withGeneratedAnswer(String answer) {
        return new AuditQuestion(index, question, originalAnswer, answer, rowIndex, cellIndex);
    }

    /**
     * Checks if this question already has an answer in the original document.
     */
    public boolean hasOriginalAnswer() {
        return originalAnswer != null && !originalAnswer.trim().isEmpty();
    }
}
