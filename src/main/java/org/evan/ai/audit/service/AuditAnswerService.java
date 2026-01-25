package org.evan.ai.audit.service;

import org.evan.ai.audit.model.AuditQuestion;

import java.util.List;

/**
 * Service for answering audit questions using RAG (Retrieval-Augmented Generation).
 */
public interface AuditAnswerService {

    /**
     * Generates an answer for a single audit question using the knowledge base.
     *
     * @param question The question to answer
     * @return The generated answer
     */
    String answerQuestion(String question);

    /**
     * Generates answers for a list of audit questions.
     * Returns the same list with generated answers filled in.
     *
     * @param questions List of audit questions to answer
     * @return List of questions with generated answers
     */
    List<AuditQuestion> answerQuestions(List<AuditQuestion> questions);

    /**
     * Generates answers for questions, optionally skipping questions that already have answers.
     *
     * @param questions List of audit questions to answer
     * @param skipExistingAnswers If true, skips questions that already have non-empty answers
     * @return List of questions with generated answers
     */
    List<AuditQuestion> answerQuestions(List<AuditQuestion> questions, boolean skipExistingAnswers);
}
