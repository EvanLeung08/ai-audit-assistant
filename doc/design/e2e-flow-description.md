# End-to-End Processing Flow

> 本文档描述问卷自动应答的完整 8 阶段流程，帮助理解每个组件的职责和数据流转。

---

## Flow Overview

```
Client → Channel → AIP → AI Engine (Analyzer → Generator → Writer) → Approval Policy → Reviewer → Delivery → KB Auto-Update
```

| Stage | Name                                    | Key Components                                                    |
| ----- | --------------------------------------- | ----------------------------------------------------------------- |
| 1     | Document Arrival                        | Client, Channel Adapter                                           |
| 2     | AIP Ingestion & Routing                 | AIP (Channel Adapters, Router, Validator)                         |
| 3     | Job Submission                          | AIP → AI Engine (Async API)                                       |
| 4     | Multi-Agent AI Processing               | Orchestrator, Doc Analyzer, Answer Generator, KB, LLM, Doc Writer |
| 5     | Confidence-Based Approval Routing       | AIP, Approval Policy Engine                                       |
| 6     | Human Review & Feedback                 | Reviewer, AIP, Feedback Store                                     |
| 7     | Finalize & Deliver                      | AIP, Channel Adapter (Outbound)                                   |
| 8     | Continuous Improvement & KB Auto-Update | Feedback Store, KB Pipeline, LLM (Embedding)                      |

---

## Stage 1: Document Arrival

### Components

| Component                                            | Role                                                                       |
| ---------------------------------------------------- | -------------------------------------------------------------------------- |
| **Client**                                           | External party (e.g., auditor, partner) who sends a questionnaire document |
| **Channel Adapter** (📧 Email / SFTP / API / Portal) | Receives inbound files from various protocol channels                      |

### Flow

1. **Client** sends a questionnaire file (`.docx` / `.xlsx`) via Email, SFTP, REST API, or the web portal.
2. **Channel Adapter** detects the incoming file and forwards it to AIP.

### Invocation

| From    | To      | Protocol            | Details                                               |
| ------- | ------- | ------------------- | ----------------------------------------------------- |
| Client  | Channel | SMTP / SFTP / HTTPS | Client sends file via their configured channel        |
| Channel | AIP     | Internal event      | File arrival triggers AIP inbound processing pipeline |

---

## Stage 2: AIP Ingestion & Routing

### Components

| Component                     | Role                                                                                      |
| ----------------------------- | ----------------------------------------------------------------------------------------- |
| **AIP — File Validator**      | Validates file format, size, integrity; extracts metadata (sender, subject, timestamps)   |
| **AIP — Document Classifier** | Classifies document type (questionnaire, contract, RFP, etc.) using rule-based + AI hints |
| **AIP — Intelligent Router**  | Routes classified documents to the appropriate processing flow                            |

### Flow

1. **File Validator** checks the file is valid and supported (Word / Excel / PDF).
2. **Document Classifier** identifies the document type → `QUESTIONNAIRE` detected.
3. **Intelligent Router** matches the classification to the configured AIP Flow (e.g., "Questionnaire Auto-Response") and routes accordingly.

### Invocation

| From                | To                  | Method                  | Details                                      |
| ------------------- | ------------------- | ----------------------- | -------------------------------------------- |
| Channel Adapter     | File Validator      | Internal (AIP pipeline) | Automatic on file arrival                    |
| File Validator      | Document Classifier | Internal                | Pass validated file + metadata               |
| Document Classifier | Intelligent Router  | Internal                | Classification result determines target flow |

### Data Produced

```json
{
  "fileId": "file-abc-123",
  "fileName": "Q4_Security_Questionnaire.docx",
  "fileType": "WORD",
  "classification": "QUESTIONNAIRE",
  "sender": "auditor@client.com",
  "receivedAt": "2026-03-31T10:15:00Z",
  "metadata": {
    "subject": "Q4 Security Assessment",
    "channel": "EMAIL"
  }
}
```

---

## Stage 3: Job Submission

### Components

| Component               | Role                                                                |
| ----------------------- | ------------------------------------------------------------------- |
| **AIP — Flow Executor** | Executes the configured flow; submits the document to the AI Engine |
| **AI Engine — Job API** | Receives async processing requests, queues jobs                     |

### Flow

1. **AIP Flow Executor** calls the AI Engine's async processing endpoint.
2. **AI Engine** validates the request, creates a job record, and returns `202 Accepted` with a `jobId`.
3. **AIP** records the `jobId` and starts a timeout watchdog.

