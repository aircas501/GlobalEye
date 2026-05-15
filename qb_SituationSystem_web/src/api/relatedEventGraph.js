/**
 * 关联事件图谱接口：按事件标题与国家查询图谱节点/关系数据。
 */
import { createHttpClient, stripTrailingSlash } from "./http";

const RELATED_EVENT_GRAPH_BASE = stripTrailingSlash(
  process.env.VUE_APP_RELATED_EVENT_GRAPH_API_BASE || "/graph-api"
);

const relatedEventGraphHttp = createHttpClient(RELATED_EVENT_GRAPH_BASE);

/**
 * 查询关联事件图谱。
 * @param {object} params 查询参数（eventTitle/country 等）
 * @returns {Promise<any>}
 */
export function getRelatedEventGraph(params) {
  return relatedEventGraphHttp.get("/graph/query", { params });
}
