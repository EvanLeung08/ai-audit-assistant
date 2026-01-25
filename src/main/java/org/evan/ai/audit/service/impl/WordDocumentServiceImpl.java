package org.evan.ai.audit.service.impl;

import org.apache.poi.xwpf.usermodel.*;
import org.evan.ai.audit.config.AuditProperties;
import org.evan.ai.audit.model.AuditQuestion;
import org.evan.ai.audit.service.WordDocumentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Implementation of WordDocumentService using Apache POI.
 * Supports both table-based and text-based audit question formats.
 */
@Service
public class WordDocumentServiceImpl implements WordDocumentService {

    private static final Logger LOGGER = LoggerFactory.getLogger(WordDocumentServiceImpl.class);

    private static final String DOCX_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String DOCX_EXTENSION = ".docx";

    // Default markers if not configured
    private static final List<String> DEFAULT_QUESTION_MARKERS = Arrays.asList("[Q]", "[Question]", "问题：", "Question:");
    private static final List<String> DEFAULT_ANSWER_MARKERS = Arrays.asList("[A]", "[Answer]", "答案：", "Answer:");

    private final List<String> questionMarkers;
    private final List<String> answerMarkers;

    public WordDocumentServiceImpl(AuditProperties auditProperties) {
        List<String> configuredQuestionMarkers = auditProperties.getDocument().getQuestionMarkers();
        List<String> configuredAnswerMarkers = auditProperties.getDocument().getAnswerMarkers();

        this.questionMarkers = (configuredQuestionMarkers != null && !configuredQuestionMarkers.isEmpty())
                ? configuredQuestionMarkers : DEFAULT_QUESTION_MARKERS;
        this.answerMarkers = (configuredAnswerMarkers != null && !configuredAnswerMarkers.isEmpty())
                ? configuredAnswerMarkers : DEFAULT_ANSWER_MARKERS;
    }

    // No-arg constructor for testing
    public WordDocumentServiceImpl() {
        this.questionMarkers = DEFAULT_QUESTION_MARKERS;
        this.answerMarkers = DEFAULT_ANSWER_MARKERS;
    }

    @Override
    public List<AuditQuestion> extractQuestions(MultipartFile file) {
        try {
            return extractQuestions(file.getInputStream(), file.getOriginalFilename());
        } catch (IOException e) {
            LOGGER.error("Failed to read uploaded file: {}", file.getOriginalFilename(), e);
            throw new RuntimeException("Failed to read uploaded file", e);
        }
    }

    @Override
    public List<AuditQuestion> extractQuestions(InputStream inputStream, String fileName) {
        List<AuditQuestion> questions = new ArrayList<>();

        try (XWPFDocument document = new XWPFDocument(inputStream)) {
            // First, try to extract from tables (most common audit document format)
            questions.addAll(extractQuestionsFromTables(document));

            // If no table-based questions found, try text-based extraction
            if (questions.isEmpty()) {
                questions.addAll(extractQuestionsFromParagraphs(document));
            }

            LOGGER.info("Extracted {} questions from document: {}", questions.size(), fileName);
        } catch (IOException e) {
            LOGGER.error("Failed to parse Word document: {}", fileName, e);
            throw new RuntimeException("Failed to parse Word document", e);
        }

        return questions;
    }

