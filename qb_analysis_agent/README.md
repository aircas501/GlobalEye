# QB Analysis Agent — Hotspot Analysis System · Multi-Agent Deduction Platform

**A multi-role deduction platform** that combines RAG (Retrieval-Augmented Generation) with multi-agent debate technology to simulate multi-party decision-making processes for hotspot event analysis, prediction, and deduction.

## Core Features

### Hotspot Analysis & Deduction Debate
- **Real-time Hotspot Analysis** — Analyze and predict hotspot events using dual-path retrieval from vector + graph databases with reranking
- **Multi-role Deduction Debate** — Simulate discussions among experts from different domains
- **Role Auto-Selection** — Automatically identify relevant countries and roles from hotspot events
- **Debate Topic Generation** — Auto-generate 5 debate topics from events with RAG context
- **Position Statements** — Each role states its initial position based on identity, stance, and retrieved intelligence
- **Multi-turn Deduction Debate** — Weighted random scheduling with reply chains for realistic discussion flow
- **Debate Summarization** — Auto-generate structured summary reports (positions, divergences, consensus, and future projections)

### Intelligent Skills & Tools
- **Extensible Skill System** — Dynamic skill loading from JSON configs with tool definitions and API integration, invoked via `/skill_name query` syntax
- **Data Query Tools** — Query various data specifications and dynamic information via the skill system
- **Role Skill Management** — Upload/download/delete role personas as Markdown files via API

### AI Code Generation
- **Intelligent Code Generation** — Standalone AI code generation service with SSE streaming and browser-side tool execution
- **Three Working Modes** — `modify` (read/write/edit files), `dryrun` (preview plan only), `analyze` (analysis only)
- **Security Protection** — Path safety validation rejecting absolute paths and path traversal attacks

### Dynamic Plugin System
- **Vue Plugin CRUD** — Upload/download/list/delete Vue SFC plugins via MinIO storage
- **SFC Parsing** — Server-side parsing of Vue single-file component template, script, and style
- **HTTP Proxy** — Solve browser CORS issues by proxying requests through the server to user's local backend
- **Per-User Isolation** — Plugin storage and backend URL configuration isolated by username

## Architecture Overview

```
┌──────────────────────────────────────────────────────────────────────┐
│                    Frontend SSE Server (FastAPI)                      │
│                          Port 9092                                    │
├──────────┬──────────┬──────────┬──────────┬──────────┬───────────────┤
│routes_chat│routes_   │routes_   │aicode_   │plugin_   │routes_build_  │
│(Chat/     │roles     │agent_    │server    │server    │rag            │
│ Stream/   │(Role     │skill     │(Code Gen)│(Plugin)  │(Remote KB)    │
│ History)  │Mgmt)     │(Skill    │          │          │               │
│           │          │ Exec)    │          │          │               │
└────┬──────┴────┬─────┴────┬─────┴────┬─────┴────┬─────┴──────┬────────┘
     │           │          │          │          │            │
┌────▼───┐ ┌────▼───┐ ┌────▼───┐ ┌────▼───┐ ┌────▼───┐ ┌────▼────────┐
│Multi-  │ │Role     │ │Agent   │ │Code Gen│ │Plugin   │ │  Storage     │
│Role    │ │Analysis │ │Skill   │ │(SSE    │ │System   │ │(BaseStorage) │
│Deduction│ │Agent   │ │Executor│ │Stream) │ │(MinIO+ │ │┌───────────┐│
│(LangGrph│ │(LLM)   │ │(Dynamic│ │3 Modes │ │ Redis)  │ ││Milvus     ││
│ +Redis)│ └─────────┘ │Tools)  │ └────┬────┘ └─────────┘ ││Storage    ││
└────┬─────┘           └────┬───┘      │                   │├───────────┤│
     │                      │          │                   ││Neo4j      ││
┌────▼──────────────────────▼──────────▼───────────────────┤│Storage    ││
│                    RAG Retrieval System                  │└───────────┘│
│  (MilitaryNewsRAG + DashScopeEmbeddings + Qwen3-Rerank)  └────────────┘
└──────────────────────────────────────────────────────────┘
```

