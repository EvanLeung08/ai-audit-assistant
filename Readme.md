# AI Audit Assistant

An intelligent audit questionnaire answering system based on Spring AI and RAG (Retrieval-Augmented Generation) technology, featuring a **Multi-Agent Collaboration Architecture**. The system automatically processes Word and Excel documents containing audit questions, retrieves relevant information from a knowledge base, and generates professional answers.

## Features

- 🤖 **Multi-Agent Collaboration Architecture**: Orchestrator, Document Analyzer, Answer Generator, and Document Writer agents work together for flexible and intelligent document processing
- 📄 **Multi-Format Document Support**: Supports both `.docx` (Word) and `.xlsx` (Excel) formats for audit questionnaires
- 🔍 **RAG Knowledge Retrieval**: Vector database-based similarity search to retrieve relevant content from the knowledge base
- 📚 **Dynamic Knowledge Base Management**: Upload, delete, and manage knowledge base documents through a web interface
- 💾 **Persistent Vector Store**: JSON-based persistent storage for document embeddings with source tracking
- 📝 **Answer Source Tracing**: Track which knowledge base documents and excerpts were used to generate each answer
- 🔐 **GitHub Copilot Integration**: OAuth device flow authentication to use GitHub Copilot API
- 🌐 **Proxy Support**: Configure corporate proxy with authentication for accessing external APIs
- 🍪 **Cookie-based Token Persistence**: Secure token storage in user cookies to avoid repeated authentication
- 🌐 **User-Friendly Web Interface**: Drag-and-drop upload with real-time processing feedback

## System Architecture

### Multi-Agent Collaboration Architecture

The system uses a Supervisor pattern with four specialized agents:

```
┌─────────────────────────────────────────────────────────────┐
│                   OrchestratorAgent                          │
│             (Workflow Coordination & Task Routing)           │
└─────────────────────┬───────────────────────────────────────┘
                      │
        ┌─────────────┼─────────────┬─────────────┐
        ▼             ▼             ▼             ▼
┌───────────────┐ ┌───────────────┐ ┌───────────────┐
│ DocumentAna-  │ │ AnswerGene-   │ │ DocumentWri-  │
│ lyzerAgent    │ │ ratorAgent    │ │ terAgent      │
│ (AI-Powered   │ │ (RAG-Based    │ │ (Format       │
│  Extraction)  │ │  Generation)  │ │  Preservation)│
└───────────────┘ └───────────────┘ └───────────────┘
```

#### Agent Responsibilities

| Agent | Responsibility | Core Capabilities |
|-------|----------------|-------------------|
| **OrchestratorAgent** | Coordinate workflow | Task decomposition, agent routing, result aggregation |
| **DocumentAnalyzerAgent** | Analyze documents using AI | Multi-strategy extraction, question identification with confidence scoring |
| **AnswerGeneratorAgent** | Generate answers with RAG | Vector search, context retrieval, answer generation, source tracking |
| **DocumentWriterAgent** | Write answers back | Format preservation for Word/Excel, cell/row mapping |

### Processing Flow

```
User Upload → OrchestratorAgent
                    │
                    ▼
         DocumentAnalyzerAgent
         (AI analyzes: Is this a question?)
                    │
                    ▼
         AnswerGeneratorAgent
         (Vector search → RAG generation)
                    │
                    ▼
         DocumentWriterAgent
         (Fill answers, preserve format)
                    │
                    ▼
              Download File
```

## Tech Stack

- **Backend Framework**: Spring Boot 3.4.1
- **AI Framework**: Spring AI 1.1.2
- **Vector Store**: Custom PersistentVectorStore (JSON-based)
- **Document Processing**: Apache POI 5.2.5 (Word & Excel)
- **Frontend**: Thymeleaf + HTMX + TailwindCSS
- **Java Version**: 21

## Quick Start

### 1. Configure Authentication

This project supports two authentication methods to access AI APIs:

#### Method 1: GitHub Copilot OAuth (Recommended, Default)

If you have a GitHub Copilot subscription, use GitHub OAuth to automatically obtain API tokens:

