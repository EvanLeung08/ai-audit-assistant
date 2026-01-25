package org.evan.ai.audit.util;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Utility to generate sample Excel audit questionnaire files for testing.
 */
public class SampleExcelGenerator {

    public static void main(String[] args) {
        try {
            // Generate to src/main/resources/samples directory
            Path samplesDir = Paths.get("src/main/resources/samples");
            Files.createDirectories(samplesDir);

            generateSampleExcel(samplesDir.resolve("audit_questionnaire_sample.xlsx").toString());
            System.out.println("Sample Excel file generated successfully!");

        } catch (IOException e) {
            System.err.println("Failed to generate sample Excel: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void generateSampleExcel(String filePath) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Audit Questions");

            // Create header style
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 12);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            // Create cell style for data
            CellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setWrapText(true);
            dataStyle.setVerticalAlignment(VerticalAlignment.TOP);
            dataStyle.setBorderBottom(BorderStyle.THIN);
            dataStyle.setBorderTop(BorderStyle.THIN);
            dataStyle.setBorderLeft(BorderStyle.THIN);
            dataStyle.setBorderRight(BorderStyle.THIN);

            // Create header row
            Row headerRow = sheet.createRow(0);
            String[] headers = {"No.", "Question", "Answer"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Sample audit questions
            String[][] questions = {
                {"1", "What is the company's policy on data backup frequency?", ""},
                {"2", "How are access controls managed for sensitive systems?", ""},
                {"3", "What encryption standards are used for data at rest?", ""},
                {"4", "Describe the incident response procedure for security breaches.", ""},
                {"5", "How often are security audits conducted?", ""},
                {"6", "What is the password policy for employee accounts?", ""},
                {"7", "How is employee security awareness training conducted?", ""},
                {"8", "What measures are in place for physical security of data centers?", ""},
                {"9", "How are third-party vendor risks assessed and managed?", ""},
                {"10", "What is the data retention policy for customer information?", ""}
            };

            // Create data rows
            for (int i = 0; i < questions.length; i++) {
                Row row = sheet.createRow(i + 1);
                for (int j = 0; j < questions[i].length; j++) {
                    Cell cell = row.createCell(j);
                    cell.setCellValue(questions[i][j]);
                    cell.setCellStyle(dataStyle);
                }
            }

            // Set column widths
            sheet.setColumnWidth(0, 6 * 256);   // No. column
            sheet.setColumnWidth(1, 60 * 256);  // Question column
            sheet.setColumnWidth(2, 60 * 256);  // Answer column

            // Write to file
            try (FileOutputStream fos = new FileOutputStream(filePath)) {
                workbook.write(fos);
            }

            System.out.println("Generated: " + filePath);
        }
    }
}
