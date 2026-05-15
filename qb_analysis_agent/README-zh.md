# QB Analysis Agent — 热点分析系统 · 多智能体推演平台

**多角色推演平台**，结合 RAG（检索增强生成）与多智能体辩论技术，模拟多方决策过程，实现热点事件的分析、预测与推演。

## 核心功能

### 热点分析与推演辩论
- **实时热点分析** — 基于向量+图数据库双路检索的热点事件分析与预测，支持重排序
- **多角色推演辩论** — 模拟不同领域专家等多角色的推演讨论
- **辩论议题生成** — 基于事件和 RAG 上下文自动生成 5 个辩论议题
- **立场陈述** — 各角色根据身份、立场和检索情报发表初始观点
- **多轮推演辩论** — 加权随机调度 + 回复链机制，模拟真实讨论流程
- **辩论总结** — 自动生成结构化总结报告（各方观点、分歧、共识与未来推演）

### 智能技能与工具
- **可扩展技能系统** — 基于 JSON 配置动态加载技能，支持工具定义和 API 集成
- **数据查询工具** — 通过技能系统查询各类数据规格和动态信息
- **角色技能管理** — 通过 API 上传/下载/删除 Markdown 格式的角色人设文件

### AI 代码生成
- **智能代码生成** — 独立的 AI 代码生成服务，支持 SSE 流式输出和浏览器端工具执行
- **三种工作模式** — `modify`（读写编辑文件）、`dryrun`（仅预览计划）、`analyze`（仅分析）
- **安全防护** — 路径安全验证，拒绝绝对路径和路径遍历攻击

### 动态插件系统
- **Vue 插件 CRUD** — 基于 MinIO 存储的 Vue SFC 插件上传/下载/列表/删除
- **SFC 解析** — 服务端解析 Vue 单文件组件的模板、脚本和样式
- **HTTP 代理** — 解决浏览器跨域问题，前端通过服务器代理访问用户本地服务
- **每个用户独立** — 按用户名隔离插件存储和后端 URL 配置

## 架构概览

```
┌──────────────────────────────────────────────────────────────────────┐
│                   前端 SSE 服务器 (FastAPI)                           │
│                         端口 9092                                     │
├──────────┬──────────┬──────────┬──────────┬──────────┬───────────────┤
│routes_chat│routes_   │routes_   │aicode_   │plugin_   │routes_build_  │
│(聊天/流/  │roles     │agent_    │server    │server    │rag            │
│ 历史记录) │(角色管理) │skill     │(代码生成) │(插件系统) │(远程建库)     │
└────┬──────┴────┬─────┴────┬─────┴────┬─────┴────┬─────┴──────┬────────┘
     │           │          │          │          │            │
┌────▼───┐ ┌────▼───┐ ┌────▼───┐ ┌────▼───┐ ┌────▼───┐ ┌────▼────────┐
│多角色推演│ │角色分析 │ │Agent技能│ │代码生成 │ │插件系统  │ │  存储层      │
│(LangGraph│ │Agent   │ │执行器   │ │(SSE流式)│ │(MinIO+  │ │(BaseStorage) │
│ +Redis) │ │(LLM)   │ │(动态工具)│ │3种模式  │ │ Redis)  │ │┌───────────┐│
└────┬─────┘ └─────────┘ └────┬───┘ └────┬────┘ └─────────┘ ││Milvus     ││
     │                        │          │                   ││Storage    ││
┌────▼────────────────────────▼──────────▼───────────────────┤├───────────┤│
│                       RAG 检索系统                          ││Neo4j      ││
│  (MilitaryNewsRAG + DashScopeEmbeddings + Qwen3-Rerank)    ││Storage    ││
└────────────────────────────────────────────────────────────┘└───────────┘│
                                                                └────────────┘
```

### 核心组件

