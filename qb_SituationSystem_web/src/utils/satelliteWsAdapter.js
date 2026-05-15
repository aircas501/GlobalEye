/**
 * 卫星态势 WebSocket 消息与业务行数据的适配（INFO/WARN、经纬度单位、TLE 等）
 */
import { computeGroundState } from "@/utils/satelliteTrack";

/**
 * 从 TLE 第一行解析 NORAD 目录号（如 "1 18951U ..." → 18951）
 * @param {string} line1
 * @returns {number|null}
 */
export function parseNoradCatalogFromTleLine1(line1) {
  if (!line1 || typeof line1 !== "string") return null;
  const m = line1.trim().match(/^\d+\s+(\d+)/);
  if (!m) return null;
  const n = parseInt(m[1], 10);
  return Number.isFinite(n) ? n : null;
}

function getLonLatUnit() {
  const u = String(
    (typeof process !== "undefined" &&
      process.env &&
      process.env.VUE_APP_SATELLITE_WS_LONLAT_UNIT) ||
      "auto"
  ).toLowerCase();
  if (u === "rad" || u === "deg" || u === "auto") return u;
  return "auto";
}

/**
 * 将接口中的纬度、经度转为度（WGS84）。
 * 支持环境变量 VUE_APP_SATELLITE_WS_LONLAT_UNIT = auto | deg | rad
 * @param {number} lat
 * @param {number} lon
 * @returns {{ lat: number, lon: number } | null}
 */
export function wsLatLonToDegrees(lat, lon) {
  const la = Number(lat);
  const lo = Number(lon);
  if (!Number.isFinite(la) || !Number.isFinite(lo)) return null;

  const unit = getLonLatUnit();
  if (unit === "rad") {
    return { lat: (la * 180) / Math.PI, lon: (lo * 180) / Math.PI };
  }
  if (unit === "deg") {
    return { lat: la, lon: lo };
  }
  // auto：经度接近 π 且落在弧度合理范围内时，按弧度解析（否则按度）
  if (
    Math.abs(lo) > 3.05 &&
    Math.abs(lo) <= Math.PI + 0.25 &&
    Math.abs(la) <= Math.PI / 2 + 0.01
  ) {
    return { lat: (la * 180) / Math.PI, lon: (lo * 180) / Math.PI };
  }
  return { lat: la, lon: lo };
}

/**
 * @param {string|undefined} epoch 如 "2026-04-03 11:37:17"
 * @returns {Date|null}
 */
export function parseSatelliteEpochDate(epoch) {
  if (epoch == null || epoch === "") return null;
  const s = String(epoch).trim().replace(" ", "T");
  const d = new Date(s);
  return Number.isFinite(d.getTime()) ? d : null;
}

/**
 * 单条 INFO content 项 → 与 OpenLayers upsert 一致的业务对象
 * @param {object} raw
 * @param {number} index
 * @returns {object|null}
 */
export function normalizeWsSatelliteInfoItem(raw) {
  if (!raw || typeof raw !== "object") return null;
  const objectId = String(raw.objectId ?? raw.object_id ?? "").trim();
  const tle1 = String(raw.telLine1 ?? raw.tle1 ?? "").trim();
  const tle2 = String(raw.telLine2 ?? raw.tle2 ?? "").trim();
  const noradCatalog = parseNoradCatalogFromTleLine1(tle1);
  const canonicalId =
    objectId || (noradCatalog != null ? String(noradCatalog) : "");
  if (!canonicalId) return null;

  const coords = wsLatLonToDegrees(raw.latitude, raw.longitude);
  if (!coords) return null;

  let altitude_km = NaN;
  const wsAlt = Number(raw.altitude);
  if (Number.isFinite(wsAlt)) {
    // 接口多为 km；极大值按米再换算为 km
    altitude_km = Math.abs(wsAlt) > 50000 ? wsAlt / 1000 : wsAlt;
  } else if (tle1 && tle2) {
    const d = parseSatelliteEpochDate(raw.epoch) || new Date();
    const st = computeGroundState(tle1, tle2, d);
    if (st) altitude_km = st.altitudeKm;
  }

  const name =
    String(raw.objectName ?? raw.name ?? "").trim() || canonicalId;

  return {
    name,
    norad_id: canonicalId,
    noradId: canonicalId,
    object_id: objectId || canonicalId,
    latitude: coords.lat,
    longitude: coords.lon,
    altitude: raw.altitude,
    altitude_km,
    tle1,
    tle2,
    telLine1: tle1,
    telLine2: tle2,
    epoch: raw.epoch,
    objectType: raw.objectType
  };
}

/**
 * @param {Array} content
 * @returns {Array<object>}
 */
export function normalizeWsSatelliteInfoItems(content) {
  if (!Array.isArray(content)) return [];
  return content.map((c) => normalizeWsSatelliteInfoItem(c)).filter(Boolean);
}
