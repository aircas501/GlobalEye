/**
 * 历史船舶态势：按时间范围查询船舶轨迹/位置等。
 * 默认经开发代理 `/vessel-api` 转发至船舶后端。
 */
import { createHttpClient, normalizeApiBase } from "./http";

const VESSEL_API_BASE = normalizeApiBase(
  process.env.VUE_APP_VESSEL_API_BASE || "/vessel-api"
);
const vesselHttp = createHttpClient(VESSEL_API_BASE);

/**
 * 按时间范围查询船舶数据。
 * @param {string|number} start 开始时间
 * @param {string|number} end 结束时间
 * @returns {Promise<any>}
 */
export function getVesselsByTimeRange(start, end) {
  return vesselHttp.get("/time/range", {
    params: { start, end }
  });
}