| 组件 | 技术栈 | 说明 |
|------|--------|------|
| **SSE 前端服务器** | FastAPI + SSE | Web 界面与实时事件流推送，集成所有子服务路由 |
| **多角色推演系统** | LangGraph + Redis | 加权调度的多智能体推演辩论（5 阶段流程） |
| **角色分析** | LangChain + LLM | 从事件中自动识别涉及的国家/角色 |
| **RAG 检索系统** | Milvus + Neo4j + DashScope | 向量+图双后端检索，LLM 语义分块，支持重排序 |
| **存储抽象层** | `BaseStorage` (ABC) | 统一 `search(query, embed_fn, k)` 接口，可插拔后端 |
| **Agent 技能执行器** | LangChain + 动态工具 | 加载 JSON 技能配置并通过 LLM Agent 执行 |
| **角色技能管理器** | Markdown 解析器 | 解析 `*Skill.md` 文件为 Role 实体并缓存 |
| **AI 代码生成服务** | FastAPI + SSE | 独立代码生成服务器，支持浏览器端工具执行 |
| **Vue 插件系统** | MinIO + Redis | SFC 插件存储、解析与 HTTP CORS 代理 |

## 快速开始

### 前置要求

- Python 3.10+
- 外部服务：Milvus（向量数据库）、Redis（会议状态/插件）、Neo4j（图数据库，可选）、MinIO（插件存储，可选）
- API 密钥：DeepSeek（或 OpenAI 兼容）LLM API 密钥、DashScope 嵌入 API 密钥

### 安装

```bash
# 1. 克隆项目
git clone https://github.com/your-username/qb-analysis-agent.git
cd qb-analysis-agent

# 2. 创建虚拟环境
python -m venv venv
venv\Scripts\activate    # Windows
# source venv/bin/activate  # Linux/Mac

# 3. 安装依赖
pip install -r requirements.txt

# 4. 配置环境变量
cp .env.example .env
# 编辑 .env 文件，填入 API 密钥和服务地址
```

### 运行

启动 SSE 前端服务器（包含所有 API 和 Web 界面）：

```bash
python front/web_api.py
# 运行在 http://localhost:9092（由 SSE_PORT 环境变量配置）
```

在浏览器中打开 `http://localhost:9092`。


### Docker

```bash
# 使用 docker-compose 构建并启动
docker-compose up -d

# 或使用部署脚本（支持 start/stop/restart/status/logs/build/backup/restore）
./deploy.sh start
```

## 配置说明

所有配置通过环境变量（`.env` 文件）进行：

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `LLM_BASE_URL` | `https://api.deepseek.com` | LLM API 端点 |
| `LLM_MODEL` | `deepseek-chat` | LLM 模型名称 |
| `LLM_API_KEY` | — | LLM API 密钥 |
| `EMBEDDING_API_KEY` | — | DashScope 嵌入 API 密钥 |
| `EMBEDDING_MODEL` | `text-embedding-v4` | 嵌入模型名称 |
| `RERANK_BASE_URL` | `https://dashscope.aliyuncs.com/compatible-api/v1/reranks` | 重排序 API 端点 |
| `RERANK_MODEL` | `qwen3-rerank` | 重排序模型名称 |
| `RERANK_API_KEY` | — | 重排序 API 密钥 |
| `MILVUS_HOST` | — | Milvus 主机地址 |
| `MILVUS_PORT` | `19530` | Milvus 端口 |
| `MILVUS_USER` | `` | Milvus 用户名 |
| `MILVUS_PASSWORD` | — | Milvus 密码 |
| `MILVUS_COLLECTION_NAME` | `us_iran_intel_chinese4` | Milvus 集合名称 |
| `NEO4J_URI` | — | Neo4j bolt 连接地址 |
| `NEO4J_USER` | `neo4j` | Neo4j 用户名 |
| `NEO4J_PASSWORD` | — | Neo4j 密码 |
| `MEET_REDIS_HOST` | — | Redis 主机地址（会议状态） |
| `MEET_REDIS_PORT` | `6379` | Redis 端口 |
| `MEET_REDIS_PASSWORD` | — | Redis 密码 |
| `MEET_REDIS_DB` | `0` | Redis 数据库编号 |
| `API_PORT` | `9000` | API 服务端口 |
| `API_HOST` | `0.0.0.0` | API 服务主机 |
| `SSE_PORT` | `9092` | SSE/前端服务端口 |
| `SSE_HOST` | `0.0.0.0` | SSE 服务主机 |
| `CORS_ORIGINS` | `*` | CORS 允许来源（逗号分隔） |
| `SKILL_API_HOST` | — | 技能查询 API 地址 |
| `LIGHTRAG_HOST_PORT` | — | LightRAG 图查询 API 端点 |
| `MINIO_ENDPOINT` | `` | MinIO 对象存储端点 |
| `MINIO_ACCESS_KEY` | `` | MinIO 访问密钥 |
| `MINIO_SECRET_KEY` | `` | MinIO 秘密密钥 |



