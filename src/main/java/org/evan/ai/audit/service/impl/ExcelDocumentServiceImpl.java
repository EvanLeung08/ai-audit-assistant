package org.evan.ai.audit.service.impl;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.evan.ai.audit.model.AuditQuestion;
import org.evan.ai.audit.service.ExcelDocumentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Implementation of ExcelDocumentService using Apache POI.
 * Supports both .xlsx (XSSF) and .xls (HSSF) formats.
 */
@Service
public class ExcelDocumentServiceImpl implements ExcelDocumentService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExcelDocumentServiceImpl.class);
    private static final String[] SUPPORTED_EXTENSIONS = {".xlsx", ".xls"};

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

    @Override
    public List<AuditQuestion> extractQuestions(MultipartFile file) {
        try {
            return extractQuestions(file.getInputStream(), file.getOriginalFilename());
        } catch (IOException e) {
            LOGGER.error("Failed to read Excel file: {}", file.getOriginalFilename(), e);
            throw new RuntimeException("Failed to read Excel file", e);
        }
    }

    @Override
    public List<AuditQuestion> extractQuestions(InputStream inputStream, String fileName) {
        List<AuditQuestion> questions = new ArrayList<>();

        try (Workbook workbook = createWorkbook(inputStream, fileName)) {
            Sheet sheet = workbook.getSheetAt(0);

            // Find question and answer columns from header row using flexible detection
            int questionCol = -1;
            int answerCol = -1;
            boolean hasHeader = false;
            Row headerRow = sheet.getRow(0);

            if (headerRow != null) {
                for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                    Cell cell = headerRow.getCell(i);
                    if (cell != null) {
                        String value = getCellStringValue(cell).toLowerCase().trim();

                        // Check for question column keywords
                        if (questionCol == -1 && matchesKeywords(value, QUESTION_COLUMN_KEYWORDS)) {
                            questionCol = i;
                            hasHeader = true;
                        }
                        // Check for answer column keywords
                        if (answerCol == -1 && matchesKeywords(value, ANSWER_COLUMN_KEYWORDS)) {
                            answerCol = i;
                            hasHeader = true;
                        }
                    }
                }
            }

            // If no header detected, try to find the best columns
            if (questionCol == -1) {
                questionCol = findBestQuestionColumn(sheet);
            }
            if (answerCol == -1 && questionCol >= 0) {
                // Default: next column after question, or last column
                answerCol = questionCol + 1 < sheet.getRow(0).getLastCellNum() ?
                            questionCol + 1 : (int) sheet.getRow(0).getLastCellNum() - 1;
            }

            // Final defaults
            if (questionCol == -1) questionCol = 0;
            if (answerCol == -1) answerCol = Math.min(1, (int) sheet.getRow(0).getLastCellNum() - 1);

            LOGGER.info("Excel parsing - questionCol={}, answerCol={}, hasHeader={}",
                    questionCol, answerCol, hasHeader);

            // Determine starting row
            int startRow = hasHeader ? 1 : 0;

            // Extract questions
            for (int rowIndex = startRow; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) continue;

                Cell questionCell = row.getCell(questionCol);
                Cell answerCell = row.getCell(answerCol);

                String questionText = questionCell != null ? getCellStringValue(questionCell).trim() : "";
                String answerText = answerCell != null ? getCellStringValue(answerCell).trim() : "";

                // Skip empty or too short questions, and header-like rows
                if (questionText.length() > 5 && !isHeaderText(questionText)) {
                    questions.add(new AuditQuestion(
                            questions.size() + 1,
                            questionText,
                            answerText.isEmpty() ? null : answerText,
                            null,
                            rowIndex,
                            answerCol
                    ));
                }
            }

            LOGGER.info("Extracted {} questions from Excel file: {}", questions.size(), fileName);

        } catch (Exception e) {
            LOGGER.error("Failed to extract questions from Excel: {}", fileName, e);
            throw new RuntimeException("Failed to parse Excel file: " + e.getMessage(), e);
        }

        return questions;
    }

    @Override
    public ByteArrayOutputStream fillAnswers(MultipartFile file, List<AuditQuestion> answeredQuestions) {
        try {
            return fillAnswers(file.getInputStream(), answeredQuestions, file.getOriginalFilename());
        } catch (IOException e) {
            LOGGER.error("Failed to read Excel file: {}", file.getOriginalFilename(), e);
            throw new RuntimeException("Failed to read Excel file", e);
        }
    }

    @Override
    public ByteArrayOutputStream fillAnswers(InputStream inputStream, List<AuditQuestion> answeredQuestions, String fileName) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        try (Workbook workbook = createWorkbook(inputStream, fileName)) {
            Sheet sheet = workbook.getSheetAt(0);

            // Create a cell style for wrapped text
            CellStyle wrapStyle = workbook.createCellStyle();
            wrapStyle.setWrapText(true);
            wrapStyle.setVerticalAlignment(VerticalAlignment.TOP);

            int filledCount = 0;
            for (AuditQuestion question : answeredQuestions) {
                String answerToWrite = question.generatedAnswer();
                if (answerToWrite == null || answerToWrite.isEmpty()) {
                    continue;
                }

                Row row = sheet.getRow(question.rowIndex());
                if (row == null) {
                    row = sheet.createRow(question.rowIndex());
                }

                Cell answerCell = row.getCell(question.cellIndex());
                if (answerCell == null) {
                    answerCell = row.createCell(question.cellIndex());
                }

                answerCell.setCellValue(answerToWrite);
                answerCell.setCellStyle(wrapStyle);
                filledCount++;

                LOGGER.debug("Filled answer at row {}, column {}", question.rowIndex(), question.cellIndex());
            }

            // Auto-size the answer column for better readability
            if (!answeredQuestions.isEmpty()) {
                int answerCol = answeredQuestions.get(0).cellIndex();
                try {
                    sheet.autoSizeColumn(answerCol);
                    // Set a maximum width to prevent too wide columns
                    int maxWidth = 100 * 256; // 100 characters
                    if (sheet.getColumnWidth(answerCol) > maxWidth) {
                        sheet.setColumnWidth(answerCol, maxWidth);
                    }
                } catch (Exception e) {
                    LOGGER.warn("Could not auto-size column: {}", e.getMessage());
                }
            }

            workbook.write(outputStream);
            LOGGER.info("Filled {} answers into Excel file: {}", filledCount, fileName);

        } catch (Exception e) {
            LOGGER.error("Failed to fill answers into Excel: {}", fileName, e);
            throw new RuntimeException("Failed to write Excel file: " + e.getMessage(), e);
        }

        return outputStream;
    }

    @Override
    public boolean isValidExcelDocument(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return false;
        }
        String filename = file.getOriginalFilename();
        if (filename == null) {
            return false;
        }
        String lowerName = filename.toLowerCase();
        return lowerName.endsWith(".xlsx") || lowerName.endsWith(".xls");
    }

    @Override
    public String[] getSupportedExtensions() {
        return SUPPORTED_EXTENSIONS;
    }

    /**
     * Creates appropriate Workbook based on file extension.
     */
    private Workbook createWorkbook(InputStream inputStream, String fileName) throws IOException {
        if (fileName != null && fileName.toLowerCase().endsWith(".xls")) {
            return new HSSFWorkbook(inputStream);
        }
        return new XSSFWorkbook(inputStream);
    }

    /**
     * Gets string value from a cell, handling different cell types.
     */
    private String getCellStringValue(Cell cell) {
        if (cell == null) return "";

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                }
                double numValue = cell.getNumericCellValue();
                // Return as integer if it's a whole number
                if (numValue == Math.floor(numValue) && !Double.isInfinite(numValue)) {
                    return String.valueOf((long) numValue);
                }
                return String.valueOf(numValue);
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return cell.getStringCellValue();
                } catch (Exception e) {
                    try {
                        return String.valueOf(cell.getNumericCellValue());
                    } catch (Exception e2) {
                        return "";
                    }
                }
            case BLANK:
            default:
                return "";
        }
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
     * Finds the best column to use as question column based on content analysis.
     */
    private int findBestQuestionColumn(Sheet sheet) {
        int bestCol = 0;
        int maxAvgLength = 0;

        Row firstDataRow = sheet.getRow(1);
        if (firstDataRow == null) firstDataRow = sheet.getRow(0);
        if (firstDataRow == null) return 0;

        for (int col = 0; col < firstDataRow.getLastCellNum(); col++) {
            int totalLength = 0;
            int count = 0;

            // Sample first few rows to determine column with longest text
            for (int row = 0; row <= Math.min(5, sheet.getLastRowNum()); row++) {
                Row r = sheet.getRow(row);
                if (r != null) {
                    Cell cell = r.getCell(col);
                    if (cell != null) {
                        String text = getCellStringValue(cell);
                        if (text != null && text.length() > 10) {
                            totalLength += text.length();
                            count++;
                        }
                    }
                }
            }

            if (count > 0) {
                int avgLength = totalLength / count;
                if (avgLength > maxAvgLength) {
                    maxAvgLength = avgLength;
                    bestCol = col;
                }
            }
        }

        return bestCol;
    }

    /**
     * Checks if text looks like a header.
     */
    private boolean isHeaderText(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase().trim();
        return matchesKeywords(lower, QUESTION_COLUMN_KEYWORDS) ||
               matchesKeywords(lower, ANSWER_COLUMN_KEYWORDS);
    }
}