    /**
     * Extracts questions from tables in the document.
     * Assumes table format: Column 1 = Question Number, Column 2 = Question, Column 3 = Answer
     * Or: Column 1 = Question, Column 2 = Answer
     */
    private List<AuditQuestion> extractQuestionsFromTables(XWPFDocument document) {
        List<AuditQuestion> questions = new ArrayList<>();
        int questionIndex = 0;

        for (XWPFTable table : document.getTables()) {
            List<XWPFTableRow> rows = table.getRows();

            // Skip header row (first row)
            for (int rowIdx = 1; rowIdx < rows.size(); rowIdx++) {
                XWPFTableRow row = rows.get(rowIdx);
                List<XWPFTableCell> cells = row.getTableCells();

                if (cells.isEmpty()) continue;

                String questionText = null;
                String answerText = null;
                int answerCellIndex = -1;

                // Determine table structure based on number of columns
                if (cells.size() >= 3) {
                    // Format: No. | Question | Answer
                    questionText = getCellText(cells.get(1));
                    answerText = getCellText(cells.get(2));
                    answerCellIndex = 2;
                } else if (cells.size() == 2) {
                    // Format: Question | Answer
                    questionText = getCellText(cells.get(0));
                    answerText = getCellText(cells.get(1));
                    answerCellIndex = 1;
                } else if (cells.size() == 1) {
                    // Single column - check if it contains a question marker
                    String cellText = getCellText(cells.get(0));
                    if (containsQuestionMarker(cellText)) {
                        questionText = removeMarkers(cellText, questionMarkers);
                    }
                }

                if (questionText != null && !questionText.trim().isEmpty()) {
                    AuditQuestion question = AuditQuestion.withOriginalAnswer(
                            questionIndex++,
                            questionText.trim(),
                            answerText != null ? answerText.trim() : "",
                            rowIdx,
                            answerCellIndex
                    );
                    questions.add(question);
                    LOGGER.debug("Extracted table question {}: {}", questionIndex, questionText.substring(0, Math.min(50, questionText.length())));
                }
            }
        }

        return questions;
    }

    /**
     * Extracts questions from paragraphs using question/answer markers.
     */
    private List<AuditQuestion> extractQuestionsFromParagraphs(XWPFDocument document) {
        List<AuditQuestion> questions = new ArrayList<>();
        List<XWPFParagraph> paragraphs = document.getParagraphs();
        int questionIndex = 0;

        for (int i = 0; i < paragraphs.size(); i++) {
            String text = paragraphs.get(i).getText();

            if (containsQuestionMarker(text)) {
                String questionText = removeMarkers(text, questionMarkers);
                String answerText = "";

                // Look for answer in next paragraph
                if (i + 1 < paragraphs.size()) {
                    String nextText = paragraphs.get(i + 1).getText();
                    if (containsAnswerMarker(nextText)) {
                        answerText = removeMarkers(nextText, answerMarkers);
                    }
                }

                AuditQuestion question = AuditQuestion.withOriginalAnswer(
                        questionIndex++,
                        questionText.trim(),
                        answerText.trim(),
                        i,  // paragraph index as row index
                        -1  // no cell index for paragraphs
                );
                questions.add(question);
                LOGGER.debug("Extracted paragraph question {}: {}", questionIndex, questionText.substring(0, Math.min(50, questionText.length())));
            }
        }

        return questions;
    }

    @Override
    public ByteArrayOutputStream fillAnswers(MultipartFile file, List<AuditQuestion> answeredQuestions) {
        try {
            // Read the file into a byte array first to allow reopening
            byte[] fileBytes = file.getBytes();
            return fillAnswers(new ByteArrayInputStream(fileBytes), answeredQuestions);
        } catch (IOException e) {
            LOGGER.error("Failed to read uploaded file for filling answers", e);
            throw new RuntimeException("Failed to read uploaded file", e);
        }
    }

    @Override
    public ByteArrayOutputStream fillAnswers(InputStream inputStream, List<AuditQuestion> answeredQuestions) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        try (XWPFDocument document = new XWPFDocument(inputStream)) {
            // Create a map for quick lookup by row index
            Map<Integer, AuditQuestion> questionsByRow = answeredQuestions.stream()
                    .collect(Collectors.toMap(AuditQuestion::rowIndex, q -> q, (a, b) -> a));

            // Check if questions were from tables or paragraphs
            boolean hasTableQuestions = answeredQuestions.stream()
                    .anyMatch(q -> q.cellIndex() >= 0);

            if (hasTableQuestions) {
                fillTableAnswers(document, questionsByRow);
            } else {
                fillParagraphAnswers(document, answeredQuestions);
            }

            document.write(outputStream);
            LOGGER.info("Successfully filled {} answers into document", answeredQuestions.size());
        } catch (IOException e) {
            LOGGER.error("Failed to fill answers into Word document", e);
            throw new RuntimeException("Failed to fill answers into Word document", e);
        }

