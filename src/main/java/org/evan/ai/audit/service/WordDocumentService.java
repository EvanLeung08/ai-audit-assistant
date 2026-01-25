package org.evan.ai.audit.service;

import org.evan.ai.audit.model.AuditQuestion;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;

/**
 * Service for parsing and manipulating Word documents.
 */
public interface WordDocumentService {

    /**
     * Parses an uploaded Word document and extracts audit questions.
     * Supports both table-based formats and text-based formats with markers.
     *
     * @param file The uploaded Word document file
     * @return List of extracted audit questions
     */
    List<AuditQuestion> extractQuestions(MultipartFile file);

    /**
     * Parses a Word document from an input stream and extracts audit questions.
     *
     * @param inputStream The input stream of the Word document
     * @param fileName The name of the file (for logging purposes)
     * @return List of extracted audit questions
     */
    List<AuditQuestion> extractQuestions(InputStream inputStream, String fileName);

    /**
     * Fills the answers into the Word document and returns the result as a byte array.
     *
     * @param file The original Word document file
     * @param answeredQuestions The questions with generated answers
     * @return ByteArrayOutputStream containing the filled document
     */
    ByteArrayOutputStream fillAnswers(MultipartFile file, List<AuditQuestion> answeredQuestions);

    /**
     * Fills the answers into the Word document from an input stream.
     *
     * @param inputStream The input stream of the original Word document
     * @param answeredQuestions The questions with generated answers
     * @return ByteArrayOutputStream containing the filled document
     */
    ByteArrayOutputStream fillAnswers(InputStream inputStream, List<AuditQuestion> answeredQuestions);

    /**
     * Checks if the file is a valid Word document.
     *
     * @param file The file to check
     * @return true if the file is a valid .docx file
     */
    boolean isValidWordDocument(MultipartFile file);
}
