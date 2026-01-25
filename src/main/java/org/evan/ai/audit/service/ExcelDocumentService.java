package org.evan.ai.audit.service;

import org.evan.ai.audit.model.AuditQuestion;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;

/**
 * Service for parsing and manipulating Excel documents.
 */
public interface ExcelDocumentService {

    /**
     * Parses an uploaded Excel document and extracts audit questions.
     */
    List<AuditQuestion> extractQuestions(MultipartFile file);

    /**
     * Parses an Excel document from an input stream and extracts audit questions.
     */
    List<AuditQuestion> extractQuestions(InputStream inputStream, String fileName);

    /**
     * Fills the answers into the Excel document and returns the result as a byte array.
     */
    ByteArrayOutputStream fillAnswers(MultipartFile file, List<AuditQuestion> answeredQuestions);

    /**
     * Fills the answers into the Excel document from an input stream.
     */
    ByteArrayOutputStream fillAnswers(InputStream inputStream, List<AuditQuestion> answeredQuestions, String fileName);

    /**
     * Checks if the file is a valid Excel document.
     */
    boolean isValidExcelDocument(MultipartFile file);

    /**
     * Gets the supported Excel file extensions.
     */
    String[] getSupportedExtensions();
}
