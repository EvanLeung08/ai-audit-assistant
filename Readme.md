# AI Audit Assistant

基于 Spring AI 和 RAG（检索增强生成）技术的智能审计问答系统。该系统可以自动处理包含审计问题的 Word 文档，利用知识库生成答案并填充到文档中。

## 功能特点

- 📄 **Word 文档解析**：支持 .docx 格式，自动识别表格和文本格式的问题
- 🔍 **RAG 知识检索**：基于向量数据库的相似度搜索，从知识库中检索相关内容
- 🤖 **AI 智能回答**：使用 GPT-4o 模型基于知识库内容生成专业答案
- 📝 **自动填充答案**：将生成的答案写回到 Word 文档对应位置
- 🌐 **友好的 Web 界面**：支持拖拽上传，实时处理反馈

## 技术栈

- **后端框架**: Spring Boot 3.4.1
- **AI 框架**: Spring AI 1.1.2
- **向量存储**: SimpleVectorStore (内存)
- **文档处理**: Apache POI 5.2.5
- **前端**: Thymeleaf + HTMX + TailwindCSS
- **Java 版本**: 21

## 快速开始

### 1. 配置认证方式

本项目支持两种认证方式访问 AI API：

#### 方式一：GitHub Copilot OAuth（推荐，默认）

如果你有 GitHub Copilot 订阅，可以使用 GitHub OAuth 认证自动获取 API Token：

1. **自动检测本地 Token**：如果你已经在 VS Code 中登录过 GitHub Copilot，系统会自动读取 `~/.config/github-copilot/hosts.json` 中的 OAuth Token。

2. **设备流认证**：如果没有本地 Token，可以通过 API 进行设备流认证：
   ```bash
   # 启动设备认证流程
   curl -X POST http://localhost:8080/api/auth/device/start
   
   # 返回类似：
   # {
   #   "userCode": "ABCD-1234",
   #   "verificationUri": "https://github.com/login/device",
   #   "deviceCode": "..."
   # }
   
   # 访问 verificationUri 并输入 userCode 完成认证
   # 然后轮询获取 Token：
   curl -X POST http://localhost:8080/api/auth/device/poll \
     -H "Content-Type: application/json" \
     -d '{"deviceCode": "..."}'
   ```

3. **手动设置 OAuth Token**：
   ```bash
   curl -X POST http://localhost:8080/api/auth/token \
     -H "Content-Type: application/json" \
     -d '{"token": "YOUR_GITHUB_OAUTH_TOKEN"}'
   ```

4. **检查认证状态**：
   ```bash
   curl http://localhost:8080/api/auth/status
   ```

#### 方式二：传统 API Key

编辑 `src/main/resources/application.yml`，切换到环境变量模式：

```yaml
copilot:
  auth-mode: env  # 切换为环境变量模式

spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      base-url: https://api.openai.com/v1  # 或其他兼容的 API 端点
```

### 2. 添加知识库文档

将你的知识库文档放置在 `src/main/resources/knowledge-base/` 目录下。支持的格式：
- Markdown (.md)
- Text (.txt)
- PDF (.pdf)
- Word (.docx)

在 `application.yml` 中配置知识库路径：

```yaml
audit:
  knowledge-base:
    documents:
      - classpath:knowledge-base/your-document.md
      - classpath:knowledge-base/another-document.pdf
```

### 3. 运行项目

```bash
cd evan-ai-audit-assistant
mvn spring-boot:run
```

访问 http://localhost:8080

## 支持的文档格式

### 表格格式（推荐）

| 序号 | 问题 | 答案 |
|------|------|------|
| 1 | 什么是内部审计的主要目标？ | （待填充） |
| 2 | 如何确保审计独立性？ | （待填充） |

或者两列格式：

| 问题 | 答案 |
|------|------|
| 问题内容... | （待填充） |

### 文本格式

使用标记符号：

```
[Q] 什么是风险评估的基本步骤？
[A] 

[Question] How to ensure data security?
[Answer] 

问题：如何进行合规性检查？
答案：
```

## API 接口

### 认证接口

