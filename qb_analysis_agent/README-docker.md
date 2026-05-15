# 热点分析系统 · 多智能体推演平台 — Docker 部署指南

## 系统概述

热点分析系统是一个多角色推演平台，结合 RAG（检索增强生成）与多智能体辩论技术，模拟多方决策过程，实现热点事件的分析、预测与推演。

核心服务：

1. **SSE / 前端服务器** (`front/web_api.py`) — 基于 FastAPI 的 Web 服务器，提供 Web 界面、SSE 实时流推送以及全部 API 接口（聊天、角色管理、技能执行、推演辩论、AI 代码生成、插件系统、知识库构建）
2. **外部依赖服务** — Milvus（向量数据库）、Redis（会议状态/插件数据）、Neo4j/LightRAG（图数据库，可选）、MinIO（插件存储，可选）

## 部署前提

### 1. 服务器要求

- Linux 服务器（推荐 Ubuntu 20.04+ 或 CentOS 7+）
- Docker Engine 20.10+
- Docker Compose 2.0+
- 至少 4 GB 内存，20 GB 磁盘空间
- 需要开放的端口：
  - **9092** — Web 前端（用户访问界面）

### 2. 网络要求

- 能够访问外部 LLM API 服务（DeepSeek、DashScope 等）
- 能够访问远程 Milvus 向量数据库
- 能够访问远程 Redis 服务
- 能够访问 Neo4j / LightRAG 图数据库 API（可选）
- 能够访问 MinIO 对象存储服务（插件系统需要）

## 快速部署

### 步骤 1：准备服务器环境

```bash
# 1. 安装 Docker
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh
sudo systemctl start docker
sudo systemctl enable docker

# 2. 安装 Docker Compose
sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose

# 3. 验证安装
docker --version
docker-compose --version
```

### 步骤 2：上传项目文件

将以下文件上传到服务器（如 `/opt/qb-analysis-agent`）：

```
├── .env                    # 环境配置文件（需自行创建）
├── .env.example            # 环境变量模板
├── Dockerfile              # 多阶段 Docker 构建文件
├── docker-compose.yaml     # 服务编排文件
├── deploy.sh               # 部署管理脚本
├── front/                  # SSE/前端服务器及路由模块
│   ├── web_api.py
│   ├── routes_chat.py
│   ├── routes_roles.py
│   ├── routes_agent_skill.py
│   ├── routes_build_rag.py
│   └── sse_queue.py
├── agent/                  # 多智能体推演系统
│   ├── multiple_meeting/
│   ├── role_analysis_agent/
│   └── agent_skill/
├── aicode/                 # AI 代码生成 + 插件系统
│   ├── aicode_server.py
│   └── plugin_server/
├── kg/                     # 存储抽象层（Milvus + Neo4j）
│   ├── base_storage.py
│   ├── milvus_impl.py
│   └── neo4j_impl.py
├── rag/                    # RAG 检索系统
│   └── military_news_rag.py
├── skills/                 # Agent 技能定义 + 角色 Markdown 文件
│   ├── equipment_skill.json
│   └── roles/
├── data/                   # 知识库文档（可选）
├── static/                 # 前端静态文件（HTML, CSS, JS）
└── requirements.txt        # Python 依赖
```

### 步骤 3：配置环境变量

```bash
# 1. 复制环境变量模板
cp .env.example .env

# 2. 编辑 .env 文件，填写实际配置
vi .env
```

必须配置的关键参数：

- `LLM_API_KEY` — LLM API 密钥（DeepSeek / DashScope 或其他 OpenAI 兼容接口）
- `EMBEDDING_API_KEY` — 嵌入模型 API 密钥
- `RERANK_API_KEY` — 重排序 API 密钥（可选但推荐）
- `MILVUS_HOST` / `MILVUS_PORT` / `MILVUS_USER` / `MILVUS_PASSWORD` — 向量数据库连接
- `MEET_REDIS_HOST` / `MEET_REDIS_PORT` / `MEET_REDIS_PASSWORD` — Redis 连接（会议状态/插件数据）
- `NEO4J_URI` / `NEO4J_USER` / `NEO4J_PASSWORD` — 图数据库（可选）
- `LIGHTRAG_HOST_PORT` — LightRAG 图查询 API（可选）
- `SKILL_API_HOST` — 技能查询 API 地址（可选）
- `MINIO_ENDPOINT` / `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` — MinIO 对象存储连接（插件系统需要）

### 步骤 4：构建和启动服务

```bash
# 1. 进入项目目录
cd /opt/qb-analysis-agent

# 2. 构建 Docker 镜像
docker-compose build

# 3. 启动所有服务（后台运行）
docker-compose up -d

# 4. 查看服务状态
docker-compose ps

# 5. 查看服务日志
docker-compose logs -f sse

# 6. 停止服务
docker-compose down
```

## 服务访问

### Web 界面

- **地址：** `http://<服务器IP>:9092`
- **功能：** 实时聊天、热点分析、多角色推演、AI 代码生成、插件管理

### 端口说明

