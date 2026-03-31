# AMG Integration Platform × AI Questionnaire Engine — Unified Architecture Design

> **Project Vision:** Combine AMG Integration Platform's file routing & connectivity capabilities with the AI Questionnaire Engine's intelligent document processing to deliver a fully automated, end-to-end questionnaire response pipeline with centralized knowledge management.

---

## 1. High-Level System Context

```mermaid
C4Context
    title System Context — AIP + AI Questionnaire Engine

    Person(client, "Client / Partner", "Sends questionnaires via email, SFTP, API, portal")
    Person(internal, "Internal Team", "Manages knowledge base, reviews results, monitors flows")

    System_Boundary(platform, "AMG Intelligent Document Platform") {
        System(amg, "AMG Integration Platform", "File routing, channel management, protocol adapters, workflow orchestration")
        System(aiq, "AI Questionnaire Engine", "Multi-agent AI processing, RAG-based answer generation, source traceability")
        SystemDb(kb, "Centralized Knowledge Base", "Shared vector store, document repository, embedding index")
    }

    System_Ext(email, "Email Server", "SMTP / IMAP / Exchange")
    System_Ext(sftp, "SFTP / FTP Server", "Secure file transfer")
    System_Ext(erp, "ERP / ECM Systems", "SAP, SharePoint, etc.")
    System_Ext(llm, "LLM API Provider", "OpenAI / Azure OpenAI / other LLM APIs")

    Rel(client, email, "Sends questionnaire")
    Rel(client, sftp, "Uploads files")
    Rel(email, amg, "Inbound channel")
    Rel(sftp, amg, "Inbound channel")
    Rel(erp, amg, "Bidirectional sync")
    Rel(amg, aiq, "Routes documents for AI processing")
    Rel(aiq, kb, "Read/write knowledge & embeddings")
    Rel(aiq, llm, "LLM & embedding API calls")
    Rel(amg, email, "Sends completed documents")
    Rel(amg, sftp, "Delivers output files")
    Rel(amg, erp, "Archives results")
    Rel(internal, amg, "Configure flows & channels")
    Rel(internal, aiq, "Manage knowledge base & review answers")
```

---

## 2. Integrated Platform Architecture