#### 检查认证状态
```bash
GET /api/auth/status

Response: {"authenticated": true, "tokenExpired": false, "expiresAt": 1234567890}
```

#### 启动设备流认证
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

#### 轮询设备认证结果
```bash
POST /api/auth/device/poll
Content-Type: application/json

Body: {"deviceCode": "..."}

Response: {"success": true, "message": "Authentication successful!"}
# 或 {"success": false, "pending": true, "message": "Waiting for user authorization..."}
```

#### 手动设置 OAuth Token
```bash
POST /api/auth/token
Content-Type: application/json

Body: {"token": "YOUR_GITHUB_OAUTH_TOKEN"}
```

#### 测试当前 Token
```bash
POST /api/auth/test

Response: {"success": true, "message": "Token is valid", "tokenPrefix": "tid=...", "expiresAt": 1234567890}
```

### 处理文档（获取结果统计）

```bash
POST /api/audit/process
Content-Type: multipart/form-data

Parameters:
- file: Word 文档文件
- skipExisting: 是否跳过已有答案的问题 (true/false)
```

### 处理并下载文档

```bash
POST /api/audit/download
Content-Type: multipart/form-data

Parameters:
- file: Word 文档文件
- skipExisting: 是否跳过已有答案的问题 (true/false)

Response: 填充答案后的 Word 文档
```

## 项目结构

```
evan-ai-audit-assistant/
├── src/main/java/org/evan/ai/audit/
│   ├── AuditAssistantApplication.java    # 应用入口
│   ├── config/
│   │   ├── KnowledgeBaseConfig.java      # 知识库配置
│   │   ├── CopilotProperties.java        # Copilot 配置属性
│   │   └── CopilotOpenAiConfig.java      # Copilot OpenAI 客户端配置
│   ├── controller/
│   │   ├── AuditController.java          # REST 控制器
│   │   └── CopilotAuthController.java    # GitHub 认证控制器
│   ├── exception/
│   │   └── GlobalExceptionHandler.java   # 全局异常处理
│   ├── model/
│   │   ├── AuditQuestion.java            # 问题模型
│   │   ├── AuditProcessResult.java       # 处理结果模型
│   │   ├── CopilotToken.java             # Copilot Token 模型
│   │   ├── DeviceCodeResponse.java       # 设备流响应模型
│   │   └── DeviceTokenResponse.java      # Token 响应模型
│   └── service/
│       ├── AuditAnswerService.java       # AI 问答服务接口
│       ├── AuditProcessService.java      # 处理服务接口
│       ├── WordDocumentService.java      # 文档服务接口
│       ├── copilot/
│       │   ├── CopilotTokenService.java  # Copilot Token 管理
│       │   └── GitHubDeviceAuthService.java # GitHub 设备流认证
│       └── impl/
│           ├── AuditAnswerServiceImpl.java
│           ├── AuditProcessServiceImpl.java
│           └── WordDocumentServiceImpl.java
├── src/main/resources/
│   ├── application.yml                   # 应用配置
│   ├── knowledge-base/                   # 知识库文档目录
│   │   ├── audit-guidelines.md
│   │   ├── compliance-rules.md
│   │   └── security-policies.md
│   └── templates/
│       ├── index.html                    # 主页面
│       └── fragments/
│           └── result.html               # 结果片段
└── pom.xml
```

## 扩展知识库

1. 将新的知识文档添加到 `src/main/resources/knowledge-base/` 目录
2. 在 `application.yml` 中添加文档路径
3. 重启应用程序

## 自定义问题标记

在 `application.yml` 中配置自定义的问题/答案标记：

```yaml
audit:
  document:
    question-markers:
      - "[Q]"
      - "[Question]"
      - "问题："
      - "Q:"
    answer-markers:
      - "[A]"
      - "[Answer]"
      - "答案："
      - "A:"
```

## 注意事项

1. **文档大小限制**：默认最大上传文件大小为 50MB
2. **处理时间**：处理时间取决于问题数量，每个问题大约需要 2-5 秒
3. **答案标识**：AI 生成的答案将以蓝色字体显示，便于区分
4. **知识库更新**：添加新知识库文档后需要重启应用

## License

MIT License