| 端口 | 服务 | 用途 |
|------|------|------|
| 9092 | SSE / 前端 (`web_api.py`) | Web 界面、聊天、SSE 实时通信（浏览器访问） |
| 9100 | AI 代码生成 (`aicode_server.py`) | 代码生成服务，SSE 流式输出（可选） |
| 9101 | 插件系统 (`plugins_server.py`) | Vue 插件管理，SFC 解析与代理（可选） |

## 数据持久化

### 挂载目录

以下目录在 `docker-compose.yaml` 中配置为挂载到容器：

- `./static/` → `/app/static/` — 前端静态文件
- `./logs/` → `/app/logs/` — 应用日志
- 数据目录（`./data/`、`./data2/`、`./data3/`）在构建时复制到镜像

### 备份数据

```bash
# 备份数据目录
tar -czf backup-data-$(date +%Y%m%d).tar.gz data/

# 恢复数据
tar -xzf backup-data-20250408.tar.gz
```

## 运维管理

### 使用部署脚本

```bash
./deploy.sh start       # 启动所有服务
./deploy.sh stop        # 停止所有服务
./deploy.sh restart     # 重启所有服务
./deploy.sh status      # 查看服务状态和资源使用
./deploy.sh logs [服务] # 查看日志（默认所有服务）
./deploy.sh build       # 重新构建 Docker 镜像
./deploy.sh cleanup     # 清理 Docker 资源
./deploy.sh backup      # 备份数据
./deploy.sh restore <文件>  # 从备份恢复数据
```

### 常用命令

```bash
# 启动 / 停止 / 重启
docker-compose start
docker-compose stop
docker-compose restart

# 查看服务状态
docker-compose ps

# 查看实时日志
docker-compose logs -f

# 进入容器（调试用）
docker-compose exec sse bash

# 更新服务（代码更新后）
docker-compose down
docker-compose build
docker-compose up -d
```

### 监控与日志

```bash
# 查看容器资源使用
docker stats

# 查看最近日志
docker-compose logs --tail=100 sse

# 搜索错误日志
docker-compose logs --tail=50 | grep -i error

# 清理无用镜像和容器
docker system prune -f
```

### 故障排查

```bash
# 检查服务状态
docker-compose ps

# 检查容器内环境变量
docker-compose exec sse env | grep -E 'LLM_|MILVUS_|REDIS_'

# 测试外部服务连通性
docker-compose exec sse python -c "
import os, redis
from dotenv import load_dotenv
load_dotenv()
# 测试 Redis
r = redis.Redis(host=os.getenv('MEET_REDIS_HOST'), port=int(os.getenv('MEET_REDIS_PORT',6379)), password=os.getenv('MEET_REDIS_PASSWORD'))
print('Redis:', r.ping())
"

# 查看错误日志
docker-compose logs --tail=100 sse | grep -iE 'error|exception|traceback'
```

## 配置优化

### 设置资源限制

在 `docker-compose.yaml` 中添加资源限制：

```yaml
services:
  sse:
    # ... 已有配置 ...
    deploy:
      resources:
        limits:
          memory: 2G
          cpus: '1.0'
        reservations:
          memory: 512M
          cpus: '0.5'
```

### 启用 Nginx 反向代理（可选）

取消 `docker-compose.yaml` 中 nginx 服务的注释，并创建 `nginx/nginx.conf` 配置文件：

```nginx
events {
    worker_connections 1024;
}

http {
    upstream sse_backend {
        server sse:9092;
    }

    server {
        listen 80;
        server_name your-domain.com;

        location / {
            proxy_pass http://sse_backend;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_buffering off;          # SSE 需要关闭缓冲
            proxy_cache off;              # SSE 需要关闭缓存
            proxy_read_timeout 86400s;    # SSE 长连接超时设置
            chunked_transfer_encoding on;
        }
    }
}
```

## 安全建议

### 环境安全

- 不要将 `.env` 文件提交到版本控制系统
- 定期轮换 API 密钥和密码
- 使用强密码保护 Redis 和数据库

### 网络安全

- 配置防火墙，仅开放必要端口（9092）
- 生产环境使用 HTTPS（通过反向代理配置）
- 将 `CORS_ORIGINS` 限制为特定域名，而非 `*`

### 数据安全

- 定期备份数据目录
- 监控日志文件，及时发现异常
- 设置容器资源限制，防止资源耗尽

## 更新升级

```bash
# 1. 停止当前服务
docker-compose down

# 2. 拉取最新代码
git pull origin main

# 3. 检查并更新环境配置（如有变化）
# 对比 .env.example 与当前 .env

# 4. 重新构建和启动
docker-compose build
docker-compose up -d
```

## 常见问题

### 服务启动失败

```bash
# 检查端口冲突
netstat -tlnp | grep 9092

# 查看容器日志
docker-compose logs sse
```

### API 连接失败

- 检查 `.env` 中的 API 密钥配置
- 验证网络是否能访问外部 API 服务
- 检查防火墙设置

### 磁盘空间不足

```bash
# 清理 Docker 无用资源
docker system prune -a -f

# 清理旧日志
find ./logs -name "*.log" -mtime +7 -delete
```

## 联系支持

如遇到部署问题，请提供以下信息：

- `docker-compose logs --tail=100` 输出
- `.env` 文件内容（已脱敏）
- 服务器系统信息（`uname -a`、`docker --version`）
