/** 低于此高度（km）视为低轨，使用低轨卫星图标 */
export const LOW_ORBIT_ALTITUDE_KM = 2000;

export function isLowOrbitSatellite(altitudeKm) {
  const h = Number(altitudeKm);
  if (!Number.isFinite(h)) return true;
  return h < LOW_ORBIT_ALTITUDE_KM;
}
