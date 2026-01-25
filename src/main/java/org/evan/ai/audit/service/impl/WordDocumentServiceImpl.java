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
 * Enhanced with flexible column detection and smart question recognition.
 */
@Service
public class WordDocumentServiceImpl implements WordDocumentService {

    private static final Logger LOGGER = LoggerFactory.getLogger(WordDocumentServiceImpl.class);

    private static final String DOCX_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String DOCX_EXTENSION = ".docx";

    // Default markers if not configured
    private static final List<String> DEFAULT_QUESTION_MARKERS = Arrays.asList("[Q]", "[Question]", "问题：", "Question:");
    private static final List<String> DEFAULT_ANSWER_MARKERS = Arrays.asList("[A]", "[Answer]", "答案：", "Answer:");

    // Keywords to identify question columns in table headers
    private static final List<String> QUESTION_COLUMN_KEYWORDS = Arrays.asList(
            "question", "问题", "audit question", "审计问题", "inquiry", "query",
            "control", "controls", "requirement", "requirements", "criteria",
            "description", "描述", "item", "项目", "content", "内容", "details"
    );

    // Keywords to identify answer columns in table headers
    private static final List<String> ANSWER_COLUMN_KEYWORDS = Arrays.asList(
            "answer", "答案", "response", "回复", "finding", "findings", "发现",
            "result", "results", "结果", "comment", "comments", "备注", "remark", "remarks",
            "observation", "status", "状态", "evidence", "证据", "note", "notes"
    );

    // Keywords to skip (these are not question/answer columns)
    private static final List<String> SKIP_COLUMN_KEYWORDS = Arrays.asList(
            "no.", "no", "序号", "#", "id", "编号", "ref", "reference"
    );

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
            // Strategy 1: Try to extract from tables (most common audit document format)
            questions.addAll(extractQuestionsFromTables(document));
            LOGGER.info("Table extraction found {} questions", questions.size());

            // Strategy 2: If few or no table-based questions found, also try text-based extraction
            if (questions.size() < 3) {
                List<AuditQuestion> paragraphQuestions = extractQuestionsFromParagraphs(document);
                LOGGER.info("Paragraph extraction found {} additional questions", paragraphQuestions.size());

                // Merge, avoiding duplicates (based on similar question text)
                for (AuditQuestion pq : paragraphQuestions) {
                    boolean isDuplicate = questions.stream()
                            .anyMatch(q -> similarText(q.question(), pq.question()));
                    if (!isDuplicate) {
                        questions.add(pq);
                    }
                }
            }

            // Strategy 3: If still no questions, try to extract any long text as potential questions
            if (questions.isEmpty()) {
                questions.addAll(extractAnyLongTextAsQuestions(document));
                LOGGER.info("Fallback extraction found {} potential questions", questions.size());
            }

