package org.evan.ai.audit.service.impl;

import org.evan.ai.audit.model.AuditProcessResult;
import org.evan.ai.audit.model.AuditQuestion;
import org.evan.ai.audit.service.AuditAnswerService;
import org.evan.ai.audit.service.AuditProcessService;
import org.evan.ai.audit.service.ExcelDocumentService;
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
 * Supports both Word (.docx) and Excel (.xlsx, .xls) documents.
 */
@Service
public class AuditProcessServiceImpl implements AuditProcessService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuditProcessServiceImpl.class);

    private final WordDocumentService wordDocumentService;
    private final ExcelDocumentService excelDocumentService;
    private final AuditAnswerService auditAnswerService;

    public AuditProcessServiceImpl(WordDocumentService wordDocumentService,
                                    ExcelDocumentService excelDocumentService,
                                    AuditAnswerService auditAnswerService) {
        this.wordDocumentService = wordDocumentService;
        this.excelDocumentService = excelDocumentService;
        this.auditAnswerService = auditAnswerService;
    }

    /**
     * Enum representing supported document types.
     */
    private enum DocumentType {
        WORD, EXCEL, UNKNOWN
    }

    @Override
    public AuditProcessResult processDocument(MultipartFile file, boolean skipExistingAnswers) {
        long startTime = System.currentTimeMillis();
        String originalFileName = file.getOriginalFilename();

        LOGGER.info("Starting to process audit document: {}", originalFileName);

        try {
            // Determine document type and validate
            DocumentType docType = getDocumentType(file);
            if (docType == DocumentType.UNKNOWN) {
                return AuditProcessResult.failure(originalFileName,
                        "Invalid file format. Please upload a .docx, .xlsx, or .xls file.");
            }

            // Step 1: Extract questions from the document
            LOGGER.info("Step 1: Extracting questions from {} document...", docType);
            List<AuditQuestion> questions = extractQuestions(file, docType);

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
            // Determine document type and validate
            DocumentType docType = getDocumentType(file);
            if (docType == DocumentType.UNKNOWN) {
                throw new IllegalArgumentException("Invalid file format. Please upload a .docx, .xlsx, or .xls file.");
            }

            // Read file bytes for reuse
            byte[] fileBytes = file.getBytes();
            String fileName = file.getOriginalFilename();

            // Step 1: Extract questions
            List<AuditQuestion> questions = extractQuestions(new ByteArrayInputStream(fileBytes), fileName, docType);

            if (questions.isEmpty()) {
                throw new IllegalArgumentException(
                        "No audit questions found in the document. Please ensure the document contains questions in a table format or uses [Q]/[A] markers.");
            }

            // Step 2: Generate answers
            List<AuditQuestion> answeredQuestions = auditAnswerService.answerQuestions(questions, skipExistingAnswers);

            // Step 3: Fill answers into document
            ByteArrayOutputStream outputStream = fillAnswers(new ByteArrayInputStream(fileBytes), answeredQuestions, fileName, docType);

            LOGGER.info("Successfully processed {} document with {} questions answered", docType, answeredQuestions.size());
            return outputStream;

        } catch (Exception e) {
            LOGGER.error("Error processing and generating document", e);
            throw new RuntimeException("Failed to process document: " + e.getMessage(), e);
        }
    }

    /**
     * Determines the document type based on file extension and validation.
     */
    private DocumentType getDocumentType(MultipartFile file) {
        if (wordDocumentService.isValidWordDocument(file)) {
            return DocumentType.WORD;
        }
        if (excelDocumentService.isValidExcelDocument(file)) {
            return DocumentType.EXCEL;
        }
        return DocumentType.UNKNOWN;
    }

    /**
     * Extracts questions based on document type.
     */
    private List<AuditQuestion> extractQuestions(MultipartFile file, DocumentType docType) {
        return switch (docType) {
            case WORD -> wordDocumentService.extractQuestions(file);
            case EXCEL -> excelDocumentService.extractQuestions(file);
            default -> throw new IllegalArgumentException("Unsupported document type");
        };
    }

    /**
     * Extracts questions from input stream based on document type.
     */
    private List<AuditQuestion> extractQuestions(ByteArrayInputStream inputStream, String fileName, DocumentType docType) {
        return switch (docType) {
            case WORD -> wordDocumentService.extractQuestions(inputStream, fileName);
            case EXCEL -> excelDocumentService.extractQuestions(inputStream, fileName);
            default -> throw new IllegalArgumentException("Unsupported document type");
        };
    }

    /**
     * Fills answers into document based on document type.
     */
    private ByteArrayOutputStream fillAnswers(ByteArrayInputStream inputStream, List<AuditQuestion> answeredQuestions, String fileName, DocumentType docType) {
        return switch (docType) {
            case WORD -> wordDocumentService.fillAnswers(inputStream, answeredQuestions);
            case EXCEL -> excelDocumentService.fillAnswers(inputStream, answeredQuestions, fileName);
            default -> throw new IllegalArgumentException("Unsupported document type");
        };
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
        return originalFileName + "_answered";
    }
}
