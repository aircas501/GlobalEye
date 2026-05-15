/**
 * HTTP 客户端工厂：统一 axios 超时与响应拦截（成功时返回 `response.data`）。
 *
 * 业务接口按领域拆在同级其它 `.js` 文件中；各文件顶部直接写明对应后端的 baseURL，
 * 便于对接多个后端地址，而不依赖 `.env` 中的接口基址变量。
 */
import axios from "axios";

/** 去掉末尾 `/`，避免与路径拼接时出现双斜杠。 */
export function stripTrailingSlash(url) {
  return String(url || "").replace(/\/+$/, "");
}

/**
 * 规范 REST 根路径：
 * - 以 `/` 开头：视为前端开发代理前缀（如 `/sat-api`），不追加 `/api`。
 * - `http(s)://` 绝对地址：若无 `/api` 后缀则自动补上，与当前后端约定一致。
 */
export function normalizeApiBase(url) {
  const normalized = stripTrailingSlash(url);
  if (!normalized) return normalized;
  if (normalized.startsWith("/")) return normalized;
  return /\/api$/i.test(normalized) ? normalized : `${normalized}/api`;
}

/**
 * @param {string} baseURL axios baseURL（已由调用方按后端约定处理好）
 */
export function createHttpClient(baseURL) {
  const client = axios.create({
    baseURL,
    timeout: 150000
  });

  client.interceptors.response.use(
    (response) => response.data,
    (error) => {
      const msg =
        error?.response?.data?.message ||
        error?.message ||
        "网络请求失败，请稍后重试";
      return Promise.reject(new Error(msg));
    }
  );

  return client;
}
