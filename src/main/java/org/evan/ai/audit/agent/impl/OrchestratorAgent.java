package org.evan.ai.audit.agent.impl;

import org.evan.ai.audit.agent.AgentContext;
import org.evan.ai.audit.agent.AgentResult;
import org.evan.ai.audit.agent.AuditAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.Map;

/**
 * Orchestrator Agent that coordinates the workflow between other agents.
 */
@Component
public class OrchestratorAgent implements AuditAgent {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrchestratorAgent.class);

    private final DocumentAnalyzerAgent documentAnalyzerAgent;
    private final AnswerGeneratorAgent answerGeneratorAgent;
    private final DocumentWriterAgent documentWriterAgent;

    public OrchestratorAgent(DocumentAnalyzerAgent documentAnalyzerAgent,
                              AnswerGeneratorAgent answerGeneratorAgent,
                              DocumentWriterAgent documentWriterAgent) {
        this.documentAnalyzerAgent = documentAnalyzerAgent;
        this.answerGeneratorAgent = answerGeneratorAgent;
        this.documentWriterAgent = documentWriterAgent;
    }

    @Override
    public String getName() { return "OrchestratorAgent"; }

    @Override
    public String getDescription() { return "Coordinates the workflow between all agents"; }

    @Override
    public AgentResult execute(AgentContext context) {
        LOGGER.info("[{}] Starting orchestrated document processing", getName());
        long startTime = System.currentTimeMillis();

        try {
            // Step 1: Document Analysis
            LOGGER.info("[{}] Step 1: Invoking DocumentAnalyzerAgent", getName());
            AgentResult analysisResult = documentAnalyzerAgent.execute(context);
            if (!analysisResult.success()) {
                LOGGER.error("[{}] Document analysis failed: {}", getName(), analysisResult.message());
                return AgentResult.failure("Document analysis failed: " + analysisResult.message());
            }
            LOGGER.info("[{}] Document analysis completed: {}", getName(), analysisResult.message());

            // Step 2: Answer Generation
            LOGGER.info("[{}] Step 2: Invoking AnswerGeneratorAgent", getName());
            AgentResult answerResult = answerGeneratorAgent.execute(context);
            if (!answerResult.success()) {
                LOGGER.error("[{}] Answer generation failed: {}", getName(), answerResult.message());
                return AgentResult.failure("Answer generation failed: " + answerResult.message());
            }
            LOGGER.info("[{}] Answer generation completed: {}", getName(), answerResult.message());

            // Step 3: Document Writing
            LOGGER.info("[{}] Step 3: Invoking DocumentWriterAgent", getName());
            AgentResult writeResult = documentWriterAgent.execute(context);
            if (!writeResult.success()) {
                LOGGER.error("[{}] Document writing failed: {}", getName(), writeResult.message());
                return AgentResult.failure("Document writing failed: " + writeResult.message());
            }
            LOGGER.info("[{}] Document writing completed: {}", getName(), writeResult.message());

            long elapsedTime = System.currentTimeMillis() - startTime;
            LOGGER.info("[{}] Document processing completed in {}ms", getName(), elapsedTime);

            return AgentResult.success(
                    String.format("Document processed successfully in %dms", elapsedTime),
                    Map.of("elapsedTime", elapsedTime,
                           "analysisResult", analysisResult.data(),
                           "answerResult", answerResult.data(),
                           "writeResult", writeResult.data())
            );

        } catch (Exception e) {
            LOGGER.error("[{}] Orchestration failed", getName(), e);
            return AgentResult.failure("Orchestration failed: " + e.getMessage());
        }
    }

    /**
     * Convenience method to process a document file.
     */
    public ByteArrayOutputStream processDocument(MultipartFile file, boolean skipExisting) throws Exception {
        LOGGER.info("[{}] Processing document: {}", getName(), file.getOriginalFilename());

        AgentContext context = new AgentContext();
        context.set(AgentContext.KEY_DOCUMENT_BYTES, file.getBytes());
        context.set(AgentContext.KEY_FILE_NAME, file.getOriginalFilename());
        context.set(AgentContext.KEY_DOCUMENT_TYPE, detectDocumentType(file));
        context.set(AgentContext.KEY_SKIP_EXISTING, skipExisting);

        AgentResult result = execute(context);

        if (!result.success()) {
            throw new RuntimeException(result.message());
        }

        return context.get(AgentContext.KEY_OUTPUT_DOCUMENT);
    }

    private String detectDocumentType(MultipartFile file) {
        String fileName = file.getOriginalFilename();
        if (fileName == null) return "UNKNOWN";
        String lowerName = fileName.toLowerCase();
        if (lowerName.endsWith(".docx")) return "WORD";
        if (lowerName.endsWith(".xlsx") || lowerName.endsWith(".xls")) return "EXCEL";
        return "UNKNOWN";
    }

    public boolean isValidDocument(MultipartFile file) {
        return !"UNKNOWN".equals(detectDocumentType(file));
    }
}
