# Plan: Knowledge Base Enhancement with Persistent Storage and Source Tracking

**STATUS: IMPLEMENTATION COMPLETE**

This plan implements three major features: (1) JSON-based persistent VectorStore, (2) Answer source tracking with log UI, and (3) User-uploadable knowledge base documents.

---

## NEW: Excel Document Support ✅

Added support for Excel (.xlsx, .xls) audit documents in addition to Word (.docx) documents.

### Created Files:
- `ExcelDocumentService.java` - Interface for Excel document operations
- `ExcelDocumentServiceImpl.java` - Implementation using Apache POI
  - Extracts questions from Excel (auto-detects Question/Answer columns)
  - Fills answers back into Excel with proper formatting
  - Supports both .xlsx (XSSF) and .xls (HSSF) formats

### Modified Files:
- `AuditProcessServiceImpl.java` - Added DocumentType enum and routing logic for Word/Excel
- `index.html` - Updated file input to accept .docx, .xlsx, .xls files

### Excel Format Expected:
- Header row with "Question" and "Answer" columns (auto-detected)
- Or default: Column A = Questions, Column B = Answers
- Data rows start from row 2

---

## Implemented Changes

### 1. Created `PersistentVectorStore.java` in `org.evan.ai.audit.vectorstore` package ✅
- Implements `VectorStore` interface
- Stores embeddings in JSON file at configurable path (`./data/vectorstore.json`)
- Each document stores: `id`, `content`, `embedding[]`, `source`, `fileName`, `lineNumber`, `addedAt` timestamp
- Implements `cosineSimilarity()` for search
- Provides `getAllDocuments()`, `deleteBySource()`, `clear()`, `getSourceFiles()` methods for management

### 2. Created `AnswerSourceLog.java` model in `org.evan.ai.audit.model` package ✅
- Records: `id`, `question`, `answer`, `sourceReferences[]`, `timestamp`, `sessionId`
- `SourceReference` contains: `documentId`, `fileName`, `source`, `contentExcerpt`, `similarityScore`, `lineNumber`, `addedAt`

### 3. Created `KnowledgeBaseService.java` interface and `KnowledgeBaseServiceImpl.java` in `org.evan.ai.audit.service` package ✅
- `uploadDocument(MultipartFile file)` - processes uploaded file, splits, embeds, stores
- `getDocumentList()` - returns list of uploaded documents for UI
- `deleteDocument(String fileName)` - removes document from store
- `getTotalChunkCount()`, `hasDocuments()`, `clearKnowledgeBase()`
- `recordAnswerLog()`, `getRecentAnswerLogs()`, `getAnswerLogsBySession()`, `clearAnswerLogs()`

### 4. Updated `KnowledgeBaseConfig.java` ✅
- Replace `SimpleVectorStore` with `PersistentVectorStore`
- Added configuration for storage path: `audit.knowledge-base.storage-path`
- Legacy documents from configuration only loaded if vector store is empty
- Added metadata enrichment (fileName, source, lineNumber) to documents

### 5. Created `KnowledgeBaseController.java` in `org.evan.ai.audit.controller` package ✅
- `POST /api/knowledge-base/upload` - upload document
- `GET /api/knowledge-base/documents` - list documents
- `DELETE /api/knowledge-base/documents/{fileName}` - delete document
- `DELETE /api/knowledge-base/documents` - clear all
- `GET /api/knowledge-base/status` - get status
- `GET /api/knowledge-base/logs` - get answer logs with sources
- `GET /api/knowledge-base/logs/session/{sessionId}` - get logs by session
- `DELETE /api/knowledge-base/logs` - clear logs

### 6. Updated `AuditAnswerServiceImpl.java` ✅
- Added source tracking via `vectorStore.similaritySearch()` before generating answer
- Logs sources used via `KnowledgeBaseService.recordAnswerLog()`
- Extracts source metadata from retrieved documents

### 7. Updated `index.html` - Added Knowledge Base Management UI ✅
- Collapsible "Knowledge Base" panel with:
  - File upload dropzone for documents (.docx, .pdf, .md, .txt)
  - Document list table (fileName, chunkCount, addedAt, delete button)
  - Total chunks count and "Clear All" button
- Collapsible "Answer Source Logs" panel with:
  - Table showing: question, source count, timestamp
  - Expandable rows showing answer excerpt and source references (file, similarity%, content excerpt)
  - "Clear Logs" button

### 8. Updated `application.yml` ✅
- Added `audit.knowledge-base.storage-path: ./data/vectorstore.json`
- Made `documents` list optional (legacy documents loaded only if store is empty)

### 9. Updated `AuditProperties.java` ✅
- Added `storagePath` property with default `./data/vectorstore.json`

### 10. Updated `pom.xml` ✅
- Added `jackson-datatype-jsr310` for LocalDateTime JSON serialization

### 11. Updated `.gitignore` ✅
- Added `/data/` to ignore vector store and upload files

## Further Considerations

1. **File format support**: Should we limit to specific formats (.docx, .pdf, .md, .txt) or support all Tika-supported formats? support all Tika-supported formats
2. **Storage cleanup**: Should uploaded source files be kept after embedding, or only store the vectors? Keeping files enables re-indexing.
3. **Concurrent access**: The JSON file approach works for single-instance deployment. For multi-instance, consider switching to SQLite or external vector DB.
