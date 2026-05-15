/**
 * 将经纬度轨迹按日界线拆成多段，避免 OpenLayers 在 3857 下画线横穿整张图。
 * @param {Array<{ lon: number, lat: number }>} points
 * @returns {Array<Array<{ lon: number, lat: number }>>}
 */
export function splitTrackAtDateline(points) {
  if (!points || !points.length) return [];
  const segments = [];
  let current = [];
  for (let i = 0; i < points.length; i++) {
    const p = points[i];
    if (!current.length) {
      current.push(p);
      continue;
    }
    const prev = current[current.length - 1];
    const dLon = Math.abs(p.lon - prev.lon);
    if (dLon > 180) {
      segments.push(current);
      current = [p];
    } else {
      current.push(p);
    }
  }
  if (current.length) segments.push(current);
  return segments;
}
