/**
 * 智能讨论（会议室 / 多角色）：与智能化统一后端（`intelligentizationApiBase`）同源基址。
 * 使用原生 `fetch` 处理 JSON 与 `text/event-stream`；`DISCUSSION_BASE` 为根 URL，接口路径以 `/api/...` 拼接。
 */
import { resolveIntelligentizationApiBase } from "./intelligentizationApiBase.js";

const DISCUSSION_BASE = resolveIntelligentizationApiBase();

/**
 * 是否建立 GET `/stream` 的 `EventSource`。
 * 与 POST `/api/send` 的流式响应可能重复，由调用方去重；不需要长连接时可改为 `false`。
 */
const DISCUSSION_SSE_STREAM_ENABLED = true;

/**
 * 兼容后端 SSE 中非严格 JSON 的 payload（单引号、未加引号的 key 等）。
 * @param {string} raw
 * @returns {object|null}
 */
export function parseDiscussionEventPayload(raw) {
  const s0 = String(raw || "").trim();
  if (!s0) return null;
  try {
    return JSON.parse(s0);
  } catch {
    /* 尝试宽松解析 */
  }
  let s = s0;
  s = s
    .replace(/[“”]/g, '"')
    .replace(/[‘’]/g, "'");
  s = s.replace(/([{,]\s*)([A-Za-z0-9_]+)\s*:/g, '$1"$2":');
  s = s.replace(/'([^'\\]*(?:\\.[^'\\]*)*)'/g, (_m, g1) => {
    const escaped = String(g1).replace(/"/g, '\\"');
    return `"${escaped}"`;
  });
  try {
    return JSON.parse(s);
  } catch {
    return null;
  }
}

/**
 * 解析 FastAPI 等返回的 422/4xx 正文，便于展示字段校验错误。
 * @param {number} status HTTP 状态码
 * @param {string} text 响应正文
 * @returns {string}
 */
export function formatDiscussionHttpError(status, text) {
  const raw = String(text || "").trim();
  if (!raw) return `请求失败 (${status})`;
  try {
    const j = JSON.parse(raw);
    if (Array.isArray(j.detail)) {
      const parts = j.detail
        .map((d) => {
          const loc = Array.isArray(d.loc)
            ? d.loc.filter((x) => x !== "body").join(".")
            : "";
          const msg = d.msg != null ? String(d.msg) : "";
          if (loc && msg) return `${loc}: ${msg}`;
          return msg || loc;
        })
        .filter(Boolean);
      if (parts.length) return `[${status}] ${parts.join("；")}`;
    }
    if (j.detail != null && typeof j.detail === "string") {
      return `[${status}] ${j.detail}`;
    }
    if (j.msg != null) return `[${status}] ${String(j.msg)}`;
    if (j.message != null) return `[${status}] ${String(j.message)}`;
  } catch {
    /* 非 JSON */
  }
  return raw.length > 800 ? `[${status}] ${raw.slice(0, 800)}…` : `[${status}] ${raw}`;
}

/**
 * POST `/api/send`：一次性 JSON 响应（非 SSE）。
 * @param {object} body 请求体
 * @param {AbortSignal} [signal]
 * @returns {Promise<any>}
 */
export async function postDiscussionSend(body, signal) {
  const url = `${DISCUSSION_BASE}/api/send`;
  const res = await fetch(url, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json"
    },
    body: JSON.stringify(body),
    signal
  });
  const text = await res.text().catch(() => "");
  let data = null;
  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    data = null;
  }
  if (!res.ok) {
    throw new Error(formatDiscussionHttpError(res.status, text));
  }
  return data;
}

/**
 * POST `/api/send`：解析 `text/event-stream`（按行 `data:` JSON）。
 * @param {object} body 请求体
 * @param {(evt: object) => void} onEvent 每解析出一条事件时回调
 * @param {AbortSignal} [signal]
 * @returns {Promise<void>}
 */
export async function postDiscussionSendStream(body, onEvent, signal) {
  const url = `${DISCUSSION_BASE}/api/send`;
  const res = await fetch(url, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "text/event-stream"
    },
    body: JSON.stringify(body),
    signal
  });
  if (!res.ok) {
    const t = await res.text().catch(() => "");
    throw new Error(formatDiscussionHttpError(res.status, t));
  }

  const ct = (res.headers.get("content-type") || "").toLowerCase();
  if (ct.includes("application/json") && !ct.includes("text/event-stream")) {
    const data = await res.json().catch(() => null);
    if (data && typeof onEvent === "function") onEvent(data);
    return;
  }

  if (!res.body) {
    throw new Error("响应不支持流式读取");
  }
  const reader = res.body.getReader();
  const decoder = new TextDecoder();
  let carry = "";
  const emitLineJson = (trimmed) => {
    if (!trimmed || typeof onEvent !== "function") return;
    if (trimmed.startsWith("data:")) {
      const payloadStr = trimmed.slice(5).trim();
      const evt = parseDiscussionEventPayload(payloadStr);
      if (evt) onEvent(evt);
      return;
    }
    if (trimmed.startsWith("{")) {
      const evt = parseDiscussionEventPayload(trimmed);
      if (evt) onEvent(evt);
    }
  };
  for (; ;) {
    const { done, value } = await reader.read();
    if (done) break;
    carry += decoder.decode(value, { stream: true });
    let idx;
    while ((idx = carry.indexOf("\n")) >= 0) {
      const line = carry.slice(0, idx).replace(/\r$/, "");
      carry = carry.slice(idx + 1);
      emitLineJson(line.trim());
    }
  }
  const tail = carry.trim();
  if (tail) emitLineJson(tail);
}

