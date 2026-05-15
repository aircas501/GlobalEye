/** @typedef {{ onToolCall?: (d: unknown) => void, onToolResult?: (d: unknown) => void, onBashPrompt?: (d: unknown) => void, onLlmReply?: (d: unknown) => void, onDone?: (d: unknown) => void, onError?: (d: unknown) => void }} SseHandlers */

import { resolveIntelligentizationApiBase } from "./intelligentizationApiBase.js";

let eventSource = null;
/** @type {SseHandlers} */
let handlers = {};
let intentionallyClosed = false;

/**
 * @param {string} chatId
 * @param {SseHandlers} h
 */
export function connectAicodeSse(chatId, h) {
  if (eventSource) disconnectAicodeSse();
  handlers = h || {};
  intentionallyClosed = false;

  const base = resolveIntelligentizationApiBase();
  const url = `${base}/api/aicode/stream?chat_id=${encodeURIComponent(chatId)}`;

  eventSource = new EventSource(url);

  eventSource.onmessage = (e) => {
    try {
      const msg = JSON.parse(e.data);
      const { event, data } = msg;
      if (event === "tool_call" && handlers.onToolCall) handlers.onToolCall(data);
      else if (event === "tool_result" && handlers.onToolResult) handlers.onToolResult(data);
      else if (event === "bash_prompt" && handlers.onBashPrompt) handlers.onBashPrompt(data);
      else if (event === "llm_reply" && handlers.onLlmReply) handlers.onLlmReply(data);
      else if (event === "done" && handlers.onDone) handlers.onDone(data);
      else if (event === "error" && handlers.onError) handlers.onError(data);
    } catch (err) {
      console.error("SSE parse error:", err);
    }
  };

  eventSource.onerror = () => {
    if (intentionallyClosed) return;
    if (handlers.onError) {
      handlers.onError({ message: "SSE 连接断开" });
    }
  };
}

export function disconnectAicodeSse() {
  intentionallyClosed = true;
  if (eventSource) {
    eventSource.close();
    eventSource = null;
  }
  handlers = {};
}