###  LightRAG项目配置开始  ###
###  如果需要使用本项目/data目录提供的数据，需要启动LightRAG项目完成向量库和图谱的构建
git clone 与本项目统计的LightRAG项目,由于LightRAG二开过，所以只能clone本项目附带的LightRAG

cd LightRAG

# 一键初始化开发环境（推荐）
make dev
source .venv/bin/activate  # 激活虚拟环境 (Linux/macOS)
# Windows 系统: .venv\Scripts\activate

# make dev 会安装测试工具链以及完整的离线依赖栈
# （API、存储后端与各类 Provider 集成），并构建前端；不会生成 .env。
# 启动服务前请先运行 make env-base，或手动从 env.example 复制并配置 .env。

# 使用 uv 的等价手动步骤
# 注意: uv sync 会自动在 .venv/ 目录创建虚拟环境
uv sync --extra test --extra offline
source .venv/bin/activate  # 激活虚拟环境 (Linux/macOS)
# Windows 系统: .venv\Scripts\activate

### 或使用 pip 和虚拟环境
# python -m venv .venv
# source .venv/bin/activate  # Windows: .venv\Scripts\activate
# pip install -e ".[test,offline]"

# 构建前端代码
cd lightrag_webui
bun install --frozen-lockfile
bun run build
cd ..

# 配置 env 文件
cp .env.example .env 
配置下列环境变量
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

FILE_UPLOAD_REMOTE_URL=http://{qb-analysis-agent的ip和端口}/receive/file
CONTENT_UPLOAD_REMOTE_URL=http://{qb-analysis-agent的ip和端口}/receive/content

###########################
### RocketMQ Configuration
###########################
ROCKETMQ_NAMESRV_ADDR=
ROCKETMQ_CONSUMER_GROUP=
ROCKETMQ_TAGS=*

# 启动API-WebUI服务
lightrag-server

在API-WebUI服务页面上传/data文件夹中的文档,或等待爬取热点事件通过RocketMQ同步
###  LightRAG项目配置结束  ###



## API 接口

### 聊天与流

| 接口 | 方法 | 说明 |
|------|------|------|
| `/stream` | GET | SSE 事件流（参数：`user_id`, `chat_id`） |
| `/api/send` | POST | 发送消息 / 运行多角色推演 |
| `/api/delete/messages` | POST | 从 Redis 删除消息 |
| `/api/get/chat/history/list` | GET | 获取用户聊天历史列表 |
| `/api/get/chat/history/context` | GET | 获取聊天会话详情 |
| `/api/del/chat/history` | POST | 删除聊天历史 |

### 角色管理

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/personas` | POST | 获取所有角色人设信息 |
| `/api/roles/skills` | GET | 列出所有角色技能 |
| `/api/roles/sample` | GET | 下载示例角色 Skill Markdown 文件 |
| `/api/roles/upload` | POST | 上传角色 Skill Markdown 文件 |
| `/api/roles/{role_name}` | DELETE | 删除指定角色技能 |
| `/api/save/role` | POST | 保存角色到 Redis |

### Agent 技能

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/execute/skill` | POST | 执行 Agent 技能（参数：`user_id`, `chat_id`, `query`） |