### Invocation

| From      | To        | Method   | Endpoint              |
| --------- | --------- | -------- | --------------------- |
| AIP       | AI Engine | `POST`   | `/api/engine/process` |
| AI Engine | AIP       | Response | `202 Accepted`        |

### Request Payload — `EngineProcessRequest`

```json
{
  "jobId": "job-20260331-001",
  "fileContent": "<base64-encoded>",
  "fileName": "Q4_Security_Questionnaire.docx",
  "fileType": "WORD",
  "callbackUrl": "https://aip.internal/api/callback/job-20260331-001",
  "skipExisting": false,
  "metadata": {
    "sender": "auditor@client.com",
    "channel": "EMAIL",
    "priority": "NORMAL"
  }
}
```

### Response — `EngineProcessResponse`

```json
{
  "jobId": "job-20260331-001",
  "status": "QUEUED",
  "message": "Job accepted for processing",
  "estimatedCompletionTime": "2026-03-31T10:20:00Z"
}
```

### Polling (Optional)

AIP may poll job status instead of (or in addition to) waiting for the callback:

| From | To        | Method | Endpoint                     |
| ---- | --------- | ------ | ---------------------------- |
| AIP  | AI Engine | `GET`  | `/api/engine/status/{jobId}` |

---

## Stage 4: Multi-Agent AI Processing

### Components

| Component                        | Role                                                                                  |
| -------------------------------- | ------------------------------------------------------------------------------------- |
| **Orchestrator Agent** (🎯)      | Coordinates the multi-agent pipeline; manages agent sequencing and error handling     |
| **Document Analyzer Agent** (📄) | Parses document structure; identifies and extracts individual questions               |
| **Answer Generator Agent** (💡)  | For each question, performs RAG search + LLM generation to produce a traceable answer |
| **Knowledge Base** (📚)          | Centralized vector store + metadata registry; serves similarity search queries        |
| **LLM API** (☁️)                 | OpenAI / Azure OpenAI API for chat completion and embedding                           |
| **Document Writer Agent** (✍️)   | Writes generated answers back into the original document format                       |

### Flow

1. **Orchestrator** receives the job and delegates to **Document Analyzer**.
2. **Document Analyzer** parses the file, identifies all questions, and sends them to LLM for classification/extraction.
3. **Orchestrator** passes extracted questions to **Answer Generator**.
4. **Answer Generator** loops through each question:
   - Calls **Knowledge Base** for vector similarity search → retrieves relevant passages with source references.
   - Calls **LLM API** with question + retrieved context → generates an answer with confidence score.
5. **Orchestrator** passes all answers to **Document Writer**.
6. **Document Writer** inserts answers into the original document format and produces a source traceability report.

### Internal Invocation (Agent-to-Agent)

| From             | To               | Method           | Details                                              |
| ---------------- | ---------------- | ---------------- | ---------------------------------------------------- |
| Orchestrator     | Doc Analyzer     | Java method call | `analyzerAgent.analyze(document)`                    |
| Doc Analyzer     | LLM API          | HTTPS            | Chat completion — batch question identification      |
| Orchestrator     | Answer Generator | Java method call | `generatorAgent.generate(questions)`                 |
| Answer Generator | Knowledge Base   | Java method call | `vectorStore.similaritySearch(query, topK)`          |
| Answer Generator | LLM API          | HTTPS            | Chat completion — answer generation with RAG context |
| Orchestrator     | Doc Writer       | Java method call | `writerAgent.write(document, answers)`               |

### Data Produced — Per Question

```json
{
  "questionId": "q-007",
  "questionText": "Describe your data encryption policy at rest and in transit.",
  "answerText": "All data at rest is encrypted using AES-256...",
  "confidenceScore": 0.82,
  "sources": [
    {
      "documentName": "security-policies.md",
      "chunkText": "Data encryption: All sensitive data must be encrypted at rest using AES-256...",
      "similarityScore": 0.91,
      "uploadedAt": "2026-01-15"
    }
  ]
}
```

---

## Stage 5: Confidence-Based Approval Routing

### Components

| Component                       | Role                                                                                        |
| ------------------------------- | ------------------------------------------------------------------------------------------- |
| **AIP — Callback Handler**      | Receives the completed result from AI Engine via callback URL                               |
| **Approval Policy Engine** (📋) | Evaluates each answer's confidence score + topic sensitivity to determine the approval path |

