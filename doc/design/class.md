classDiagram
class AuditAgent {
<<interface>>
+getName() String
+getDescription() String
+execute(AgentContext) AgentResult
+canHandle(AgentContext) boolean
}

    class AgentContext {
        -Map~String,Object~ context
        +set(String key, Object value)
        +get(String key) T
        +KEY_DOCUMENT_BYTES$
        +KEY_FILE_NAME$
        +KEY_DOCUMENT_TYPE$
        +KEY_QUESTIONS$
        +KEY_ANSWERS$
        +KEY_OUTPUT_DOCUMENT$
    }
    
    class AgentResult {
        <<record>>
        +boolean success
        +String message
        +Map~String,Object~ data
        +success(String, Map) AgentResult$
        +failure(String) AgentResult$
    }
    
    class OrchestratorAgent {
        -DocumentAnalyzerAgent analyzerAgent
        -AnswerGeneratorAgent generatorAgent
        -DocumentWriterAgent writerAgent
        +processDocument(MultipartFile, boolean) ByteArrayOutputStream
        +execute(AgentContext) AgentResult
    }
    
    class DocumentAnalyzerAgent {
        -ChatClient chatClient
        -ObjectMapper objectMapper
        +execute(AgentContext) AgentResult
        -extractFromTables(XWPFDocument) List~CandidateQuestion~
        -extractFromParagraphs(XWPFDocument) List~CandidateQuestion~
        -analyzeBatchWithAI(List~CandidateQuestion~) List~AuditQuestion~
    }
    
    class AnswerGeneratorAgent {
        -ChatClient chatClient
        -VectorStore vectorStore
        -KnowledgeBaseService knowledgeBaseService
        +execute(AgentContext) AgentResult
        -generateAnswer(String question, List~Document~ context) String
    }
    
    class DocumentWriterAgent {
        -WordDocumentService wordService
        -ExcelDocumentService excelService
        +execute(AgentContext) AgentResult
        -writeToWord(byte[], List~AuditQuestion~) ByteArrayOutputStream
        -writeToExcel(byte[], List~AuditQuestion~) ByteArrayOutputStream
    }
    
    AuditAgent <|.. OrchestratorAgent
    AuditAgent <|.. DocumentAnalyzerAgent
    AuditAgent <|.. AnswerGeneratorAgent
    AuditAgent <|.. DocumentWriterAgent
    
    OrchestratorAgent --> DocumentAnalyzerAgent : Step 1
    OrchestratorAgent --> AnswerGeneratorAgent : Step 2
    OrchestratorAgent --> DocumentWriterAgent : Step 3
    
    DocumentAnalyzerAgent ..> AgentContext : uses
    AnswerGeneratorAgent ..> AgentContext : uses
    DocumentWriterAgent ..> AgentContext : uses
    
    DocumentAnalyzerAgent ..> AgentResult : returns
    AnswerGeneratorAgent ..> AgentResult : returns
    DocumentWriterAgent ..> AgentResult : returns
