/**
 * 智能化统一后端（9092）：
 * - 开发：`/intelligentization-api` 由 vue.config.js 代理，剥前缀后转发到 9092 根路径。
 * - 生产：`.env.production` 同样使用 `/intelligentization-api`，由 Nginx/网关反代到 9092 并剥前缀（与 dev 行为一致）。
 *
 * `VUE_APP_AICODE_API_BASE`、`VUE_APP_DISCUSSION_API_BASE`、`VUE_APP_PLUGINS_API_BASE`
 * 应配置为同一值；亦可使用 `VUE_APP_INTELLIGENTIZATION_API_BASE` 单独覆盖。
 */

export function stripTrailingSlash(url) {
  return String(url || "").replace(/\/+$/, "");
}

const DEFAULT_BASE = "/intelligentization-api";

/**
 * @returns {string}
 */
export function resolveIntelligentizationApiBase() {
  const env = typeof process !== "undefined" && process.env ? process.env : {};
  const raw =
    env.VUE_APP_INTELLIGENTIZATION_API_BASE ||
    env.VUE_APP_AICODE_API_BASE ||
    env.VUE_APP_DISCUSSION_API_BASE ||
    env.VUE_APP_PLUGINS_API_BASE;
  return stripTrailingSlash(String(raw || DEFAULT_BASE));
}

/**
 * axios 在 baseURL 含路径时，若请求 path 以 `/` 开头会从「站点根」拼接；统一去掉前导斜杠再交给 axios。
 * @param {string} absoluteApiPath 如 `/api/aicode/health`
 * @returns {string}
 */
export function joinIntelligentizationAxiosPath(absoluteApiPath) {
  const p = String(absoluteApiPath || "").trim();
  if (!p) return p;
  return p.replace(/^\/+/, "");
}
