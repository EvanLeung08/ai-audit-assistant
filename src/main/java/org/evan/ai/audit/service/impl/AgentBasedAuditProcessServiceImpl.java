package org.evan.ai.audit.service.impl;

import org.evan.ai.audit.agent.AgentContext;
import org.evan.ai.audit.agent.impl.OrchestratorAgent;
import org.evan.ai.audit.model.AuditProcessResult;
import org.evan.ai.audit.model.AuditQuestion;
import org.evan.ai.audit.service.AuditProcessService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * Agent-based implementation of AuditProcessService.
 * Uses multi-agent collaboration for document processing.
 *
 * Enable by setting: audit.use-agent-mode=true
 */
@Service
@Primary
@ConditionalOnProperty(name = "audit.use-agent-mode", havingValue = "true", matchIfMissing = false)
public class AgentBasedAuditProcessServiceImpl implements AuditProcessService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgentBasedAuditProcessServiceImpl.class);

    private final OrchestratorAgent orchestratorAgent;

    public AgentBasedAuditProcessServiceImpl(OrchestratorAgent orchestratorAgent) {
        this.orchestratorAgent = orchestratorAgent;
        LOGGER.info("Agent-based AuditProcessService initialized - Multi-Agent mode ENABLED");
    }

    @Override
    public AuditProcessResult processDocument(MultipartFile file, boolean skipExistingAnswers) {
        long startTime = System.currentTimeMillis();
        String originalFileName = file.getOriginalFilename();

        LOGGER.info("[Agent Mode] Starting to process audit document: {}", originalFileName);

        try {
            if (!orchestratorAgent.isValidDocument(file)) {
                return AuditProcessResult.failure(originalFileName,
                        "Invalid file format. Please upload a .docx, .xlsx, or .xls file.");
            }

            AgentContext context = new AgentContext();
            context.set(AgentContext.KEY_DOCUMENT_BYTES, file.getBytes());
            context.set(AgentContext.KEY_FILE_NAME, originalFileName);
            context.set(AgentContext.KEY_DOCUMENT_TYPE, detectDocumentType(file));
            context.set(AgentContext.KEY_SKIP_EXISTING, skipExistingAnswers);

            var result = orchestratorAgent.execute(context);

            if (!result.success()) {
                return AuditProcessResult.failure(originalFileName, result.message());
            }

            List<AuditQuestion> answeredQuestions = context.get(AgentContext.KEY_ANSWERED_QUESTIONS);
            long processingTime = System.currentTimeMillis() - startTime;
            String processedFileName = generateOutputFileName(originalFileName);

            return AuditProcessResult.success(originalFileName, processedFileName,
                    answeredQuestions != null ? answeredQuestions : List.of(), processingTime);

        } catch (Exception e) {
            LOGGER.error("[Agent Mode] Error processing document: {}", originalFileName, e);
            return AuditProcessResult.failure(originalFileName, "Processing failed: " + e.getMessage());
        }
    }

    @Override
    public ByteArrayOutputStream processAndGetDocument(MultipartFile file, boolean skipExistingAnswers) {
        LOGGER.info("[Agent Mode] Processing document for download: {}", file.getOriginalFilename());

        try {
            return orchestratorAgent.processDocument(file, skipExistingAnswers);
        } catch (Exception e) {
            LOGGER.error("[Agent Mode] Error processing document", e);
            throw new RuntimeException("Failed to process document: " + e.getMessage(), e);
        }
    }

    private String detectDocumentType(MultipartFile file) {
        String fileName = file.getOriginalFilename();
        if (fileName == null) return "UNKNOWN";
        String lowerName = fileName.toLowerCase();
        if (lowerName.endsWith(".docx")) return "WORD";
        if (lowerName.endsWith(".xlsx") || lowerName.endsWith(".xls")) return "EXCEL";
        return "UNKNOWN";
    }

    private String generateOutputFileName(String originalFileName) {
        if (originalFileName == null) return "audit_answered.docx";
        int dotIndex = originalFileName.lastIndexOf('.');
        if (dotIndex > 0) {
            return originalFileName.substring(0, dotIndex) + "_answered" + originalFileName.substring(dotIndex);
        }
        return originalFileName + "_answered";
    }
}