### Flow

1. **AI Engine** calls the AIP callback URL with the completed document, confidence scores, and source logs.
2. **AIP Callback Handler** parses the result and invokes the **Approval Policy Engine**.
3. **Policy Engine** evaluates each answer and determines the routing tier:

| Condition                                               | Routing Decision  | Action                                                  |
| ------------------------------------------------------- | ----------------- | ------------------------------------------------------- |
| All answers ≥ 85% confidence AND no sensitive topics    | **AUTO-APPROVE**  | Mark as AI-generated (auditable), skip human review     |
| Any answer between 60%–85% confidence                   | **SINGLE REVIEW** | Assign to one primary reviewer                          |
| Any answer < 60% confidence OR sensitive topic detected | **ESCALATION**    | Assign to senior reviewer + domain expert (dual review) |

### Invocation

| From      | To            | Method                        | Endpoint / Details                                 |
| --------- | ------------- | ----------------------------- | -------------------------------------------------- |
| AI Engine | AIP           | `POST`                        | `{callbackUrl}` — delivers `EngineCallbackPayload` |
| AIP       | Policy Engine | Internal                      | Rule evaluation per answer                         |
| AIP       | Reviewer      | Internal / Email notification | Task assignment with draft + sources               |

### Callback Payload — `EngineCallbackPayload`

```json
{
  "jobId": "job-20260331-001",
  "status": "COMPLETED",
  "outputFile": "<base64-encoded>",
  "outputFileName": "Q4_Security_Questionnaire_filled.docx",
  "stats": {
    "totalQuestionsFound": 42,
    "questionsAnswered": 40,
    "questionsSkipped": 2,
    "avgConfidenceScore": 0.78,
    "processingTimeMs": 45200
  },
  "sourceLogs": [ ... ]
}
```

### Policy Configuration (YAML)

```yaml
approval:
  tiers:
    auto-approve:
      min-confidence: 0.85
      exclude-topics: [financial, legal, PII]
    single-review:
      min-confidence: 0.60
      reviewer-pool: primary-reviewers
    escalation:
      below-confidence: 0.60
      sensitive-topics: [financial, legal, PII]
      reviewer-pool: senior-reviewers
  timeout:
    review-sla: 24h
    escalation-sla: 4h
```

---

## Stage 6: Human Review & Feedback Collection

### Components

| Component               | Role                                                                                 |
| ----------------------- | ------------------------------------------------------------------------------------ |
| **Reviewer** (👤)       | Human reviewer who evaluates AI-generated answers against source references          |
| **AIP — Review Portal** | Web UI presenting the draft document, each answer's confidence, and source citations |
| **Feedback Store** (📊) | Persistent store logging every review decision for analytics and KB improvement      |

### Flow

1. **Reviewer** receives a task notification (email / portal alert) with the draft document.
2. For each flagged answer, the reviewer sees:
   - The AI-generated answer
   - Confidence score
   - Source passages used (with document name + similarity score)
3. Reviewer makes a decision per answer:

| Decision   | Action                                      | Feedback Logged                                          |
| ---------- | ------------------------------------------- | -------------------------------------------------------- |
| **Accept** | Answer approved as-is                       | `ACCEPTED {questionId, confidence}`                      |
| **Edit**   | Reviewer provides corrected answer + reason | `EDITED {questionId, original, corrected, reason}`       |
| **Reject** | Answer rejected entirely                    | `REJECTED {questionId, reason}` → triggers re-generation |

