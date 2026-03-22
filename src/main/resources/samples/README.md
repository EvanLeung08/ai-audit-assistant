# Sample Questionnaire Documents

This directory contains sample questionnaire documents for testing the AI Questionnaire Assistant.

## Available Test Documents

### 1. audit_questionnaire_table.docx (English - Table Format)
- **Format**: 3-column table (No., Question, Answer)
- **Questions**: 10 IT security questionnaire questions
- **Language**: English
- **Use case**: Testing table-based question extraction

Sample questions:
- What encryption standards are used for data at rest?
- How is access control implemented for critical systems?
- What is the password policy for user accounts?

### 2. audit_questionnaire_text.docx (English - Text Marker Format)
- **Format**: [Q]/[A] text markers
- **Questions**: 5 compliance questionnaire questions
- **Language**: English
- **Use case**: Testing text marker-based question extraction

Sample questions:
- What are the data retention policies and how are they enforced?
- How is personal data protected in accordance with privacy regulations?

### 3. audit_questionnaire_chinese.docx (Chinese - Table Format)
- **Format**: 3-column table (序号, 审计问题, 答案/发现)
- **Questions**: 10 信息安全问卷问题
- **Language**: Chinese (中文)
- **Use case**: Testing Chinese language support

Sample questions:
- 公司的数据加密策略是什么？如何确保敏感数据的安全？
- 访问控制机制是如何实施的？是否采用最小权限原则？

### 4. audit_questionnaire_sample.xlsx (English - Excel Format)
- **Format**: 3-column Excel spreadsheet (No., Question, Answer)
- **Questions**: 10 IT security questionnaire questions
- **Language**: English
- **Use case**: Testing Excel document support

To generate this file, run:
```bash
./mvnw test -Dtest=SampleExcelGeneratorTest
```

Sample questions:
- What is the company's policy on data backup frequency?
- How are access controls managed for sensitive systems?
- What encryption standards are used for data at rest?

## Supported File Formats

The AI Questionnaire Assistant supports:
- **Word Documents**: `.docx` (table format or [Q]/[A] markers)
- **Excel Spreadsheets**: `.xlsx`, `.xls` (column-based format)

## How to Test

1. Start the application:
   ```bash
   ./mvnw spring-boot:run
   ```

2. Open http://localhost:8080 in your browser

3. Upload one of the sample documents

4. The system will:
   - Extract all questions from the document
   - Query the knowledge base for relevant information
   - Generate AI-powered answers using RAG
   - Fill the answers back into the document
   - Provide the completed document for download

## Regenerating Sample Documents

To regenerate the sample documents, run the test:

```bash
./mvnw test -Dtest=SampleDocumentGeneratorTest#generateAllSampleDocuments
```

## Expected Results

After processing, each document should have:
- All answer cells/sections filled with AI-generated content
- Answers displayed in blue color to distinguish from original text
- Content based on the knowledge base documents (`audit-guidelines.md`, `compliance-rules.md`, `security-policies.md`)
