"""
aicode/server.py — 方案 A 纯浏览器端代码生成服务器
LLM 推理在服务器，文件操作通过 SSE 委托浏览器执行
"""
import asyncio
import json
import os
import re
import time
import traceback
import uuid
from pathlib import Path

from dotenv import load_dotenv
from fastapi import APIRouter, FastAPI, HTTPException, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse, StreamingResponse
from openai import AsyncOpenAI

load_dotenv()

LLM_MODEL = os.getenv("LLM_MODEL", "deepseek-chat")
LLM_API_KEY = os.getenv("LLM_API_KEY")
LLM_BASE_URL = os.getenv("LLM_BASE_URL")

SKILL_FILE = Path(__file__).parent / "generator_code_skill.md"
VUE_SKILL_FILE = Path(__file__).parent / "vue_rule_skill.md"
STATIC_DIR = Path(__file__).parent / "frontend" / "dist"

# ── 工具定义（仅定义，执行权在浏览器） ──────────────────────

BROWSER_TOOLS = ["read_file", "write_file", "edit_file", "glob", "grep"]

# 危险路径模式：绝对路径、Windows 盘符、路径遍历
_DANGEROUS_PATH_RE = re.compile(r'^(\.\.|/|[a-zA-Z]:[\\/]|\\\\)')


def validate_file_path(file_path: str) -> str | None:
    """校验文件路径是否安全。返回错误信息字符串，None 表示通过。"""
    if not file_path or not isinstance(file_path, str):
        return "路径为空或无效"
    if _DANGEROUS_PATH_RE.match(file_path):
        return f"禁止使用绝对路径或路径遍历: {file_path}"
    # 拒绝包含 .. 和 . 段的路径
    segments = file_path.replace('\\', '/').split('/')
    for seg in segments:
        if seg in ('', '.', '..'):
            return f"路径包含非法段 '{seg}': {file_path}"
    return None
SERVER_TOOLS = ["bash"]  # bash 在服务器端执行（仅回显指令）

TOOLS = [
    {"type": "function", "function": {"name": "read_file",
        "description": "读取文件内容。浏览器将在用户本地文件系统执行此操作。",
        "parameters": {"type": "object", "properties": {
            "file_path": {"type": "string", "description": "文件路径（相对于用户项目根目录）"}},
            "required": ["file_path"]}}},
    {"type": "function", "function": {"name": "write_file",
        "description": "创建或覆写文件。浏览器将在用户本地文件系统执行此操作。",
        "parameters": {"type": "object", "properties": {
            "file_path": {"type": "string", "description": "文件路径"},
            "content": {"type": "string", "description": "要写入的完整文件内容"}},
            "required": ["file_path", "content"]}}},
    {"type": "function", "function": {"name": "edit_file",
        "description": "精确替换文件中的某段字符串（必须唯一匹配）。浏览器将在用户本地文件系统执行此操作。",
        "parameters": {"type": "object", "properties": {
            "file_path": {"type": "string", "description": "文件路径"},
            "old_string": {"type": "string", "description": "要替换的文本（必须在文件中唯一）"},
            "new_string": {"type": "string", "description": "替换后的文本"}},
            "required": ["file_path", "old_string", "new_string"]}}},
    {"type": "function", "function": {"name": "glob",
        "description": "按 glob 模式查找文件。浏览器将在用户本地文件系统执行此操作。",
        "parameters": {"type": "object", "properties": {
            "pattern": {"type": "string", "description": "Glob 模式，如 **/*.py、src/**/*.java"}},
            "required": ["pattern"]}}},
    {"type": "function", "function": {"name": "grep",
        "description": "在文件内容中搜索正则表达式。浏览器将在用户本地文件系统执行此操作。",
        "parameters": {"type": "object", "properties": {
            "pattern": {"type": "string", "description": "正则表达式搜索模式"},
            "path": {"type": "string", "description": "搜索路径，默认为项目根目录"}},
            "required": ["pattern"]}}},
    {"type": "function", "function": {"name": "bash",
        "description": "执行 Shell 命令。此操作将在服务器端提示用户手动执行。",
        "parameters": {"type": "object", "properties": {
            "command": {"type": "string", "description": "要执行的命令"}},
            "required": ["command"]}}},
]

