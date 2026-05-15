/**
 * 地区热点事件相关接口：
 * - 地区 -> 热点事件
 * - 热点事件 -> 新闻摘要列表
 * - 新闻ID -> AI解析正文（含 target 坐标标注）
 */
import { createHttpClient, normalizeApiBase } from "./http";

const HOT_EVENTS_API_BASE = normalizeApiBase("/hot-api");
const hotEventsHttp = createHttpClient(HOT_EVENTS_API_BASE);

/**
 * 根据地区查询热点事件。
 * @param {string} locationName 地区/国家名称
 * @param {string} topic 领域（军事/政治/经济）
 * @returns {Promise<any>}
 */
export function getHotEventsByLocation(locationName, topic) {
  const name = encodeURIComponent(String(locationName || "").trim());
  const topicText = String(topic || "").trim();
  const query = topicText ? `?topic=${encodeURIComponent(topicText)}` : "";
  return hotEventsHttp.get(`/hot-events/by-location-filtered/${name}${query}`);
}

/**
 * 根据热点事件名称查询相关新闻摘要。
 * @param {string} eventName 热点事件名称
 * @param {string} topic 领域（军事/政治/经济）
 * @returns {Promise<any>}
 */
export function getHotEventArticles(eventName, topic) {
  const name = encodeURIComponent(String(eventName || "").trim());
  const topicText = String(topic || "").trim();
  const query = topicText ? `?topic=${encodeURIComponent(topicText)}` : "";
  return hotEventsHttp.get(`/hot-events/articles-filtered/${name}${query}`);
}

/**
 * 根据新闻 MySQL ID 查询 AI 解析正文。
 * @param {number|string} articleId 新闻 ID（MySQL）
 * @returns {Promise<any>}
 */
export function getParsedArticleContent(articleId) {
  const id = encodeURIComponent(String(articleId || "").trim());
  return hotEventsHttp.get(`/article/parse/${id}`);
}
