# AI SWTBot Assistant - Project Documentation

## Table of Contents
1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Core Components](#core-components)
4. [Self-Healing Agent System](#self-healing-agent-system)
5. [File Structure](#file-structure)
6. [Data Flow](#data-flow)
7. [Configuration](#configuration)
8. [Usage Guide](#usage-guide)

---

## Overview

**AI SWTBot Assistant** là một Eclipse plugin giúp tự động hóa việc tạo test cases cho e² studio IDE sử dụng SWTBot framework.

### Key Features
- **Jira Integration**: Fetch test cases từ Jira/Zephyr với PAT authentication
- **AI Code Generation**: Sử dụng LLM (OpenAI-compatible API) để generate SWTBot test code
- **Self-Healing Agent**: Tự động phát hiện và sửa lỗi trong code được generate
- **Workspace Indexing**: Tìm kiếm và đề xuất helper methods từ codebase hiện tại
- **Two-Column Display**: Hiển thị test steps trong bảng 2 cột (Step | Expected Result)

### Tech Stack
- **Platform**: Eclipse RCP (Rich Client Platform)
- **UI Framework**: SWT (Standard Widget Toolkit)
- **Language**: Java 21
- **HTTP Client**: Java 11 HttpClient
- **JSON Parsing**: Gson 2.10.1
- **AI API**: OpenAI-compatible (REST API)

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           ECLIPSE IDE                                    │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐                 │
│  │  TicketView  │  │ GenerateCmd  │  │  Preference  │                 │
│  │  (UI View)   │  │   (Handler)  │  │    (Page)    │                 │
│  └──────┬───────┘  └──────┬───────┘  └──────────────┘                 │
│         │                 │                                            │
│         └────────┬────────┘                                            │
│                  ▼                                                      │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                    AGENT INTEGRATION LAYER                       │   │
│  │  ┌─────────────────────────────────────────────────────────┐   │   │
│  │  │              SelfHealingAgent                          │   │   │
│  │  │  ┌─────────┐ ┌──────────┐ ┌─────────┐ ┌──────────────┐  │   │   │
│  │  │  │ Generate│→│ Validate │→│ Analyze │→│    Fix       │  │   │   │
│  │  │  │  (LLM)  │  │  (AST)   │  │(Errors) │  │(Auto-fix)   │  │   │   │
│  │  │  └─────────┘ └──────────┘ └─────────┘ └──────────────┘  │   │   │
│  │  └─────────────────────────────────────────────────────────┘   │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                           │                                             │
│           ┌───────────────┼───────────────┐                             │
│           ▼               ▼               ▼                             │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐                  │
│  │   LlmClient  │ │ ZephyrClient │ │WorkspaceIndexer│                 │
│  │  (AI Service) │ │ (Jira API)   │ │ (Code Search)  │                 │
│  └──────────────┘ └──────────────┘ └──────────────┘                  │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## Core Components

### 1. UI Layer (`views/`)

#### `TicketView.java`
- **Purpose**: Main UI view hiển thị Jira ticket và test steps
- **Features**:
  - Input field cho Jira ticket key (e.g., RSC-23001)
  - "Fetch" button để lấy dữ liệu từ Jira
  - Two-column table hiển thị test steps
  - Status label hiển thị kết quả fetch
  - Generate button để tạo test code
- **Key Methods**:
  - `fetchTicket()`: Gọi ZephyrClient để fetch ticket
  - `displayTicket()`: Cập nhật UI với ticket data
  - `createStepsSection()`: Tạo table 2 cột

#### `PackageSelectionDialog.java`
- **Purpose**: Dialog để chọn package destination cho generated test
- **Features**: Tree view hiển thị cấu trúc package trong project

---

### 2. Commands (`commands/`)

#### `GenerateCommand.java`
- **Purpose**: Handler cho "Generate SWTBot Test" command
- **Flow**:
  1. Lấy ticket data từ TicketView
  2. Kiểm tra project và package selection
  3. Gọi AgentIntegration để generate code
  4. Tạo hoặc update test file
- **Integration Point**: Sử dụng `SelfHealingAgent` thay vì gọi LLM trực tiếp

---

### 3. Jira Integration (`jira/`)

#### `ZephyrClient.java`
- **Purpose**: Client để gọi Jira REST API và Zephyr API
- **Authentication**: Bearer Token (PAT - Personal Access Token)
- **Endpoints**:
  - Jira Issue API: `/rest/api/2/issue/{key}`
  - Zephyr Test Steps: `/rest/zapi/latest/teststep/{id}`
- **Retry Logic**: Thử nhiều endpoint Zephyr khác nhau
- **Response Parsing**: Handle nhiều định dạng JSON khác nhau

#### `TicketData.java`
- **Purpose**: Data model cho Jira ticket
- **Fields**:
  - `key`: Ticket ID (e.g., RSC-23001)
  - `name`: Summary/title
  - `description`: Mô tả test case
  - `precondition`: Điều kiện tiên quyết
  - `steps`: List of `TestStep`
- **Nested Class**: `TestStep` (index, description, expectedResult)

---

### 4. LLM Integration (`llm/`)

#### `LlmClient.java`
- **Purpose**: Client gọi LLM API (OpenAI-compatible)
- **Constructor**: `LlmClient(String endpoint, String apiKey)`
- **Key Method**: 
  ```java
  String generate(String model, String systemPrompt, String userPrompt)
  ```
- **API Format**: OpenAI Chat Completions API
- **Timeout**: 30 seconds
- **Error Handling**: HTTP status check, JSON parsing

#### `SwtbotPromptBuilder.java`
- **Purpose**: Xây dựng prompts cho LLM
- **System Prompt**: Định nghĩa rules và output format
- **User Prompt**: Kết hợp ticket info + test steps + examples
- **Keyword Extraction**: Từ name, description, steps để tìm relevant examples

---

### 5. Workspace Indexing (`indexer/`)

#### `WorkspaceIndexer.java`
- **Purpose**: Tìm kiếm và index code trong workspace
- **Features**:
  - `findRelevantExamples()`: Tìm SWTBot test files liên quan
  - `extractHelperMethods()`: Trích xuất public methods từ helper classes
- **Scoring Algorithm**: Keyword matching với domain weights
- **Helper Detection**: Nhận diện classes có tên chứa "Helper", "Util", "Page", "Action"
- **Method Extraction**: Parse method signatures + javadoc

---

### 6. Preferences (`preferences/`)

#### `PreferenceConstants.java`
- **Purpose**: Define preference keys
- **Keys**:
  - `P_LLVM_ENDPOINT`: LLM API endpoint
  - `P_LLVM_API_KEY`: API key
  - `P_LLVM_MODEL`: Model name (e.g., gpt-4)
  - `P_JIRA_URL`: Jira base URL
  - `P_JIRA_TOKEN`: Jira PAT

#### `PreferencePage.java`
- **Purpose**: UI cho Eclipse preferences
- **Fields**: String editors cho tất cả settings

#### `PreferenceInitializer.java`
- **Purpose**: Set default values cho preferences

---

### 7. Utilities (`util/`)

#### `BuildValidator.java`
- **Purpose**: Runtime diagnostics cho Eclipse dependencies
- **Usage**: Gọi từ Activator để verify build
- **Checks**: Eclipse UI, SWT, JFace, Core Runtime, Gson

---

## Self-Healing Agent System

### Overview
Self-healing agent tự động phát hiện và sửa lỗi trong code được AI generate, giảm thiểu manual fixing.

### Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    SelfHealingAgent                           │
│                                                               │
│  Iteration 0: GENERATE                                        │
│  ├── Build prompt (ticket + examples)                        │
│  ├── Call LLM (LlmClient.generate)                           │
│  └── Initial code                                            │
│                          ↓                                    │
│  Iteration 1-3: VALIDATE → ANALYZE → FIX (loop max 3)         │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐           │
│  │  Syntax     │  │   Error     │  │   Code      │           │
│  │  Validator  │→ │  Analyzer   │→ │   Fixer     │           │
│  │             │  │             │  │             │           │
│  │ Check for:  │  │ Categorize: │  │ Auto-fix:   │           │
│  │ • Typos     │  │ • Undefined │  │ • Typos     │           │
│  │ • Imports   │  │   methods   │  │ • Imports   │           │
│  │ • Patterns  │  │ • Missing   │  │ • Patterns  │           │
│  └─────────────┘  │   imports   │  │ • Helpers   │           │
│                   └─────────────┘  └─────────────┘           │
│                          ↓                                    │
│  Final: Return code + healing log                             │
└─────────────────────────────────────────────────────────────┘
```

### Components

#### `SelfHealingAgent.java`
- **Orchestrator**: Điều phối quá trình generate → validate → fix
- **Max Iterations**: 3 lần retry
- **Result**: `GenerationResult` chứa code, log, success/fail status

#### `SyntaxValidator.java`
- **Purpose**: Lightweight validation (không cần full compilation)
- **Checks**:
  - Bot method typos (`bot.buton()` → `bot.button()`)
  - Missing imports (detect class names)
  - Shell patterns (cần `.activate()`)
  - Syntax structure (class declaration)

#### `ErrorAnalyzer.java`
- **Purpose**: Phân tích errors và xác định strategy
- **Output**: `ErrorAnalysis` với categorized errors

#### `ErrorAnalysis.java`
- **Data Model**: Kết quả phân tích
- **Categorization**: `UNDEFINED_METHOD`, `MISSING_IMPORT`, `SYNTAX_ERROR`
- **Extracted Info**: undefined methods, missing imports

#### `CompileError.java`
- **Data Model**: Single error
- **Fields**: line, column, message, type, offendingCode

#### `ValidationResult.java`
- **Data Model**: Kết quả validation
- **Fields**: valid (boolean), list of errors

#### `CodeFixer.java`
- **Purpose**: Tự động sửa lỗi
- **Fix Types**:
  1. **Typos**: Map<String, String> common corrections
  2. **Imports**: Thêm import statements
  3. **Shell Patterns**: Fix `bot.shell()` usage
  4. **Helper Suggestions**: Gợi ý DDRHelper, PinHelper, etc.

#### `FixResult.java`
- **Data Model**: Kết quả sau khi fix
- **Fields**: originalCode, fixedCode, appliedFixes, fixCount

#### `AgentIntegration.java`
- **Purpose**: Integration point với Eclipse UI
- **Settings**: Lấy từ preferences (endpoint, apiKey, model)
- **Progress**: Hỗ trợ IProgressMonitor

---

## File Structure

```
ai-swtbot-assistant/
├── .gitignore                          # Git ignore rules
├── SETUP.md                            # Build setup guide
├── PROJECT_DOCUMENTATION.md            # This file
│
├── com.renesas.swtbot.assistant/
│   ├── .classpath                      # Eclipse classpath
│   ├── .project                        # Eclipse project config
│   ├── plugin.xml                      # Eclipse plugin manifest
│   ├── build.properties                # Build configuration
│   ├── ai-swtbot-assistant.target      # Target platform (full)
│   ├── minimal.target                  # Target platform (minimal)
│   ├── META-INF/
│   │   └── MANIFEST.MF                 # OSGi bundle manifest
│   │
│   ├── .settings/                      # Eclipse preferences
│   │   ├── org.eclipse.jdt.core.prefs
│   │   ├── org.eclipse.pde.core.prefs
│   │   └── org.eclipse.pde.ds.annotations.prefs
│   │
│   └── src/com/renesas/swtbot/assistant/
│       │
│       ├── Activator.java              # Plugin entry point
│       │
│       ├── agent/                      # Self-healing system
│       │   ├── AgentIntegration.java   # Eclipse integration
│       │   ├── SelfHealingAgent.java # Main orchestrator
│       │   ├── analysis/             # Validation & analysis
│       │   │   ├── CompileError.java
│       │   │   ├── ErrorAnalysis.java
│       │   │   ├── ErrorAnalyzer.java
│       │   │   ├── SyntaxValidator.java
│       │   │   └── ValidationResult.java
│       │   └── fix/                  # Auto-fix logic
│       │       ├── CodeFixer.java
│       │       └── FixResult.java
│       │
│       ├── commands/                   # Eclipse commands
│       │   └── GenerateCommand.java    # Generate test handler
│       │
│       ├── indexer/                    # Code indexing
│       │   └── WorkspaceIndexer.java   # Search & extract helpers
│       │
│       ├── jira/                       # Jira integration
│       │   ├── ZephyrClient.java       # Jira/Zephyr API client
│       │   └── model/
│       │       └── TicketData.java     # Ticket data model
│       │
│       ├── llm/                        # LLM integration
│       │   ├── LlmClient.java          # LLM API client
│       │   └── SwtbotPromptBuilder.java # Prompt builder
│       │
│       ├── perspective/                # Eclipse perspective
│       │   └── AssistantPerspective.java
│       │
│       ├── preferences/                # Settings
│       │   ├── PreferenceConstants.java
│       │   ├── PreferenceInitializer.java
│       │   └── PreferencePage.java
│       │
│       ├── util/                       # Utilities
│       │   └── BuildValidator.java     # Build diagnostics
│       │
│       └── views/                      # UI views
│           ├── TicketView.java         # Main ticket view
│           └── PackageSelectionDialog.java
│
├── com.renesas.swtbot.assistant.feature/    # Eclipse feature
│   ├── feature.xml
│   ├── build.properties
│   └── .project
│
└── com.renesas.swtbot.assistant.updatesite/ # Update site
    ├── site.xml
    ├── build.properties
    └── .project
```

---

## Data Flow

### 1. Fetch Jira Ticket

```
User enters "RSC-23001" → TicketView.fetchTicket()
                                    ↓
                    ZephyrClient.fetchTestCase("RSC-23001")
                                    ↓
                    ┌───────────────┴───────────────┐
                    ↓                               ↓
            Jira Issue API              Zephyr Test Steps API
            (get summary, desc)         (get test steps)
                    ↓                               ↓
                    └───────────────┬───────────────┘
                                    ↓
                    TicketData (key, name, description, steps)
                                    ↓
                    TicketView.displayTicket()
                                    ↓
                    Update UI (titleLabel, stepsTable)
```

### 2. Generate Test with Self-Healing

```
User clicks "Generate" → GenerateCommand.execute()
                                    ↓
                    AgentIntegration.generateTestWithHealing()
                                    ↓
                    ┌───────────────┴───────────────┐
                    ↓                               ↓
            SelfHealingAgent              (Progress Monitor)
            .generateAndFix()              UI updates
                    ↓
    ┌───────────┬───────────┬───────────┐
    ↓           ↓           ↓           ↓
PromptBuilder  LlmClient  SyntaxValidator  CodeFixer
(build prompts) (generate)   (validate)     (fix)
    └───────────┴───────────┴───────────┘
                    ↓
            Loop (max 3 iterations)
                    ↓
            GenerationResult
            (code + healing log)
                    ↓
            Write to file / Show preview
```

### 3. Index Workspace for Examples

```
GenerateCommand needs examples
            ↓
WorkspaceIndexer.findRelevantExamples()
            ↓
    ┌───────┴───────┐
    ↓               ↓
Scan test folder  Find SWTBot files
    ↓               ↓
Score by keywords  (DDR, pin, board...)
    ↓
Return top 2 files
    ↓
Include in LLM prompt
```

---

## Configuration

### Eclipse Preferences (Window → Preferences → AI SWTBot Assistant)

| Setting | Key | Example |
|---------|-----|---------|
| LLM Endpoint | `llm.endpoint` | `https://api.openai.com/v1` |
| LLM API Key | `llm.apikey` | `sk-...` |
| LLM Model | `llm.model` | `gpt-4` |
| Jira URL | `jira.url` | `https://jira.renesas.eu` |
| Jira Token | `jira.token` | `Bearer ...` |

### Target Platform

**File**: `minimal.target`

Components:
- Eclipse Platform 4.31
- JDT (Java Development Tools)
- PDE (Plugin Development Environment)
- SWTBot (Test framework)
- Gson 2.10.1

---

## Usage Guide

### 1. Initial Setup

```bash
# 1. Clone repository
git clone https://github.com/pas162/swtbot-assistant.git

# 2. Import into Eclipse
File → Import → Existing Projects into Workspace

# 3. Set Target Platform
Open minimal.target → Set as Target Platform

# 4. Configure Preferences
Window → Preferences → AI SWTBot Assistant
→ Enter Jira URL, LLM Endpoint, API Keys
```

### 2. Daily Usage

**Fetch Ticket**:
1. Open AI SWTBot Assistant view
2. Enter ticket key (e.g., `RSC-23001`)
3. Click **Fetch**
4. Xem test steps trong table

**Generate Test**:
1. Select target project
2. Click **Generate**
3. Chọn package destination
4. Xem healing log trong console
5. Review generated code

### 3. Self-Healing Output Example

```
=== Self-Healing Agent Log ===
=== Iteration 0: Initial Generation ===

=== Iteration 1: Validation & Fix ===
Found 2 error(s):
  - Line 45: bot.buton() not found
  - Line 62: Missing import SWTBotShell
Analysis: 1 UNDEFINED_METHOD error(s), 1 MISSING_IMPORT error(s)
✓ Applied 2 fix(es):
  - Fixed typo: bot.buton() → bot.button()
  - Added import: org.eclipse.swtbot.swt.finder.widgets.SWTBotShell

=== Iteration 2: Validation & Fix ===
✓ Code is valid! No errors found.
==============================
```

---

## Extension Points

### Thêm New Fix Strategy

Edit `CodeFixer.java`:

```java
private String applyCustomFix(String code, List<String> appliedFixes) {
    // Your custom fix logic
    return fixedCode;
}
```

### Thêm New Validation Rule

Edit `SyntaxValidator.java`:

```java
private void checkCustomRule(String code, List<CompileError> errors) {
    if (code.contains("pattern")) {
        errors.add(new CompileError(line, col, "message", ErrorType.SYNTAX_ERROR));
    }
}
```

---

## Troubleshooting

| Issue | Cause | Solution |
|-------|-------|----------|
| "org.eclipse cannot be resolved" | Target platform not loaded | Open minimal.target → Set as Target Platform |
| "Jira not configured" | Missing preferences | Enter Jira URL and token in Preferences |
| "Failed to fetch ticket" | Network/auth issue | Check PAT token, verify Jira URL |
| "LLM API error" | Invalid API key/endpoint | Verify endpoint and API key |
| "No test steps available" | Wrong ticket type | Ensure ticket is Zephyr test case |
| Healing log shows no fixes | Unknown error patterns | Add new patterns to CodeFixer |

---

## API Reference

### Key Classes Summary

| Class | Package | Purpose |
|-------|---------|---------|
| `TicketView` | `views` | Main UI component |
| `SelfHealingAgent` | `agent` | Generate + heal orchestrator |
| `LlmClient` | `llm` | LLM API communication |
| `ZephyrClient` | `jira` | Jira/Zephyr API |
| `WorkspaceIndexer` | `indexer` | Code search & extraction |
| `SyntaxValidator` | `agent.analysis` | Code validation |
| `CodeFixer` | `agent.fix` | Auto-fix logic |

---

## Development Notes

### Adding New Features

1. **New UI Component**: Extend `TicketView.java` or create new ViewPart
2. **New AI Provider**: Extend `LlmClient.java` hoặc tạo subclass
3. **New Fix Strategy**: Thêm method trong `CodeFixer.java`
4. **New Validation**: Thêm check trong `SyntaxValidator.java`

### Testing

- Unit tests: Add JUnit tests trong `src/test/java`
- Manual testing: Launch Eclipse Application từ Run menu
- Debug: Use Eclipse debugger với breakpoints trong agent classes

---

## License

Internal use only - Renesas Electronics Corporation

---

## Authors

- Development Team - Renesas Electronics
- AI Assistant Integration - Cascade (Claude)

---

**Last Updated**: May 5, 2026
**Version**: 1.0.0
