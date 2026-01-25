package org.evan.ai.audit.agent.impl;

import org.evan.ai.audit.agent.AgentContext;
import org.evan.ai.audit.agent.AgentResult;
import org.evan.ai.audit.agent.AuditAgent;
import org.evan.ai.audit.model.AuditQuestion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.poi.xwpf.usermodel.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;

/**
 * Agent responsible for writing answers back into the document.
 */
@Component
public class DocumentWriterAgent implements AuditAgent {

    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentWriterAgent.class);

    @Override
    public String getName() { return "DocumentWriterAgent"; }

    @Override
    public String getDescription() { return "Writes answers into the document"; }

    @Override
    public boolean canHandle(AgentContext context) {
        return context.has(AgentContext.KEY_ANSWERED_QUESTIONS) && context.has(AgentContext.KEY_DOCUMENT_BYTES);
    }

    @Override
    public AgentResult execute(AgentContext context) {
        LOGGER.info("[{}] Starting document writing", getName());

        try {
            byte[] documentBytes = context.get(AgentContext.KEY_DOCUMENT_BYTES);
            String documentType = context.get(AgentContext.KEY_DOCUMENT_TYPE);
            String fileName = context.get(AgentContext.KEY_FILE_NAME);
            List<AuditQuestion> answeredQuestions = context.get(AgentContext.KEY_ANSWERED_QUESTIONS);

            if (documentBytes == null || answeredQuestions == null) {
                return AgentResult.failure("Missing document data or answered questions");
            }

            ByteArrayOutputStream outputStream;

            if ("WORD".equals(documentType)) {
                outputStream = writeToWord(documentBytes, answeredQuestions);
            } else if ("EXCEL".equals(documentType)) {
                outputStream = writeToExcel(documentBytes, answeredQuestions, fileName);
            } else {
                return AgentResult.failure("Unsupported document type: " + documentType);
            }

            context.set(AgentContext.KEY_OUTPUT_DOCUMENT, outputStream);

            LOGGER.info("[{}] Wrote {} answers to document", getName(), answeredQuestions.size());
            return AgentResult.success("Document written with " + answeredQuestions.size() + " answers",
                    Map.of("bytesWritten", outputStream.size()));

        } catch (Exception e) {
            LOGGER.error("[{}] Error writing document", getName(), e);
            return AgentResult.failure("Document writing failed: " + e.getMessage());
        }
    }

    private ByteArrayOutputStream writeToWord(byte[] documentBytes, List<AuditQuestion> questions) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(documentBytes))) {
            Map<Integer, AuditQuestion> questionsByRow = questions.stream()
                    .filter(q -> q.generatedAnswer() != null)
                    .collect(Collectors.toMap(AuditQuestion::rowIndex, q -> q, (a, b) -> a));

            boolean hasTableQuestions = questions.stream().anyMatch(q -> q.cellIndex() >= 0);

            if (hasTableQuestions) {
                for (XWPFTable table : document.getTables()) {
                    List<XWPFTableRow> rows = table.getRows();
                    for (int rowIdx = 0; rowIdx < rows.size(); rowIdx++) {
                        AuditQuestion question = questionsByRow.get(rowIdx);
                        if (question != null && question.generatedAnswer() != null && question.cellIndex() >= 0) {
                            XWPFTableRow row = rows.get(rowIdx);
                            List<XWPFTableCell> cells = row.getTableCells();
                            if (question.cellIndex() < cells.size()) {
                                XWPFTableCell answerCell = cells.get(question.cellIndex());
                                for (int i = answerCell.getParagraphs().size() - 1; i >= 0; i--) {
                                    answerCell.removeParagraph(i);
                                }
                                XWPFParagraph para = answerCell.addParagraph();
                                XWPFRun run = para.createRun();
                                run.setText(question.generatedAnswer());
                                run.setColor("0000FF");
                            }
                        }
                    }
                }
            }
            document.write(outputStream);
        }
        return outputStream;
    }

    private ByteArrayOutputStream writeToExcel(byte[] documentBytes, List<AuditQuestion> questions, String fileName) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        try (Workbook workbook = fileName != null && fileName.toLowerCase().endsWith(".xls")
                ? new HSSFWorkbook(new ByteArrayInputStream(documentBytes))
                : new XSSFWorkbook(new ByteArrayInputStream(documentBytes))) {

            Sheet sheet = workbook.getSheetAt(0);
            CellStyle wrapStyle = workbook.createCellStyle();
            wrapStyle.setWrapText(true);
            wrapStyle.setVerticalAlignment(VerticalAlignment.TOP);

            for (AuditQuestion question : questions) {
                if (question.generatedAnswer() == null || question.generatedAnswer().isEmpty()) continue;

                Row row = sheet.getRow(question.rowIndex());
                if (row == null) row = sheet.createRow(question.rowIndex());

                Cell answerCell = row.getCell(question.cellIndex());
                if (answerCell == null) answerCell = row.createCell(question.cellIndex());

                answerCell.setCellValue(question.generatedAnswer());
                answerCell.setCellStyle(wrapStyle);
            }
            workbook.write(outputStream);
        }
        return outputStream;
    }
}