```mermaid
flowchart TB
    subgraph ExternalChannels["🌐 External Channels"]
        EMAIL["📧 Email<br/>(SMTP/IMAP)"]
        SFTP["📁 SFTP/FTP"]
        API["🔌 REST API<br/>(Partner Portal)"]
        PORTAL["🖥️ Web Portal<br/>(Manual Upload)"]
    end

    subgraph AIP["🔄 AMG Integration Platform"]
        direction TB
        subgraph Inbound["Inbound Processing"]
            ChannelAdapter["Channel Adapters<br/>(Email, SFTP, API, Portal)"]
            FileDetector["File Type Detector<br/>& Validator"]
            Router["Intelligent Router<br/>(Rule-based + AI classification)"]
        end

        subgraph FlowEngine["Workflow Engine"]
            FlowDef["Flow Definitions<br/>(YAML / UI Config)"]
            FlowExec["Flow Executor<br/>(Step orchestration)"]
            RetryMgr["Retry & Error Manager"]
        end

        subgraph Outbound["Outbound Delivery"]
            OutputRouter["Output Router"]
            ChannelOut["Channel Adapters<br/>(Email, SFTP, API, ECM)"]
            AuditLog["Delivery Audit Log"]
        end
    end

    subgraph AIEngine["🧠 AI Questionnaire Engine"]
        direction TB
        subgraph AgentLayer["Multi-Agent Layer"]
            Orchestrator["🎯 Orchestrator Agent"]
            DocAnalyzer["📄 Document Analyzer Agent"]
            AnswerGen["💡 Answer Generator Agent"]
            DocWriter["✍️ Document Writer Agent"]
        end

        subgraph AIServices["AI Services"]
            RAG["RAG Pipeline<br/>(Retrieve → Augment → Generate)"]
            Embedder["Embedding Service"]
            ConfScore["Confidence Scoring"]
            SourceTrack["Source Tracker"]
        end

        subgraph AIAPI["Engine API"]
            ProcessAPI["POST /api/engine/process"]
            StatusAPI["GET /api/engine/status/{jobId}"]
            CallbackAPI["Callback Webhook"]
        end
    end

    subgraph CentralKB["📚 Centralized Knowledge Base"]
        direction TB
        DocRepo["Document Repository<br/>(Original files)"]
        VectorDB["Vector Store<br/>(Embeddings + Metadata)"]
        SourceIndex["Source Index<br/>(Traceability registry)"]
        KBAdmin["KB Admin API<br/>(Upload, delete, rebuild)"]
    end

    subgraph ExternalAI["☁️ AI Provider"]
        LLMProvider["LLM API Provider<br/>(OpenAI / Azure OpenAI / Custom)"]
    end

    subgraph Monitoring["📊 Operations"]
        Dashboard["Processing Dashboard"]
        AlertMgr["Alert Manager"]
        MetricsDB["Metrics Store"]
    end

    %% Inbound flow
    EMAIL --> ChannelAdapter
    SFTP --> ChannelAdapter
    API --> ChannelAdapter
    PORTAL --> ChannelAdapter
    ChannelAdapter --> FileDetector
    FileDetector --> Router

    %% AIP to AI Engine
    Router -->|"Questionnaire detected"| FlowExec
    FlowExec -->|"POST /api/engine/process"| ProcessAPI
    ProcessAPI --> Orchestrator
    Orchestrator --> DocAnalyzer
    DocAnalyzer --> AnswerGen
    AnswerGen --> DocWriter

    %% AI internals
    AnswerGen --> RAG
    RAG --> VectorDB
    RAG --> LLMProvider
    Embedder --> LLMProvider
    SourceTrack --> SourceIndex

    %% KB connections
    KBAdmin --> DocRepo
    KBAdmin --> VectorDB
    DocRepo --> Embedder
    Embedder --> VectorDB

    %% Output flow
    DocWriter -->|"Completed document"| CallbackAPI
    CallbackAPI -->|"Webhook notification"| FlowExec
    FlowExec --> OutputRouter
    OutputRouter --> ChannelOut
    ChannelOut --> EMAIL
    ChannelOut --> SFTP
    ChannelOut --> AuditLog

    %% Monitoring
    FlowExec -.-> MetricsDB
    Orchestrator -.-> MetricsDB
    MetricsDB -.-> Dashboard
    Dashboard -.-> AlertMgr

    %% Internal team
    PORTAL -.->|"KB Management"| KBAdmin
```

---

## 3. End-to-End Processing Flow

```mermaid
sequenceDiagram
    autonumber
    participant Client as 👤 Client
    participant Channel as 📧 Email / SFTP
    participant AIP as 🔄 AMG Integration Platform
    participant Router as 🔀 Intelligent Router
    participant Engine as 🧠 AI Engine
    participant Analyzer as 📄 Doc Analyzer
    participant Generator as 💡 Answer Generator
    participant KB as 📚 Knowledge Base
    participant LLM as ☁️ LLM API
    participant Writer as ✍️ Doc Writer
    participant Outbound as 📤 Outbound Channel

    rect rgb(227, 242, 253)
        Note over Client,Channel: 1. Document Arrival
        Client->>Channel: Send questionnaire<br/>(Word / Excel)
        Channel->>AIP: File received via adapter
    end

    rect rgb(255, 243, 224)
        Note over AIP,Router: 2. AIP Ingestion & Routing
        AIP->>AIP: File validation & metadata extraction
        AIP->>Router: Classify document type
        Router->>Router: Rule match: questionnaire pattern detected
        Router->>AIP: Route to AI Engine flow
    end

    rect rgb(232, 234, 246)
        Note over AIP,Engine: 3. Job Submission
        AIP->>Engine: POST /api/engine/process<br/>{file, callbackUrl, jobId, metadata}
        Engine-->>AIP: 202 Accepted {jobId, status: QUEUED}
        AIP->>AIP: Record job, set timeout watchdog
    end

    rect rgb(252, 228, 236)
        Note over Engine,Writer: 4. AI Multi-Agent Processing
        Engine->>Analyzer: Step 1: Analyze document
        Analyzer->>LLM: Batch classify candidates
        LLM-->>Analyzer: Question identification results
        Analyzer-->>Engine: Questions extracted (with confidence)

        Engine->>Generator: Step 2: Generate answers
        loop For each question
            Generator->>KB: Vector similarity search (top-K)
            KB-->>Generator: Relevant passages + source metadata
            Generator->>LLM: Generate answer (question + context)
            LLM-->>Generator: Answer + reasoning
            Generator->>Generator: Record source traceability log
        end
        Generator-->>Engine: All answers generated

        Engine->>Writer: Step 3: Write back to document
        Writer->>Writer: Preserve original format<br/>Fill answers into template
        Writer-->>Engine: Completed document (byte stream)
    end

    rect rgb(200, 230, 201)
        Note over Engine,Outbound: 5. Result Delivery
        Engine->>AIP: POST callback {jobId, status: COMPLETED, file}
        AIP->>AIP: Attach traceability report
        AIP->>Outbound: Route output per flow config
        alt Reply via Email
            Outbound->>Client: Email with filled document + source report
        else Deliver to SFTP
            Outbound->>Channel: Upload to output folder
        else Archive to ECM
            Outbound->>AIP: Store in SharePoint / document system
        end
    end

    rect rgb(255, 248, 225)
        Note over AIP,AIP: 6. Audit & Monitoring
        AIP->>AIP: Log processing metrics<br/>Update dashboard<br/>Archive job record
    end
```

