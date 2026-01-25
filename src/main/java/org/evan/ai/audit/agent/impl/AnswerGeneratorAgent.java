package org.evan.ai.audit.agent.impl;

import org.evan.ai.audit.agent.AgentContext;
import org.evan.ai.audit.agent.AgentResult;
import org.evan.ai.audit.agent.AuditAgent;
import org.evan.ai.audit.config.KnowledgeBaseConfig;
import org.evan.ai.audit.model.AuditQuestion;
import org.evan.ai.audit.model.AnswerSourceLog;
import org.evan.ai.audit.service.KnowledgeBaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Agent responsible for generating answers to audit questions using RAG.
 */
@Component
public class AnswerGeneratorAgent implements AuditAgent {

    private static final Logger LOGGER = LoggerFactory.getLogger(AnswerGeneratorAgent.class);

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final KnowledgeBaseConfig.KnowledgeBaseLoader knowledgeBaseLoader;
    private final KnowledgeBaseService knowledgeBaseService;

    private static final String SYSTEM_PROMPT = """
        You are an expert audit assistant. Answer audit-related questions based on the provided context.
        
        FORMATTING RULES:
        1. DO NOT use Markdown formatting (no **, ##, -, *, etc.)
        2. Write as plain text only
        3. Keep answers professional and suitable for audit documentation
        """;

    public AnswerGeneratorAgent(ChatClient.Builder chatClientBuilder,
                                 VectorStore vectorStore,
                                 KnowledgeBaseConfig.KnowledgeBaseLoader knowledgeBaseLoader,
                                 KnowledgeBaseService knowledgeBaseService) {
        this.vectorStore = vectorStore;
        this.knowledgeBaseLoader = knowledgeBaseLoader;
        this.knowledgeBaseService = knowledgeBaseService;
        this.chatClient = chatClientBuilder
                .defaultAdvisors(QuestionAnswerAdvisor.builder(vectorStore).build())
                .defaultSystem(SYSTEM_PROMPT)
                .build();
    }

    @Override
    public String getName() { return "AnswerGeneratorAgent"; }

    @Override
    public String getDescription() { return "Generates answers using RAG"; }

    @Override
    public boolean canHandle(AgentContext context) {
        return context.has(AgentContext.KEY_QUESTIONS);
    }

    @Override
    public AgentResult execute(AgentContext context) {
        LOGGER.info("[{}] Starting answer generation", getName());

        try {
            knowledgeBaseLoader.ensureLoaded();

            List<AuditQuestion> questions = context.get(AgentContext.KEY_QUESTIONS);
            Boolean skipExisting = context.get(AgentContext.KEY_SKIP_EXISTING);
            if (skipExisting == null) skipExisting = false;

            if (questions == null || questions.isEmpty()) {
                return AgentResult.failure("No questions to answer");
            }

            List<AuditQuestion> answeredQuestions = new ArrayList<>();
            int answeredCount = 0, skippedCount = 0;

            for (int i = 0; i < questions.size(); i++) {
                AuditQuestion question = questions.get(i);

                if (skipExisting && question.hasOriginalAnswer()) {
                    answeredQuestions.add(question.withGeneratedAnswer(question.originalAnswer()));
                    skippedCount++;
                    continue;
                }

                try {
                    List<Document> relevantDocs = vectorStore.similaritySearch(
                            SearchRequest.builder().query(question.question()).topK(5).build());

                    String answer = chatClient.prompt().user(question.question()).call().content();
                    String cleanAnswer = cleanMarkdown(answer);

                    answeredQuestions.add(question.withGeneratedAnswer(cleanAnswer));
                    answeredCount++;

                    logAnswerWithSources(question.question(), cleanAnswer, relevantDocs, context.getSessionId());

                } catch (Exception e) {
                    LOGGER.error("Failed to answer question {}", i + 1, e);
                    answeredQuestions.add(question.withGeneratedAnswer("[Error generating answer]"));
                }
            }

            context.set(AgentContext.KEY_ANSWERED_QUESTIONS, answeredQuestions);

            LOGGER.info("[{}] Completed: {} answered, {} skipped", getName(), answeredCount, skippedCount);
            return AgentResult.success(String.format("Generated %d answers, skipped %d", answeredCount, skippedCount),
                    Map.of("answeredCount", answeredCount, "skippedCount", skippedCount));

        } catch (Exception e) {
            LOGGER.error("[{}] Error generating answers", getName(), e);
            return AgentResult.failure("Answer generation failed: " + e.getMessage());
        }
    }

    private String cleanMarkdown(String text) {
        if (text == null) return "";
        return text.replaceAll("\\*\\*([^*]+)\\*\\*", "$1")
                   .replaceAll("\\*([^*]+)\\*", "$1")
                   .replaceAll("#{1,6}\\s*", "")
                   .replaceAll("`([^`]+)`", "$1")
                   .trim();
    }

    private void logAnswerWithSources(String question, String answer, List<Document> docs, String sessionId) {
        try {
            List<AnswerSourceLog.SourceReference> sources = new ArrayList<>();
            for (Document doc : docs) {
                String content = doc.getText();
                String excerpt = content != null ? content.substring(0, Math.min(200, content.length())) : "";

                AnswerSourceLog.SourceReference ref = new AnswerSourceLog.SourceReference(
                        doc.getId(),
                        (String) doc.getMetadata().getOrDefault("fileName", "unknown"),
                        excerpt,
                        0.0
                );
                ref.setSource((String) doc.getMetadata().getOrDefault("source", ""));
                ref.setLineNumber(doc.getMetadata().get("lineNumber") != null
                        ? ((Number) doc.getMetadata().get("lineNumber")).intValue() : 0);
                String addedAt = (String) doc.getMetadata().getOrDefault("addedAt", "");
                if (addedAt != null && !addedAt.isEmpty()) {
                    try {
                        ref.setAddedAt(LocalDateTime.parse(addedAt));
                    } catch (Exception ignored) {}
                }
                sources.add(ref);
            }

            AnswerSourceLog log = new AnswerSourceLog(question, answer);
            log.setSourceReferences(sources);
            log.setSessionId(sessionId);
            knowledgeBaseService.recordAnswerLog(log);
        } catch (Exception e) {
            LOGGER.warn("Failed to log answer sources: {}", e.getMessage());
        }
    }
}
