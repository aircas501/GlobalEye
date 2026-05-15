/**
 * 态势实时 WebSocket
 *
 * 协议：连接成功后按 dataType 逐个发送订阅：
 *   { action: "subscribe", dataType: "satellite" }
 *   { action: "subscribe", dataType: "aircraft" }
 */

/**
 * 将 `/path` 形式地址转换为当前站点的 ws/wss 绝对地址，便于走 devServer 代理。
 * @param {string} url
 * @returns {string}
 */
function resolveWebSocketUrl(url) {
  const raw = String(url || "").trim();
  if (!raw) return raw;
  if (/^wss?:\/\//i.test(raw)) return raw;
  if (raw.startsWith("/") && typeof window !== "undefined" && window.location) {
    const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
    return `${protocol}//${window.location.host}${raw}`;
  }
  return raw;
}

/** 开发环境走 devServer `vue.config.js` 中 `/satellite-ws` 代理；生产可配相对路径或完整 wss:// */
const SATELLITE_WS_URL =
  String(process.env.VUE_APP_SATELLITE_WS_URL || "/satellite-ws/ws").trim();

function safeSendJson(ws, payload) {
  if (!ws) return;
  try {
    ws.send(JSON.stringify(payload));
  } catch (e) {
    console.warn("[SatelliteWS] send subscribe failed", e);
  }
}

/**
 * @param {object} options
 * @param {(data: string) => void} options.onMessage
 * @param {() => void} [options.onOpen]
 * @param {() => void} [options.onClose]
 * @returns {WebSocket|null}
 */
export function createSatelliteSituationWebSocket(options) {
  const { onMessage, onOpen, onClose, subscribeTypes } = options || {};
  if (typeof WebSocket === "undefined") return null;
  const wsUrl = resolveWebSocketUrl(SATELLITE_WS_URL);
  if (!wsUrl) {
    console.error("[SatelliteWS] missing WebSocket URL (set VUE_APP_SATELLITE_WS_URL or use default /satellite-ws/ws)");
    return null;
  }

  let ws;
  try {
    ws = new WebSocket(wsUrl);
  } catch (e) {
    console.error("[SatelliteWS] construct failed", e);
    return null;
  }

  ws.onopen = () => {
    console.info("[SatelliteWS] connected", wsUrl);
    const topics = Array.isArray(subscribeTypes) && subscribeTypes.length
      ? subscribeTypes
      : ["satellite", "aircraft"];
    topics.forEach((topicRaw) => {
      const topic = String(topicRaw || "").trim().toLowerCase();
      if (!topic) return;
      safeSendJson(ws, { action: "subscribe", dataType: topic });
    });
    if (typeof onOpen === "function") onOpen();
  };

  ws.onmessage = (ev) => {
    if (typeof onMessage === "function") onMessage(ev.data);
  };

  ws.onerror = (err) => {
    console.error("[SatelliteWS] error", err);
  };

  ws.onclose = () => {
    console.info("[SatelliteWS] closed");
    if (typeof onClose === "function") onClose();
  };

  return ws;
}