1. Start the application and visit http://localhost:8080
2. Click **"Authorize with GitHub"** on the authorization card
3. Enter the displayed code on GitHub's device authorization page
4. The system will automatically poll and complete the authentication

The token is persisted in the user's cookies for automatic refresh and reuse.

**Via API (optional):**
```bash
# Start device authentication flow
curl -X POST http://localhost:8080/api/auth/device/start

# Returns:
# {
#   "userCode": "ABCD-1234",
#   "verificationUri": "https://github.com/login/device",
#   "deviceCode": "..."
# }

# Visit verificationUri and enter userCode to complete authentication
# Then poll to get token:
curl -X POST http://localhost:8080/api/auth/device/poll \
  -H "Content-Type: application/json" \
  -d '{"deviceCode": "..."}'
```

#### Method 2: Traditional API Key

Edit `src/main/resources/application.yml` to switch to environment variable mode:

```yaml
copilot:
  auth-mode: env  # Switch to environment variable mode

spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      base-url: https://api.openai.com/v1  # Or other compatible API endpoints
```

### 2. Configure Proxy (For Corporate Networks)

If you need a proxy to access GitHub APIs:

**Via Web Interface:**
1. Enable the proxy checkbox in the authorization card
2. Enter proxy server address and port
3. Enter your corporate username/password if required
4. Click "Save Proxy Settings"

**Via Configuration File:**
```yaml
copilot:
  proxy:
    host: proxy.company.com
    port: 8080
    type: HTTP
```

### 3. Build Knowledge Base

#### Via Web Interface (Recommended)

1. Expand the **"Knowledge Base"** card on the main page
2. Upload documents by dragging or clicking the upload area
3. Supported formats: `.docx`, `.pdf`, `.md`, `.txt`
4. Documents are automatically processed, split, and embedded
5. View document list and delete documents as needed

#### Via Configuration File (Legacy)

Place documents in `src/main/resources/knowledge-base/` and configure:

```yaml
audit:
  knowledge-base:
    storage-path: ./data/vectorstore.json
    uploads-dir: ./data/uploads
    documents:
      - classpath:knowledge-base/your-document.md
      - classpath:knowledge-base/another-document.pdf
```

### 4. Run the Project

```bash
cd evan-ai-audit-assistant
./start.sh
# or
mvn spring-boot:run
```

Visit http://localhost:8080

## Supported Document Formats

### Word Table Format (Recommended)

| No. | Question | Answer |
|-----|----------|--------|
| 1 | What are the main objectives of internal audit? | (To be filled) |
| 2 | How to ensure audit independence? | (To be filled) |

Or two-column format:

| Question | Answer |
|----------|--------|
| Question content... | (To be filled) |

### Excel Format

Upload `.xlsx` files with questions in rows. The system will:
1. Detect question columns automatically using AI
2. Find or create answer columns
3. Output answered Excel file with the same format

### Text Format (with Markers)

Using markers:

```
[Q] What are the basic steps of risk assessment?
[A] 

[Question] How to ensure data security?
[Answer] 

Question: How to perform compliance checks?
Answer:
```

## API Reference

### Authentication Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/auth/status` | GET | Check authentication status |
| `/api/auth/device/start` | POST | Start device flow authentication |
| `/api/auth/device/poll` | POST | Poll device authentication result |
| `/api/auth/logout` | POST | Clear all tokens and logout |
| `/api/auth/token` | POST | Manually set OAuth token |
| `/api/auth/test` | POST | Test current token validity |

#### Check Authentication Status
```bash
GET /api/auth/status

Response: {"authenticated": true, "tokenExpired": false, "expiresAt": 1234567890}
```

#### Start Device Flow Authentication
```bash
POST /api/auth/device/start

Response: {
  "deviceCode": "...",
  "userCode": "ABCD-1234",
  "verificationUri": "https://github.com/login/device",
  "verificationUriComplete": "https://github.com/login/device?code=ABCD-1234",
  "expiresIn": 900,
  "interval": 5
}
```