# ── App ──────────────────────────────────────────────────

app = FastAPI(title="AI Code Generator")
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["*"],
                   allow_headers=["*"], allow_credentials=True)

# 独立路由模块，可被其他 FastAPI app（如 web_api.py）导入复用
aicode_router = APIRouter()
app.include_router(aicode_router)


# ── 会话管理 ────────────────────────────────────────────

class Session:
    def __init__(self, chat_id: str):
        self.chat_id = chat_id
        self.messages: list = []
        self.sse_queue: asyncio.Queue = asyncio.Queue(maxsize=32)
        self.tool_event = asyncio.Event()
        self.tool_result: str | None = None
        self.cancelled = False
        self.created_at = time.time()

    async def emit(self, event_type: str, data: dict):
        payload = json.dumps({"event": event_type, "data": data}, ensure_ascii=False)
        try:
            self.sse_queue.put_nowait(payload)
        except asyncio.QueueFull:
            pass  # 丢弃旧事件，前端跟不上就算了

    async def delegate_tool(self, tool_name: str, args: dict) -> str:
        """委托浏览器执行工具，等待结果返回"""
        await self.emit("tool_call", {"tool": tool_name, "args": args})
        self.tool_event.clear()
        try:
            await asyncio.wait_for(self.tool_event.wait(), timeout=120)
        except asyncio.TimeoutError:
            return "❌ 工具执行超时 (120s)，浏览器未响应"
        return self.tool_result or "❌ 工具执行结果为空"

    def post_result(self, result: str):
        self.tool_result = result
        self.tool_event.set()


sessions: dict[str, Session] = {}


def load_skill_prompt() -> str:
    """加载通用代码生成 skill"""
    text = SKILL_FILE.read_text(encoding="utf-8")
    m = re.search(r"^---\s*\n.*?\n---\s*\n", text, re.DOTALL)
    return (text[m.end():] if m else text).strip()


def load_vue_skill_prompt() -> str:
    """加载 Vue SFC 专项 skill"""
    if not VUE_SKILL_FILE.exists():
        return ""
    text = VUE_SKILL_FILE.read_text(encoding="utf-8")
    m = re.search(r"^---\s*\n.*?\n---\s*\n", text, re.DOTALL)
    return (text[m.end():] if m else text).strip()


_base_prompt = load_skill_prompt()
_vue_prompt = load_vue_skill_prompt()
SYSTEM_PROMPT = _base_prompt
if _vue_prompt:
    SYSTEM_PROMPT += "\n\n---\n\n" + _vue_prompt
SYSTEM_PROMPT += f"""

## 重要：当前模式为浏览器端执行

你运行在一个 Web 应用中。用户通过浏览器访问，项目文件在用户本地磁盘上。
你可以调用 read_file / write_file / edit_file / glob / grep 工具，这些操作由浏览器在用户本地执行。

### 验证项目根目录
- 在开始任何文件操作前，先调用 `glob("*")` 确认根目录状态
- 如果返回 `❌ 未选择项目文件夹`，提示用户在左侧文件树中选择根目录
- 如果返回了文件列表（或 `未找到匹配`），说明根目录已就绪，直接继续工作
- **禁止不经验证就直接假设根目录未设置并反复提示用户**

### 文件路径（沙箱限制 ⚠️）
- 所有文件路径相对于用户项目的根目录。用户已经通过浏览器选择了项目文件夹。
- **绝对禁止**使用绝对路径（如 /etc/passwd、C:\\Windows\\...）
- **绝对禁止**使用路径遍历（如 ../../../sensitive/file）
- **绝对禁止**路径中包含 ".." 或 "." 段
- 所有文件读写操作严格限制在项目文件夹内，前端会拒绝越权路径

### bash 命令
bash 命令无法在用户本地自动执行。如果你调用 bash，用户会看到命令提示并手动在终端运行。
因此：
- 需要编译、运行、安装依赖时，使用 bash 并在回复中告诉用户如何操作
- 文件操作（读/写/搜索）直接用 read_file / write_file / edit_file / glob / grep

### 工作流程
1. 先用 glob 了解项目结构
2. 用 grep 搜索关键代码
3. 用 read_file 阅读需要修改的文件
4. 用 edit_file 执行精准修改
5. 每个修改完成后在回复中总结
"""


