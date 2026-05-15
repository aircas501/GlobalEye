"""
插件系统 — Vue SFC 解析 + HTTP 代理（可独立运行，也可被 web_api.py 集成）

架构：
    Minio 存储 .vue → Python 解析 SFC → 浏览器运行时编译 → 动态渲染
    + HTTP 代理解决跨域：前端 → 服务器 → 用户本地服务

独立启动：
    python -m aicode.plugin_server.plugins_server    # 端口 9101

集成到 web_api.py：
    from aicode.plugin_server.plugins_server import plugin_router
    app.include_router(plugin_router)
"""
import logging
from datetime import datetime
import re

import httpx
from fastapi import APIRouter, FastAPI, Form, HTTPException, Request, UploadFile, File
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import PlainTextResponse

from .plugin_redis import get_url_prefix, set_url_prefix, delete_url_prefix
from . import plugin_minio

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s - %(levelname)s: %(message)s"
)

# ══════════════════════════════════════════════════════════════
# 路由（可被外部 app 挂载）
# ══════════════════════════════════════════════════════════════

plugin_router = APIRouter()


# ── Vue SFC 解析器 ──────────────────────────────────────
def parse_vue_sfc(source: str) -> dict:
    """解析 Vue 单文件组件，提取 template / script / style"""
    result = {
        "template": "",
        "script": "",
        "style": "",
        "scoped": False,
        "styles": [],
    }

    # 处理嵌套 <template> 标签（如 <template v-else>）
    tmpl_start = re.search(r'<template\b[^>]*>', source)
    if tmpl_start:
        depth = 1
        pos = tmpl_start.end()
        for m in re.finditer(r'(<template\b[^>]*>)|(</template>)', source[pos:], re.DOTALL):
            if m.group(1):
                depth += 1
            elif m.group(2):
                depth -= 1
                if depth == 0:
                    result["template"] = source[pos:pos + m.start()].strip()
                    break

    script_match = re.search(
        r'<script\b[^>]*>(.*?)</script>', source, re.DOTALL
    )
    if script_match:
        result["script"] = script_match.group(1).strip()

    for m in re.finditer(r'<style\b([^>]*)>(.*?)</style>', source, re.DOTALL):
        attrs = m.group(1)
        css = m.group(2).strip()
        is_scoped = "scoped" in attrs
        result["styles"].append({"content": css, "scoped": is_scoped})
        if is_scoped:
            result["scoped"] = True
        if not result["style"]:
            result["style"] = css
        else:
            result["style"] += "\n" + css

    return result


# ── 路由：插件管理 ──────────────────────────────────────

@plugin_router.get("/api/plugins")
async def list_plugins(username: str = "default"):
    """列出用户所有可用插件（每个 file_id 仅返回最新版本）"""
    files = plugin_minio.list_plugins(username)
    return {
        "plugins": [
            {
                "id": p.file_id,
                "file_name": p.file_name,
                "component_name": p.component_name,
                "date": p.date,
                "size": p.size,
            }
            for p in files
        ],
        "total": len(files),
    }


@plugin_router.get("/api/plugins/detail")
async def get_plugin(plugin_id: str, username: str = "default"):
    """获取并解析单个插件（自动取最新日期+最大版本）"""
    resolved = plugin_minio.resolve_plugin(username, plugin_id)
    if not resolved:
        raise HTTPException(404, f"插件 '{plugin_id}' 不存在")

    parsed = parse_vue_sfc(resolved["source"])

    return {
        "id": resolved["file_id"],
        "file_name": resolved["file_name"],
        "date": resolved["date"],
        "template": parsed["template"],
        "script": parsed["script"],
        "styles": parsed["styles"],
        "raw": resolved["source"],
    }


@plugin_router.get("/api/plugins/raw")
async def get_plugin_raw(plugin_id: str, username: str = "default"):
    """返回原始 .vue 文件内容"""
    source = plugin_minio.get_plugin_source(username, plugin_id)
    if not source:
        raise HTTPException(404, f"插件 '{plugin_id}' 不存在")
    return PlainTextResponse(source, media_type="text/plain")


@plugin_router.post("/api/plugins/upload")
async def upload_plugin(
    file: UploadFile = File(...),
    username: str = Form("default"),
):
    """
    上传 .vue 插件文件到 Minio。

    Form 参数:
        file:     .vue 文件（必填）
        username: 用户名（默认 default）
    """
    if not file.filename or not file.filename.lower().endswith(".vue"):
        raise HTTPException(400, "仅支持 .vue 文件")

    source = await file.read()
    await file.seek(0)

    try:
        source_str = source.decode("utf-8")
    except UnicodeDecodeError:
        raise HTTPException(400, "文件编码无效，需为 UTF-8")

    if "<template>" not in source_str and "<template " not in source_str:
        raise HTTPException(400, "文件缺少 <template> 区块")

    file_name = file.filename
    file_id = file_name[:-4]  # 去 .vue 后缀
    today = datetime.now().strftime("%Y%m%d")

    try:
        plugin_minio.put_plugin(username, today, file_name, source_str)
    except Exception as e:
        raise HTTPException(500, f"上传 Minio 失败: {e}")

    return {
        "ok": True,
        "id": file_id,
        "file_name": file_name,
        "date": today,
        "size": len(source),
    }