### Core Components

| Component | Technology | Description |
|-----------|------------|-------------|
| **SSE Frontend Server** | FastAPI + SSE | Web interface with real-time event streaming, integrates all sub-service routes |
| **Multi-Role Deduction** | LangGraph + Redis | Weighted scheduling multi-agent deduction debate (5-stage flow) |
| **Role Analysis** | LangChain + LLM | Auto-identify countries/roles from events |
| **RAG System** | Milvus + Neo4j + DashScope | Dual-backend retrieval (vector + graph), LLM semantic chunking, reranking |
| **Storage Abstraction** | `BaseStorage` (ABC) | Unified `search(query, embed_fn, k)` interface for pluggable backends |
| **Agent Skill Executor** | LangChain + Dynamic Tools | Load JSON skill configs and execute via LLM agent |
| **Role Skills Manager** | Markdown Parser | Parse `*Skill.md` files into Role entities with caching |
| **AI Code Generation** | FastAPI + SSE | Standalone code generation server with browser-side tool execution |
| **Vue Plugin System** | MinIO + Redis | SFC plugin storage, parsing, and HTTP CORS proxy |

## Quick Start

### Prerequisites

- Python 3.10+
- External services: Milvus (vector DB), Redis (meeting state/plugins), Neo4j (graph DB, optional), MinIO (plugin storage, optional)
- API keys: DeepSeek (or OpenAI-compatible) LLM API key, DashScope embedding API key

### Installation

```bash
# 1. Clone the project
git clone https://github.com/your-username/qb-analysis-agent.git
cd qb-analysis-agent

# 2. Create virtual environment
python -m venv venv
venv\Scripts\activate    # Windows
# source venv/bin/activate  # Linux/Mac

# 3. Install dependencies
pip install -r requirements.txt

# 4. Configure environment
cp .env.example .env
# Edit .env with your API keys and service hosts
```

### Run

Start the SSE frontend server (includes all APIs and web interface):

```bash
python front/web_api.py
# Runs on http://localhost:9092 (configured via SSE_PORT env var)
```

Open `http://localhost:9092` in your browser.

### Optional: Standalone Sub-Services

```bash
# AI Code Generation Service (port 9100)
python -m aicode.aicode_server

# Vue Plugin System Service (port 9101)
python -m aicode.plugin_server.plugins_server
```

### Docker

```bash
# Build and start with docker-compose
docker-compose up -d

# Or use the deploy script (supports start/stop/restart/status/logs/build/backup/restore)
./deploy.sh start
```

## Configuration

All configuration via environment variables (`.env` file):

