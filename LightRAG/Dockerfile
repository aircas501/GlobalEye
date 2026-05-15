# syntax=docker/dockerfile:1

# Frontend build stage
FROM --platform=$BUILDPLATFORM oven/bun:1 AS frontend-builder

WORKDIR /app

# 正确配置 Bun 国内镜像源（修复 bun config 报错）
ENV BUN_CONFIG_REGISTRY=https://registry.npmmirror.com

COPY lightrag_webui/ ./lightrag_webui/

RUN --mount=type=cache,target=/root/.bun/install/cache \
    cd lightrag_webui \
    && bun install --frozen-lockfile \
    && bun run build

# Python build stage
FROM python:3.12-slim AS builder

ENV DEBIAN_FRONTEND=noninteractive
ENV UV_SYSTEM_PYTHON=1
ENV UV_COMPILE_BYTECODE=1

WORKDIR /app

# 修复 sources.list 不存在问题 + 阿里云源
RUN apt-get clean && \
    if [ -f /etc/apt/sources.list.d/debian.sources ]; then \
        sed -i 's/deb.debian.org/mirrors.aliyun.com/g' /etc/apt/sources.list.d/debian.sources; \
    fi && \
    if [ -f /etc/apt/sources.list ]; then \
        sed -i 's/deb.debian.org/mirrors.aliyun.com/g' /etc/apt/sources.list; \
    fi && \
    apt-get update \
    && apt-get install -y --no-install-recommends \
        curl \
        build-essential \
        pkg-config \
    && rm -rf /var/lib/apt/lists/*

# 安装 RocketMQ C++ 客户端库（rocketmq-client-python 编译依赖）
# 先在服务器上下载 .deb 放到 /opt/lightrag/ 目录，构建时直接复制，避免下载慢
# wget https://archive.apache.org/dist/rocketmq/rocketmq-client-cpp/2.0.0/rocketmq-client-cpp-2.0.0.amd64.deb
COPY rocketmq-client-cpp-*.deb /tmp/rocketmq/
RUN if ls /tmp/rocketmq/rocketmq-client-cpp-*.deb 1>/dev/null 2>&1; then \
        dpkg -i /tmp/rocketmq/rocketmq-client-cpp-*.deb 2>/dev/null || apt-get install -y -f --no-install-recommends; \
        ldconfig; \
        echo "RocketMQ C++ client installed from local .deb"; \
    else \
        echo "WARNING: No local rocketmq .deb found. rocketmq-client-python will not be available."; \
    fi \
    && rm -rf /tmp/rocketmq

# 国内极速安装 uv（走阿里云 PyPI 镜像）
RUN pip install uv -i https://mirrors.aliyun.com/pypi/simple/

ENV PATH="/root/.local/bin:${PATH}"
RUN mkdir -p /root/.local/share/uv

# uv 配置国内 PyPI 源（环境变量方式，兼容所有 uv 版本）
ENV UV_INDEX_URL=https://mirrors.aliyun.com/pypi/simple/
ENV UV_DEFAULT_INDEX=https://mirrors.aliyun.com/pypi/simple/
# 网络不稳定，增大下载超时和并发
ENV UV_HTTP_TIMEOUT=300
ENV UV_CONCURRENT_DOWNLOADS=8

COPY pyproject.toml .
COPY setup.py .
COPY uv.lock .

RUN --mount=type=cache,target=/root/.local/share/uv \
    uv sync --frozen --no-dev --extra api --extra offline --no-install-project --no-editable

COPY lightrag/ ./lightrag/
COPY --from=frontend-builder /app/lightrag/api/webui ./lightrag/api/webui

RUN --mount=type=cache,target=/root/.local/share/uv \
    uv sync --frozen --no-dev --extra api --extra offline --no-editable \
    && /app/.venv/bin/python -m ensurepip --upgrade \
    && /app/.venv/bin/python -m pip install --no-build-isolation rocketmq-client-python -i https://mirrors.aliyun.com/pypi/simple/

RUN mkdir -p /app/data/tiktoken \
    && uv run lightrag-download-cache --cache-dir /app/data/tiktoken || status=$?; \
    if [ -n "${status:-}" ] && [ "$status" -ne 0 ] && [ "$status" -ne 2 ]; then exit "$status"; fi

# Final stage
FROM python:3.12-slim

WORKDIR /app

# 最终阶段也修复源路径
RUN apt-get clean && \
    if [ -f /etc/apt/sources.list.d/debian.sources ]; then \
        sed -i 's/deb.debian.org/mirrors.aliyun.com/g' /etc/apt/sources.list.d/debian.sources; \
    fi && \
    if [ -f /etc/apt/sources.list ]; then \
        sed -i 's/deb.debian.org/mirrors.aliyun.com/g' /etc/apt/sources.list; \
    fi

COPY --from=builder /usr/local/bin/uv /usr/local/bin/uv
ENV UV_SYSTEM_PYTHON=1
ENV UV_INDEX_URL=https://mirrors.aliyun.com/pypi/simple/
ENV UV_HTTP_TIMEOUT=300
ENV UV_CONCURRENT_DOWNLOADS=8

COPY --from=builder /usr/local/lib /usr/local/lib
COPY --from=builder /root/.local /root/.local
COPY --from=builder /app/.venv /app/.venv
COPY --from=builder /app/lightrag ./lightrag
COPY pyproject.toml .
COPY setup.py .
COPY uv.lock .

RUN ldconfig

ENV PATH=/app/.venv/bin:/root/.local/bin:$PATH

RUN --mount=type=cache,target=/root/.local/share/uv \
    uv sync --frozen --no-dev --extra api --extra offline --no-editable \
    && /app/.venv/bin/python -m ensurepip --upgrade \
    && /app/.venv/bin/python -m pip install --no-build-isolation rocketmq-client-python -i https://mirrors.aliyun.com/pypi/simple/

RUN mkdir -p /app/data/rag_storage /app/data/inputs /app/data/tiktoken
COPY --from=builder /app/data/tiktoken /app/data/tiktoken

ENV TIKTOKEN_CACHE_DIR=/app/data/tiktoken
ENV WORKING_DIR=/app/data/rag_storage
ENV INPUT_DIR=/app/data/inputs

EXPOSE 9621
ENTRYPOINT ["python", "-m", "lightrag.api.lightrag_server"]