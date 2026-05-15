import logging
import os
import uvicorn
from dotenv import load_dotenv
from fastapi import FastAPI
from fastapi.responses import FileResponse
from starlette.middleware.cors import CORSMiddleware

from front.routes_chat import router as chat_router
from front.routes_roles import router as roles_router
from front.routes_agent_skill import router as agent_skill_router
from front.routes_threat import router as threat_router
from front.routes_build_rag import router as build_rag_router
from aicode.aicode_server import aicode_router
from aicode.plugin_server.routes_plugin import plugin_router

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s - %(levelname)s: %(message)s"
)

load_dotenv()
app = FastAPI()

# CORS 配置：从环境变量读取允许的来源，逗号分隔
CORS_ORIGINS = os.getenv("CORS_ORIGINS", "*")

if CORS_ORIGINS == "*":
    cors_origins = ["*"]
else:
    cors_origins = [origin.strip() for origin in CORS_ORIGINS.split(",") if origin.strip()]

app.add_middleware(
    CORSMiddleware,
    allow_origins=cors_origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
    expose_headers=["*"],
    max_age=600,
)

# ── 挂载各模块路由 ──
app.include_router(chat_router)
app.include_router(roles_router)
app.include_router(agent_skill_router)
app.include_router(threat_router)
app.include_router(aicode_router)
app.include_router(plugin_router)
app.include_router(build_rag_router)


# ── 首页 ──
@app.get("/")
async def read_index():
    return FileResponse("../static/index.html")


if __name__ == '__main__':
    port = int(os.getenv("SSE_PORT", "9092"))
    host = os.getenv("SSE_HOST", "0.0.0.0")
    print("=" * 50)
    print("QB Analysis Agent Started.")
    print("=" * 50)
    uvicorn.run(app, host=host, port=port, timeout_graceful_shutdown=120)