            LOGGER.info("Total extracted {} questions from document: {}", questions.size(), fileName);
        } catch (IOException e) {
            LOGGER.error("Failed to parse Word document: {}", fileName, e);
            throw new RuntimeException("Failed to parse Word document", e);
        }

        return questions;
    }

    /**
     * Fallback extraction: treats any reasonably long text as a potential question.
     */
    private List<AuditQuestion> extractAnyLongTextAsQuestions(XWPFDocument document) {
        List<AuditQuestion> questions = new ArrayList<>();
        int questionIndex = 0;

        // First try tables
        for (XWPFTable table : document.getTables()) {
            for (int rowIdx = 0; rowIdx < table.getRows().size(); rowIdx++) {
                XWPFTableRow row = table.getRows().get(rowIdx);
                List<XWPFTableCell> cells = row.getTableCells();

                for (int cellIdx = 0; cellIdx < cells.size(); cellIdx++) {
                    String cellText = getCellText(cells.get(cellIdx));
                    // Consider any cell with substantial text as a potential question
                    if (cellText != null && cellText.trim().length() > 30 && !isHeaderRow(cellText)) {
                        // Find next cell for answer
                        int answerCellIdx = (cellIdx + 1 < cells.size()) ? cellIdx + 1 : -1;
                        String answerText = answerCellIdx >= 0 ? getCellText(cells.get(answerCellIdx)) : "";

                        questions.add(AuditQuestion.withOriginalAnswer(
                                questionIndex++,
                                cellText.trim(),
                                answerText.trim(),
                                rowIdx,
                                answerCellIdx >= 0 ? answerCellIdx : cellIdx
                        ));
                        break; // Only one question per row
                    }
                }
            }
        }

        // Then try paragraphs if still nothing
        if (questions.isEmpty()) {
            List<XWPFParagraph> paragraphs = document.getParagraphs();
            for (int i = 0; i < paragraphs.size(); i++) {
                String text = paragraphs.get(i).getText();
                if (text != null && text.trim().length() > 40) {
                    questions.add(AuditQuestion.withOriginalAnswer(
                            questionIndex++,
                            text.trim(),
                            "",
                            i,
                            -1
                    ));
                }
            }
        }

        return questions;
    }

    /**
     * Checks if two texts are similar (for duplicate detection).
     */
    private boolean similarText(String text1, String text2) {
        if (text1 == null || text2 == null) return false;
        String normalized1 = text1.toLowerCase().replaceAll("\\s+", " ").trim();
        String normalized2 = text2.toLowerCase().replaceAll("\\s+", " ").trim();

        // Check if one contains the other or if they're very similar
        if (normalized1.contains(normalized2) || normalized2.contains(normalized1)) {
            return true;
        }

        // Simple similarity check based on common prefix
        int minLen = Math.min(normalized1.length(), normalized2.length());
        if (minLen < 20) return false;

        int commonPrefix = 0;
        for (int i = 0; i < minLen; i++) {
            if (normalized1.charAt(i) == normalized2.charAt(i)) {
                commonPrefix++;
            } else {
                break;
            }
        }

        return commonPrefix > minLen * 0.7;
    }

    /**
     * Extracts questions from tables in the document.
     * Uses smart column detection to find question and answer columns.
     */
    private List<AuditQuestion> extractQuestionsFromTables(XWPFDocument document) {
        List<AuditQuestion> questions = new ArrayList<>();
        int questionIndex = 0;

        for (XWPFTable table : document.getTables()) {
            List<XWPFTableRow> rows = table.getRows();
            if (rows.isEmpty()) continue;

            // Detect column structure from header row
            ColumnMapping columnMapping = detectColumnMapping(rows.get(0));
            LOGGER.debug("Detected column mapping: questionCol={}, answerCol={}",
                    columnMapping.questionColumn, columnMapping.answerColumn);

            // Determine starting row (skip header if detected)
            int startRow = columnMapping.hasHeader ? 1 : 0;

            for (int rowIdx = startRow; rowIdx < rows.size(); rowIdx++) {
                XWPFTableRow row = rows.get(rowIdx);
                List<XWPFTableCell> cells = row.getTableCells();

                if (cells.isEmpty()) continue;

                String questionText = null;
                String answerText = null;
                int answerCellIndex = -1;

                // Use detected column mapping
                if (columnMapping.questionColumn >= 0 && columnMapping.questionColumn < cells.size()) {
                    questionText = getCellText(cells.get(columnMapping.questionColumn));
                }

                if (columnMapping.answerColumn >= 0 && columnMapping.answerColumn < cells.size()) {
                    answerText = getCellText(cells.get(columnMapping.answerColumn));
                    answerCellIndex = columnMapping.answerColumn;
                }

                // Fallback: if no question found, try to find any cell with question-like content
                if ((questionText == null || questionText.trim().isEmpty()) && cells.size() > 0) {
                    for (int i = 0; i < cells.size(); i++) {
                        String cellText = getCellText(cells.get(i));
                        if (looksLikeQuestion(cellText)) {
                            questionText = cellText;
                            // Assume answer is in next column if exists
                            if (i + 1 < cells.size()) {
                                answerText = getCellText(cells.get(i + 1));
                                answerCellIndex = i + 1;
                            }
                            break;
                        }
                    }
                }

                // Skip empty questions or header-like rows
                if (questionText != null && !questionText.trim().isEmpty() &&
                    !isHeaderRow(questionText) && questionText.length() > 3) {

                    AuditQuestion question = AuditQuestion.withOriginalAnswer(
                            questionIndex++,
                            questionText.trim(),
                            answerText != null ? answerText.trim() : "",
                            rowIdx,
                            answerCellIndex >= 0 ? answerCellIndex : (cells.size() > 1 ? cells.size() - 1 : 0)
                    );
                    questions.add(question);
                    LOGGER.debug("Extracted table question {}: {}", questionIndex,
                            questionText.substring(0, Math.min(50, questionText.length())));
                }
            }
        }

        return questions;
    }

    /**
     * Detects the column mapping from the header row.
     */
    private ColumnMapping detectColumnMapping(XWPFTableRow headerRow) {
        List<XWPFTableCell> cells = headerRow.getTableCells();
        int questionCol = -1;
        int answerCol = -1;
        boolean hasHeader = false;

        for (int i = 0; i < cells.size(); i++) {
            String headerText = getCellText(cells.get(i)).toLowerCase().trim();

            // Check if this looks like a header row
            if (matchesKeywords(headerText, QUESTION_COLUMN_KEYWORDS) ||
                matchesKeywords(headerText, ANSWER_COLUMN_KEYWORDS) ||
                matchesKeywords(headerText, SKIP_COLUMN_KEYWORDS)) {
                hasHeader = true;
            }

            // Find question column
            if (questionCol == -1 && matchesKeywords(headerText, QUESTION_COLUMN_KEYWORDS)) {
                questionCol = i;
            }

            // Find answer column
            if (answerCol == -1 && matchesKeywords(headerText, ANSWER_COLUMN_KEYWORDS)) {
                answerCol = i;
            }
        }

        // If no header detected, use default column positions
        if (!hasHeader || questionCol == -1) {
            if (cells.size() >= 3) {
                // Assume: No. | Question | Answer
                questionCol = 1;
                answerCol = 2;
            } else if (cells.size() == 2) {
                // Assume: Question | Answer
                questionCol = 0;
                answerCol = 1;
            } else {
                questionCol = 0;
                answerCol = -1;
            }
        }

        // If answer column not found but question column found, assume next column
        if (answerCol == -1 && questionCol >= 0 && questionCol + 1 < cells.size()) {
            answerCol = questionCol + 1;
        }

        return new ColumnMapping(questionCol, answerCol, hasHeader);
    }

    /**
     * Checks if text matches any of the keywords.
     */
    private boolean matchesKeywords(String text, List<String> keywords) {
        if (text == null || text.isEmpty()) return false;
        String lowerText = text.toLowerCase();
        return keywords.stream().anyMatch(keyword ->
                lowerText.contains(keyword.toLowerCase()) ||
                lowerText.equals(keyword.toLowerCase()));
    }

    /**
     * Checks if a cell text looks like a question.
     */
    private boolean looksLikeQuestion(String text) {
        if (text == null || text.trim().length() < 10) return false;
        String trimmed = text.trim();

        // Contains question mark
        if (trimmed.contains("?") || trimmed.contains("？")) return true;

        // Contains question markers
        if (containsQuestionMarker(trimmed)) return true;

        // Starts with question words
        String lower = trimmed.toLowerCase();
        if (lower.startsWith("what ") || lower.startsWith("how ") ||
            lower.startsWith("why ") || lower.startsWith("when ") ||
            lower.startsWith("where ") || lower.startsWith("who ") ||
            lower.startsWith("which ") || lower.startsWith("is ") ||
            lower.startsWith("are ") || lower.startsWith("do ") ||
            lower.startsWith("does ") || lower.startsWith("can ") ||
            lower.startsWith("could ") || lower.startsWith("should ") ||
            lower.startsWith("describe ") || lower.startsWith("explain ") ||
            lower.startsWith("provide ") || lower.startsWith("list ")) {
            return true;
        }

        // Long enough text that could be a question/requirement
        return trimmed.length() > 30;
    }

    /**
     * Checks if the text looks like a header row.
     */
    private boolean isHeaderRow(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase().trim();
        return matchesKeywords(lower, QUESTION_COLUMN_KEYWORDS) ||
               matchesKeywords(lower, ANSWER_COLUMN_KEYWORDS) ||
               matchesKeywords(lower, SKIP_COLUMN_KEYWORDS);
    }

    /**
     * Column mapping result.
     */
    private static class ColumnMapping {
        final int questionColumn;
        final int answerColumn;
        final boolean hasHeader;

        ColumnMapping(int questionColumn, int answerColumn, boolean hasHeader) {
            this.questionColumn = questionColumn;
            this.answerColumn = answerColumn;
            this.hasHeader = hasHeader;
        }
    }

    /**
     * Extracts questions from paragraphs using question/answer markers or smart detection.
     */
    private List<AuditQuestion> extractQuestionsFromParagraphs(XWPFDocument document) {
        List<AuditQuestion> questions = new ArrayList<>();
        List<XWPFParagraph> paragraphs = document.getParagraphs();
        int questionIndex = 0;

        for (int i = 0; i < paragraphs.size(); i++) {
            String text = paragraphs.get(i).getText();
            if (text == null || text.trim().isEmpty()) continue;

            boolean isQuestion = false;
            String questionText = text;

            // Check for explicit question markers
            if (containsQuestionMarker(text)) {
                questionText = removeMarkers(text, questionMarkers);
                isQuestion = true;
            }
            // Check if it looks like a question
            else if (looksLikeQuestion(text)) {
                questionText = text;
                isQuestion = true;
            }
            // Check for numbered items that might be questions (e.g., "1. What is...")
            else if (isNumberedQuestion(text)) {
                questionText = removeNumberPrefix(text);
                isQuestion = true;
            }

            if (isQuestion && questionText.trim().length() > 10) {
                String answerText = "";

                // Look for answer in next paragraph
                if (i + 1 < paragraphs.size()) {
                    String nextText = paragraphs.get(i + 1).getText();
                    if (nextText != null && containsAnswerMarker(nextText)) {
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
                LOGGER.debug("Extracted paragraph question {}: {}", questionIndex,
                        questionText.substring(0, Math.min(50, questionText.length())));
            }
        }

        return questions;
    }

    /**
     * Checks if text is a numbered question (e.g., "1. What is...", "1) Describe...")
     */
    private boolean isNumberedQuestion(String text) {
        if (text == null || text.trim().length() < 15) return false;
        String trimmed = text.trim();

        // Match patterns like "1.", "1)", "1:", "a.", "a)", etc.
        if (trimmed.matches("^\\d+[.):].+") || trimmed.matches("^[a-zA-Z][.):].+")) {
            String afterNumber = removeNumberPrefix(trimmed);
            return looksLikeQuestion(afterNumber);
        }
        return false;
    }

    /**
     * Removes number prefix from text (e.g., "1. Question" -> "Question")
     */
    private String removeNumberPrefix(String text) {
        if (text == null) return "";
        return text.replaceFirst("^\\s*[\\da-zA-Z]+[.):]\\s*", "").trim();
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
