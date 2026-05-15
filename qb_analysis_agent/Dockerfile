# 美伊局势情报分析系统 Dockerfile
# 多阶段构建以减小镜像大小

# 第一阶段：构建阶段
FROM python:3.11-slim AS builder

# 设置工作目录
WORKDIR /app
ENV PYTHONPATH=/app

# 复制依赖文件
COPY requirements.txt .

# 创建虚拟环境并安装Python依赖
RUN python -m venv /opt/venv
ENV PATH="/opt/venv/bin:$PATH"
RUN pip install --no-cache-dir --upgrade pip -i https://mirrors.aliyun.com/pypi/simple/ && \
    pip install --no-cache-dir -r requirements.txt -i https://mirrors.aliyun.com/pypi/simple/ --trusted-host mirrors.aliyun.com

# 第二阶段：运行时阶段
FROM python:3.11-slim

# 设置环境变量
ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1
ENV PATH="/opt/venv/bin:$PATH"

# 创建非root用户
RUN groupadd -r appuser && useradd -r -g appuser appuser

# 设置工作目录
WORKDIR /app
ENV PYTHONPATH=/app
# 从构建阶段复制虚拟环境
COPY --from=builder /opt/venv /opt/venv

# 复制应用代码
COPY . .

# 创建必要的目录并设置权限
RUN mkdir -p /app/data /app/data2 /app/data3 /app/static && \
    chown -R appuser:appuser /app

# 切换到非root用户
USER appuser

# 暴露端口
# API服务端口：9000
# SSE服务端口：9092
EXPOSE 9090 9092
# 默认命令（可以通过docker-compose覆盖）
CMD ["python", "api.py"]