4. If **Rejected**: AIP sends the question back to the AI Engine with additional context (reviewer's reason, supplementary instructions). The revised answer is re-routed to the reviewer.

### Invocation

| From     | To             | Method                       | Details                                           |
| -------- | -------------- | ---------------------------- | ------------------------------------------------- |
| AIP      | Reviewer       | Email + Portal               | Task notification with review link                |
| Reviewer | AIP            | HTTPS (Portal UI)            | Submit decision per answer                        |
| AIP      | Feedback Store | Internal                     | Log decision with full context                    |
| AIP      | AI Engine      | `POST` `/api/engine/process` | Re-generate rejected answers (with extra context) |

### Feedback Record Schema

```json
{
  "feedbackId": "fb-20260331-042",
  "jobId": "job-20260331-001",
  "questionId": "q-007",
  "reviewerId": "reviewer-jane",
  "decision": "EDITED",
  "originalAnswer": "All data at rest is encrypted using AES-256...",
  "correctedAnswer": "All data at rest is encrypted using AES-256. In transit, TLS 1.3 is enforced...",
  "reason": "Missing in-transit encryption details",
  "confidence": 0.82,
  "reviewedAt": "2026-03-31T11:05:00Z"
}
```

---

## Stage 7: Finalize & Deliver

### Components

| Component                       | Role                                                                               |
| ------------------------------- | ---------------------------------------------------------------------------------- |
| **AIP — Document Finalizer**    | Merges all approved/edited answers into the final document                         |
| **AIP — Audit Trail Generator** | Produces a traceability report: which answers were auto-approved, reviewed, edited |
| **AIP — Output Router**         | Delivers the final document via the configured outbound channel                    |
| **Channel Adapter (Outbound)**  | Sends the document back to the client via Email / SFTP / API / Portal              |

### Flow

1. **Document Finalizer** collects all approved answers (auto-approved + reviewer-approved + edits) and merges them into the final document.
2. **Audit Trail Generator** creates a traceability report containing:
   - Per-answer: source references, confidence score, review decision, reviewer ID
   - Overall: processing time, total questions, answer rate, avg confidence
3. **Output Router** determines the delivery channel (same channel as inbound, or configured override).
4. **Channel Adapter** delivers the filled document + traceability report to the client.

### Invocation

| From              | To              | Method                  | Details                                |
| ----------------- | --------------- | ----------------------- | -------------------------------------- |
| AIP Finalizer     | AIP Audit Trail | Internal                | Generate audit report                  |
| AIP Output Router | Channel Adapter | Internal (AIP pipeline) | Route to configured outbound channel   |
| Channel Adapter   | Client          | SMTP / SFTP / HTTPS     | Deliver final document + source report |

### Delivered Package

| File                                    | Description                                                           |
| --------------------------------------- | --------------------------------------------------------------------- |
| `Q4_Security_Questionnaire_filled.docx` | Original document with all answers filled in                          |
| `Q4_Security_Questionnaire_sources.pdf` | Traceability report: each answer's sources, confidence, review status |
| `audit-log.json` (internal)             | Full audit trail archived for compliance                              |

---

## Stage 8: Continuous Improvement & KB Auto-Update

### Components

| Component                  | Role                                                                                     |
| -------------------------- | ---------------------------------------------------------------------------------------- |
| **Feedback Store** (📊)    | Aggregates reviewer feedback; detects recurring correction patterns                      |
| **Knowledge Gap Analyzer** | Identifies topics where the KB lacks coverage (based on low confidence + repeated edits) |
| **KB Ingestion Pipeline**  | Validates, chunks, embeds, and indexes new/updated documents into the vector store       |
| **AIP — Auto-Ingest**      | Watches configured folders/channels for new reference documents to feed into KB          |
| **LLM API — Embedding**    | Generates vector embeddings for new/updated document chunks                              |

### Triggers

| Trigger              | Source           | Description                                                      |
| -------------------- | ---------------- | ---------------------------------------------------------------- |
| **Feedback-driven**  | Feedback Store   | Repeated corrections on similar topics → flag as knowledge gap   |
| **Correction-based** | Feedback Store   | High-quality reviewer edits → propose as new KB entries          |
| **Scheduled scan**   | Cron / Scheduler | Daily/weekly scan of watched folders for new/modified files      |
| **AIP auto-ingest**  | AIP channel      | New documents arriving via AIP channels flagged for KB ingestion |
| **Manual upload**    | Admin Portal     | Admin manually uploads new reference documents                   |

### Flow

1. **Feedback Store** periodically aggregates metrics:
   - Topics with frequent `EDITED` / `REJECTED` decisions → flag as **knowledge gap**.
   - High-quality corrections → propose as **new KB entries** (admin approval required).
2. **AIP Auto-Ingest** / **Scheduled Scanner** detects new or modified reference documents.
3. **KB Ingestion Pipeline** processes each document:
   - **Validate** format and content quality.
   - **Diff** against existing KB entries (new / updated / deleted).
   - **Chunk & split** into semantic segments.
   - **Embed** via LLM embedding API.
   - **Index** into vector store; update metadata registry.
4. **Post-update**: verify search quality with sample queries, notify admins, write audit log.

### Invocation

| From           | To              | Method               | Endpoint / Details                           |
| -------------- | --------------- | -------------------- | -------------------------------------------- |
| Feedback Store | KB Gap Analyzer | Internal (scheduled) | Aggregate metrics → detect gaps              |
| Feedback Store | KB Pipeline     | Internal             | Propose new entries from corrections         |
| AIP            | KB Pipeline     | `POST`               | `/api/kb/documents` — auto-ingest new files  |
| Admin          | KB Pipeline     | `POST`               | `/api/kb/documents` — manual upload          |
| KB Pipeline    | LLM API         | HTTPS                | Embedding API — generate vectors             |
| Admin          | KB Pipeline     | `POST`               | `/api/kb/rebuild` — full reindex (if needed) |

### KB Health Metrics

| Metric                     | Description                                   | Target    |
| -------------------------- | --------------------------------------------- | --------- |
| Coverage rate              | % of questions answered with confidence ≥ 0.7 | > 85%     |
| Gap count                  | Flagged knowledge gaps pending resolution     | < 10      |
| Freshness                  | Average age of KB documents                   | < 90 days |
| Feedback-to-update latency | Time from correction to KB update             | < 7 days  |

---

## Component Interaction Summary

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                            COMPLETE E2E DATA FLOW                              │
│                                                                                │
│  👤 Client                                                                     │
│    │                                                                           │
│    ▼                                                                           │
│  📧 Channel Adapter (Email / SFTP / API / Portal)                             │
│    │                                                                           │
│    ▼                                                                           │
│  🔄 AIP ─── Validate ─── Classify ─── Route                                   │
│    │                                                                           │
│    │  POST /api/engine/process                                                 │
│    ▼                                                                           │
│  🧠 AI Engine                                                                  │
│    ├─→ 📄 Doc Analyzer ──→ ☁️ LLM (question extraction)                       │
│    ├─→ 💡 Answer Generator ──→ 📚 KB (RAG search) ──→ ☁️ LLM (answer gen)     │
│    └─→ ✍️ Doc Writer (fill document)                                           │
│    │                                                                           │
│    │  POST {callbackUrl}                                                       │
│    ▼                                                                           │
│  🔄 AIP ─── 📋 Approval Policy                                                │
│    │         ├─ ≥85% non-sensitive → ✅ Auto-approve                           │
│    │         ├─ 60%-85%            → 👤 Single Review                          │
│    │         └─ <60% or sensitive  → 👥 Escalation                             │
│    │                                                                           │
│    ▼                                                                           │
│  👤 Reviewer ─── Accept / Edit / Reject                                        │
│    │                 │                                                          │
│    │                 ▼                                                          │
│    │           📊 Feedback Store                                               │
│    │                 │                                                          │
│    ▼                 │                                                          │
│  🔄 AIP ─── Merge ─── Audit Trail ─── Deliver                                 │
│    │                                                                           │
│    ▼                                                                           │
│  📧 Channel ──→ 👤 Client (filled document + source report)                   │
│                                                                                │
│  ┌─ 🔁 Continuous Improvement Loop ──────────────────────┐                     │
│  │  📊 Feedback Store → 📚 KB Gap Analyzer               │                     │
│  │  🔄 AIP Auto-Ingest → 📚 KB Ingestion Pipeline       │                     │
│  │  📚 KB Pipeline → ☁️ LLM (re-embed) → 📚 Vector Store │                     │
│  └────────────────────────────────────────────────────────┘                     │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## API Endpoint Reference

| Endpoint                     | Method | Direction               | Stage | Description                                  |
| ---------------------------- | ------ | ----------------------- | ----- | -------------------------------------------- |
| `/api/engine/process`        | POST   | AIP → AI Engine         | 3     | Submit document for async processing         |
| `/api/engine/status/{jobId}` | GET    | AIP → AI Engine         | 3     | Poll job status (optional)                   |
| `/api/engine/cancel/{jobId}` | POST   | AIP → AI Engine         | 3     | Cancel a running job                         |
| `{callbackUrl}`              | POST   | AI Engine → AIP         | 5     | Deliver completed result + confidence scores |
| `/api/kb/documents`          | POST   | AIP / Admin → AI Engine | 8     | Upload document to knowledge base            |
| `/api/kb/documents`          | GET    | Admin Portal            | 8     | List all KB documents                        |
| `/api/kb/documents/{id}`     | DELETE | Admin Portal            | 8     | Remove document from KB                      |
| `/api/kb/stats`              | GET    | Dashboard               | 8     | Knowledge base health statistics             |
| `/api/kb/rebuild`            | POST   | Admin Portal            | 8     | Rebuild vector index                         |