| Variable | Default | Description |
|----------|---------|-------------|
| `LLM_BASE_URL` | `https://api.deepseek.com` | LLM API endpoint |
| `LLM_MODEL` | `deepseek-chat` | LLM model name |
| `LLM_API_KEY` | — | LLM API key |
| `EMBEDDING_API_KEY` | — | DashScope embedding API key |
| `EMBEDDING_MODEL` | `text-embedding-v4` | Embedding model name |
| `RERANK_BASE_URL` | `https://dashscope.aliyuncs.com/compatible-api/v1/reranks` | Rerank API endpoint |
| `RERANK_MODEL` | `qwen3-rerank` | Rerank model name |
| `RERANK_API_KEY` | — | Rerank API key |
| `MILVUS_HOST` | — | Milvus host |
| `MILVUS_PORT` | `19530` | Milvus port |
| `MILVUS_USER` | `` | Milvus username |
| `MILVUS_PASSWORD` | — | Milvus password |
| `MILVUS_COLLECTION_NAME` | `us_iran_intel_chinese4` | Milvus collection name |
| `NEO4J_URI` | — | Neo4j bolt URI |
| `NEO4J_USER` | `neo4j` | Neo4j username |
| `NEO4J_PASSWORD` | — | Neo4j password |
| `MEET_REDIS_HOST` | — | Redis host (meeting state) |
| `MEET_REDIS_PORT` | `6379` | Redis port |
| `MEET_REDIS_PASSWORD` | — | Redis password |
| `MEET_REDIS_DB` | `0` | Redis database number |
| `API_PORT` | `9000` | API server port |
| `API_HOST` | `0.0.0.0` | API server host |
| `SSE_PORT` | `9092` | SSE/frontend server port |
| `SSE_HOST` | `0.0.0.0` | SSE server host |
| `CORS_ORIGINS` | `*` | CORS allowed origins |
| `SKILL_API_HOST` | — | Skill query API host |
| `LIGHTRAG_HOST_PORT` | — | LightRAG graph query API endpoint |
| `MINIO_ENDPOINT` | `` | MinIO object storage endpoint |
| `MINIO_ACCESS_KEY` | `` | MinIO access key |
| `MINIO_SECRET_KEY` | `` | MinIO secret key |



### LightRAG Configuration ###
### To use data provided in this project's /data directory, start LightRAG to build vector store and knowledge graph
Clone the LightRAG project alongside this project. Since LightRAG has been customized, only clone the LightRAG provided with this project.

cd LightRAG

# One-click development environment setup (recommended)
make dev
source .venv/bin/activate  # Activate virtual environment (Linux/macOS)
# Windows: .venv\Scripts\activate

# make dev installs test tools, complete offline dependencies, builds frontend; does not generate .env
# Run make env-base before starting, or manually copy from env.example and configure .env

# Equivalent manual steps using uv
# Note: uv sync automatically creates virtual environment in .venv/
uv sync --extra test --extra offline
source .venv/bin/activate  # Activate virtual environment (Linux/macOS)
# Windows: .venv\Scripts\activate

### Or use pip and virtual environment
# python -m venv .venv
# source .venv/bin/activate  # Windows: .venv\Scripts\activate
# pip install -e ".[test,offline]"

# Build frontend
cd lightrag_webui
bun install --frozen-lockfile
bun run build
cd ..

# Configure env file
cp .env.example .env
Configure the following environment variables:
LLM_BINDING_API_KEY
EMBEDDING_BINDING_API_KEY
NEO4J_URI
NEO4J_USERNAME
NEO4J_PASSWORD
NEO4J_DATABASE
MILVUS_URI
MILVUS_DB_NAME
MILVUS_USER
MILVUS_PASSWORD

FILE_UPLOAD_REMOTE_URL=http://{qb-analysis-agent-ip:port}/receive/file
CONTENT_UPLOAD_REMOTE_URL=http://{qb-analysis-agent-ip:port}/receive/content

###########################
### RocketMQ Configuration
###########################
ROCKETMQ_NAMESRV_ADDR=
ROCKETMQ_CONSUMER_GROUP=
ROCKETMQ_TAGS=*

# Start API-WebUI server
lightrag-server

Upload documents from /data folder in the API-WebUI interface, or wait for hotspot events to sync via RocketMQ
### End LightRAG Configuration ###



## API Endpoints

### Chat & Streaming

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/stream` | GET | SSE event stream (params: `user_id`, `chat_id`) |
| `/api/send` | POST | Send message / run multi-role deduction |
| `/api/delete/messages` | POST | Delete messages from Redis |
| `/api/get/chat/history/list` | GET | Get user's chat history list |
| `/api/get/chat/history/context` | GET | Get chat conversation details |
| `/api/del/chat/history` | POST | Delete a chat from history |

### Role Management

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/personas` | POST | Get all role personas |
| `/api/roles/skills` | GET | List all role skills |
| `/api/roles/sample` | GET | Download sample role skill Markdown |
| `/api/roles/upload` | POST | Upload role skill Markdown file |
| `/api/roles/{role_name}` | DELETE | Delete a role skill |
| `/api/save/role` | POST | Save roles to Redis |

