package org.evan.ai.audit.controller;

import io.github.wimdeblauwe.htmx.spring.boot.mvc.HtmxResponse;
import org.evan.ai.audit.agent.impl.OrchestratorAgent;
import org.evan.ai.audit.model.AuditProcessResult;
import org.evan.ai.audit.service.AuditProcessService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

/**
 * Controller for handling audit document processing requests.
 * Supports both Word (.docx) and Excel (.xlsx, .xls) documents.
 * Uses multi-agent architecture (OrchestratorAgent) for document processing.
 */
@Controller
public class AuditController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuditController.class);

    private final AuditProcessService auditProcessService;
    private final OrchestratorAgent orchestratorAgent;

    public AuditController(AuditProcessService auditProcessService,
                          OrchestratorAgent orchestratorAgent) {
        this.auditProcessService = auditProcessService;
        this.orchestratorAgent = orchestratorAgent;
    }

    /**
     * Check if file is a valid supported document (Word or Excel).
     */
    private boolean isValidDocument(MultipartFile file) {
        // Use OrchestratorAgent's validation which covers both Word and Excel
        return orchestratorAgent.isValidDocument(file);
    }

    /**
     * Get the content type for the response based on file type.
     */
    private String getContentType(String fileName) {
        if (fileName == null) {
            return "application/octet-stream";
        }
        String lowerName = fileName.toLowerCase();
        if (lowerName.endsWith(".xlsx")) {
            return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        } else if (lowerName.endsWith(".xls")) {
            return "application/vnd.ms-excel";
        } else {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }
    }

    /**
     * Displays the main upload page.
     */
    @GetMapping("/")
    public String index() {
        return "index";
    }

    /**
     * Handles file upload and processing via HTMX.
     * Returns an HTMX response with processing results.
     */
    @PostMapping("/upload")
    public HtmxResponse uploadAndProcess(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "skipExisting", defaultValue = "false") boolean skipExisting,
            Model model) {

        LOGGER.info("Received file upload: {}, skipExisting: {}",
                file.getOriginalFilename(), skipExisting);

        // Validate file
        if (file.isEmpty()) {
            model.addAttribute("error", "Please select a file to upload.");
            return HtmxResponse.builder()
                    .view("fragments/result :: error")
                    .build();
        }

        if (!isValidDocument(file)) {
            model.addAttribute("error", "Invalid file format. Please upload a .docx, .xlsx, or .xls file.");
            return HtmxResponse.builder()
                    .view("fragments/result :: error")
                    .build();
        }

        try {
            // Process the document
            AuditProcessResult result = auditProcessService.processDocument(file, skipExisting);

            if (result.isSuccess()) {
                model.addAttribute("result", result);
                model.addAttribute("fileName", file.getOriginalFilename());
                return HtmxResponse.builder()
                        .view("fragments/result :: success")
                        .build();
            } else {
                model.addAttribute("error", result.errorMessage());
                return HtmxResponse.builder()
                        .view("fragments/result :: error")
                        .build();
            }
        } catch (Exception e) {
            LOGGER.error("Error processing file", e);
            model.addAttribute("error", "Processing failed: " + e.getMessage());
            return HtmxResponse.builder()
                    .view("fragments/result :: error")
                    .build();
        }
    }

    /**
     * Handles file download after processing.
     * Uses the multi-agent architecture (OrchestratorAgent) for document processing.
     * Processes the document and returns the filled document as a download.
     */
    @PostMapping("/download")
    public ResponseEntity<byte[]> downloadProcessedDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "skipExisting", defaultValue = "false") boolean skipExisting) {

        LOGGER.info("Processing document for download using multi-agent architecture: {}", file.getOriginalFilename());

        try {
            if (!isValidDocument(file)) {
                return ResponseEntity.badRequest()
                        .contentType(MediaType.TEXT_PLAIN)
                        .body("Invalid file format. Please upload a .docx, .xlsx, or .xls file.".getBytes());
            }

            // Use the multi-agent OrchestratorAgent to process the document
            ByteArrayOutputStream outputStream = orchestratorAgent.processDocument(file, skipExisting);
            byte[] documentBytes = outputStream.toByteArray();

            // Generate output filename
            String originalFileName = file.getOriginalFilename();
            String outputFileName = generateOutputFileName(originalFileName);

            // Encode filename for Content-Disposition header
            String encodedFileName = URLEncoder.encode(outputFileName, StandardCharsets.UTF_8)
                    .replace("+", "%20");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(getContentType(originalFileName)));
            headers.setContentLength(documentBytes.length);
            headers.setContentDispositionFormData("attachment", outputFileName);
            headers.set("Content-Disposition",
                    "attachment; filename=\"" + outputFileName + "\"; filename*=UTF-8''" + encodedFileName);
            // Prevent caching
            headers.setCacheControl("no-cache, no-store, must-revalidate");
            headers.setPragma("no-cache");
            headers.setExpires(0);

            LOGGER.info("Returning document processed by multi-agent system: {} ({} bytes)", outputFileName, documentBytes.length);
            return new ResponseEntity<>(documentBytes, headers, HttpStatus.OK);

        } catch (Exception e) {
            // Check if this is a client disconnect (can be safely ignored)
            if (isClientDisconnectException(e)) {
                LOGGER.warn("Client disconnected during processing: {}", file.getOriginalFilename());
                return null;
            }
            LOGGER.error("Error downloading processed document", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(("Error processing document: " + e.getMessage()).getBytes());
        }
    }

    /**
     * Check if exception is caused by client disconnect.
     */
    private boolean isClientDisconnectException(Throwable e) {
        if (e == null) return false;

        // Check exception class name
        String className = e.getClass().getName();
        if (className.contains("ClientAbortException") ||
            className.contains("ClosedChannelException") ||
            className.contains("AsyncRequestNotUsableException")) {
            return true;
        }

        // Check exception message
        String message = e.getMessage();
        if (message != null && (message.contains("ClientAbortException") ||
                                message.contains("ClosedChannelException") ||
                                message.contains("Broken pipe") ||
                                message.contains("Connection reset") ||
                                message.contains("AsyncRequestNotUsableException"))) {
            return true;
        }

        // Check cause recursively
        return isClientDisconnectException(e.getCause());
    }

    /**
     * REST API endpoint for processing audit documents using multi-agent architecture.
     * Returns JSON with processing results.
     */
    @PostMapping("/api/audit/process")
    @ResponseBody
    public ResponseEntity<AuditProcessResult> processDocumentApi(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "skipExisting", defaultValue = "false") boolean skipExisting) {

        LOGGER.info("API request to process document using multi-agent architecture: {}", file.getOriginalFilename());

        if (!isValidDocument(file)) {
            return ResponseEntity.badRequest()
                    .body(AuditProcessResult.failure(file.getOriginalFilename(),
                            "Invalid file format. Please upload a .docx, .xlsx, or .xls file."));
        }

        try {
            // Use the multi-agent OrchestratorAgent to process the document
            long startTime = System.currentTimeMillis();
            orchestratorAgent.processDocument(file, skipExisting);
            long processingTime = System.currentTimeMillis() - startTime;

            // Create success result
            String outputFileName = generateOutputFileName(file.getOriginalFilename());
            AuditProcessResult result = AuditProcessResult.success(
                    file.getOriginalFilename(),
                    outputFileName,
                    Collections.emptyList(), // Questions list not available from agent context directly
                    processingTime
            );

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            LOGGER.error("Error processing document via API", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(AuditProcessResult.failure(file.getOriginalFilename(), e.getMessage()));
        }
    }

    private String generateOutputFileName(String originalFileName) {
        if (originalFileName == null) {
            return "audit_answered.docx";
        }
        int dotIndex = originalFileName.lastIndexOf('.');
        if (dotIndex > 0) {
            return originalFileName.substring(0, dotIndex) + "_answered" + originalFileName.substring(dotIndex);
        }
        return originalFileName + "_answered.docx";
    }
}
