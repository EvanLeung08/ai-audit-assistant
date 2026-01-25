package org.evan.ai.audit.agent.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.evan.ai.audit.agent.AgentContext;
import org.evan.ai.audit.agent.AgentResult;
import org.evan.ai.audit.agent.AuditAgent;
import org.evan.ai.audit.model.AuditQuestion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.*;

import org.apache.poi.xwpf.usermodel.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;

/**
 * Agent responsible for analyzing document structure and extracting questions.
 * Uses AI to understand document layout and identify actual audit questions,
 * filtering out descriptions, headers, and other non-question content.
 */
@Component
public class DocumentAnalyzerAgent implements AuditAgent {

    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentAnalyzerAgent.class);

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    private static final String QUESTION_ANALYSIS_PROMPT = """
        You are an expert at analyzing audit documents. Analyze the following text content and determine:
        1. Is this text an actual audit question that requires an answer?
        2. Or is it just a description, header, label, instruction, or other non-question content?
        
        An audit question typically:
        - Asks about policies, procedures, controls, or compliance
        - Requires a substantive answer explaining how something is done
        - Often starts with words like "How", "What", "Describe", "Explain", "Does", "Is there"
        - Asks for evidence, documentation, or implementation details
        
        NOT an audit question if it's:
        - A section header or title (e.g., "Access Control", "Security Policies")
        - A simple label or category (e.g., "Control ID", "Risk Level")
        - Instructions for the auditor (e.g., "Please attach evidence")
        - Empty or contains only numbers/codes
        - Very short text (less than 10 characters) without a question mark
        
        Text to analyze:
        "%s"
        
        Respond with ONLY a JSON object:
        {"isQuestion": true/false, "confidence": 0.0-1.0, "reason": "brief explanation"}
        """;

    private static final String BATCH_ANALYSIS_PROMPT = """
        You are an expert at analyzing audit documents. I will provide you with a list of text items extracted from a document table.
        For each item, determine if it is an actual audit question that needs an answer, or just a description/header/label.
        
        An audit question typically:
        - Asks about policies, procedures, controls, or compliance
        - Requires a substantive answer
        - Often contains words like "How", "What", "Describe", "Explain", "Does", "Is there", "Are there"
        
        NOT a question if it's:
        - A section header, title, or category label
        - Very short text without substance
        - Instructions for the auditor
        - Just a number, code, or reference ID
        
        Items to analyze (format: index|text):
        %s
        
        Respond with ONLY a JSON array of objects:
        [{"index": 0, "isQuestion": true/false, "confidence": 0.8}, ...]
        
        Include ALL items in your response, in the same order.
        """;

    public DocumentAnalyzerAgent(ChatClient.Builder chatClientBuilder, ObjectMapper objectMapper) {
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    @Override
    public String getName() {
        return "DocumentAnalyzerAgent";
    }

    @Override
    public String getDescription() {
        return "Analyzes document structure and uses AI to identify actual audit questions";
    }

    @Override
    public AgentResult execute(AgentContext context) {
        LOGGER.info("[{}] Starting AI-powered document analysis", getName());

        try {
            byte[] documentBytes = context.get(AgentContext.KEY_DOCUMENT_BYTES);
            String fileName = context.get(AgentContext.KEY_FILE_NAME);
            String documentType = context.get(AgentContext.KEY_DOCUMENT_TYPE);

            if (documentBytes == null) {
                return AgentResult.failure("No document data in context");
            }

            // Step 1: Extract all potential questions from document
            List<CandidateQuestion> candidates;
            if ("WORD".equals(documentType)) {
                candidates = extractCandidatesFromWord(documentBytes);
            } else if ("EXCEL".equals(documentType)) {
                candidates = extractCandidatesFromExcel(documentBytes, fileName);
            } else {
                return AgentResult.failure("Unsupported document type: " + documentType);
            }

            if (candidates.isEmpty()) {
                return AgentResult.failure("No potential questions found in the document");
            }

            LOGGER.info("[{}] Found {} candidate items, using AI to filter actual questions", getName(), candidates.size());

            // Step 2: Use AI to analyze and filter actual questions
            List<AuditQuestion> questions = analyzeWithAI(candidates);

            if (questions.isEmpty()) {
                return AgentResult.failure("AI analysis found no actual audit questions in the document. " +
                        "Please ensure the document contains questions in a table format.");
            }

            context.set(AgentContext.KEY_QUESTIONS, questions);
            LOGGER.info("[{}] AI identified {} actual audit questions from {} candidates",
                    getName(), questions.size(), candidates.size());

            return AgentResult.success(
                    String.format("Identified %d audit questions from %d candidates", questions.size(), candidates.size()),
                    Map.of("questionCount", questions.size(), "candidateCount", candidates.size())
            );
        } catch (Exception e) {
            LOGGER.error("[{}] Error analyzing document", getName(), e);
            return AgentResult.failure("Document analysis failed: " + e.getMessage());
        }
    }

    /**
     * Use AI to analyze candidates and identify actual questions.
     */
    private List<AuditQuestion> analyzeWithAI(List<CandidateQuestion> candidates) {
        List<AuditQuestion> questions = new ArrayList<>();

        // For efficiency, analyze in batches
        int batchSize = 20;
        for (int i = 0; i < candidates.size(); i += batchSize) {
            int end = Math.min(i + batchSize, candidates.size());
            List<CandidateQuestion> batch = candidates.subList(i, end);

            try {
                List<AuditQuestion> batchQuestions = analyzeBatchWithAI(batch);
                questions.addAll(batchQuestions);
            } catch (Exception e) {
                LOGGER.warn("[{}] Batch AI analysis failed, falling back to rule-based for this batch: {}",
                        getName(), e.getMessage());
                // Fallback: use rule-based filtering for this batch
                for (CandidateQuestion candidate : batch) {
                    if (isLikelyQuestion(candidate.text)) {
                        questions.add(new AuditQuestion(
                                questions.size(),
                                candidate.text,
                                candidate.existingAnswer,
                                null,
                                candidate.rowIndex,
                                candidate.answerColumnIndex
                        ));
                    }
                }
            }
        }

        return questions;
    }

    /**
     * Analyze a batch of candidates using AI.
     */
    private List<AuditQuestion> analyzeBatchWithAI(List<CandidateQuestion> batch) {
        List<AuditQuestion> questions = new ArrayList<>();

        // Build the prompt with all items
        StringBuilder itemsBuilder = new StringBuilder();
        for (int i = 0; i < batch.size(); i++) {
            CandidateQuestion candidate = batch.get(i);
            // Truncate long text for the prompt
            String text = candidate.text.length() > 300 ?
                    candidate.text.substring(0, 300) + "..." : candidate.text;
            itemsBuilder.append(i).append("|").append(text.replace("\n", " ")).append("\n");
        }

        String prompt = String.format(BATCH_ANALYSIS_PROMPT, itemsBuilder.toString());

        try {
            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            LOGGER.debug("[{}] AI batch analysis response: {}", getName(), response);

            // Parse the JSON response
            List<Map<String, Object>> analysisResults = parseAIResponse(response);

            for (Map<String, Object> result : analysisResults) {
                int index = ((Number) result.get("index")).intValue();
                boolean isQuestion = (Boolean) result.get("isQuestion");
                double confidence = result.get("confidence") != null ?
                        ((Number) result.get("confidence")).doubleValue() : 0.5;

                if (index >= 0 && index < batch.size() && isQuestion && confidence >= 0.6) {
                    CandidateQuestion candidate = batch.get(index);
                    questions.add(new AuditQuestion(
                            questions.size(),
                            candidate.text,
                            candidate.existingAnswer,
                            null,
                            candidate.rowIndex,
                            candidate.answerColumnIndex
                    ));
                }
            }
        } catch (Exception e) {
            LOGGER.warn("[{}] Failed to parse AI response: {}", getName(), e.getMessage());
            throw e;
        }

        return questions;
    }

    /**
     * Parse AI response JSON.
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseAIResponse(String response) {
        try {
            // Clean up response - remove markdown code blocks if present
            String cleaned = response.trim();
            if (cleaned.startsWith("```json")) {
                cleaned = cleaned.substring(7);
            } else if (cleaned.startsWith("```")) {
                cleaned = cleaned.substring(3);
            }
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.substring(0, cleaned.length() - 3);
            }
            cleaned = cleaned.trim();

            return objectMapper.readValue(cleaned, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            LOGGER.error("[{}] Failed to parse AI response as JSON: {}", getName(), response);
            throw new RuntimeException("Failed to parse AI response", e);
        }
    }

    /**
     * Rule-based fallback to check if text is likely a question.
     */
    private boolean isLikelyQuestion(String text) {
        if (text == null || text.trim().length() < 15) {
            return false;
        }

        String lower = text.toLowerCase().trim();

        // Check for question indicators
        boolean hasQuestionWords = lower.startsWith("how ") || lower.startsWith("what ") ||
                lower.startsWith("describe ") || lower.startsWith("explain ") ||
                lower.startsWith("does ") || lower.startsWith("do ") ||
                lower.startsWith("is there ") || lower.startsWith("are there ") ||
                lower.startsWith("please describe") || lower.startsWith("please explain") ||
                lower.contains("?") || lower.startsWith("provide ") ||
                lower.startsWith("list ") || lower.startsWith("identify ");

        // Check for audit-related keywords
        boolean hasAuditKeywords = lower.contains("control") || lower.contains("policy") ||
                lower.contains("procedure") || lower.contains("process") ||
                lower.contains("implement") || lower.contains("document") ||
                lower.contains("evidence") || lower.contains("compliance") ||
                lower.contains("security") || lower.contains("access") ||
                lower.contains("risk") || lower.contains("audit");

        // Exclude obvious non-questions
        boolean isLikelyHeader = lower.length() < 30 && !lower.contains("?") &&
                !hasQuestionWords && Character.isUpperCase(text.trim().charAt(0));

        return (hasQuestionWords || (hasAuditKeywords && text.length() > 50)) && !isLikelyHeader;
    }

    // ==================== Document Extraction Methods ====================

    private List<CandidateQuestion> extractCandidatesFromWord(byte[] documentBytes) {
        List<CandidateQuestion> candidates = new ArrayList<>();

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(documentBytes))) {

            // Strategy 1: Extract from tables
            List<CandidateQuestion> tableCandidates = extractFromTables(document);
            candidates.addAll(tableCandidates);

            // Strategy 2: If no table content found, extract from paragraphs
            if (candidates.isEmpty()) {
                LOGGER.info("No table content found, trying paragraph extraction");
                List<CandidateQuestion> paragraphCandidates = extractFromParagraphs(document);
                candidates.addAll(paragraphCandidates);
            }

            LOGGER.info("Extracted {} candidates from Word document", candidates.size());

        } catch (Exception e) {
            LOGGER.error("Error extracting candidates from Word document", e);
        }

        return candidates;
    }

    /**
     * Extract candidates from all tables in the document.
     */
    private List<CandidateQuestion> extractFromTables(XWPFDocument document) {
        List<CandidateQuestion> candidates = new ArrayList<>();
        int globalRowIndex = 0;

        for (XWPFTable table : document.getTables()) {
            List<XWPFTableRow> rows = table.getRows();
            if (rows.isEmpty()) continue;

            // Try to detect question and answer columns
            int[] columns = detectColumns(rows);
            int questionCol = columns[0];
            int answerCol = columns[1];

            LOGGER.debug("Table detected with questionCol={}, answerCol={}", questionCol, answerCol);

            // Process each row (including first row if it might contain questions)
            for (int rowIdx = 0; rowIdx < rows.size(); rowIdx++) {
                XWPFTableRow row = rows.get(rowIdx);
                List<XWPFTableCell> cells = row.getTableCells();

                if (cells.isEmpty()) continue;

                // Strategy A: Use detected columns
                if (questionCol < cells.size()) {
                    String questionText = getCellText(cells.get(questionCol));
                    String answerText = answerCol < cells.size() ? getCellText(cells.get(answerCol)) : "";

                    if (isValidCandidate(questionText)) {
                        candidates.add(new CandidateQuestion(
                                questionText.trim(),
                                answerText.isEmpty() ? null : answerText.trim(),
                                globalRowIndex + rowIdx,
                                answerCol
                        ));
                    }
                }

                // Strategy B: If only one cell with substantial text, treat as question
                if (cells.size() == 1) {
                    String cellText = getCellText(cells.get(0));
                    if (isValidCandidate(cellText) && cellText.length() > 30) {
                        // Check if not already added
                        boolean alreadyAdded = candidates.stream()
                                .anyMatch(c -> c.text.equals(cellText.trim()));
                        if (!alreadyAdded) {
                            candidates.add(new CandidateQuestion(
                                    cellText.trim(),
                                    null,
                                    globalRowIndex + rowIdx,
                                    0
                            ));
                        }
                    }
                }

                // Strategy C: Check all cells for question-like content
                for (int cellIdx = 0; cellIdx < cells.size(); cellIdx++) {
                    String cellText = getCellText(cells.get(cellIdx));
                    if (isValidCandidate(cellText) && looksLikeQuestion(cellText)) {
                        String answerText = (cellIdx + 1 < cells.size()) ?
                                getCellText(cells.get(cellIdx + 1)) : "";

                        // Check if not already added
                        boolean alreadyAdded = candidates.stream()
                                .anyMatch(c -> c.text.equals(cellText.trim()));
                        if (!alreadyAdded) {
                            candidates.add(new CandidateQuestion(
                                    cellText.trim(),
                                    answerText.isEmpty() ? null : answerText.trim(),
                                    globalRowIndex + rowIdx,
                                    cellIdx + 1
                            ));
                        }
                    }
                }
            }

            globalRowIndex += rows.size();
        }

        return candidates;
    }

    /**
     * Extract candidates from paragraphs (for non-table documents).
     */
    private List<CandidateQuestion> extractFromParagraphs(XWPFDocument document) {
        List<CandidateQuestion> candidates = new ArrayList<>();
        List<XWPFParagraph> paragraphs = document.getParagraphs();

        for (int i = 0; i < paragraphs.size(); i++) {
            String text = paragraphs.get(i).getText();
            if (text == null || text.trim().isEmpty()) continue;

            text = text.trim();

            // Check for Q/A markers
            if (text.startsWith("[Q]") || text.startsWith("Q:") ||
                text.startsWith("Question:") || text.startsWith("问题：")) {

                String questionText = text.replaceFirst("^\\[Q\\]|^Q:|^Question:|^问题：", "").trim();
                String answerText = null;

                // Look for answer in next paragraph
                if (i + 1 < paragraphs.size()) {
                    String nextText = paragraphs.get(i + 1).getText();
                    if (nextText != null && (nextText.startsWith("[A]") ||
                        nextText.startsWith("A:") || nextText.startsWith("Answer:") ||
                        nextText.startsWith("答案："))) {
                        answerText = nextText.replaceFirst("^\\[A\\]|^A:|^Answer:|^答案：", "").trim();
                    }
                }

                if (isValidCandidate(questionText)) {
                    candidates.add(new CandidateQuestion(questionText, answerText, i, 0));
                }
            }
            // Check for numbered questions (e.g., "1. How do you...", "1) What is...")
            else if (text.matches("^\\d+[.)]\\s+.*") && text.length() > 20) {
                if (isValidCandidate(text)) {
                    candidates.add(new CandidateQuestion(text, null, i, 0));
                }
            }
            // Check for question-like paragraphs
            else if (looksLikeQuestion(text) && text.length() > 30) {
                candidates.add(new CandidateQuestion(text, null, i, 0));
            }
        }

        return candidates;
    }

    /**
     * Detect question and answer columns in a table.
     */
    private int[] detectColumns(List<XWPFTableRow> rows) {
        int questionCol = 0;
        int answerCol = 1;

        // Check first few rows for header-like content
        for (int rowIdx = 0; rowIdx < Math.min(3, rows.size()); rowIdx++) {
            List<XWPFTableCell> cells = rows.get(rowIdx).getTableCells();

            for (int i = 0; i < cells.size(); i++) {
                String text = getCellText(cells.get(i)).toLowerCase();

                // Question column indicators
                if (text.contains("question") || text.contains("control") ||
                    text.contains("requirement") || text.contains("description") ||
                    text.contains("inquiry") || text.contains("item") ||
                    text.contains("请求") || text.contains("问题") || text.contains("要求")) {
                    questionCol = i;
                }
                // Answer column indicators
                else if (text.contains("answer") || text.contains("response") ||
                         text.contains("evidence") || text.contains("comment") ||
                         text.contains("reply") || text.contains("status") ||
                         text.contains("回答") || text.contains("答案") || text.contains("证据")) {
                    answerCol = i;
                }
            }
        }

        // Ensure answer column is after question column
        if (answerCol <= questionCol && rows.size() > 0 &&
            rows.get(0).getTableCells().size() > questionCol + 1) {
            answerCol = questionCol + 1;
        }

        return new int[]{questionCol, answerCol};
    }

    /**
     * Check if text looks like a question.
     */
    private boolean looksLikeQuestion(String text) {
        if (text == null || text.length() < 15) return false;

        String lower = text.toLowerCase().trim();

        // Contains question mark
        if (lower.contains("?")) return true;

        // Starts with question words
        if (lower.startsWith("how ") || lower.startsWith("what ") ||
            lower.startsWith("describe ") || lower.startsWith("explain ") ||
            lower.startsWith("does ") || lower.startsWith("do ") ||
            lower.startsWith("is there ") || lower.startsWith("are there ") ||
            lower.startsWith("please ") || lower.startsWith("provide ") ||
            lower.startsWith("list ") || lower.startsWith("identify ") ||
            lower.startsWith("verify ") || lower.startsWith("confirm ")) {
            return true;
        }

        return false;
    }

    /**
     * Check if text is a valid candidate (not too short, not just whitespace).
     */
    private boolean isValidCandidate(String text) {
        return text != null && text.trim().length() > 5;
    }

    private List<CandidateQuestion> extractCandidatesFromExcel(byte[] documentBytes, String fileName) {
        List<CandidateQuestion> candidates = new ArrayList<>();

        try (Workbook workbook = createWorkbook(documentBytes, fileName)) {
            Sheet sheet = workbook.getSheetAt(0);

            int questionCol = 0, answerCol = 1;

            Row headerRow = sheet.getRow(0);
            if (headerRow != null) {
                for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                    Cell cell = headerRow.getCell(i);
                    if (cell != null) {
                        String value = getCellValue(cell).toLowerCase();
                        if (value.contains("question") || value.contains("control") ||
                            value.contains("requirement") || value.contains("description")) {
                            questionCol = i;
                        } else if (value.contains("answer") || value.contains("response") ||
                                   value.contains("evidence")) {
                            answerCol = i;
                        }
                    }
                }
            }

            for (int rowIdx = 1; rowIdx <= sheet.getLastRowNum(); rowIdx++) {
                Row row = sheet.getRow(rowIdx);
                if (row == null) continue;

                String questionText = getCellValue(row.getCell(questionCol));
                String answerText = getCellValue(row.getCell(answerCol));

                if (questionText != null && questionText.trim().length() > 5) {
                    candidates.add(new CandidateQuestion(
                            questionText.trim(),
                            answerText.isEmpty() ? null : answerText.trim(),
                            rowIdx,
                            answerCol
                    ));
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error extracting candidates from Excel document", e);
        }

        return candidates;
    }

    // ==================== Helper Classes and Methods ====================

    /**
     * Represents a candidate question before AI filtering.
     */
    private static class CandidateQuestion {
        final String text;
        final String existingAnswer;
        final int rowIndex;
        final int answerColumnIndex;

        CandidateQuestion(String text, String existingAnswer, int rowIndex, int answerColumnIndex) {
            this.text = text;
            this.existingAnswer = existingAnswer;
            this.rowIndex = rowIndex;
            this.answerColumnIndex = answerColumnIndex;
        }
    }

    private String getCellText(XWPFTableCell cell) {
        if (cell == null) return "";
        StringBuilder sb = new StringBuilder();
        for (XWPFParagraph p : cell.getParagraphs()) {
            sb.append(p.getText()).append(" ");
        }
        // Also check for nested tables
        for (XWPFTable nestedTable : cell.getTables()) {
            for (XWPFTableRow row : nestedTable.getRows()) {
                for (XWPFTableCell nestedCell : row.getTableCells()) {
                    for (XWPFParagraph p : nestedCell.getParagraphs()) {
                        sb.append(p.getText()).append(" ");
                    }
                }
            }
        }
        return sb.toString().trim();
    }

    private Workbook createWorkbook(byte[] data, String fileName) throws Exception {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        if (fileName != null && fileName.toLowerCase().endsWith(".xls")) {
            return new HSSFWorkbook(bais);
        }
        return new XSSFWorkbook(bais);
    }

    private String getCellValue(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf(cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }
}