---

## 4. Centralized Knowledge Base Architecture

```mermaid
flowchart TB
    subgraph Sources["📥 Knowledge Sources"]
        WebUpload["🖥️ Web Portal Upload"]
        AIPIngest["🔄 AIP Auto-Ingest<br/>(Watched folders / channels)"]
        APIUpload["🔌 API Upload<br/>POST /api/kb/documents"]
        BulkImport["📦 Bulk Import<br/>(Migration tool)"]
    end

    subgraph Pipeline["⚙️ Ingestion Pipeline"]
        Validator["Format Validator<br/>(.docx, .pdf, .md, .txt, .xlsx)"]
        Splitter["Document Splitter<br/>(Chunk by section / paragraph)"]
        Embedder["Embedding Generator<br/>(text-embedding-ada-002 / custom)"]
        Indexer["Vector Indexer<br/>(Write to centralized store)"]
        MetaWriter["Metadata Writer<br/>(Source, timestamp, version, tags)"]
    end

    subgraph Storage["💾 Centralized Storage"]
        direction LR
        subgraph DocStore["Document Repository"]
            OrigFiles["Original Files<br/>(S3 / NFS / Local)"]
            FileRegistry["File Registry<br/>(name, hash, version, status)"]
        end
        subgraph VectorLayer["Vector Store"]
            EmbedIndex["Embedding Index<br/>(JSON / Pinecone / pgvector)"]
            ChunkStore["Chunk Store<br/>(text + metadata per chunk)"]
        end
        subgraph TraceStore["Traceability Store"]
            SourceLog["Answer Source Logs"]
            UsageStats["Document Usage Stats"]
        end
    end

    subgraph Consumers["📤 Consumers"]
        AIEngine["🧠 AI Engine<br/>(similarity search)"]
        AdminUI["🖥️ Admin Portal<br/>(browse, delete, rebuild)"]
        ReportSvc["📊 Reporting Service<br/>(coverage, usage, gaps)"]
    end

    WebUpload --> Validator
    AIPIngest --> Validator
    APIUpload --> Validator
    BulkImport --> Validator

    Validator --> Splitter
    Splitter --> Embedder
    Embedder --> Indexer
    Indexer --> EmbedIndex
    Indexer --> ChunkStore
    Splitter --> MetaWriter
    MetaWriter --> FileRegistry
    Validator --> OrigFiles

    EmbedIndex --> AIEngine
    ChunkStore --> AIEngine
    SourceLog --> ReportSvc
    FileRegistry --> AdminUI
    UsageStats --> ReportSvc

    AIEngine -->|"Record usage"| SourceLog
    AIEngine -->|"Update stats"| UsageStats
```

---

## 5. Deployment Architecture