### AI 代码生成

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/aicode/health` | GET | 代码生成服务健康检查 |
| `/api/aicode/stream` | GET | AI 代码生成 SSE 事件流 |
| `/api/aicode/chat/new` | POST | 创建新的代码生成会话 |
| `/api/aicode/chat/send` | POST | 发送代码生成指令（支持 `modify`/`dryrun`/`analyze` 模式） |
| `/api/aicode/chat/tool/result` | POST | 回传浏览器端工具执行结果 |
| `/api/aicode/chat/cancel` | POST | 取消当前代码生成任务 |
| `/api/aicode/chat/messages` | GET | 获取会话消息历史 |

### 插件系统

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/plugins` | GET | 列出当前用户的所有 Vue 插件（按日期去重） |
| `/api/plugins/detail` | GET | 获取插件详情（解析后的 SFC 各部分） |
| `/api/plugins/raw` | GET | 获取插件 .vue 源文件 |
| `/api/plugins/upload` | POST | 上传 Vue SFC 插件到 MinIO |
| `/api/plugin-proxy` | POST | HTTP 代理请求，转发至用户配置的后端 |
| `/api/plugin-url` | GET/POST/DELETE | 管理用户的后端代理 URL 配置 |

### 知识库构建

| 接口 | 方法 | 说明 |
|------|------|------|
| `/receive/file` | POST | 上传文件自动构建 RAG 知识库（写入 Milvus + Neo4j） |
| `/receive/text` | POST | 上传文本自动构建 RAG 知识库 |

## 使用说明

### 多角色推演流程

1. **保存角色** — 通过 `/api/save/role` 将角色保存到 Redis
2. **选择角色** (`stage="role_choose"`) — AI 从事件中识别涉及的国家和角色
3. **生成议题** (`stage="topic_generation"`) — AI 生成 5 个辩论议题
4. **立场陈述** (`stage="position"`) — 各角色发表初始观点
5. **辩论** (`stage="debate"`) — 加权随机调度多轮辩论，含回复链机制，辩论结束后自动生成结构化总结报告


### AI 代码生成

支持三种工作模式：

- **modify** — 根据指令读写编辑文件（默认）
- **dryrun** — 仅生成计划预览，不实际修改
- **analyze** — 仅分析代码，不做修改

代码生成服务会自动加载 `aicode/` 下的 Markdown 技能文件作为编码规范。

### 插件系统

1. 上传 Vue SFC 插件到 MinIO：
   ```bash
   curl -X POST http://localhost:9092/api/plugins/upload \
     -F "file=@data-table.vue" \
     -F "user_id=myuser"
   ```
2. 配置后端代理 URL（解决浏览器跨域）：
   ```bash
   curl -X POST http://localhost:9092/api/plugin-url \
     -F "user_id=myuser" \
     -F "url=http://my-backend:8080"
   ```
3. 通过代理调用后端 API：
   ```bash
   curl -X POST http://localhost:9092/api/plugin-proxy \
     -F "user_id=myuser" \
     -F "path=/api/endpoint"
   ```

### 添加情报数据

**方式一：本地文件**
1. 将 UTF-8 编码的 `.txt` 文件放入 `data/`、`data2/` 或 `data3/` 目录
2. 通过 API 重建向量库：`POST /rebuild` (force_rebuild=true)
3. 文档经 LLM 智能语义切分、嵌入后存入 Milvus + Neo4j

**方式二：远程上传**
```bash
# 上传文件
curl -X POST http://localhost:9092/receive/file -F "file=@intel_report.txt"

# 上传文本
curl -X POST http://localhost:9092/receive/text -F "text=最新情报内容..."
```

### 添加新角色类型

1. 在 `skills/roles/` 目录创建 `*Skill.md` 文件，格式如下：
   ```markdown
   # 角色姓名
   **身份**：角色身份
   **人设**：角色人设描述
   **发言风格**：语气和措辞特点
   **禁止内容**：不允许的内容
   **核心立场**：主要观点和主张
   ```
