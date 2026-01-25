package org.evan.ai.audit.service;

import org.evan.ai.audit.model.AuditProcessResult;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;

/**
 * Main service for processing audit documents.
 * Orchestrates the document parsing, question answering, and document filling.
 */
public interface AuditProcessService {

    /**
     * Processes an uploaded audit document:
     * 1. Parses the document to extract questions
     * 2. Generates answers using RAG
     * 3. Fills answers back into the document
     *
     * @param file The uploaded Word document
     * @param skipExistingAnswers If true, skips questions that already have answers
     * @return Processing result with statistics and the filled document
     */
    AuditProcessResult processDocument(MultipartFile file, boolean skipExistingAnswers);

    /**
     * Gets the processed document as a byte stream for download.
     *
     * @param file The uploaded Word document
     * @param skipExistingAnswers If true, skips questions that already have answers
     * @return ByteArrayOutputStream containing the filled document
     */
    ByteArrayOutputStream processAndGetDocument(MultipartFile file, boolean skipExistingAnswers);
}