### Agent Skills

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/execute/skill` | POST | Execute an agent skill (params: `user_id`, `chat_id`, `query`) |

### AI Code Generation

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/aicode/health` | GET | Code generation service health check |
| `/api/aicode/stream` | GET | AI code generation SSE event stream |
| `/api/aicode/chat/new` | POST | Create a new code generation session |
| `/api/aicode/chat/send` | POST | Send code generation instruction (supports `modify`/`dryrun`/`analyze` modes) |
| `/api/aicode/chat/tool/result` | POST | Return browser-side tool execution result |
| `/api/aicode/chat/cancel` | POST | Cancel current code generation task |
| `/api/aicode/chat/messages` | GET | Get session message history |

### Plugin System

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/plugins` | GET | List all Vue plugins for current user (deduplicated by date) |
| `/api/plugins/detail` | GET | Get plugin details (parsed SFC sections) |
| `/api/plugins/raw` | GET | Get plugin .vue source file |
| `/api/plugins/upload` | POST | Upload Vue SFC plugin to MinIO |
| `/api/plugin-proxy` | POST | HTTP proxy request, forwarded to user-configured backend |
| `/api/plugin-url` | GET/POST/DELETE | Manage user's backend proxy URL configuration |

### Knowledge Base Builder

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/receive/file` | POST | Upload file to auto-build RAG knowledge base (write to Milvus + Neo4j) |
| `/receive/text` | POST | Upload text to auto-build RAG knowledge base |

## Usage

### Multi-Role Deduction Flow

1. **Save roles** to Redis via `/api/save/role`
2. **Choose roles** (`stage="role_choose"`) — AI identifies relevant countries/roles from the event
3. **Generate topics** (`stage="topic_generation"`) — AI generates 5 debate topics
4. **Position statements** (`stage="position"`) — Each role states its position
5. **Debate** (`stage="debate"`) — Multi-turn deduction debate with weighted scheduling and reply chains, ending with structured summary report

### Agent Skills

Use the `/skill_name query` format to invoke skills:

```
/equipment_skill Query the latest hotspot event data
```

Skills are defined as JSON files in the `skills/` directory with tool definitions, API URLs, and execution rules.

### AI Code Generation

Three working modes:

- **modify** — Read, write, and edit files based on instructions (default)
- **dryrun** — Generate and preview plan only, no actual modifications
- **analyze** — Analyze code only, no modifications

The code generation service auto-loads Markdown skill files from `aicode/` as coding standards.

### Plugin System

1. Upload a Vue SFC plugin to MinIO:
   ```bash
   curl -X POST http://localhost:9092/api/plugins/upload \
     -F "file=@data-table.vue" \
     -F "user_id=myuser"
   ```
2. Configure backend proxy URL (solves browser CORS):
   ```bash
   curl -X POST http://localhost:9092/api/plugin-url \
     -F "user_id=myuser" \
     -F "url=http://my-backend:8080"
   ```
3. Call backend API through proxy:
   ```bash
   curl -X POST http://localhost:9092/api/plugin-proxy \
     -F "user_id=myuser" \
     -F "path=/api/endpoint"
   ```

### Adding Intelligence Data

**Option 1: Local files**
1. Place UTF-8 encoded `.txt` files in `data/`, `data2/`, or `data3/` directory
2. Rebuild vector store via API: `POST /rebuild` (force_rebuild=true)
3. Documents are split via LLM semantic chunking, embedded, and stored in Milvus + Neo4j

**Option 2: Remote upload**
```bash
# Upload file
curl -X POST http://localhost:9092/receive/file -F "file=@intel_report.txt"

# Upload text
curl -X POST http://localhost:9092/receive/text -F "text=Latest intelligence content..."
```

### Adding New Role Types