2. 通过 `/api/roles/upload` 上传，或直接放入 `skills/roles/` 目录
3. 服务器启动时自动加载角色（支持热加载，运行时上传即时生效）

## 项目结构

```
├── front/
│   ├── web_api.py                # 主入口 — SSE/前端服务器 (端口 9092)
│   ├── routes_chat.py            # 聊天、流、历史记录、角色保存路由
│   ├── routes_roles.py           # 角色技能管理路由 (增删改查)
│   ├── routes_agent_skill.py     # Agent 技能执行路由
│   ├── routes_build_rag.py       # 远程 RAG 知识库构建路由
│   └── sse_queue.py              # SSE 广播异步消息队列
├── agent/
│   ├── multiple_meeting/         # 多角色推演辩论系统
│   │   ├── multi_meet_agent.py   # 主辩论编排器 (LangGraph, 5 阶段流程)
│   │   ├── debate_scheduler.py   # 加权随机发言调度器
│   │   ├── meeting_redis_server.py # Redis 状态持久化
│   │   ├── meeting_constants.py  # 响应码常量
│   │   ├── role_skills_manager.py # 角色文件解析与缓存
│   │   └── entity/
│   │       └── role.py           # 角色实体 (姓名、身份、人设、立场等)
│   ├── role_analysis_agent/      # 自动识别国家/角色
│   │   └── role_analysis_agent.py
│   └── agent_skill/              # 动态技能执行
│       └── agent_skills.py       # 技能加载、工具构建、执行器
├── aicode/                       # AI 代码生成 + 插件系统
│   ├── aicode_server.py          # 代码生成服务 (端口 9100, SSE 流式)
│   ├── plugin_server/            # Vue 插件系统
│      ├── plugins_server.py     # 插件 CRUD + SFC 解析 + HTTP 代理
│      ├── plugin_minio.py       # MinIO 存储操作
│      ├── plugin_redis.py       # Redis 后端 URL 配置
│      └── routes_plugin.py      # 插件路由注册
├── kg/                           # 知识图谱 / 存储抽象层
│   ├── base_storage.py           # 抽象基类 BaseStorage (ABC)
│   ├── milvus_impl.py            # MilvusStorage — 向量相似度检索
│   └── neo4j_impl.py             # Neo4jStorage — 图知识检索
├── rag/
│   └── military_news_rag.py      # RAG 系统，双后端检索 + LLM 分块 + 重排序
├── skills/
│   ├── equipment_skill.json      # 数据查询技能定义
│   └── roles/                    # 角色人设 Markdown 文件
│       ├── 军事家Skill.md
│       ├── 政治家Skill.md
│       ├── 经济学家Skill.md
│       └── 股票分析师Skill.md
├── data/                         # 情报新闻 — 原始数据集
├── static/                       # 前端静态文件 (HTML, CSS, JS)
├── docker-compose.yaml           # Docker Compose 服务编排
├── Dockerfile                    # 多阶段 Docker 构建
├── deploy.sh                     # 部署管理脚本 (start/stop/backup/restore)
├── requirements.txt              # Python 依赖
├── pyproject.toml                # 项目元数据与构建配置
└── .env.example                  # 环境变量模板
```

## 安全说明

- API 密钥和数据库密码仅存储在 `.env` 文件中（已加入 `.gitignore`）
- 在生产环境部署前，请轮换所有已暴露的密钥
- 生产环境应将 `CORS_ORIGINS` 限制为特定域名
- 切勿将 `.env` 或包含凭据的文件提交到版本控制

## 贡献

欢迎贡献！请参见 [CONTRIBUTING.md](CONTRIBUTING.md) 了解指南。

## 致谢

本项目引用了开源项目 [LightRAG](https://github.com/HKUDS/LightRAG) 用于基于图的知识检索功能。在此衷心感谢 LightRAG 开发团队的贡献。

## 许可证

本项目采用 MIT 许可证 — 详见 [LICENSE](LICENSE) 文件。