/**
 * GET `/api/personas`：获取可参与讨论的专家角色列表。
 * 请求参数：无
 * @returns {Promise<any>}
 */
export async function getPersonas() {
  const url = `${DISCUSSION_BASE}/api/personas`;
  const res = await fetch(url, {
    method: "POST",
    headers: { Accept: "application/json" }
  });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(
      data?.msg ||
      data?.message ||
      formatDiscussionHttpError(res.status, JSON.stringify(data))
    );
  }
  return data;
}

/**
 * POST `/api/save/role`：保存角色配置。
 * @param {object} payload 请求体，字段由后端约定
 * @returns {Promise<any>}
 */
export async function postSaveRoles(payload) {
  const url = `${DISCUSSION_BASE}/api/save/role`;
  const res = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json", Accept: "application/json" },
    body: JSON.stringify(payload)
  });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(
      data?.msg ||
      data?.message ||
      formatDiscussionHttpError(res.status, JSON.stringify(data))
    );
  }
  return data;
}

/**
 * POST `/api/del/message`：删除讨论消息。
 * @param {object} payload 请求体，字段由后端约定
 * @returns {Promise<any>}
 */
export async function postDelMessages(payload) {
  const url = `${DISCUSSION_BASE}/api/del/message`;
  const res = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json", Accept: "application/json" },
    body: JSON.stringify(payload)
  });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(
      data?.msg ||
      data?.message ||
      formatDiscussionHttpError(res.status, JSON.stringify(data))
    );
  }
  return data;
}

/**
 * GET `/api/get/chat/history/list`：查询用户历史会话列表。
 * @param {string} userId
 * @returns {Promise<any>}
 */
export async function getChatHistoryList(userId) {
  const uid = encodeURIComponent(String(userId || ""));
  const url = `${DISCUSSION_BASE}/api/get/chat/history/list?user_id=${uid}`;
  const res = await fetch(url, {
    method: "GET",
    headers: { Accept: "application/json" }
  });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(
      data?.msg ||
      data?.message ||
      formatDiscussionHttpError(res.status, JSON.stringify(data))
    );
  }
  return data;
}

/**
 * GET `/api/get/chat/history/context`：查询指定会话历史详情。
 * @param {string} userId
 * @param {string} chatId
 * @returns {Promise<any>}
 */
export async function getChatHistoryContext(userId, chatId) {
  const uid = encodeURIComponent(String(userId || ""));
  const cid = encodeURIComponent(String(chatId || ""));
  const url = `${DISCUSSION_BASE}/api/get/chat/history/context?user_id=${uid}&chat_id=${cid}`;
  const res = await fetch(url, {
    method: "GET",
    headers: { Accept: "application/json" }
  });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(
      data?.msg ||
      data?.message ||
      formatDiscussionHttpError(res.status, JSON.stringify(data))
    );
  }
  return data;
}

/**
 * POST `/api/del/chat/history`：删除某个历史会话。
 * @param {object} payload { user_id, chat_id }
 * @returns {Promise<any>}
 */
export async function postDelChatHistory(payload) {
  const url = `${DISCUSSION_BASE}/api/del/chat/history`;
  const res = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json", Accept: "application/json" },
    body: JSON.stringify(payload || {})
  });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(
      data?.msg ||
      data?.message ||
      formatDiscussionHttpError(res.status, JSON.stringify(data))
    );
  }
  return data;
}

/**
 * 构造长连接 SSE：`GET /stream?user_id=&chat_id=`。
 * @param {string} userId
 * @param {string} chatId
 * @returns {string} 完整 URL
 */
export function getDiscussionStreamUrl(userId, chatId) {
  const uid = encodeURIComponent(String(userId || ""));
  const cid = encodeURIComponent(String(chatId || ""));
  return `${DISCUSSION_BASE}/stream?user_id=${uid}&chat_id=${cid}`;
}

/**
 * 是否启用 GET `/stream` 的 `EventSource`（见文件顶部 `DISCUSSION_SSE_STREAM_ENABLED`）。
 * @returns {boolean}
 */
export function isDiscussionSseStreamEnabled() {
  return DISCUSSION_SSE_STREAM_ENABLED;
}
