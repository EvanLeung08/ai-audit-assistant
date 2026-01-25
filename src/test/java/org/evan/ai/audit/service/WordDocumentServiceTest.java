package org.evan.ai.audit.service;

import org.apache.poi.xwpf.usermodel.*;
import org.evan.ai.audit.model.AuditQuestion;
import org.evan.ai.audit.service.impl.WordDocumentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for WordDocumentService.
 */
class WordDocumentServiceTest {

    private WordDocumentServiceImpl wordDocumentService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        wordDocumentService = new WordDocumentServiceImpl();
    }

    @Test
    void testIsValidWordDocument_ValidDocx() throws IOException {
        byte[] docxContent = createSimpleDocx();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                docxContent
        );

        assertTrue(wordDocumentService.isValidWordDocument(file));
    }

    @Test
    void testIsValidWordDocument_InvalidFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                "Hello World".getBytes()
        );

        assertFalse(wordDocumentService.isValidWordDocument(file));
    }

    @Test
    void testIsValidWordDocument_EmptyFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                new byte[0]
        );

        assertFalse(wordDocumentService.isValidWordDocument(file));
    }

    @Test
    void testExtractQuestionsFromTable() throws IOException {
        byte[] docxContent = createDocxWithTable();

        List<AuditQuestion> questions = wordDocumentService.extractQuestions(
                new ByteArrayInputStream(docxContent), "test.docx");

        assertNotNull(questions);
        assertEquals(2, questions.size());
        assertEquals("What is the audit policy?", questions.get(0).question());
        assertEquals("How to ensure compliance?", questions.get(1).question());
    }

    /**
     * Creates a simple empty DOCX file.
     */
    private byte[] createSimpleDocx() throws IOException {
        try (XWPFDocument document = new XWPFDocument()) {
            XWPFParagraph para = document.createParagraph();
            XWPFRun run = para.createRun();
            run.setText("Test document");

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.write(out);
            return out.toByteArray();
        }
    }

    /**
     * Creates a DOCX file with a table containing questions.
     */
    private byte[] createDocxWithTable() throws IOException {
        try (XWPFDocument document = new XWPFDocument()) {
            // Create a table with 3 rows (header + 2 questions)
            XWPFTable table = document.createTable(3, 3);

            // Header row
            XWPFTableRow headerRow = table.getRow(0);
            headerRow.getCell(0).setText("No.");
            headerRow.getCell(1).setText("Question");
            headerRow.getCell(2).setText("Answer");

            // Question 1
            XWPFTableRow row1 = table.getRow(1);
            row1.getCell(0).setText("1");
            row1.getCell(1).setText("What is the audit policy?");
            row1.getCell(2).setText("");

            // Question 2
            XWPFTableRow row2 = table.getRow(2);
            row2.getCell(0).setText("2");
            row2.getCell(1).setText("How to ensure compliance?");
            row2.getCell(2).setText("");

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.write(out);
            return out.toByteArray();
        }
    }
}
