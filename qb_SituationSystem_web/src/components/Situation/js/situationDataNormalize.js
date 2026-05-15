/**
 * 态势场景共享数据规范化工具：
 * 供 2D/3D 场景共同使用，统一时间参数与实时实体字段结构。
 */

/**
 * 规范历史查询时间：支持 `YYYY-MM-DD HH:mm` 与 `YYYY-MM-DDTHH:mm`。
 * @param {string|number|null|undefined} value
 * @returns {string}
 */
export function normalizeHistoryApiDateTime(value) {
  const trimmed = String(value || "").trim();
  if (!trimmed) return "";
  const normalized = trimmed.replace(" ", "T");
  if (/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(normalized)) {
    return `${normalized}:00`;
  }
  return normalized;
}

/**
 * 将 ws 消息中的 `content/data` 统一为数组。
 * @param {object} payload
 * @returns {Array}
 */
function pickWsRows(payload) {
  const src = payload?.content != null ? payload.content : payload?.data;
  if (Array.isArray(src)) return src;
  return src && typeof src === "object" ? [src] : [];
}

/**
 * 规范化单条航班数据（经纬度、高度、航向）。
 * @param {object} row
 * @returns {object|null}
 */
export function normalizeAircraftRow(row) {
  if (!row || typeof row !== "object") return null;
  const icao24 = String(row.icao24 || row.icao || "").trim();
  const longitude = Number(row.longitude);
  const latitude = Number(row.latitude);
  if (!icao24 || !Number.isFinite(longitude) || !Number.isFinite(latitude)) {
    return null;
  }
  const altitude = Number(row.geoAltitude ?? row.altitude ?? row.baroAltitude ?? 0);
  const heading = Number(row.trueTrack ?? row.heading ?? row.headingDeg ?? 0);
  return {
    ...row,
    icao24,
    longitude,
    latitude,
    altitude: Number.isFinite(altitude) ? altitude : 0,
    geoAltitude: Number.isFinite(altitude) ? altitude : 0,
    heading: Number.isFinite(heading) ? heading : 0,
    trueTrack: Number.isFinite(heading) ? heading : 0
  };
}

/**
 * 批量规范化航班数据。
 * @param {object} payload
 * @returns {Array<object>}
 */
export function normalizeAircraftRows(payload) {
  return pickWsRows(payload).map((row) => normalizeAircraftRow(row)).filter(Boolean);
}

/**
 * 规范化单条船舶数据（经纬度、船名、航向）。
 * @param {object} row
 * @returns {object|null}
 */
export function normalizeShipRow(row) {
  if (!row || typeof row !== "object") return null;
  const longitude = Number(row.longitude ?? row.lon ?? row.lng ?? row.LONGITUDE);
  const latitude = Number(row.latitude ?? row.lat ?? row.LATITUDE);
  if (!Number.isFinite(longitude) || !Number.isFinite(latitude)) {
    return null;
  }
  const mmsi = String(row.mmsi ?? row.MMSI ?? "").trim();
  const id = String(row.id ?? row.shipId ?? row.vesselId ?? "").trim();
  const name = String(row.name_ais ?? row.shipName ?? row.vesselName ?? row.name ?? "").trim();
  const heading = Number(row.course_over_ground ?? row.cog ?? row.heading ?? 0);
  return {
    ...row,
    id: id || undefined,
    mmsi,
    name_ais: name,
    longitude,
    latitude,
    course_over_ground: Number.isFinite(heading) ? heading : 0
  };
}

/**
 * 批量规范化船舶数据。
 * @param {object} payload
 * @returns {Array<object>}
 */
export function normalizeShipRows(payload) {
  return pickWsRows(payload).map((row) => normalizeShipRow(row)).filter(Boolean);
}
