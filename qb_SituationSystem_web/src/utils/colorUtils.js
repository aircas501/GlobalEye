/**
 * 颜色工具（态势 / OpenLayers 样式用）
 * @module utils/colorUtils
 */

export function hexToRgb(hex) {
  const normalized = String(hex || "").replace("#", "");
  if (normalized.length !== 6) return { r: 255, g: 255, b: 255 };
  const r = parseInt(normalized.slice(0, 2), 16);
  const g = parseInt(normalized.slice(2, 4), 16);
  const b = parseInt(normalized.slice(4, 6), 16);
  return { r, g, b };
}

/** 将 #RRGGBB 转为 rgba() 字符串，供 Canvas / OL Fill 等使用 */
export function hexToRgba(hex, alpha) {
  const { r, g, b } = hexToRgb(hex);
  return `rgba(${r}, ${g}, ${b}, ${alpha})`;
}

export function clampNumber(n, min, max) {
  return Math.max(min, Math.min(max, n));
}
