/**
 * 市场分析：根据新闻内容分析受影响的市场（标的/板块等）。
 * 经 `VUE_APP_RECOMMENDATION_API_BASE`（默认 `/recommendation-api`）转发。
 */
import { createHttpClient, normalizeApiBase } from "./http";

const MARKET_ANALYSIS_API_BASE = normalizeApiBase(
  process.env.VUE_APP_RECOMMENDATION_API_BASE || "/recommendation-api"
);
const marketAnalysisHttp = createHttpClient(MARKET_ANALYSIS_API_BASE);

/**
 * @param {Record<string, unknown>} payload 如 title、content、publishTime 等
 * @returns {Promise<any>}
 */
export function analyzeMarketsFromNews(payload) {
  return marketAnalysisHttp.post("/news-stock/analyze", payload || {});
}
