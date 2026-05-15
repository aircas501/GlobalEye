import axios from "axios";
import {
  joinIntelligentizationAxiosPath,
  resolveIntelligentizationApiBase
} from "./intelligentizationApiBase.js";

const base = resolveIntelligentizationApiBase();

export const aicodeHttp = axios.create({
  baseURL: base,
  timeout: 30000
});

export function aicodePost(path, data) {
  return aicodeHttp.post(joinIntelligentizationAxiosPath(path), data);
}

/** @param {Record<string, unknown>} [extra] 合并进 axios 配置，如 `{ signal }` */
export function aicodeGet(path, params, extra) {
  const cfg = extra && typeof extra === "object" ? { ...extra } : {};
  if (params !== undefined) cfg.params = params;
  return aicodeHttp.get(joinIntelligentizationAxiosPath(path), cfg);
}