```mermaid
flowchart TB
    subgraph Cloud["☁️ Production Environment"]
        subgraph LB["Load Balancer / API Gateway"]
            APIGW["API Gateway<br/>(Rate limiting, auth, routing)"]
        end

        subgraph AIPCluster["AMG Integration Platform"]
            AIP1["AIP Instance 1"]
            AIP2["AIP Instance 2"]
            AIPQueue["Message Queue<br/>(RabbitMQ / Kafka)"]
        end

        subgraph AICluster["AI Questionnaire Engine"]
            AI1["AI Engine Instance 1"]
            AI2["AI Engine Instance 2"]
            JobQueue["Job Queue<br/>(Async processing)"]
        end

        subgraph SharedStorage["Shared Storage Layer"]
            S3["Object Storage<br/>(Documents & uploads)"]
            VectorDB["Vector Database<br/>(Embeddings)"]
            RelDB["Relational DB<br/>(Metadata, logs, jobs)"]
            Cache["Redis Cache<br/>(Tokens, sessions)"]
        end

        subgraph ExtServices["External Services"]
            LLMAPI["LLM API Provider<br/>(OpenAI / Azure OpenAI)"]
            EmailSvc["Email Service"]
            SFTPSvc["SFTP Endpoints"]
        end
    end

    APIGW --> AIP1
    APIGW --> AIP2
    APIGW --> AI1
    APIGW --> AI2

    AIP1 --> AIPQueue
    AIP2 --> AIPQueue
    AIPQueue --> AI1
    AIPQueue --> AI2

    AI1 --> VectorDB
    AI2 --> VectorDB
    AI1 --> S3
    AI2 --> S3
    AI1 --> LLMAPI
    AI2 --> LLMAPI

    AIP1 --> S3
    AIP2 --> S3
    AIP1 --> RelDB
    AI1 --> RelDB
    AI1 --> Cache

    AIP1 --> EmailSvc
    AIP1 --> SFTPSvc
```

---

## 6. API Contract Between AIP and AI Engine

```mermaid
classDiagram
    class EngineProcessRequest {
        +String jobId
        +byte[] fileContent
        +String fileName
        +String fileType
        +String callbackUrl
        +boolean skipExisting
        +Map~String,String~ metadata
    }

    class EngineProcessResponse {
        +String jobId
        +String status
        +String message
        +String estimatedCompletionTime
    }

    class EngineCallbackPayload {
        +String jobId
        +String status
        +byte[] outputFile
        +String outputFileName
        +ProcessingStats stats
        +List~AnswerSourceLog~ sourceLogs
        +String errorMessage
    }

    class ProcessingStats {
        +int totalQuestionsFound
        +int questionsAnswered
        +int questionsSkipped
        +double avgConfidenceScore
        +long processingTimeMs
    }

    class AnswerSourceLog {
        +String questionText
        +String answerText
        +double confidenceScore
        +List~SourceReference~ sources
    }

    class SourceReference {
        +String documentName
        +String chunkText
        +double similarityScore
        +String uploadedAt
    }

    class JobStatusResponse {
        +String jobId
        +String status
        +int progress
        +ProcessingStats stats
    }

    EngineCallbackPayload --> ProcessingStats
    EngineCallbackPayload --> AnswerSourceLog
    AnswerSourceLog --> SourceReference
    JobStatusResponse --> ProcessingStats
```

### API Endpoints

| Endpoint                     | Method | Description                          | Called By                |
| ---------------------------- | ------ | ------------------------------------ | ------------------------ |
| `/api/engine/process`        | POST   | Submit document for async processing | AIP → AI Engine          |
| `/api/engine/status/{jobId}` | GET    | Poll job processing status           | AIP → AI Engine          |
| `/api/engine/cancel/{jobId}` | POST   | Cancel a running job                 | AIP → AI Engine          |
| `/api/kb/documents`          | POST   | Upload document to knowledge base    | AIP / Portal → AI Engine |
| `/api/kb/documents`          | GET    | List all KB documents                | Admin Portal             |
| `/api/kb/documents/{id}`     | DELETE | Remove document from KB              | Admin Portal             |
| `/api/kb/stats`              | GET    | Knowledge base statistics            | Dashboard                |
| `/api/kb/rebuild`            | POST   | Rebuild vector index                 | Admin Portal             |
| `{callbackUrl}`              | POST   | Deliver completed result             | AI Engine → AIP          |

