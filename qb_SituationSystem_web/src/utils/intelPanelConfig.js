/**
 * 右侧面板（情报面板）网格：默认布局与校验工具。
 * 不写入 localStorage，刷新页面后恢复内置默认（或由当前运行态传入设置弹窗）。
 */

export const INTEL_PANEL_COL_MIN = 1;
export const INTEL_PANEL_COL_MAX = 2;
export const INTEL_PANEL_ROW_MIN = 2;
export const INTEL_PANEL_ROW_MAX = 10;

/** 右侧情报区默认：2 列 × 3 行（新闻/监控已固定在左侧底部，不在此网格） */
export const DEFAULT_INTEL_GRID = { cols: 2, rows: 3 };

/** 旧版网格键，已从产品移除；出现在已保存配置中时整格丢弃 */
const DEPRECATED_INTEL_PANEL_KEYS = new Set([
  "ai-intel",
  "live-news",
  "live-monitor",
  "risk-country",
  "space-threat",
  "sat-transit",
  "sat-anomaly",
  "sat-pass"
]);

export const DEFAULT_INTEL_PANELS = [
  { id: "hot-1", componentKey: "hot", x: 1, y: 1, w: 2, h: 1 },
  { id: "market-stock-1", componentKey: "market-stock", x: 1, y: 2, w: 1, h: 1 },
  { id: "rel-event-1", componentKey: "rel-event", x: 2, y: 2, w: 1, h: 1 }
];

function clampInt(n, min, max) {
  const v = Math.round(Number(n));
  if (!Number.isFinite(v)) return min;
  return Math.min(max, Math.max(min, v));
}

export function clampIntelCols(cols) {
  return clampInt(cols, INTEL_PANEL_COL_MIN, INTEL_PANEL_COL_MAX);
}

export function clampIntelRows(rows) {
  return clampInt(rows, INTEL_PANEL_ROW_MIN, INTEL_PANEL_ROW_MAX);
}

/** 将面板列表摊平为「每格一个组件」草稿（跨格面板：所占各格均为同一 componentKey） */
export function panelsToCells(cols, rows, panels) {
  const c = clampIntelCols(cols);
  const r = clampIntelRows(rows);
  const cells = Array(c * r).fill("");
  const list = Array.isArray(panels) ? panels : [];
  for (let i = 0; i < list.length; i += 1) {
    const p = list[i] || {};
    const key = p.componentKey != null ? String(p.componentKey).trim() : "";
    if (!key || DEPRECATED_INTEL_PANEL_KEYS.has(key)) continue;
    const x0 = clampInt(p.x, 1, c);
    const y0 = clampInt(p.y, 1, r);
    const w = Math.max(1, Math.round(Number(p.w)) || 1);
    const h = Math.max(1, Math.round(Number(p.h)) || 1);
    for (let dy = 0; dy < h; dy += 1) {
      for (let dx = 0; dx < w; dx += 1) {
        const cx = x0 + dx;
        const cy = y0 + dy;
        if (cx >= 1 && cx <= c && cy >= 1 && cy <= r) {
          const idx = (cy - 1) * c + (cx - 1);
          const cellToken =
            key === "personal-plugin"
              ? (() => {
                  const pid = p.pluginId != null ? String(p.pluginId).trim() : "";
                  return pid ? `personal-plugin|${pid}` : "personal-plugin";
                })()
              : key;
          cells[idx] = cellToken;
        }
      }
    }
  }
  return cells;
}

/** 由每格组件生成 1×1 面板列表（空串表示空格） */
export function buildPanelsFromCells(cols, rows, cells) {
  const c = clampIntelCols(cols);
  const r = clampIntelRows(rows);
  const list = Array.isArray(cells) ? cells.slice(0, c * r) : [];
  while (list.length < c * r) list.push("");
  const layout = [];
  for (let i = 0; i < c * r; i += 1) {
    const key = list[i] != null ? String(list[i]).trim() : "";
    if (!key || DEPRECATED_INTEL_PANEL_KEYS.has(key)) continue;
    const x = (i % c) + 1;
    const y = Math.floor(i / c) + 1;
    let componentKey = key;
    let pluginId = "";
    if (key.startsWith("personal-plugin|")) {
      componentKey = "personal-plugin";
      pluginId = key.slice("personal-plugin|".length).trim();
    }
    const row = {
      id: `grid-${x}-${y}`,
      componentKey,
      x,
      y,
      w: 1,
      h: 1
    };
    if (pluginId) row.pluginId = pluginId;
    layout.push(row);
  }
  return layout;
}

export function normalizeStoredPanels(cols, rows, panels) {
  const c = clampIntelCols(cols);
  const r = clampIntelRows(rows);
  const out = [];
  const raw = Array.isArray(panels) ? panels : [];
  for (let i = 0; i < raw.length; i += 1) {
    const p = raw[i] || {};
    const key = p.componentKey != null ? String(p.componentKey).trim() : "";
    if (!key || DEPRECATED_INTEL_PANEL_KEYS.has(key)) continue;
    let x = clampInt(p.x, 1, c);
    let y = clampInt(p.y, 1, r);
    let w = Math.max(1, Math.round(Number(p.w)) || 1);
    let h = Math.max(1, Math.round(Number(p.h)) || 1);
    w = Math.min(w, c - x + 1);
    h = Math.min(h, r - y + 1);
    const pluginId = p.pluginId != null ? String(p.pluginId).trim() : "";
    const pluginFileName = p.pluginFileName != null ? String(p.pluginFileName).trim() : "";
    const row = {
      id: p.id != null ? String(p.id) : `panel-${i + 1}`,
      componentKey: key,
      x,
      y,
      w,
      h
    };
    if (pluginId) row.pluginId = pluginId;
    if (pluginFileName) row.pluginFileName = pluginFileName;
    out.push(row);
  }
  return out;
}

export function getDefaultIntelConfig() {
  return {
    v: 1,
    cols: DEFAULT_INTEL_GRID.cols,
    rows: DEFAULT_INTEL_GRID.rows,
    panels: DEFAULT_INTEL_PANELS.map((p) => ({ ...p }))
  };
}