1. Create a `*Skill.md` file in `skills/roles/` with the required format:
   ```markdown
   # Role Name
   **Identity**: role identity
   **Persona**: role persona
   **Speaking Style**: tone and phrasing characteristics
   **Forbidden Content**: content not allowed
   **Core Stance**: key positions and arguments
   ```
2. Upload via `/api/roles/upload` or place directly in `skills/roles/`
3. Roles auto-load on server startup (supports hot-reload on upload)

## Project Structure

```
├── front/
│   ├── web_api.py                # Main entry — SSE/Frontend server (port 9092)
│   ├── routes_chat.py            # Chat, stream, history, role save routes
│   ├── routes_roles.py           # Role skill management routes (CRUD)
│   ├── routes_agent_skill.py     # Agent skill execution route
│   ├── routes_build_rag.py       # Remote RAG knowledge base builder route
│   └── sse_queue.py              # Async message queue for SSE broadcasting
├── agent/
│   ├── multiple_meeting/         # Multi-role deduction debate system
│   │   ├── multi_meet_agent.py   # Main debate orchestrator (LangGraph, 5-stage flow)
│   │   ├── debate_scheduler.py   # Weighted random speaking order scheduler
│   │   ├── meeting_redis_server.py # Redis state persistence
│   │   ├── meeting_constants.py  # Response code constants
│   │   ├── role_skills_manager.py # Role file parser & cache
│   │   └── entity/
│   │       └── role.py           # Role entity (name, identity, persona, stance...)
│   ├── role_analysis_agent/      # Auto country/role identification
│   │   └── role_analysis_agent.py
│   └── agent_skill/              # Dynamic skill execution
│       └── agent_skills.py       # Skill loader, tool builder, executor
├── aicode/                       # AI code generation + plugin system
│   ├── aicode_server.py          # Code generation server (port 9100, SSE streaming)
│   ├── plugin_server/            # Vue plugin system
│   │   ├── plugins_server.py     # Plugin CRUD + SFC parser + HTTP proxy
│   │   ├── plugin_minio.py       # MinIO storage operations
│   │   ├── plugin_redis.py       # Redis backend URL configuration
│   │   └── routes_plugin.py      # Plugin route registration
├── kg/                           # Knowledge Graph / Storage abstraction layer
│   ├── base_storage.py           # Abstract BaseStorage (ABC)
│   ├── milvus_impl.py            # MilvusStorage — vector similarity search
│   └── neo4j_impl.py             # Neo4jStorage — graph knowledge retrieval
├── rag/
│   └── military_news_rag.py      # RAG system, dual-backend retrieval + LLM chunking + rerank
├── skills/
│   ├── equipment_skill.json      # Data query skill definition
│   └── roles/                    # Role persona Markdown files
│       ├── 军事家Skill.md
│       ├── 政治家Skill.md
│       ├── 经济学家Skill.md
│       └── 股票分析师Skill.md
├── data/                         # Intelligence news — original dataset
├── static/                       # Frontend static files (HTML, CSS, JS)
├── docker-compose.yaml           # Docker Compose service orchestration
├── Dockerfile                    # Multi-stage Docker build
├── deploy.sh                     # Deployment management script (start/stop/backup/restore)
├── requirements.txt              # Python dependencies
├── pyproject.toml                # Project metadata & build config
├── .env.example                  # Environment variable template
```

## Security

- **API keys** and **database passwords** should only be stored in `.env` (already in `.gitignore`)
- Rotate all exposed keys before deploying in production
- For production, restrict `CORS_ORIGINS` to specific domains
- Never commit `.env` or files containing credentials

## Contributing

Contributions are welcome! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

## Acknowledgments

This project references the open-source project [LightRAG](https://github.com/HKUDS/LightRAG) for graph-based knowledge retrieval functionality. We sincerely appreciate the contributions of the LightRAG development team.

## License

This project is licensed under the MIT License — see [LICENSE](LICENSE) for details.
