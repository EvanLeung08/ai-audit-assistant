package org.evan.ai.audit.util;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test to generate sample Excel file.
 */
class SampleExcelGeneratorTest {

    @Test
    void generateSampleExcelFile() throws Exception {
        Path samplesDir = Paths.get("src/main/resources/samples");
        Files.createDirectories(samplesDir);

        Path excelPath = samplesDir.resolve("audit_questionnaire_sample.xlsx");
        SampleExcelGenerator.generateSampleExcel(excelPath.toString());

        assertTrue(Files.exists(excelPath), "Sample Excel file should be created");
        System.out.println("Sample Excel created at: " + excelPath.toAbsolutePath());
    }
}
