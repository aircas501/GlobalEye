/**
 * 统一解析卫星高度（km），兼容实时/历史接口字段差异（km 或米）
 * @param {Object|null|undefined} satellite
 * @returns {number}
 */
export function parseSatelliteAltitudeKm(satellite) {
  const r = satellite && typeof satellite === "object" ? satellite : null;
  if (!r) return NaN;
  const kmExplicit = Number(
    r.altitude_km ?? r.altitudeKm ?? r.height_km ?? r.heightKm
  );
  if (Number.isFinite(kmExplicit)) return kmExplicit;
  const meters = Number(
    r.altitude_m ?? r.altitudeM ?? r.height_m ?? r.heightM ?? r.altitudeMeter
  );
  if (Number.isFinite(meters)) return meters / 1000;
  const generic = Number(r.altitude ?? r.height ?? r.alt ?? r.elevation);
  if (!Number.isFinite(generic)) return NaN;
  return Math.abs(generic) > 50000 ? generic / 1000 : generic;
}
