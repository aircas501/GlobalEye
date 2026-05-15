import axios from "axios";
import { PLUGIN_API_USERNAME } from "./pluginConstants.js";
import {
  joinIntelligentizationAxiosPath,
  resolveIntelligentizationApiBase
} from "./intelligentizationApiBase.js";

/**
 * 与 aicode、讨论室共用智能化基址（`resolveIntelligentizationApiBase`）。
 */
const base = resolveIntelligentizationApiBase();

export const pluginHttp = axios.create({
  baseURL: base,
  timeout: 120000
});

/**
 * baseURL 含路径时 axios 对以 `/` 开头的 url 会从站点根拼接，统一去掉前导斜杠。
 * @param {string} path 如 `/api/plugins`
 */
function pluginGetPath(path) {
  const p = path.startsWith("/") ? path : `/${path}`;
  return joinIntelligentizationAxiosPath(p);
}

/**
 * @param {string} [username]
 */
export function fetchPluginsList(username = PLUGIN_API_USERNAME) {
  return pluginHttp.get(pluginGetPath("/api/plugins"), { params: { username } });
}

export function fetchPluginDetail(pluginId, username = PLUGIN_API_USERNAME) {
  return pluginHttp.get(pluginGetPath("/api/plugins/detail"), {
    params: { plugin_id: pluginId, username }
  });
}

export function postPluginProxy(body) {
  return pluginHttp.post(pluginGetPath("/api/plugin-proxy"), body);
}

/**
 * 上传 .vue 组件（multipart/form-data：file、username）
 * @param {File} file
 * @param {string} [username]
 */
export function uploadPluginVue(file, username = PLUGIN_API_USERNAME) {
  const form = new FormData();
  form.append("file", file);
  form.append("username", username);
  return pluginHttp.post(pluginGetPath("/api/plugins/upload"), form);
}

/**
 * 设置组件后端 URL 映射（Redis）
 * @param {{ username?: string, plugin_name: string, url_prefix: string }} body
 */
export function postPluginUrlMapping(body) {
  const b = body && typeof body === "object" ? body : {};
  return pluginHttp.post(pluginGetPath("/api/plugin-url"), {
    username: b.username != null ? String(b.username) : PLUGIN_API_USERNAME,
    plugin_name: String(b.plugin_name || "").trim(),
    url_prefix: String(b.url_prefix || "").trim()
  });
}

/**
 * 查询组件 URL 映射
 * @param {{ username?: string, plugin_name: string }} params
 */
export function getPluginUrlMapping(params) {
  const p = params && typeof params === "object" ? params : {};
  return pluginHttp.get(pluginGetPath("/api/plugin-url"), {
    params: {
      username: p.username != null ? String(p.username) : PLUGIN_API_USERNAME,
      plugin_name: String(p.plugin_name || "").trim()
    }
  });
}

/**
 * 删除组件 URL 映射
 * @param {{ username?: string, plugin_name: string }} params
 */
export function deletePluginUrlMapping(params) {
  const p = params && typeof params === "object" ? params : {};
  return pluginHttp.delete(pluginGetPath("/api/plugin-url"), {
    params: {
      username: p.username != null ? String(p.username) : PLUGIN_API_USERNAME,
      plugin_name: String(p.plugin_name || "").trim()
    }
  });
}