---

## 7. Flow Configuration Example (AIP Side)

```mermaid
flowchart LR
    subgraph FlowDef["📋 AIP Flow: Questionnaire Auto-Response"]
        S1["📧 Trigger:<br/>Email received<br/>Subject contains 'questionnaire'"]
        S2["📎 Extract:<br/>Download attachment<br/>Validate file type"]
        S3["🧠 Process:<br/>POST /api/engine/process<br/>Wait for callback"]
        S4["✅ Check:<br/>Confidence > threshold?"]
        S5A["📤 Auto-send:<br/>Reply with filled doc<br/>+ source report"]
        S5B["👤 Manual review:<br/>Route to reviewer<br/>with draft + sources"]
        S6["📊 Log:<br/>Record metrics<br/>Archive to ECM"]
    end

    S1 --> S2 --> S3 --> S4
    S4 -->|"Yes: high confidence"| S5A
    S4 -->|"No: needs review"| S5B
    S5A --> S6
    S5B --> S6
```

---

## 8. User Feedback & Continuous Improvement Loop

The system supports a closed-loop feedback mechanism: reviewers can accept, edit, or reject AI-generated answers. Feedback data is used to improve future answer quality and track reviewer confidence over time.

```mermaid
sequenceDiagram
    autonumber
    participant Engine as 🧠 AI Engine
    participant AIP as 🔄 AMG Integration Platform
    participant Reviewer as 👤 Reviewer
    participant FeedbackDB as 📊 Feedback Store
    participant KB as 📚 Knowledge Base
    participant LLM as ☁️ LLM API

    rect rgb(232, 234, 246)
        Note over Engine,Reviewer: 1. AI Generates Draft
        Engine->>AIP: Completed document + confidence scores
        AIP->>Reviewer: Route for review (with draft + sources)
    end

    rect rgb(255, 243, 224)
        Note over Reviewer,FeedbackDB: 2. Reviewer Provides Feedback
        Reviewer->>Reviewer: Review each answer
        alt Accept answer
            Reviewer->>AIP: Accept (no changes)
            AIP->>FeedbackDB: Log: ACCEPTED {questionId, confidence}
        else Edit answer
            Reviewer->>AIP: Submit corrected answer
            AIP->>FeedbackDB: Log: EDITED {questionId, original, corrected, reason}
        else Reject answer
            Reviewer->>AIP: Reject with reason
            AIP->>FeedbackDB: Log: REJECTED {questionId, reason}
        end
    end

    rect rgb(200, 230, 201)
        Note over AIP,AIP: 3. Finalize & Deliver
        AIP->>AIP: Merge reviewer decisions into final document
        AIP->>AIP: Deliver finalized document to client
    end

    rect rgb(252, 228, 236)
        Note over FeedbackDB,KB: 4. Continuous Improvement
        FeedbackDB->>FeedbackDB: Aggregate feedback metrics
        alt Repeated corrections detected
            FeedbackDB->>KB: Flag knowledge gap
            Note over KB: Admin reviews gap report<br/>and uploads new documents
        end
        alt High-quality corrections available
            FeedbackDB->>KB: Suggest new KB entries<br/>(from reviewer corrections)
            KB->>LLM: Re-embed updated content
        end
    end
```

### Feedback Data Model

| Field             | Type      | Description                              |
| ----------------- | --------- | ---------------------------------------- |
| `jobId`           | String    | Processing job reference                 |
| `questionId`      | String    | Specific question identifier             |
| `action`          | Enum      | ACCEPTED / EDITED / REJECTED             |
| `originalAnswer`  | String    | AI-generated answer                      |
| `correctedAnswer` | String    | Reviewer's corrected version (if edited) |
| `rejectionReason` | String    | Why the answer was rejected              |
| `confidenceScore` | Double    | AI confidence at generation time         |
| `reviewerNotes`   | String    | Free-text reviewer comment               |
| `reviewedAt`      | Timestamp | When the review happened                 |
| `reviewerId`      | String    | Who performed the review                 |

