/**
 * 卫星态势：时间段查询、过境/异动目的分析等。
 * - 主服务：开发环境经代理前缀 `/sat-api`。
 */
import { createHttpClient, normalizeApiBase } from "./http";

const SATELLITE_API_BASE = normalizeApiBase(
  process.env.VUE_APP_SAT_API_BASE || "/sat-api"
);
const satelliteHttp = createHttpClient(SATELLITE_API_BASE);

const OVERPASS_API_BASE = normalizeApiBase(
  process.env.VUE_APP_OVERPASS_API_BASE || "/overpass-api"
);
const overpassHttp = createHttpClient(OVERPASS_API_BASE);

/**
 * 按时间范围查询卫星数据。
 * @param {string|number} start 开始时间
 * @param {string|number} end 结束时间
 * @returns {Promise<any>}
 */
export function getSatellitesByTimeRange(start, end) {
  return satelliteHttp.get("/sat/query/range", {
    params: { start, end }
  });
}

/**
 * 卫星变轨对比分析（前后 TLE + 国家过境对比）。
 * @param {object} payload
 * @param {string} payload.oldTleLine1
 * @param {string} payload.oldTleLine2
 * @param {string} payload.newTleLine1
 * @param {string} payload.newTleLine2
 * @param {number} [payload.beforeDurationHours]
 * @param {number} [payload.afterDurationHours]
 * @param {number} [payload.stepSeconds]
 * @param {number} [payload.mergeThresholdSeconds]
 * @returns {Promise<any>}
 */
export function postSatelliteOrbitChangeComparison(payload) {
  return overpassHttp.post("/satellite/orbit-change-comparison", payload);
}
