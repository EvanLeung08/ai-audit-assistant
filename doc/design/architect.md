---
title: Evan AI Audit Assistant - Complete System Architecture
---
flowchart TB
subgraph External["🌐 External Systems"]
subgraph GitHub["GitHub OAuth"]
DeviceFlow["Device Flow API"]
OAuthToken["OAuth Token API"]
end
subgraph CopilotAPI["GitHub Copilot API"]
TokenAPI["/copilot_internal/v2/token"]
ChatAPI["/chat/completions"]
EmbedAPI["/embeddings"]
end
end

    subgraph Presentation["🖥️ Presentation Layer"]
        WebUI["Web UI (index.html)"]
        Templates["Thymeleaf Templates"]
    end

    subgraph Controllers["📡 Controller Layer"]
        AuditCtrl["AuditController<br/>POST /download<br/>POST /upload"]
        AuthCtrl["CopilotAuthController<br/>GET /api/auth/status<br/>POST /api/auth/device/*"]
        KBCtrl["KnowledgeBaseController<br/>POST /api/knowledge-base/upload<br/>GET /api/knowledge-base/documents"]
    end

    subgraph AgentLayer["🤖 Multi-Agent Orchestration Layer"]
        subgraph AgentCore["Agent Framework"]
            IAuditAgent["«interface»<br/>AuditAgent"]
            AgentContext["AgentContext<br/>• KEY_DOCUMENT_BYTES<br/>• KEY_QUESTIONS<br/>• KEY_ANSWERS<br/>• KEY_OUTPUT_DOCUMENT"]
            AgentResult["AgentResult<br/>• success<br/>• message<br/>• data"]
        end
        
        subgraph Agents["Agent Implementations"]
            Orchestrator["🎯 OrchestratorAgent<br/>Workflow Coordinator"]
            DocAnalyzer["📄 DocumentAnalyzerAgent<br/>AI-Powered Analysis<br/>Multi-Strategy Extraction"]
            AnswerGen["💡 AnswerGeneratorAgent<br/>RAG-Based Answering<br/>Knowledge Retrieval"]
            DocWriter["✍️ DocumentWriterAgent<br/>Document Population<br/>Format Preservation"]
        end
    end

    subgraph Services["⚙️ Service Layer"]
        subgraph DocServices["Document Services"]
            IWordSvc["WordDocumentService"]
            IExcelSvc["ExcelDocumentService"]
            WordImpl["WordDocumentServiceImpl"]
            ExcelImpl["ExcelDocumentServiceImpl"]
        end
        
        subgraph AuditSvc["Audit Services"]
            IAuditProcess["AuditProcessService"]
            IAuditAnswer["AuditAnswerService"]
            AuditProcessImpl["AuditProcessServiceImpl"]
            AuditAnswerImpl["AuditAnswerServiceImpl"]
        end
        
        subgraph KBSvc["Knowledge Base"]
            IKBService["KnowledgeBaseService"]
            KBServiceImpl["KnowledgeBaseServiceImpl"]
        end
        
        subgraph CopilotSvc["Copilot Auth Services"]
            TokenSvc["CopilotTokenService<br/>Cookie Persistence<br/>Auto Refresh"]
            DeviceAuthSvc["GitHubDeviceAuthService"]
            ProxySvc["ProxyService"]
            CookieSvc["TokenCookieService"]
        end
    end

    subgraph AILayer["🧠 AI & Vector Store Layer"]
        subgraph SpringAI["Spring AI Integration"]
            ChatModel["OpenAiChatModel"]
            EmbedModel["OpenAiEmbeddingModel"]
            ChatClient["ChatClient"]
        end
        
        subgraph VectorStore["Vector Store"]
            PersistVS["PersistentVectorStore<br/>JSON Storage<br/>Similarity Search"]
        end
    end

    subgraph Storage["💾 Persistent Storage"]
        VSFile[("vectorstore.json")]
        TokenFile[("oauth-token.json")]
        UploadsDir[("uploads/")]
    end

    subgraph Config["⚙️ Configuration"]
        CopilotConfig["CopilotOpenAiConfig<br/>Dynamic Token Auth"]
        CopilotProps["CopilotProperties"]
        KBConfig["KnowledgeBaseConfig"]
        AuditProps["AuditProperties"]
    end

    subgraph Models["📦 Domain Models"]
        AuditQ["AuditQuestion"]
        ProcessResult["AuditProcessResult"]
        SourceLog["AnswerSourceLog"]
        CopilotToken["CopilotToken"]
    end

    %% Presentation to Controllers
    WebUI --> AuditCtrl
    WebUI --> AuthCtrl
    WebUI --> KBCtrl

    %% Controllers to Services/Agents
    AuditCtrl --> Orchestrator
    AuthCtrl --> TokenSvc
    AuthCtrl --> DeviceAuthSvc
    AuthCtrl --> ProxySvc
    KBCtrl --> IKBService

    %% Agent Orchestration Flow
    Orchestrator --> DocAnalyzer
    DocAnalyzer --> AnswerGen
    AnswerGen --> DocWriter

    %% Agents use AI
    DocAnalyzer --> ChatClient
    AnswerGen --> ChatClient
    AnswerGen --> PersistVS

    %% Document Writer uses Document Services
    DocWriter --> WordImpl
    DocWriter --> ExcelImpl

    %% Knowledge Base
    KBServiceImpl --> PersistVS
    KBServiceImpl --> EmbedModel
    AuditAnswerImpl --> PersistVS
    AuditAnswerImpl --> ChatClient

    %% Token & Auth
    TokenSvc --> CookieSvc
    TokenSvc --> TokenAPI
    DeviceAuthSvc --> DeviceFlow
    DeviceAuthSvc --> OAuthToken
    DeviceAuthSvc --> ProxySvc

    %% Config
    CopilotConfig --> TokenSvc
    ChatModel --> CopilotConfig
    EmbedModel --> CopilotConfig

    %% AI to External
    ChatModel --> ChatAPI
    EmbedModel --> EmbedAPI

    %% Storage
    PersistVS --> VSFile
    KBServiceImpl --> UploadsDir

    %% Styling
    classDef external fill:#e8f5e9,stroke:#4caf50
    classDef presentation fill:#e3f2fd,stroke:#2196f3
    classDef controller fill:#fff3e0,stroke:#ff9800
    classDef agent fill:#fce4ec,stroke:#e91e63
    classDef service fill:#e8eaf6,stroke:#3f51b5
    classDef ai fill:#fff8e1,stroke:#ffc107
    classDef storage fill:#ffd54f,stroke:#f57c00
    classDef config fill:#eceff1,stroke:#607d8b

    class DeviceFlow,OAuthToken,TokenAPI,ChatAPI,EmbedAPI external
    class WebUI,Templates presentation
    class AuditCtrl,AuthCtrl,KBCtrl controller
    class Orchestrator,DocAnalyzer,AnswerGen,DocWriter,IAuditAgent,AgentContext,AgentResult agent
    class TokenSvc,DeviceAuthSvc,ProxySvc,CookieSvc,IWordSvc,IExcelSvc,WordImpl,ExcelImpl,IAuditProcess,IAuditAnswer,AuditProcessImpl,AuditAnswerImpl,IKBService,KBServiceImpl service
    class ChatModel,EmbedModel,ChatClient,PersistVS ai
    class VSFile,TokenFile,UploadsDir storage
    class CopilotConfig,CopilotProps,KBConfig,AuditProps config