### Feedback Metrics Dashboard

- **Acceptance rate** — % of answers accepted without changes
- **Edit rate** — % of answers that needed correction
- **Rejection rate** — % of answers fully rejected
- **Avg confidence vs outcome** — correlation between AI confidence and reviewer action
- **Knowledge gap report** — topics with high rejection/edit rates
- **Reviewer turnaround time** — time from draft to finalized document

---

## 9. User Review & Approval Workflow

The review workflow supports configurable approval policies: auto-approve for high-confidence answers, single-reviewer for medium confidence, and multi-reviewer escalation for low confidence or sensitive topics.

```mermaid
flowchart TB
    subgraph Input["📥 AI Engine Output"]
        Result["Completed Document\n+ Confidence Scores\n+ Source References"]
    end

    subgraph Policy["📋 Approval Policy Engine"]
        Evaluate["Evaluate Each Answer"]
        HighConf{"Confidence\n≥ 85%?"}
        MedConf{"Confidence\n≥ 60%?"}
        Sensitive{"Sensitive\nTopic?"}
    end

    subgraph AutoApprove["✅ Auto-Approve Path"]
        Auto["Auto-approve\n& mark as AI-generated"]
        AutoLog["Log: auto-approved\n(auditable)"]
    end

    subgraph SingleReview["👤 Single Review Path"]
        Assign1["Assign to primary reviewer"]
        Review1["Reviewer: Accept / Edit / Reject"]
        Decision1{"Approved?"}
    end

    subgraph EscalationReview["👥 Escalation Path"]
        Assign2["Assign to senior reviewer\n+ domain expert"]
        Review2["Dual review required"]
        Decision2{"Both approved?"}
    end

    subgraph Output["📤 Final Output"]
        Finalize["Merge approved answers"]
        Deliver["Deliver to client\nvia AIP channel"]
        Archive["Archive with\naudit trail"]
    end

    subgraph Rejected["🔄 Re-process"]
        Regen["Re-generate with\nadditional context"]
        ManualFill["Manual answer\nby reviewer"]
    end

    Result --> Evaluate --> HighConf
    HighConf -->|"Yes"| Sensitive
    Sensitive -->|"No"| Auto --> AutoLog --> Finalize
    Sensitive -->|"Yes"| Assign2
    HighConf -->|"No"| MedConf
    MedConf -->|"Yes"| Assign1
    MedConf -->|"No"| Assign2

    Assign1 --> Review1 --> Decision1
    Decision1 -->|"Yes"| Finalize
    Decision1 -->|"No"| Rejected

    Assign2 --> Review2 --> Decision2
    Decision2 -->|"Yes"| Finalize
    Decision2 -->|"No"| Rejected

    Regen --> Evaluate
    ManualFill --> Finalize

    Finalize --> Deliver --> Archive

    Rejected --> Regen
    Rejected --> ManualFill
```

### Approval Policy Configuration

```yaml
approval:
  policies:
    auto-approve:
      min-confidence: 0.85
      exclude-topics: ["financial", "legal", "regulatory"]
      require-source-count: 2 # At least 2 source references
    single-review:
      min-confidence: 0.60
      max-confidence: 0.85
      assign-to: "primary-reviewer-pool"
      sla-hours: 24
    escalation:
      below-confidence: 0.60
      sensitive-topics: ["financial", "legal", "regulatory"]
      assign-to: ["senior-reviewer", "domain-expert"]
      require-unanimous: true
      sla-hours: 48
  notifications:
    on-assignment: true
    on-sla-breach: true
    channels: ["email", "slack"]
```

---

## 10. Knowledge Base Auto-Update Mechanism

The knowledge base evolves continuously through four automated channels: scheduled document scans, feedback-driven gap detection, AIP-ingested new documents, and version-controlled content refresh.