#### Poll Device Authentication Result
```bash
POST /api/auth/device/poll
Content-Type: application/json

Body: {"deviceCode": "..."}

Response: {"success": true, "message": "Authentication successful!"}
# or {"success": false, "pending": true, "message": "Waiting for user authorization..."}
```

### Document Processing Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/audit/process` | POST | Process document and return statistics |
| `/api/audit/download` | POST | Process and download filled document |
| `/download` | POST | Download processed document (form submission) |

#### Process Document (Get Statistics)

```bash
POST /api/audit/process
Content-Type: multipart/form-data

Parameters:
- file: Word or Excel document file
- skipExisting: Skip questions with existing answers (true/false)
```

#### Process and Download Document

```bash
POST /api/audit/download
Content-Type: multipart/form-data

Parameters:
- file: Word or Excel document file
- skipExisting: Skip questions with existing answers (true/false)

Response: Filled document with answers
```

### Knowledge Base Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/knowledge-base/upload` | POST | Upload document to knowledge base |
| `/api/knowledge-base/documents` | GET | List all knowledge base documents |
| `/api/knowledge-base/documents/{fileName}` | DELETE | Delete a document from knowledge base |
| `/api/knowledge-base/stats` | GET | Get knowledge base statistics |
| `/api/knowledge-base/answer-logs` | GET | Get answer source tracking logs |

### Proxy Configuration Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/auth/proxy` | POST | Configure proxy settings |
| `/api/auth/proxy/status` | GET | Get current proxy status |

## Project Structure

```
evan-ai-audit-assistant/
├── src/main/java/org/evan/ai/audit/
│   ├── AuditAssistantApplication.java       # Application entry point
│   ├── agent/                               # Multi-Agent Framework
│   │   ├── AuditAgent.java                  # Agent interface
│   │   ├── AgentContext.java                # Shared context between agents
│   │   ├── AgentResult.java                 # Agent execution result
│   │   └── impl/
│   │       ├── OrchestratorAgent.java       # Workflow coordinator
│   │       ├── DocumentAnalyzerAgent.java   # AI-powered document analysis
│   │       ├── AnswerGeneratorAgent.java    # RAG-based answer generation
│   │       └── DocumentWriterAgent.java     # Document output handler
│   ├── config/
│   │   ├── AuditProperties.java             # Audit configuration properties
│   │   ├── CopilotOpenAiConfig.java         # Dynamic token injection
│   │   ├── CopilotProperties.java           # Copilot/proxy configuration
│   │   └── KnowledgeBaseConfig.java         # Knowledge base setup
│   ├── controller/
│   │   ├── AuditController.java             # Document processing endpoints
│   │   ├── CopilotAuthController.java       # OAuth & proxy endpoints
│   │   └── KnowledgeBaseController.java     # Knowledge base management
│   ├── exception/
│   │   └── GlobalExceptionHandler.java      # Global exception handling
│   ├── model/
│   │   ├── AgentContext.java                # Context for agent communication
│   │   ├── AgentResult.java                 # Agent operation result
│   │   ├── AnswerSourceLog.java             # Source tracking model
│   │   ├── AuditProcessResult.java          # Processing result
│   │   ├── AuditQuestion.java               # Question model
│   │   ├── CopilotToken.java                # Token model
│   │   ├── DeviceCodeResponse.java          # Device flow response
│   │   └── DeviceTokenResponse.java         # Token response
│   ├── service/
│   │   ├── AuditAnswerService.java          # Answer service interface
│   │   ├── AuditProcessService.java         # Process service interface
│   │   ├── ExcelDocumentService.java        # Excel handling interface
│   │   ├── KnowledgeBaseService.java        # Knowledge base interface
│   │   ├── WordDocumentService.java         # Word handling interface
│   │   ├── copilot/
│   │   │   ├── CopilotTokenService.java     # Token management with cookie support
│   │   │   ├── GitHubDeviceAuthService.java # GitHub device flow
│   │   │   ├── ProxyService.java            # Proxy configuration
│   │   │   └── TokenCookieService.java      # Cookie persistence
│   │   └── impl/
│   │       ├── AgentBasedAuditProcessServiceImpl.java  # Agent-based processing
│   │       ├── AuditAnswerServiceImpl.java
│   │       ├── AuditProcessServiceImpl.java
│   │       ├── ExcelDocumentServiceImpl.java
│   │       ├── KnowledgeBaseServiceImpl.java
│   │       └── WordDocumentServiceImpl.java
│   ├── util/
│   └── vectorstore/
│       └── PersistentVectorStore.java       # JSON-based vector storage
├── src/main/resources/
│   ├── application.yml                       # Application configuration
│   ├── knowledge-base/                       # Default knowledge documents
│   │   ├── audit-guidelines.md
│   │   ├── compliance-rules.md
│   │   └── security-policies.md
│   ├── samples/                              # Sample documents
│   │   ├── audit_questionnaire_sample.xlsx
│   │   ├── audit_questionnaire_table.docx
│   │   └── ...
│   └── templates/
│       └── index.html                        # Main UI
├── data/                                     # Runtime data
│   ├── vectorstore.json                      # Persistent vector store
│   └── uploads/                              # Uploaded documents
├── doc/design/                               # Design documentation
│   ├── architect.md                          # System architecture diagram
│   ├── multi-agent diagram.md                # Multi-agent sequence diagram
│   └── oAuth diagram.md                      # OAuth flow diagram
└── pom.xml
```

