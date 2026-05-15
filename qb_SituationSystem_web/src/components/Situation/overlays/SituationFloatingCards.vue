<template>
  <div>
    <div v-if="selectedPoint" class="point-detail-card">
      <div class="card-head">
        <span>目标详情</span>
        <button type="button" class="close-btn" @click="$emit('close-point')">x</button>
      </div>
      <div class="card-row">类型：{{ typeCategoryDisplay('intel') }}</div>
      <div class="card-row">名称：{{ pointDetailName }}</div>
      <div class="card-row">经度：{{ pointDetailLon }}</div>
      <div class="card-row">纬度：{{ pointDetailLat }}</div>
    </div>

    <div v-if="selectedSituationDetail" class="situation-detail-card">
      <div class="card-head">
        <span>{{ selectedSituationDetail.title }}</span>
        <button type="button" class="close-btn" @click="$emit('close-situation')">x</button>
      </div>
      <template v-if="selectedSituationDetail.type === 'satellite'">
        <div class="card-row">类型：{{ typeCategoryDisplay(selectedSituationDetail.type) }}</div>
        <div class="card-row">ID：{{ selectedSituationDetail.objectId }}</div>
        <div class="card-row">名称：{{ selectedSituationDetail.name }}</div>
        <div class="card-row">经度：{{ selectedSituationDetail.longitude }}</div>
        <div class="card-row">纬度：{{ selectedSituationDetail.latitude }}</div>
        <div class="card-row">高度：{{ selectedSituationDetail.altitude }}</div>
      </template>
      <template v-else-if="selectedSituationDetail.type === 'flight'">
        <div class="card-row">类型：{{ typeCategoryDisplay(selectedSituationDetail.type) }}</div>
        <div class="card-row">标识：{{ selectedSituationDetail.identifier || "-" }}</div>
        <div class="card-row">机型：{{ selectedSituationDetail.model || "-" }}</div>
        <div class="card-row">经度：{{ selectedSituationDetail.longitude || "-" }}</div>
        <div class="card-row">纬度：{{ selectedSituationDetail.latitude || "-" }}</div>
        <div class="card-row">几何高度(m)：{{ selectedSituationDetail.geomAltitude || "-" }}</div>
        <div class="card-row">地速(m/s)：{{ selectedSituationDetail.groundSpeed || "-" }}</div>
        <div class="card-row">真航向(°)：{{ selectedSituationDetail.trueHeading || "-" }}</div>
      </template>
      <template v-else-if="selectedSituationDetail.type === 'vessel'">
        <div class="card-row">类型：{{ typeCategoryDisplay(selectedSituationDetail.type) }}</div>
        <div class="card-row">名称：{{ selectedSituationDetail.name || "-" }}</div>
        <div class="card-row">船型：{{ selectedSituationDetail.navyType || "-" }}</div>
        <div class="card-row">经度：{{ selectedSituationDetail.longitude || "-" }}</div>
        <div class="card-row">纬度：{{ selectedSituationDetail.latitude || "-" }}</div>
        <div class="card-row">航速(节)：{{ selectedSituationDetail.speedOverGround || "-" }}</div>
        <div class="card-row">航向(°)：{{ selectedSituationDetail.courseOverGround || "-" }}</div>
        <div class="card-row">国家：{{ selectedSituationDetail.country || "-" }}</div>
      </template>
      <template v-else>
        <div class="card-row">类型：{{ typeCategoryDisplay(selectedSituationDetail.type) }}</div>
        <div class="card-row">名称：{{ selectedSituationDetail.name || "-" }}</div>
      </template>
    </div>
  </div>
</template>

<script>
/**
 * 情报点详情与态势实体（机船星等）详情浮卡
 * @module components/Situation/overlays/SituationFloatingCards
 */
/** 与 parseCoordinates(point.coordinates) 一致：字符串为「纬度,经度」 */
function parsePointCoordinates(coordinates) {
  const s = String(coordinates || "").trim();
  if (!s) return { lon: "—", lat: "—" };
  const parts = s.split(",");
  if (parts.length >= 2) {
    return { lat: parts[0].trim(), lon: parts[1].trim() };
  }
  return { lon: "—", lat: "—" };
}

export default {
  name: "SituationFloatingCards",
  props: {
    selectedPoint: { type: Object, default: null },
    selectedSituationDetail: { type: Object, default: null }
  },
  computed: {
    pointDetailName() {
      const p = this.selectedPoint;
      if (!p) return "—";
      const n = String(p.name ?? p.region ?? p.title ?? "").trim();
      return n || "—";
    },
    pointDetailLon() {
      const p = this.selectedPoint;
      if (!p) return "—";
      return parsePointCoordinates(p.coordinates).lon;
    },
    pointDetailLat() {
      const p = this.selectedPoint;
      if (!p) return "—";
      return parsePointCoordinates(p.coordinates).lat;
    }
  },
  methods: {
    typeCategoryDisplay(kind) {
      const t = String(kind || "").trim();
      const map = {
        satellite: "Satellite",
        flight: "Aircraft",
        vessel: "Ship",
        intel: "TargetPoint"
      };
      return map[t] || (t || "-");
    }
  }
};
</script>