        return outputStream;
    }

    /**
     * Fills answers into table cells.
     */
    private void fillTableAnswers(XWPFDocument document, Map<Integer, AuditQuestion> questionsByRow) {
        for (XWPFTable table : document.getTables()) {
            List<XWPFTableRow> rows = table.getRows();

            for (int rowIdx = 0; rowIdx < rows.size(); rowIdx++) {
                AuditQuestion question = questionsByRow.get(rowIdx);
                if (question != null && question.generatedAnswer() != null && question.cellIndex() >= 0) {
                    XWPFTableRow row = rows.get(rowIdx);
                    List<XWPFTableCell> cells = row.getTableCells();

                    if (question.cellIndex() < cells.size()) {
                        XWPFTableCell answerCell = cells.get(question.cellIndex());

                        // Clear existing content and set new answer
                        clearCellContent(answerCell);

                        XWPFParagraph para = answerCell.getParagraphs().isEmpty()
                                ? answerCell.addParagraph()
                                : answerCell.getParagraphs().get(0);

                        XWPFRun run = para.createRun();
                        run.setText(question.generatedAnswer());
                        run.setColor("0000FF"); // Blue color to distinguish AI answers

                        LOGGER.debug("Filled answer for row {}", rowIdx);
                    }
                }
            }
        }
    }

    /**
     * Fills answers into paragraphs.
     */
    private void fillParagraphAnswers(XWPFDocument document, List<AuditQuestion> answeredQuestions) {
        List<XWPFParagraph> paragraphs = document.getParagraphs();

        for (AuditQuestion question : answeredQuestions) {
            if (question.generatedAnswer() == null) continue;

            int paragraphIndex = question.rowIndex();

            // Find or create the answer paragraph after the question
            int answerParagraphIndex = paragraphIndex + 1;

            if (answerParagraphIndex < paragraphs.size()) {
                XWPFParagraph answerPara = paragraphs.get(answerParagraphIndex);
                String existingText = answerPara.getText();

                // If next paragraph contains an answer marker, update it
                if (containsAnswerMarker(existingText)) {
                    clearParagraphContent(answerPara);
                    XWPFRun run = answerPara.createRun();
                    run.setText("[A] " + question.generatedAnswer());
                    run.setColor("0000FF");
                } else {
                    // Insert a new answer paragraph
                    XWPFParagraph newPara = document.insertNewParagraph(
                            answerPara.getCTP().newCursor());
                    if (newPara != null) {
                        XWPFRun run = newPara.createRun();
                        run.setText("[A] " + question.generatedAnswer());
                        run.setColor("0000FF");
                    }
                }
            }
        }
    }

    @Override
    public boolean isValidWordDocument(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return false;
        }

        String filename = file.getOriginalFilename();
        String contentType = file.getContentType();

        boolean validExtension = filename != null && filename.toLowerCase().endsWith(DOCX_EXTENSION);
        boolean validContentType = DOCX_CONTENT_TYPE.equals(contentType);

        return validExtension || validContentType;
    }

    // Helper methods

    private String getCellText(XWPFTableCell cell) {
        StringBuilder text = new StringBuilder();
        for (XWPFParagraph para : cell.getParagraphs()) {
            text.append(para.getText()).append(" ");
        }
        return text.toString().trim();
    }

    private boolean containsQuestionMarker(String text) {
        if (text == null) return false;
        return questionMarkers.stream().anyMatch(marker ->
                text.toLowerCase().contains(marker.toLowerCase()));
    }

    private boolean containsAnswerMarker(String text) {
        if (text == null) return false;
        return answerMarkers.stream().anyMatch(marker ->
                text.toLowerCase().contains(marker.toLowerCase()));
    }

    private String removeMarkers(String text, List<String> markers) {
        String result = text;
        for (String marker : markers) {
            result = result.replaceAll("(?i)" + java.util.regex.Pattern.quote(marker), "");
        }
        return result.trim();
    }

    private void clearCellContent(XWPFTableCell cell) {
        for (int i = cell.getParagraphs().size() - 1; i >= 0; i--) {
            cell.removeParagraph(i);
        }
    }

    private void clearParagraphContent(XWPFParagraph para) {
        for (int i = para.getRuns().size() - 1; i >= 0; i--) {
            para.removeRun(i);
        }
    }
}
