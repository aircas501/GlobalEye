/**
 * 中文国名表：来自 `public/data/countryNamesZh.json`（与 `BASE_URL` 子路径部署兼容）。
 */

/** @type {Record<string, string>|null} */
let cache = null;
/** @type {Promise<Record<string, string>>|null} */
let inflight = null;

function countryNamesZhUrl() {
  const base = String(process.env.BASE_URL || "/").replace(/\/?$/, "/");
  return `${base}data/countryNamesZh.json`;
}

/**
 * 拉取并缓存国名表；可重复调用，共享同一请求。
 * @returns {Promise<Record<string, string>>}
 */
export function loadCountryNamesZh() {
  if (cache) return Promise.resolve(cache);
  if (inflight) return inflight;
  inflight = fetch(countryNamesZhUrl())
    .then((r) => {
      if (!r.ok) throw new Error(`HTTP ${r.status}`);
      return r.json();
    })
    .then((j) => {
      cache =
        j && typeof j === "object" && !Array.isArray(j)
          ? /** @type {Record<string, string>} */ (j)
          : {};
      inflight = null;
      return cache;
    })
    .catch((e) => {
      inflight = null;
      console.warn("[countryNamesZh] load failed", e);
      cache = {};
      return cache;
    });
  return inflight;
}

/**
 * 同步读取已缓存条目；须先 `await loadCountryNamesZh()`（例如在加载国家 GeoJSON 前）。
 * @param {string} iso
 * @returns {string}
 */
export function getCountryNamesZh(iso) {
  if (!iso || !cache) return "";
  const z = cache[iso];
  return typeof z === "string" ? z : "";
}
