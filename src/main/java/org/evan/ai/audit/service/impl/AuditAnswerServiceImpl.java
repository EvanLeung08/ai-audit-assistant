package org.evan.ai.audit.service.impl;

import org.evan.ai.audit.config.KnowledgeBaseConfig;
import org.evan.ai.audit.model.AuditQuestion;
import org.evan.ai.audit.service.AuditAnswerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Implementation of AuditAnswerService using Spring AI with RAG.
 */
@Service
public class AuditAnswerServiceImpl implements AuditAnswerService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuditAnswerServiceImpl.class);

    private final ChatClient chatClient;
    private final KnowledgeBaseConfig.KnowledgeBaseLoader knowledgeBaseLoader;

    private static final String SYSTEM_PROMPT = """
        You are an expert audit assistant. Your role is to answer audit-related questions 
        based on the provided knowledge base context.
        
        IMPORTANT FORMATTING RULES:
        1. DO NOT use any Markdown formatting (no **, ##, -, *, `, etc.)
        2. Write answers as plain text only, like a human would write in a formal document
        3. Use simple punctuation and line breaks for structure
        4. Do not use bullet points with special characters - use numbers (1, 2, 3) or write in paragraphs
        5. Do not use bold, italic, or any special formatting
        
        Content Guidelines:
        1. Provide clear, concise, and accurate answers based on the knowledge base
        2. If the knowledge base contains relevant information, use it to form your answer
        3. If the question is not covered in the knowledge base, provide a general best-practice answer
        4. Keep answers professional and suitable for audit documentation
        5. If you're unsure, acknowledge the limitation and provide the best guidance possible
        
        Answer the following audit question in plain text format:
        """;

    public AuditAnswerServiceImpl(ChatClient.Builder chatClientBuilder, VectorStore vectorStore,
                                   KnowledgeBaseConfig.KnowledgeBaseLoader knowledgeBaseLoader) {
        this.knowledgeBaseLoader = knowledgeBaseLoader;
        this.chatClient = chatClientBuilder
                .defaultAdvisors(
                        QuestionAnswerAdvisor.builder(vectorStore)
                                .build()
                )
                .defaultSystem(SYSTEM_PROMPT)
                .build();
    }

    @Override
    public String answerQuestion(String question) {
        LOGGER.debug("Answering question: {}", question.substring(0, Math.min(100, question.length())));

        // Ensure knowledge base is loaded before answering
        knowledgeBaseLoader.ensureLoaded();

        try {
            String answer = chatClient.prompt()
                    .user(question)
                    .call()
                    .content();

            // Clean any remaining Markdown formatting
            String cleanAnswer = cleanMarkdown(answer);

            LOGGER.debug("Generated answer: {}", cleanAnswer.substring(0, Math.min(100, cleanAnswer.length())));
            return cleanAnswer;
        } catch (Exception e) {
            LOGGER.error("Failed to generate answer for question: {}", question, e);
            return "Unable to generate answer: " + e.getMessage();
        }
    }

    /**
     * Removes Markdown formatting from the text to produce clean plain text.
     */
    private String cleanMarkdown(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        String result = text;

        // Remove headers (### Header -> Header)
        result = Pattern.compile("^#{1,6}\\s*", Pattern.MULTILINE).matcher(result).replaceAll("");

        // Remove bold/italic (**text** or __text__ -> text)
        result = result.replaceAll("\\*\\*(.+?)\\*\\*", "$1");
        result = result.replaceAll("__(.+?)__", "$1");

        // Remove italic (*text* or _text_ -> text) - be careful not to remove underscores in words
        result = result.replaceAll("(?<!\\w)\\*(.+?)\\*(?!\\w)", "$1");
        result = result.replaceAll("(?<!\\w)_(.+?)_(?!\\w)", "$1");

        // Remove inline code (`code` -> code)
        result = result.replaceAll("`(.+?)`", "$1");

        // Remove code blocks (```code``` -> code)
        result = result.replaceAll("```[\\s\\S]*?```", "");

        // Remove links [text](url) -> text
        result = result.replaceAll("\\[(.+?)\\]\\(.+?\\)", "$1");

        // Remove images ![alt](url) -> alt
        result = result.replaceAll("!\\[(.*)\\]\\(.+?\\)", "$1");

        // Convert bullet points to plain text (- item or * item -> item)
        result = Pattern.compile("^\\s*[-*+]\\s+", Pattern.MULTILINE).matcher(result).replaceAll("");

        // Convert numbered lists with special formatting (1. item -> 1. item) - keep as is
        // But remove any leading whitespace normalization

        // Remove horizontal rules (---, ***, ___)
        result = Pattern.compile("^[-*_]{3,}\\s*$", Pattern.MULTILINE).matcher(result).replaceAll("");

        // Remove blockquotes (> text -> text)
        result = Pattern.compile("^>\\s*", Pattern.MULTILINE).matcher(result).replaceAll("");

        // Remove strikethrough (~~text~~ -> text)
        result = result.replaceAll("~~(.+?)~~", "$1");

        // Clean up multiple blank lines
        result = result.replaceAll("\n{3,}", "\n\n");

        // Trim whitespace
        result = result.trim();

        return result;
    }

    @Override
    public List<AuditQuestion> answerQuestions(List<AuditQuestion> questions) {
        return answerQuestions(questions, false);
    }

    @Override
    public List<AuditQuestion> answerQuestions(List<AuditQuestion> questions, boolean skipExistingAnswers) {
        List<AuditQuestion> answeredQuestions = new ArrayList<>();
        int totalQuestions = questions.size();
        int currentQuestion = 0;

        for (AuditQuestion question : questions) {
            currentQuestion++;
            LOGGER.info("Processing question {}/{}: {}",
                    currentQuestion,
                    totalQuestions,
                    question.question().substring(0, Math.min(50, question.question().length())));

            // Skip questions that already have answers if requested
            if (skipExistingAnswers && question.hasOriginalAnswer()) {
                LOGGER.debug("Skipping question {} - already has answer", question.index());
                answeredQuestions.add(question);
                continue;
            }

            try {
                String answer = answerQuestion(question.question());
                answeredQuestions.add(question.withGeneratedAnswer(answer));
            } catch (Exception e) {
                LOGGER.error("Failed to answer question {}: {}", question.index(), e.getMessage());
                answeredQuestions.add(question.withGeneratedAnswer("Error generating answer: " + e.getMessage()));
            }
        }

        LOGGER.info("Completed answering {} questions", answeredQuestions.size());
        return answeredQuestions;
    }
}
