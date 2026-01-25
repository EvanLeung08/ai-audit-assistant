package org.evan.ai.audit.util;

import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblWidth;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblWidth;

import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;

/**
 * Utility class to generate sample audit questionnaire documents for testing.
 * Run this class directly to generate test documents.
 */
public class SampleDocumentGenerator {

    public static void main(String[] args) throws IOException {
        String outputPath = "src/main/resources/samples/";

        // Generate table format document
        generateTableFormatDocument(outputPath + "audit_questionnaire_table.docx");
        System.out.println("✅ Generated: " + outputPath + "audit_questionnaire_table.docx");

        // Generate text marker format document
        generateTextFormatDocument(outputPath + "audit_questionnaire_text.docx");
        System.out.println("✅ Generated: " + outputPath + "audit_questionnaire_text.docx");

        // Generate Chinese format document
        generateChineseFormatDocument(outputPath + "audit_questionnaire_chinese.docx");
        System.out.println("✅ Generated: " + outputPath + "audit_questionnaire_chinese.docx");

        System.out.println("\n🎉 All sample documents generated successfully!");
    }

    /**
     * Generates a document with questions in table format.
     */
    public static void generateTableFormatDocument(String filePath) throws IOException {
        try (XWPFDocument document = new XWPFDocument()) {
            // Title
            XWPFParagraph title = document.createParagraph();
            title.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun titleRun = title.createRun();
            titleRun.setText("IT Security Audit Questionnaire");
            titleRun.setBold(true);
            titleRun.setFontSize(18);
            titleRun.addBreak();

            // Subtitle
            XWPFParagraph subtitle = document.createParagraph();
            subtitle.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun subtitleRun = subtitle.createRun();
            subtitleRun.setText("Date: January 2026 | Auditor: ____________");
            subtitleRun.setFontSize(12);
            subtitleRun.addBreak();
            subtitleRun.addBreak();

            // Create table with questions
            String[][] questions = {
                {"1", "What encryption standards are used for data at rest?", ""},
                {"2", "How is access control implemented for critical systems?", ""},
                {"3", "What is the password policy for user accounts?", ""},
                {"4", "How often are security vulnerability scans performed?", ""},
                {"5", "What is the incident response procedure for security breaches?", ""},
                {"6", "How are backup and recovery procedures tested?", ""},
                {"7", "What authentication methods are used for remote access?", ""},
                {"8", "How is network traffic monitored for suspicious activity?", ""},
                {"9", "What is the process for patch management?", ""},
                {"10", "How are audit logs retained and protected?", ""}
            };

            XWPFTable table = document.createTable(questions.length + 1, 3);

            // Set column widths
            table.setWidth("100%");

            // Header row
            XWPFTableRow headerRow = table.getRow(0);
            setCellText(headerRow.getCell(0), "No.", true);
            setCellText(headerRow.getCell(1), "Audit Question", true);
            setCellText(headerRow.getCell(2), "Answer / Findings", true);

            // Data rows
            for (int i = 0; i < questions.length; i++) {
                XWPFTableRow row = table.getRow(i + 1);
                setCellText(row.getCell(0), questions[i][0], false);
                setCellText(row.getCell(1), questions[i][1], false);
                setCellText(row.getCell(2), questions[i][2], false);
            }

            // Footer note
            XWPFParagraph footer = document.createParagraph();
            footer.setSpacingBefore(200);
            XWPFRun footerRun = footer.createRun();
            footerRun.addBreak();
            footerRun.setText("Note: Please fill in the 'Answer / Findings' column with appropriate responses.");
            footerRun.setItalic(true);
            footerRun.setFontSize(10);

            // Save document
            try (FileOutputStream out = new FileOutputStream(filePath)) {
                document.write(out);
            }
        }
    }

