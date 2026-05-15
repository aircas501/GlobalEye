/**
 * 2D/3D 态势共用的静态资源与配色（避免 OpenLayersScene / GlobeScene 重复定义）
 * @module components/Situation/js/situationEntityConstants
 */

import { isLowOrbitSatellite } from "@/utils/satelliteOrbit";
import { parseSatelliteAltitudeKm } from "@/utils/satelliteAltitude";

export const AIRCRAFT_ICON_URL = "/assets/svg/airForce.svg";
export const NAVY_ICON_URL = "/assets/svg/navy.svg";
export const INTEL_ICON_URL = "/assets/svg/情报.svg";
export const LOW_ORBIT_SATELLITE_ICON_URL = "/assets/svg/lowOrbitSatellite.svg";
export const HIGH_ORBIT_SATELLITE_ICON_URL = "/assets/svg/highOrbitSatellite.svg";

/** 与 2D 地图态势点文字色一致，供 3D 标签等复用 */
export const SATELLITE_NAME_COLOR = "#1F6BB6";
export const FLIGHT_NAME_COLOR = "#C8B6FF";
export const VESSEL_NAME_COLOR = "#3BA79E";
export const INTEL_NAME_COLOR = "#FF5AAE";

/**
 * 根据高度判断低轨/高轨，返回对应 SVG 路径
 * @param {object} satellite 含高度字段的态势卫星对象
 */
export function situationSatelliteIconUrl(satellite) {
  const altKm = parseSatelliteAltitudeKm(satellite);
  return isLowOrbitSatellite(altKm)
    ? LOW_ORBIT_SATELLITE_ICON_URL
    : HIGH_ORBIT_SATELLITE_ICON_URL;
}

/** 情报点 coordinates 文案 "lat,lon" → [lon, lat] */
export function parseCoordinates(coordinateText) {
  if (!coordinateText) return [0, 0];
  const [latRaw, lonRaw] = String(coordinateText).split(",");
  const lat = parseFloat(latRaw);
  const lon = parseFloat(lonRaw);
  return [isNaN(lon) ? 0 : lon, isNaN(lat) ? 0 : lat];
}