# ── 路由：HTTP 代理（解决浏览器跨域） ──────────────────

@plugin_router.post("/api/plugin-proxy")
async def plugin_proxy(request: Request):
    """
    通用 HTTP 代理：浏览器 → 服务器 → 用户本地服务

    从 Redis 中查找 url_prefix 拼接完整 URL，解决浏览器跨域限制。

    Redis key: plugin:url:{username}:{plugin_name}
    """
    body = await request.json()
    path = body.get("url")
    method = body.get("method", "GET").upper()
    username = body.get("username", "default")
    plugin_name = body.get("plugin_name", "")
    req_headers = body.get("headers") or {}
    req_body = body.get("body")
    logging.info(f"plugin_proxy, path:{path}, method:{method}, username:{username}, plugin_name:{plugin_name}")
    if not path:
        raise HTTPException(400, "缺少 url 参数")

    url_prefix = get_url_prefix(username, plugin_name)
    if not url_prefix:
        raise HTTPException(502, f"未找到插件 {plugin_name} 的后端地址，请先设置 plugin:url:{username}:{plugin_name}")

    target_url = f"{url_prefix.rstrip('/')}{path}"
    print(f"[plugin-proxy] {method} {target_url} (plugin: {plugin_name})")

    skip_headers = {"host", "content-length", "connection", "transfer-encoding"}
    forward_headers = {
        k: v for k, v in req_headers.items()
        if k.lower() not in skip_headers
    }

    try:
        async with httpx.AsyncClient(timeout=30) as client:
            if method == "GET":
                resp = await client.get(target_url, headers=forward_headers)
            elif method == "POST":
                resp = await client.post(target_url, json=req_body, headers=forward_headers)
            elif method == "PUT":
                resp = await client.put(target_url, json=req_body, headers=forward_headers)
            elif method == "DELETE":
                resp = await client.delete(target_url, headers=forward_headers)
            else:
                raise HTTPException(400, f"不支持的 HTTP 方法: {method}")

            try:
                resp_body = resp.json()
            except Exception:
                resp_body = resp.text

            return {
                "status": resp.status_code,
                "headers": dict(resp.headers),
                "body": resp_body,
            }
    except httpx.ConnectError:
        raise HTTPException(502, f"无法连接目标服务: {target_url}")
    except httpx.TimeoutException:
        raise HTTPException(504, "请求超时")


# ── 路由：插件 URL 管理（Redis） ──────────────────────

@plugin_router.post("/api/plugin-url")
async def set_plugin_url(request: Request):
    """
    设置插件的后端地址映射。

    请求：
        POST /api/plugin-url
        {
            "username": "admin",
            "plugin_name": "data-table",
            "url_prefix": "http://localhost:8081"
        }
    """
    body = await request.json()
    username = body.get("username", "default")
    plugin_name = body.get("plugin_name")
    url_prefix = body.get("url_prefix")

    if not plugin_name or not url_prefix:
        raise HTTPException(400, "缺少 plugin_name 或 url_prefix")

    try:
        key = set_url_prefix(username, plugin_name, url_prefix)
    except Exception as e:
        raise HTTPException(500, f"Redis 写入失败: {e}")
    return {"ok": True, "key": key, "url_prefix": url_prefix}


@plugin_router.get("/api/plugin-url")
async def get_plugin_url(username: str = "default", plugin_name: str = ""):
    """查询插件的后端地址映射"""
    if not plugin_name:
        raise HTTPException(400, "缺少 plugin_name")
    try:
        url_prefix = get_url_prefix(username, plugin_name)
    except Exception as e:
        raise HTTPException(500, f"Redis 读取失败: {e}")
    if not url_prefix:
        raise HTTPException(404, f"未找到 plugin:url:{username}:{plugin_name}")
    return {"key": f"plugin:url:{username}:{plugin_name}", "url_prefix": url_prefix}


@plugin_router.delete("/api/plugin-url")
async def del_plugin_url(username: str = "default", plugin_name: str = ""):
    """删除插件 URL 映射"""
    if not plugin_name:
        raise HTTPException(400, "缺少 plugin_name")
    try:
        key = delete_url_prefix(username, plugin_name)
    except Exception as e:
        raise HTTPException(500, f"Redis 删除失败: {e}")
    return {"ok": True, "key": key}


# ── 健康检查 ────────────────────────────────────────────

@plugin_router.get("/api/plugin-health")
async def health():
    return {"status": "ok", "storage": "minio", "bucket": plugin_minio.MINIO_BUCKET}


# ══════════════════════════════════════════════════════════════
# 独立运行（开发/调试用）
# ══════════════════════════════════════════════════════════════

def create_app() -> FastAPI:
    """创建独立运行的 FastAPI 应用"""
    app = FastAPI(title="Plugin System API")
    app.add_middleware(
        CORSMiddleware,
        allow_origins=["*"],
        allow_methods=["*"],
        allow_headers=["*"],
    )
    app.include_router(plugin_router)
    return app


if __name__ == "__main__":
    import uvicorn
    print("=" * 60)
    print("  插件系统 Demo 服务器（独立模式）")
    print(f"  Minio: {plugin_minio.MINIO_BUCKET=}")
    print(f"  访问: http://localhost:9101")
    print("=" * 60)
    uvicorn.run(create_app(), host="0.0.0.0", port=9101)
