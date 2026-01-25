sequenceDiagram
autonumber
participant User as 👤 User
participant UI as 🖥️ Web UI
participant Ctrl as 📡 AuditController
participant Orch as 🎯 OrchestratorAgent
participant Analyzer as 📄 DocumentAnalyzerAgent
participant Generator as 💡 AnswerGeneratorAgent
participant Writer as ✍️ DocumentWriterAgent
participant AI as 🤖 ChatClient (Copilot)
participant VS as 🗄️ VectorStore

    rect rgb(227, 242, 253)
        Note over User,UI: Document Upload
        User->>UI: Upload audit document (.docx/.xlsx)
        UI->>Ctrl: POST /download (file, skipExisting)
    end

    rect rgb(252, 228, 236)
        Note over Ctrl,Orch: Agent Orchestration
        Ctrl->>Orch: processDocument(file, skipExisting)
        Note over Orch: Create AgentContext<br/>with document bytes
    end

    rect rgb(255, 243, 224)
        Note over Orch,Analyzer: Step 1: Document Analysis
        Orch->>Analyzer: execute(context)
        Analyzer->>Analyzer: Extract candidates<br/>(Tables + Paragraphs)
        Analyzer->>AI: Batch analyze: Is this a question?
        AI-->>Analyzer: [{isQuestion, confidence}]
        Analyzer->>Analyzer: Filter confidence >= 0.6
        Analyzer-->>Orch: AgentResult.success<br/>(questions in context)
    end

    rect rgb(232, 234, 246)
        Note over Orch,Generator: Step 2: Answer Generation
        Orch->>Generator: execute(context)
        loop For each question
            Generator->>VS: similaritySearch(question, topK=5)
            VS-->>Generator: Relevant documents
            Generator->>AI: Generate answer (question + context)
            AI-->>Generator: Generated answer
        end
        Generator-->>Orch: AgentResult.success<br/>(answers in context)
    end

    rect rgb(255, 248, 225)
        Note over Orch,Writer: Step 3: Document Writing
        Orch->>Writer: execute(context)
        alt WORD document
            Writer->>Writer: Fill Word table cells
        else EXCEL document
            Writer->>Writer: Fill Excel cells
        end
        Writer-->>Orch: AgentResult.success<br/>(output document)
    end

    rect rgb(200, 230, 201)
        Note over Orch,User: Response
        Orch-->>Ctrl: ByteArrayOutputStream
        Ctrl-->>UI: HTTP Response (binary)
        UI-->>User: Download file<br/>(filename_answered.docx)
    end