## Configuration Reference

### application.yml Main Configuration

```yaml
# Copilot Authentication
copilot:
  auth-mode: oauth  # oauth or env
  github:
    client-id: Iv1.b507a08c87ecfe98
  api:
    base-url: https://api.githubcopilot.com
  proxy:
    host: proxy.company.com
    port: 8080
    type: HTTP

# Audit Configuration
audit:
  # Enable multi-agent mode (recommended)
  use-agent-mode: true
  knowledge-base:
    # Persistent vector store path
    storage-path: ./data/vectorstore.json
    # Uploaded documents storage
    uploads-dir: ./data/uploads
    documents:
      - classpath:knowledge-base/audit-guidelines.md

# Spring AI Configuration
spring:
  ai:
    openai:
      chat:
        options:
          model: gpt-4o
      embedding:
        options:
          model: copilot-text-embedding-ada-002
```

## Extending Knowledge Base

### Via Web Interface (Recommended)
1. Expand the "Knowledge Base" card on the main page
2. Drag and drop or click to upload new documents
3. Documents are automatically processed and added to the vector store

### Via Configuration File
1. Add new knowledge documents to `src/main/resources/knowledge-base/` directory
2. Add document paths in `application.yml`
3. Restart the application

## Custom Question Markers

Configure custom question/answer markers in `application.yml`:

```yaml
audit:
  document:
    question-markers:
      - "[Q]"
      - "[Question]"
      - "Question:"
      - "Q:"
    answer-markers:
      - "[A]"
      - "[Answer]"
      - "Answer:"
      - "A:"
```

## Important Notes

1. **File Size Limit**: Default maximum upload size is 50MB
2. **Processing Time**: Each question takes approximately 2-5 seconds
3. **Answer Marking**: AI-generated answers are displayed in blue font for easy identification
4. **Knowledge Base Updates**: Documents uploaded via web interface are immediately available
5. **Token Persistence**: Tokens are stored in cookies; clearing cookies requires re-authentication
6. **Agent Mode**: Set `audit.use-agent-mode: true` for AI-powered document analysis (recommended)
7. **Proxy Authentication**: If your corporate proxy requires authentication, enter credentials in the web interface

## Troubleshooting

### DNS Resolution Failed
If you see "Failed to resolve 'api.github.com'", enable proxy in the web interface and configure your corporate proxy settings.

### Token Expired
The system automatically refreshes tokens using the stored OAuth token. If issues persist, click "Sign Out" and re-authorize.

### No Questions Found
The AI-powered document analyzer uses multiple strategies:
- Table extraction (prioritized)
- Paragraph extraction with keyword detection
- AI confidence scoring (threshold: 0.6)

For flexible document formats, the analyzer adapts automatically.

## License

MIT License