    /**
     * Generates a document with questions using [Q]/[A] markers.
     */
    public static void generateTextFormatDocument(String filePath) throws IOException {
        try (XWPFDocument document = new XWPFDocument()) {
            // Title
            XWPFParagraph title = document.createParagraph();
            title.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun titleRun = title.createRun();
            titleRun.setText("Compliance Audit Questions");
            titleRun.setBold(true);
            titleRun.setFontSize(18);
            titleRun.addBreak();
            titleRun.addBreak();

            String[] questions = {
                "What are the data retention policies and how are they enforced?",
                "How is personal data protected in accordance with privacy regulations?",
                "What controls are in place for financial reporting accuracy?",
                "How are third-party vendors assessed for compliance?",
                "What is the process for handling customer complaints related to data privacy?"
            };

            for (String question : questions) {
                // Question paragraph
                XWPFParagraph qPara = document.createParagraph();
                XWPFRun qRun = qPara.createRun();
                qRun.setText("[Q] " + question);
                qRun.setBold(true);

                // Answer paragraph (empty)
                XWPFParagraph aPara = document.createParagraph();
                XWPFRun aRun = aPara.createRun();
                aRun.setText("[A] ");
                aRun.addBreak();
            }

            // Save document
            try (FileOutputStream out = new FileOutputStream(filePath)) {
                document.write(out);
            }
        }
    }

    /**
     * Generates a document with questions in Chinese format.
     */
    public static void generateChineseFormatDocument(String filePath) throws IOException {
        try (XWPFDocument document = new XWPFDocument()) {
            // Title
            XWPFParagraph title = document.createParagraph();
            title.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun titleRun = title.createRun();
            titleRun.setText("信息安全审计问卷");
            titleRun.setBold(true);
            titleRun.setFontSize(18);
            titleRun.addBreak();

            XWPFParagraph datePara = document.createParagraph();
            datePara.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun dateRun = datePara.createRun();
            dateRun.setText("审计日期：2026年1月");
            dateRun.setFontSize(12);
            dateRun.addBreak();
            dateRun.addBreak();

            // Create table with Chinese questions
            String[][] questions = {
                {"1", "公司的数据加密策略是什么？如何确保敏感数据的安全？", ""},
                {"2", "访问控制机制是如何实施的？是否采用最小权限原则？", ""},
                {"3", "密码策略的具体要求是什么？（长度、复杂度、更换周期）", ""},
                {"4", "安全漏洞扫描的频率是多少？发现漏洞后如何处理？", ""},
                {"5", "发生安全事件时的应急响应流程是什么？", ""},
                {"6", "数据备份策略是什么？备份恢复测试的频率？", ""},
                {"7", "远程访问使用什么认证方式？是否启用多因素认证？", ""},
                {"8", "如何监控网络流量以检测可疑活动？", ""},
                {"9", "系统补丁管理流程是什么？关键补丁的部署时间要求？", ""},
                {"10", "审计日志的保留期限是多久？如何保护日志的完整性？", ""}
            };

            XWPFTable table = document.createTable(questions.length + 1, 3);
            table.setWidth("100%");

            // Header row
            XWPFTableRow headerRow = table.getRow(0);
            setCellText(headerRow.getCell(0), "序号", true);
            setCellText(headerRow.getCell(1), "审计问题", true);
            setCellText(headerRow.getCell(2), "答案/发现", true);

            // Data rows
            for (int i = 0; i < questions.length; i++) {
                XWPFTableRow row = table.getRow(i + 1);
                setCellText(row.getCell(0), questions[i][0], false);
                setCellText(row.getCell(1), questions[i][1], false);
                setCellText(row.getCell(2), questions[i][2], false);
            }

            // Footer
            XWPFParagraph footer = document.createParagraph();
            footer.setSpacingBefore(200);
            XWPFRun footerRun = footer.createRun();
            footerRun.addBreak();
            footerRun.setText("备注：请在‘答案/发现’列填写相应的审计回复。");
            footerRun.setItalic(true);
            footerRun.setFontSize(10);

            // Save document
            try (FileOutputStream out = new FileOutputStream(filePath)) {
                document.write(out);
            }
        }
    }

    private static void setCellText(XWPFTableCell cell, String text, boolean bold) {
        XWPFParagraph para = cell.getParagraphs().get(0);
        XWPFRun run = para.createRun();
        run.setText(text);
        run.setBold(bold);
        if (bold) {
            cell.setColor("E0E0E0"); // Light gray background for headers
        }
    }
}
