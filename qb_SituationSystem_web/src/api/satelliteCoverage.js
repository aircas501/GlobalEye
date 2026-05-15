/**
 * 卫星覆盖：单时刻星下点/覆盖范围计算。
 * 与 `satellite.js` 中过境分析共用 `8081` 一类后端（REST 根含 `/api`）。
 */
import { createHttpClient, normalizeApiBase } from "./http";

const COVERAGE_API_BASE = normalizeApiBase(
  process.env.VUE_APP_OVERPASS_API_BASE || "/overpass-api"
);
const coverageHttp = createHttpClient(COVERAGE_API_BASE);

/**
 * 将日期时间格式整理为后端可解析形式。
 * @param {string|undefined|null} dateTime
 * @returns {string}
 */
function normalizeDateTime(dateTime) {
  const s = String(dateTime ?? "").trim();
  if (!s) return "";
  if (/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/.test(s)) return `${s}.000`;
  if (/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{1,3}$/.test(s)) {
    const parts = s.split(".");
    const ms = parts[1] || "0";
    const padded = String(ms).padEnd(3, "0").slice(0, 3);
    return `${parts[0]}.${padded}`;
  }
  if (s.includes("T")) return s.replace("T", " ").replace(/Z$/, "");
  return s;
}

/**
 * 根据 TLE 与目标时刻计算覆盖；后端要求 POST，参数走 query。
 * @param {object} args
 * @param {string} args.tleLine1
 * @param {string} args.tleLine2
 * @param {string} args.dateTime 目标时刻（北京时间）
 * @returns {Promise<any>}
 */
export function calculateSatelliteCoverage({ tleLine1, tleLine2, dateTime }) {
  const params = {
    tleLine1: String(tleLine1 ?? "").trim(),
    tleLine2: String(tleLine2 ?? "").trim(),
    dateTime: normalizeDateTime(dateTime)
  };
  return coverageHttp.post("/satellite/calculate-coverage", {}, { params });
}