def build_system_message(target_mode: str) -> str:
    notes = {
        "modify": "\n## 当前模式：代码修改\n请根据用户需求修改代码，使用工具完成实际操作。",
        "dryrun": "\n## ⚠️ 当前模式：预览计划\n请探索代码并用 read_file 查看相关文件，在文本回复中输出修改计划。**不要调用 write_file 或 edit_file。** 用户确认后会切换到修改模式。",
        "analyze": "\n## 当前模式：分析代码\n请探索代码库结构，输出结构化分析报告。",
    }
    return SYSTEM_PROMPT + notes.get(target_mode, "")


# ── API 路由 ─────────────────────────────────────────────

@aicode_router.get("/api/aicode/health")
async def health():
    return {"status": "ok", "sessions": len(sessions)}


@aicode_router.get("/api/aicode/stream")
async def stream(chat_id: str):
    session = sessions.get(chat_id)
    if not session:
        raise HTTPException(404, "会话不存在")

    async def gen():
        while not session.cancelled:
            try:
                payload = await asyncio.wait_for(session.sse_queue.get(), timeout=30)
                yield f"data: {payload}\n\n"
            except asyncio.TimeoutError:
                yield f"data: {json.dumps({'event': 'heartbeat'})}\n\n"
    return StreamingResponse(gen(), media_type="text/event-stream",
                             headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"})


@aicode_router.post("/api/aicode/chat/new")
async def chat_new(request: Request):
    chat_id = uuid.uuid4().hex[:12]
    sessions[chat_id] = Session(chat_id)
    return {"chat_id": chat_id}


@aicode_router.post("/api/aicode/chat/send")
async def chat_send(request: Request):
    body = await request.json()
    chat_id = body.get("chat_id")
    instruction = body.get("instruction", "").strip()
    mode = body.get("mode", "modify")

    if not instruction:
        raise HTTPException(400, "指令为空")

    if chat_id and chat_id in sessions:
        session = sessions[chat_id]
    else:
        chat_id = uuid.uuid4().hex[:12]
        session = Session(chat_id)
        sessions[chat_id] = session

    # 构造 system + user 消息
    if not session.messages:
        session.messages.append({"role": "system", "content": build_system_message(mode)})
        session.messages.append({"role": "user", "content": f"""## 用户需求
{instruction}

## 执行前必做
在动手写代码之前，先用 glob 查看项目目录结构了解现有文件。
- 若已有相关文件 → 用 grep 定位 → read_file 阅读 → edit_file 精准修改
- 若项目为空 → 用 write_file 创建新文件
请现在开始执行。"""})
    else:
        session.messages.append({"role": "user", "content": instruction})

    asyncio.create_task(_run_agent(session, mode))
    return {"chat_id": chat_id, "status": "started"}


@aicode_router.post("/api/aicode/chat/tool/result")
async def tool_result(request: Request):
    body = await request.json()
    chat_id = body["chat_id"]
    result = body.get("result", "")
    session = sessions.get(chat_id)
    if session:
        session.post_result(result)
    return {"ok": True}


@aicode_router.post("/api/aicode/chat/cancel")
async def chat_cancel(request: Request):
    body = await request.json()
    chat_id = body.get("chat_id")
    session = sessions.get(chat_id)
    if session:
        session.cancelled = True
        session.tool_event.set()  # 解除阻塞
    return {"ok": True}


@aicode_router.get("/api/aicode/chat/messages")
async def chat_messages(chat_id: str):
    session = sessions.get(chat_id)
    if not session:
        raise HTTPException(404, "会话不存在")
    return {"messages": session.messages}


# ── 核心 Agent 循环 ──────────────────────────────────────

async def _run_agent(session: Session, mode: str):
    client = AsyncOpenAI(api_key=LLM_API_KEY, base_url=LLM_BASE_URL)
    dry_run = (mode == "dryrun")

    try:
        max_iter = 30
        for _ in range(max_iter):
            if session.cancelled:
                await session.emit("done", {"reason": "cancelled"})
                return

            response = await client.chat.completions.create(
                model=LLM_MODEL, messages=session.messages, tools=TOOLS,
                tool_choice="auto", temperature=0.1, max_tokens=4096, timeout=120)

            msg = response.choices[0].message

            if msg.tool_calls:
                if len(msg.tool_calls) > 5:
                    msg.tool_calls = msg.tool_calls[:5]
                session.messages.append(msg.model_dump())

                for tc in msg.tool_calls:
                    tool_name = tc.function.name
                    try:
                        args = json.loads(tc.function.arguments)
                    except json.JSONDecodeError:
                        session.messages.append({"role": "tool", "tool_call_id": tc.id,
                            "content": "❌ 参数 JSON 解析失败"})
                        continue

                    if dry_run and tool_name in ("write_file", "edit_file", "bash"):
                        session.messages.append({"role": "tool", "tool_call_id": tc.id,
                            "content": "❌ 预览模式不允许写操作。请只在文本中描述修改计划。"})
                        continue

                    if tool_name in BROWSER_TOOLS:
                        # 安全校验：文件操作路径必须在项目文件夹内
                        if tool_name in ("read_file", "write_file", "edit_file") and "file_path" in args:
                            path_err = validate_file_path(args["file_path"])
                            if path_err:
                                result = f"❌ {path_err}"
                                session.messages.append({"role": "tool", "tool_call_id": tc.id, "content": result})
                                continue
                        if tool_name == "grep" and "path" in args and args["path"]:
                            path_err = validate_file_path(args["path"])
                            if path_err:
                                result = f"❌ {path_err}"
                                session.messages.append({"role": "tool", "tool_call_id": tc.id, "content": result})
                                continue
                        # 委托浏览器执行
                        result = await session.delegate_tool(tool_name, args)
                    elif tool_name == "bash":
                        # bash 无法在浏览器执行，返回提示
                        result = f"""⚠️ 无法在浏览器中自动执行命令。

请在终端手动运行以下命令，并将结果粘贴到对话中：

```
{args.get('command', '')}
```

如果不需要此命令的结果，请告诉我继续下一步。"""
                        await session.emit("bash_prompt", {"command": args.get("command", "")})
                    else:
                        result = f"❌ 未知工具: {tool_name}"

                    if len(result) > 8000:
                        result = result[:8000] + "\n... (已截断)"

                    await session.emit("tool_result", {"tool": tool_name, "success": not result.startswith("❌"), "preview": result[:300]})
                    session.messages.append({"role": "tool", "tool_call_id": tc.id, "content": result})
                continue

            # 最终文本回复
            reply = msg.content or "(无文本回复)"
            session.messages.append({"role": "assistant", "content": reply})
            await session.emit("llm_reply", {"content": reply, "is_final": True})
            await session.emit("done", {"mode": mode})

            if dry_run:
                # 预览模式：追加提示消息，等待用户确认
                session.messages.append({"role": "user",
                    "content": "用户已查看上述计划。如果确认执行，请回复 'ready_to_execute'。"})
            return

        await session.emit("error", {"message": "达到最大工具调用次数"})
    except Exception as e:
        await session.emit("error", {"message": str(e), "traceback": traceback.format_exc()})
    finally:
        # 清理过期会话（30 分钟无活动）
        pass


# ── 静态文件 ─────────────────────────────────────────────

@app.get("/index")
async def index():
    index_file = STATIC_DIR / "index.html"
    if index_file.exists():
        return FileResponse(index_file)
    return FileResponse(Path(__file__).parent / "frontend" / "index.html")


@app.get("/assets/{filename:path}")
async def assets(filename: str):
    p = STATIC_DIR / "assets" / filename
    if p.exists():
        return FileResponse(p)
    raise HTTPException(404)


# ── 启动 ─────────────────────────────────────────────────

if __name__ == "__main__":
    import uvicorn
    port = int(os.getenv("AICODE_PORT", "9100"))
    print(f"AI Code Generator starting on http://localhost:{port}")
    uvicorn.run(app, host="0.0.0.0", port=port)
