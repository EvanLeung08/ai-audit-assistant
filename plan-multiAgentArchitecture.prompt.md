# Plan: Multi-Agent Collaboration Architecture

**STATUS: IMPLEMENTATION COMPLETE**

This plan transforms the audit assistant from a monolithic service-based architecture to a multi-agent collaboration architecture.

---

## Architecture Overview

```
                    ┌─────────────────────────────────────────┐
                    │         Orchestrator Agent              │
                    │   (Coordinates workflow, manages state)  │
                    └─────────────────┬───────────────────────┘
                                      │
            ┌─────────────────────────┼─────────────────────────┐
            │                         │                         │
            ▼                         ▼                         ▼
┌───────────────────┐   ┌───────────────────┐   ┌───────────────────┐
│ Document Analyzer │   │  Answer Generator │   │  Document Writer  │
│      Agent        │   │      Agent        │   │      Agent        │
│                   │   │                   │   │                   │
│ • Analyzes docs   │   │ • RAG with KB     │   │ • Writes answers  │
│ • Extracts Q&A    │   │ • Generates       │   │ • Preserves fmt   │
│ • AI fallback     │   │   answers         │   │ • Multi-format    │
└───────────────────┘   └───────────────────┘   └───────────────────┘
```

---

## Created Files

### Core Agent Interfaces
| File | Description |
|------|-------------|
| `agent/AuditAgent.java` | Base interface for all agents |
| `agent/AgentContext.java` | Shared context between agents |
| `agent/AgentResult.java` | Result of agent execution |

### Agent Implementations
| File | Description |
|------|-------------|
| `agent/impl/DocumentAnalyzerAgent.java` | Analyzes document structure, extracts questions using rules + AI |
| `agent/impl/AnswerGeneratorAgent.java` | Generates answers using RAG from knowledge base |
| `agent/impl/DocumentWriterAgent.java` | Writes answers back to Word/Excel documents |
| `agent/impl/OrchestratorAgent.java` | Coordinates the workflow between all agents |

### Service Integration
| File | Description |
|------|-------------|
| `service/impl/AgentBasedAuditProcessServiceImpl.java` | New service implementation using agents |

---

## Configuration

Enable multi-agent mode in `application.yml`:

```yaml
audit:
  # Enable multi-agent collaboration mode
  use-agent-mode: true
```

When `use-agent-mode: true`:
- Uses `AgentBasedAuditProcessServiceImpl` (marked as `@Primary`)
- Agents collaborate via shared `AgentContext`
- AI-assisted document analysis for unknown formats

When `use-agent-mode: false` (or not set):
- Uses original `AuditProcessServiceImpl`
- Rule-based document processing
- Backward compatible

---

## Key Features

### 1. DocumentAnalyzerAgent
- **Rule-based extraction**: Tries predefined patterns first
- **AI fallback**: Uses LLM to analyze document structure when rules fail
- **Multi-format support**: Word (.docx) and Excel (.xlsx, .xls)
- **Smart column detection**: Identifies question/answer columns automatically

### 2. AnswerGeneratorAgent
- **RAG-based**: Retrieves relevant context from knowledge base
- **Source tracking**: Logs which documents were used for answers
- **Skip existing**: Option to preserve existing answers
- **Clean formatting**: Removes markdown from AI responses

### 3. DocumentWriterAgent
- **Format preservation**: Maintains original document structure
- **Visual distinction**: Writes AI answers in blue color
- **Multi-format output**: Supports Word and Excel

### 4. OrchestratorAgent
- **Pipeline coordination**: Executes agents in sequence
- **Error handling**: Reports which step failed
- **Timing**: Tracks total processing time
- **Context management**: Sets up and manages shared context

---

## Agent Communication

Agents communicate via `AgentContext` with these keys:

| Key | Type | Description |
|-----|------|-------------|
| `KEY_DOCUMENT_BYTES` | `byte[]` | Original document content |
| `KEY_DOCUMENT_TYPE` | `String` | "WORD" or "EXCEL" |
| `KEY_FILE_NAME` | `String` | Original file name |
| `KEY_QUESTIONS` | `List<AuditQuestion>` | Extracted questions |
| `KEY_ANSWERED_QUESTIONS` | `List<AuditQuestion>` | Questions with answers |
| `KEY_OUTPUT_DOCUMENT` | `ByteArrayOutputStream` | Final document |
| `KEY_SKIP_EXISTING` | `Boolean` | Skip existing answers flag |

---

## Benefits

1. **Flexibility**: Each agent can be upgraded independently
2. **Extensibility**: Easy to add new agents (e.g., QualityCheckerAgent)
3. **AI-powered analysis**: Better handling of unknown document formats
4. **Observability**: Clear logging of each step
5. **Testability**: Agents can be unit tested in isolation
6. **Maintainability**: Separation of concerns

---

## Usage

The system is fully backward compatible:
- Set `audit.use-agent-mode: true` to use agents
- Set `audit.use-agent-mode: false` to use original service

Both modes produce identical output for supported document formats.