```mermaid
flowchart TB
    subgraph Triggers["🔔 Update Triggers"]
        Scheduled["⏰ Scheduled Scan\n(Daily / Weekly)"]
        Feedback["📊 Feedback-Driven\n(Gap detection)"]
        AIPIngest["🔄 AIP Auto-Ingest\n(New file detected)"]
        Manual["👤 Admin Upload\n(Manual trigger)"]
    end

    subgraph Detection["🔍 Change Detection"]
        FolderWatch["Watched Folder Scanner\n(S3 / NFS / SharePoint)"]
        HashCheck["File Hash Comparator\n(Detect modified files)"]
        VersionCheck["Version Registry Check\n(New version available?)"]
        GapAnalyzer["Knowledge Gap Analyzer\n(From feedback data)"]
    end

    subgraph Pipeline["⚙️ Update Pipeline"]
        Validate["Validate & Classify"]
        Diff["Diff Against Existing\n(New / Updated / Deleted)"]
        Decision{"Update\nType?"}
    end

    subgraph NewDoc["📄 New Document"]
        Split1["Chunk & Split"]
        Embed1["Generate Embeddings"]
        Index1["Add to Vector Store"]
        Meta1["Register in Metadata"]
    end

    subgraph UpdateDoc["📝 Updated Document"]
        Invalidate["Invalidate old embeddings"]
        Split2["Re-chunk document"]
        Embed2["Re-generate embeddings"]
        Index2["Replace in Vector Store"]
        Meta2["Update version & timestamp"]
    end

    subgraph DeleteDoc["🗑️ Removed Document"]
        Remove["Remove from Vector Store"]
        Orphan["Check for orphan references"]
        Meta3["Mark as archived"]
    end

    subgraph PostUpdate["✅ Post-Update"]
        Notify["Notify admins"]
        Stats["Update KB statistics"]
        Verify["Verify search quality\n(Sample queries)"]
        Log["Audit log entry"]
    end

    Scheduled --> FolderWatch
    Feedback --> GapAnalyzer
    AIPIngest --> FolderWatch
    Manual --> Validate

    FolderWatch --> HashCheck
    HashCheck --> VersionCheck
    GapAnalyzer --> Validate
    VersionCheck --> Validate

    Validate --> Diff --> Decision
    Decision -->|"New"| Split1 --> Embed1 --> Index1 --> Meta1
    Decision -->|"Updated"| Invalidate --> Split2 --> Embed2 --> Index2 --> Meta2
    Decision -->|"Deleted"| Remove --> Orphan --> Meta3

    Meta1 --> Notify
    Meta2 --> Notify
    Meta3 --> Notify
    Notify --> Stats --> Verify --> Log
```

### Auto-Update Configuration

```yaml
knowledge-base:
  auto-update:
    enabled: true

    # Scheduled scanning
    schedule:
      cron: "0 2 * * *" # Daily at 2 AM
      full-rebuild-cron: "0 3 * * 0" # Weekly full rebuild on Sunday

    # Watched sources
    sources:
      - type: s3
        bucket: company-policies
        prefix: /approved/
        watch-interval: 300 # Check every 5 minutes
      - type: sharepoint
        site: compliance-team
        library: Final Documents
        watch-interval: 600
      - type: amg-channel
        channel-id: kb-ingest
        auto-process: true

    # Change detection
    detection:
      hash-algorithm: SHA-256
      track-versions: true
      max-file-size-mb: 100

    # Feedback-driven updates
    feedback-integration:
      enabled: true
      gap-threshold: 3 # Flag topic after 3 rejections
      auto-suggest-from-edits: true
      min-corrections-for-suggestion: 5

    # Quality verification
    verification:
      enabled: true
      sample-query-count: 10
      min-relevance-score: 0.7
      alert-on-degradation: true

    # Notifications
    notifications:
      on-new-document: true
      on-update: true
      on-delete: true
      on-gap-detected: true
      channels: ["email", "slack", "dashboard"]
```

### KB Health Monitoring

| Metric                           | Description                                      | Alert Threshold |
| -------------------------------- | ------------------------------------------------ | --------------- |
| **Document freshness**           | Age of newest document per topic                 | > 90 days       |
| **Coverage score**               | % of question topics with matching KB content    | < 80%           |
| **Stale embedding ratio**        | % of embeddings older than source document       | > 10%           |
| **Gap report count**             | Unresolved knowledge gaps from feedback          | > 5 open        |
| **Search quality score**         | Avg relevance of top-K results on sample queries | < 0.7           |
| **Update pipeline success rate** | % of auto-updates completed without error        | < 95%           |

