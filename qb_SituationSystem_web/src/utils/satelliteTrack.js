/**
 * 卫星星下点轨迹计算模块
 * 基于 TLE（两行轨道根数）使用 SGP4 算法计算卫星地面投影轨迹
 *
 * @module satelliteTrack
 * @requires satellite.js
 *
 * 主要函数：
 *   - computeGroundPoint(line1, line2, date) - 计算单个时刻的星下点经纬度
 *   - computeGroundTrack(line1, line2, options) - 计算一段时间内的星下点轨迹数组
 *
 * TLE 格式示例：
 *   line1: "1 25544U 98067A   21275.52525463 ..."
 *   line2: "2 25544  51.6434 ..."
 */
// 入口由 vue.config.js resolve.alias 固定到 v4 的 lib/index.js
import * as satellite from "satellite.js";

/**
 * 单次计算指定时间的星下点经纬度
 * @param {string} line1 TLE 第一行
 * @param {string} line2 TLE 第二行
 * @param {Date} [date=new Date()] 计算时刻
 * @returns {{ lon:number, lat:number } | null}
 */
export function computeGroundPoint(line1, line2, date = new Date()) {
  if (!line1 || !line2) return null
  const satrec = satellite.twoline2satrec(line1.trim(), line2.trim())

  const pv = satellite.propagate(satrec, date)
  if (!pv || !pv.position) return null

  const gmst = satellite.gstime(date)
  const geo = satellite.eciToGeodetic(pv.position, gmst)

  const lon = satellite.degreesLong(geo.longitude)
  const lat = satellite.degreesLat(geo.latitude)

  if (!Number.isFinite(lon) || !Number.isFinite(lat)) return null
  return { lon, lat }
}

/**
 * 单次计算星下点经纬度与大地高度（km），用于低轨/高轨图标区分等
 * @param {string} line1
 * @param {string} line2
 * @param {Date} [date=new Date()]
 * @returns {{ lon:number, lat:number, altitudeKm:number } | null}
 */
export function computeGroundState(line1, line2, date = new Date()) {
  if (!line1 || !line2) return null
  const satrec = satellite.twoline2satrec(line1.trim(), line2.trim())

  const pv = satellite.propagate(satrec, date)
  if (!pv || !pv.position) return null

  const gmst = satellite.gstime(date)
  const geo = satellite.eciToGeodetic(pv.position, gmst)

  const lon = satellite.degreesLong(geo.longitude)
  const lat = satellite.degreesLat(geo.latitude)
  const altitudeKm = Number(geo.height)

  if (!Number.isFinite(lon) || !Number.isFinite(lat) || !Number.isFinite(altitudeKm)) return null
  return { lon, lat, altitudeKm }
}

/**
 * 从 TLE 第二行解析 mean motion（每天圈数）
 * @param {string} line2
 * @returns {number|null}
 */
export function parseMeanMotionFromTle2(line2) {
  const s = String(line2 || "").trim();
  if (!s) return null;
  const parts = s.split(/\s+/);
  const raw = parts[parts.length - 1];
  const n = Number(raw);
  if (!Number.isFinite(n) || n <= 0) return null;
  return n;
}

/**
 * 根据 mean motion 估算轨道周期（分钟）
 * @param {string} line2 TLE 第二行
 * @returns {number}
 */
export function computeOrbitPeriodMinutes(line2) {
  const mm = parseMeanMotionFromTle2(line2);
  if (!mm) return 95;
  return 1440 / mm;
}

/**
 * 采样完整一圈空间轨道（经纬高，非星下点轨迹）
 * @param {string} line1
 * @param {string} line2
 * @param {Object} options
 * @param {Date} [options.start=new Date()]
 * @param {number} [options.stepSeconds=30]
 * @returns {Array<{ lon:number, lat:number, altitudeKm:number, date:Date }>}
 */
export function computeOrbitTrackByPeriod(line1, line2, {
  start = new Date(),
  stepSeconds = 30,
  closeLoop = true,
  debug = false,
  debugTag = ""
} = {}) {
  const log = (...args) => {
    if (!debug) return;
    const tag = debugTag ? `[OrbitTrack:${debugTag}]` : "[OrbitTrack]";
    // eslint-disable-next-line no-console
    console.debug(tag, ...args);
  };
  if (!line1 || !line2) return [];
  const periodMinutes = computeOrbitPeriodMinutes(line2);
  const totalSeconds = Math.max(1, periodMinutes * 60);
  const step = Math.max(1, Number(stepSeconds) || 30);
  const totalSteps = Math.max(2, Math.ceil(totalSeconds / step));
  const out = [];
  log("sampling-start", {
    periodMinutes,
    totalSeconds,
    stepSeconds: step,
    totalSteps,
    closeLoop
  });

  for (let i = 0; i <= totalSteps; i += 1) {
    const t = Math.min(totalSeconds, i * step);
    const date = new Date(start.getTime() + t * 1000);
    const state = computeGroundState(line1, line2, date);
    if (!state) continue;
    out.push({
      lon: state.lon,
      lat: state.lat,
      altitudeKm: state.altitudeKm,
      date
    });
  }
  log("sampling-finished", { points: out.length });
  if (closeLoop && out.length >= 2) {
    const first = out[0];
    const last = out[out.length - 1];
    const dLon = Math.abs(Number(last.lon) - Number(first.lon));
    const dLat = Math.abs(Number(last.lat) - Number(first.lat));
    const dAlt = Math.abs(Number(last.altitudeKm) - Number(first.altitudeKm));
    // 周期采样理论上首尾应接近；若仍有微小误差，补首点保证几何闭环
    if (dLon > 1e-6 || dLat > 1e-6 || dAlt > 1e-6) {
      log("close-loop-append-first-point", { dLon, dLat, dAlt });
      out.push({
        lon: first.lon,
        lat: first.lat,
        altitudeKm: first.altitudeKm,
        date: new Date(first.date)
      });
    } else {
      log("close-loop-no-append", { dLon, dLat, dAlt });
    }
  }
  log("sampling-end", { finalPoints: out.length });
  return out;
}

/**
 * 根据 TLE 计算一段时间内的星下点轨迹（经纬度）
 * @param {string} line1 TLE 第一行
 * @param {string} line2 TLE 第二行
 * @param {Object} options
 * @param {Date} [options.start=new Date()] 起始时间
 * @param {number} [options.minutes=90] 推演时长（分钟）
 * @param {number} [options.stepSeconds=60] 采样步长（秒）
 * @returns {Array<{ lon:number, lat:number, date:Date }>}
 */
export function computeGroundTrack(line1, line2, {
  start = new Date(),
  minutes = 90,
  stepSeconds = 60
} = {}) {
  if (!line1 || !line2) return []

  const result = []
  const totalSteps = Math.floor((minutes * 60) / stepSeconds)

  for (let i = 0; i <= totalSteps; i++) {
    const date = new Date(start.getTime() + i * stepSeconds * 1000)
    const p = computeGroundPoint(line1, line2, date)
    if (!p) continue
    result.push({ lon: p.lon, lat: p.lat, date })
  }

  return result
}


