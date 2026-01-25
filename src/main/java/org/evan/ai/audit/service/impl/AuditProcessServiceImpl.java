package org.evan.ai.audit.service.impl;

import org.evan.ai.audit.model.AuditProcessResult;
import org.evan.ai.audit.model.AuditQuestion;
import org.evan.ai.audit.service.AuditAnswerService;
import org.evan.ai.audit.service.AuditProcessService;
import org.evan.ai.audit.service.WordDocumentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * Implementation of AuditProcessService.
 * Orchestrates the complete audit document processing workflow.
 */
@Service
public class AuditProcessServiceImpl implements AuditProcessService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuditProcessServiceImpl.class);

    private final WordDocumentService wordDocumentService;
    private final AuditAnswerService auditAnswerService;

    public AuditProcessServiceImpl(WordDocumentService wordDocumentService,
                                    AuditAnswerService auditAnswerService) {
        this.wordDocumentService = wordDocumentService;
        this.auditAnswerService = auditAnswerService;
    }

    @Override
    public AuditProcessResult processDocument(MultipartFile file, boolean skipExistingAnswers) {
        long startTime = System.currentTimeMillis();
        String originalFileName = file.getOriginalFilename();

        LOGGER.info("Starting to process audit document: {}", originalFileName);

        try {
            // Validate file
            if (!wordDocumentService.isValidWordDocument(file)) {
                return AuditProcessResult.failure(originalFileName,
                        "Invalid file format. Please upload a .docx file.");
            }

            // Step 1: Extract questions from the document
            LOGGER.info("Step 1: Extracting questions from document...");
            List<AuditQuestion> questions = wordDocumentService.extractQuestions(file);

            if (questions.isEmpty()) {
                return AuditProcessResult.failure(originalFileName,
                        "No audit questions found in the document. Please ensure the document contains questions in a table format or uses [Q]/[A] markers.");
            }
            LOGGER.info("Extracted {} questions from document", questions.size());

            // Step 2: Generate answers using RAG
            LOGGER.info("Step 2: Generating answers using AI...");
            List<AuditQuestion> answeredQuestions = auditAnswerService.answerQuestions(questions, skipExistingAnswers);
            LOGGER.info("Generated answers for {} questions", answeredQuestions.size());

            // Calculate processing time
            long processingTime = System.currentTimeMillis() - startTime;

            // Generate output filename
            String processedFileName = generateOutputFileName(originalFileName);

            return AuditProcessResult.success(
                    originalFileName,
                    processedFileName,
                    answeredQuestions,
                    processingTime
            );

        } catch (Exception e) {
            LOGGER.error("Error processing document: {}", originalFileName, e);
            return AuditProcessResult.failure(originalFileName, "Processing failed: " + e.getMessage());
        }
    }

    @Override
    public ByteArrayOutputStream processAndGetDocument(MultipartFile file, boolean skipExistingAnswers) {
        LOGGER.info("Processing and generating output document for: {}", file.getOriginalFilename());

        try {
            // Validate file
            if (!wordDocumentService.isValidWordDocument(file)) {
                throw new IllegalArgumentException("Invalid file format. Please upload a .docx file.");
            }

            // Read file bytes for reuse
            byte[] fileBytes = file.getBytes();

            // Step 1: Extract questions
            List<AuditQuestion> questions = wordDocumentService.extractQuestions(
                    new ByteArrayInputStream(fileBytes), file.getOriginalFilename());

            if (questions.isEmpty()) {
                throw new IllegalArgumentException(
                        "No audit questions found in the document. Please ensure the document contains questions in a table format or uses [Q]/[A] markers.");
            }

            // Step 2: Generate answers
            List<AuditQuestion> answeredQuestions = auditAnswerService.answerQuestions(questions, skipExistingAnswers);

            // Step 3: Fill answers into document
            ByteArrayOutputStream outputStream = wordDocumentService.fillAnswers(
                    new ByteArrayInputStream(fileBytes), answeredQuestions);

            LOGGER.info("Successfully processed document with {} questions answered", answeredQuestions.size());
            return outputStream;

        } catch (Exception e) {
            LOGGER.error("Error processing and generating document", e);
            throw new RuntimeException("Failed to process document: " + e.getMessage(), e);
        }
    }

    /**
     * Generates the output filename by adding "_answered" suffix.
     */
    private String generateOutputFileName(String originalFileName) {
        if (originalFileName == null) {
            return "audit_answered.docx";
        }

        int dotIndex = originalFileName.lastIndexOf('.');
        if (dotIndex > 0) {
            return originalFileName.substring(0, dotIndex) + "_answered" + originalFileName.substring(dotIndex);
        }
        return originalFileName + "_answered.docx";
    }
}