---

## 11. Migration Roadmap

```mermaid
gantt
    title Integration Roadmap — AIP × AI Questionnaire Engine
    dateFormat  YYYY-MM-DD
    axisFormat  %b %Y

    section Phase 1 — Foundation
    API contract design & review           :p1a, 2026-04-01, 2w
    AI Engine async job API                :p1b, after p1a, 3w
    Centralized KB storage layer           :p1c, after p1a, 3w
    Basic AIP → AI Engine connector        :p1d, after p1b, 2w

    section Phase 2 — Core Integration
    AIP inbound channel adapters           :p2a, after p1d, 3w
    Callback & output delivery flow        :p2b, after p1d, 3w
    Confidence-based routing               :p2c, after p2a, 2w
    KB auto-ingest from AIP channels       :p2d, after p1c, 2w

    section Phase 3 — Production Hardening
    Retry, timeout, error handling         :p3a, after p2b, 2w
    Monitoring dashboard                   :p3b, after p2c, 2w
    Load testing & performance tuning      :p3c, after p3a, 2w
    Security review & audit logging        :p3d, after p3b, 2w

    section Phase 4 — Go Live
    UAT with real questionnaires           :p4a, after p3c, 2w
    Pilot rollout (1–2 clients)            :p4b, after p4a, 3w
    Production GA                          :milestone, after p4b, 0d
```

---

## 12. Key Design Decisions

| Decision                     | Choice                                       | Rationale                                                         |
| ---------------------------- | -------------------------------------------- | ----------------------------------------------------------------- |
| **Communication pattern**    | Async (job queue + callback)                 | Questionnaire processing takes 30s–5min; sync calls would timeout |
| **Knowledge base ownership** | AI Engine owns KB; AIP feeds documents       | Single source of truth for embeddings; AIP handles file routing   |
| **Vector store**             | Start with JSON-based, migrate to pgvector   | JSON works for MVP; pgvector scales for production                |
| **File storage**             | Shared object storage (S3 / NFS)             | Both AIP and AI Engine need access to original files              |
| **Auth for AI API**          | Centralized token service with cache         | Avoid per-request OAuth overhead; token reuse via Redis           |
| **Confidence routing**       | AIP decides based on AI Engine scores        | Business rules stay in AIP; AI Engine focuses on processing       |
| **Traceability**             | Source logs stored in centralized DB         | Accessible by both dashboard and compliance reports               |
| **Retry strategy**           | AIP handles retries with exponential backoff | AI Engine is stateless per job; AIP owns workflow state           |
| **Feedback loop**            | Reviewer corrections feed back into KB       | Continuous improvement; answers get better over time              |
| **Review workflow**          | Confidence-based tiered approval             | Auto-approve high confidence; escalate sensitive/low confidence   |
| **KB auto-update**           | Scheduled scan + feedback-driven gap fill    | Knowledge stays fresh without manual intervention                 |

---

## 13. Unified Product Positioning

```
┌─────────────────────────────────────────────────────────────────┐
│              AMG Intelligent Document Platform                   │
│         "From File Integration to File Intelligence"            │
├─────────────────────────────┬───────────────────────────────────┤
│  AMG Integration Platform   │   AI Questionnaire Engine         │
│  ─────────────────────────  │   ──────────────────────────────  │
│  • Channel management       │   • Multi-agent AI processing     │
│  • Protocol adapters        │   • RAG-based answer generation   │
│  • Workflow orchestration   │   • Source traceability            │
│  • File routing & delivery  │   • Centralized knowledge base    │
│  • Retry & error handling   │   • Confidence scoring            │
│  • Audit logging            │   • Format-preserving output      │
├─────────────────────────────┴───────────────────────────────────┤
│              Shared Infrastructure                               │
│  • Object storage  • Vector DB  • Message queue  • Monitoring   │
└─────────────────────────────────────────────────────────────────┘
```

**Investor pitch (one sentence):**

> "AIP already routes millions of files across enterprise channels. Now we're adding an AI intelligence layer that _understands_ and _responds to_ those documents — starting with automated questionnaire processing, with contract review and RFP automation next."
