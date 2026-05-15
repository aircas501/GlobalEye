<!--
  态势模块 · 3D 画布：Cesium 地球、国家面、实时/历史实体与标签。
  浮层 UI 与 2D 共用 overlays/ 与 css/situationSceneShared.css。
-->
<template>
  <div class="globe-scene">
    <div ref="globeContainer" class="globe-canvas"></div>

    <SituationModeSwitch v-model="situationMode" name="situationModeGlobe" />

    <SituationCountryPanel
      :visible="isScene3D && countriesReady"
      :selected-flag-url="selectedFlagUrl"
      :selected-country-line-text="selectedCountryLineText"
      :country-search="countrySearch"
      :filtered-countries="filteredCountries"
      @search-input="onCountrySearchInput"
      @apply-first-match="applyFirstSearchMatch"
      @select-country="selectCountryById"
      @flag-error="onFlagImgError"
    />

    <SituationFloatingCards
      :selected-point="selectedPoint"
      :selected-situation-detail="selectedSituationDetail"
      @close-point="selectedPoint = null"
      @close-situation="selectedSituationDetail = null"
    />
    <SituationLayerPanel
      :layer-visibility="layerVisibility"
      @update:layerVisibility="onLayerVisibilityFromPanel"
    />

    <SituationHistoryPanel
      :visible="situationMode === 'history'"
      :history-start-time="historyStartTime"
      :history-end-time="historyEndTime"
      :history-speed="historySpeed"
      :history-current-minute="historyCurrentMinute"
      :history-slider-max="historySliderMax"
      :history-playback-time-text="historyPlaybackTimeText"
      :history-playing="historyPlaying"
      @update:historyStartTime="historyStartTime = $event"
      @update:historyEndTime="historyEndTime = $event"
      @update:historySpeed="historySpeed = $event"
      @update:historyCurrentMinute="historyCurrentMinute = $event"
      @open-datetime-picker="openNativeDateTimePicker"
      @progress-drag-start="onProgressDragStart"
      @progress-drag-end="onProgressDragEnd"
      @play-pause="historyPlayPause"
      @end="historyEnd"
    />
  </div>
</template>

<script>
import { getCountryNamesZh, loadCountryNamesZh } from "@/api/countryNamesZh.js";
import countries from "i18n-iso-countries";
import { getVesselsByTimeRange } from "@/api/historicalVesselSituation";
import { getSatellitesByTimeRange } from "@/api/satellite";
import {
  AIRCRAFT_ICON_URL,
  FLIGHT_NAME_COLOR,
  INTEL_ICON_URL,
  INTEL_NAME_COLOR,
  NAVY_ICON_URL,
  parseCoordinates,
  SATELLITE_NAME_COLOR,
  situationSatelliteIconUrl,
  VESSEL_NAME_COLOR
} from "@/components/Situation/js/situationEntityConstants";
import {
  normalizeAircraftRow as normalizeSituationAircraftRow,
  normalizeAircraftRows as normalizeSituationAircraftRows,
  normalizeHistoryApiDateTime,
  normalizeShipRow as normalizeSituationShipRow,
  normalizeShipRows as normalizeSituationShipRows
} from "@/components/Situation/js/situationDataNormalize";
import SituationCountryPanel from "./overlays/SituationCountryPanel.vue";
import SituationFloatingCards from "./overlays/SituationFloatingCards.vue";
import SituationHistoryPanel from "./overlays/SituationHistoryPanel.vue";
import SituationLayerPanel from "./overlays/SituationLayerPanel.vue";
import SituationModeSwitch from "./overlays/SituationModeSwitch.vue";
import { parseSatelliteAltitudeKm } from "@/utils/satelliteAltitude";
import { createSatelliteSituationWebSocket } from "@/api/SituationWebSocket";
import { computeGroundState } from "@/utils/satelliteTrack";
import { normalizeWsSatelliteInfoItems } from "@/utils/satelliteWsAdapter";

const COUNTRIES_GEOJSON_URL = "/data/countries.geojson";

/**
 * 3D 态势主组件：Cesium Viewer、国家 Primitive、实时/历史实体与相机控制。
 * 父级可调用：resetView()、onContainerResize()。
 */
export default {
  name: "GlobeScene",
  components: {
    SituationCountryPanel,
    SituationFloatingCards,
    SituationHistoryPanel,
    SituationLayerPanel,
    SituationModeSwitch
  },
  props: {
    points: { type: Array, default: () => [] }
  },
  data() {
    return {
      viewer: null,
      selectedPoint: null,
      pointEntities: [],
      Cesium: null,
      _screenSpaceHandler: null,
      countryDataSource: null,
      countryEntitiesByIso: {},
      countryList: [],
      countriesReady: false,
      countrySearch: "",
      _flagImgFailed: false,
      _flagFallbackTried: false,
      hoveredIso: null,
      selectedIso: null,
      searchFocusIso: null,
      isScene3D: true,
      _morphRemoveListener: null,
      situationMode: "realtime",
      situationSocket: null,
      situationWsManualClose: false,
      situationWsReconnectTimer: null,
      flightEntitiesByIcao: {},
      shipEntitiesById: {},
      satelliteEntitiesById: {},
      selectedSatelliteNoradId: null,
      hoveredSatelliteNoradId: null,
      layerVisibility: {
        intel: true,
        flight: true,
        vessel: true,
        satellite: true
      },
      selectedSituationDetail: null,
      historyStartTime: "2026-03-20T00:00:00",
      historyEndTime: "2026-03-21T00:00:00",
      historySpeed: 1,
      historyCurrentMinute: 0,
      historyPlaying: false,
      historyTimer: null,
      _historyProgressDragging: false,
      historyLoading: false,
      satelliteWarnFlashIdMap: {},
      warnFlashPhase: false,
      _warnFlashTimer: null
    };
  },
  computed: {
    selectedCountryLineText() {
      if (!this.selectedIso) return "未选择";
      return this.getCountrySearchDisplay(this.selectedIso);
    },
    selectedFlagUrl() {
      if (!this.selectedIso || this._flagImgFailed) return "";
      if (this._flagFallbackTried) return this.getFallbackFlagUrl(this.selectedIso);
      return this.getFlagUrlForIso(this.selectedIso);
    },
    filteredCountries() {
      const q = (this.countrySearch || "").trim();
      if (!q) return [];
      const qLower = q.toLowerCase();
      const twAliases = ["台湾", "臺灣", "中国台湾"];
      return this.countryList
        .filter((c) => {
          if (c.nameZh.includes(q)) return true;
          if (c.nameEn && c.nameEn.toLowerCase().includes(qLower)) return true;
          if (
            c.id === "CHN" &&
            twAliases.some((a) => a.includes(q) || q.includes(a))
          ) {
            return true;
          }
          return false;
        })
        .slice(0, 20);
    },
    historyMaxMinutes() {
      const start = new Date(this.historyStartTime).getTime();
      const end = new Date(this.historyEndTime).getTime();
      if (!isFinite(start) || !isFinite(end) || end <= start) return 24 * 60;
      return Math.floor((end - start) / 60000);
    },
    historySliderMax() {
      return Math.max(1, this.historyMaxMinutes);
    },
    historyPlaybackTimeText() {
      const start = new Date(this.historyStartTime);
      if (!isFinite(start.getTime())) return "00:00";
      const d = new Date(start.getTime() + this.historyCurrentMinute * 60000);
      const hh = String(d.getHours()).padStart(2, "0");
      const mm = String(d.getMinutes()).padStart(2, "0");
      const ss = String(d.getSeconds()).padStart(2, "0");
      return `${hh}:${mm}:${ss} (${this.historyCurrentMinute} / ${this.historyMaxMinutes} 分钟)`;
    }
  },
  watch: {
    points: {
      handler(newVal) {
        this.renderIntelligencePoints(newVal);
      },
      deep: true
    },
    historyCurrentMinute() {
      this.syncHistoryClock();
    },
    historyMaxMinutes(newVal) {
      if (this.historyCurrentMinute > newVal) {
        this.historyCurrentMinute = newVal;
      }
    },
    countrySearch(newVal) {
      if (!newVal) this.clearSearchFocusStyle();
    },
    selectedIso(newIso) {
      this._flagImgFailed = false;
      this._flagFallbackTried = false;
      const countryName = newIso ? this.getCountryZhName(newIso) : "";
      this.$emit("country-selected", countryName);
    },
    situationMode(newMode) {
      if (newMode === "realtime") {
        this.historyEnd();
        this.clearVesselEntities();
        this.clearSatelliteEntities();
        this.connectSituationSocket();
      } else {
        this.disconnectSituationSocket();
        this.clearFlightEntities();
        this.clearVesselEntities();
        this.clearSatelliteEntities();
        this.selectedSituationDetail = null;
        this.syncHistoryClock();
      }
    },
    selectedSatelliteNoradId() {
      this.refreshSatelliteVisualStates();
    }
  },
  mounted() {
    this.Cesium = window.Cesium;
    this.initViewer();
    // 2D/3D 切换会重新挂载组件；points 初始值不一定会触发 watch，因此补一次初始化渲染
    this.renderIntelligencePoints(this.points);
  },
  beforeDestroy() {
    this.historyEnd();
    this.disconnectSituationSocket();
    this.clearFlightEntities();
    this.clearVesselEntities();
    this.clearSatelliteEntities();
    if (this._morphRemoveListener && this.viewer) {
      this.viewer.scene.morphComplete.removeEventListener(
        this._morphRemoveListener
      );
      this._morphRemoveListener = null;
    }
    if (this._screenSpaceHandler) {
      this._screenSpaceHandler.destroy();
      this._screenSpaceHandler = null;
    }
    if (this.viewer) {
      this.viewer.destroy();
      this.viewer = null;
    }
  },
  methods: {
    onFlagImgError() {
      if (!this._flagFallbackTried) {
        this._flagFallbackTried = true;
        return;
      }
      this._flagImgFailed = true;
    },

    /** 主源：flagcdn 使用 ISO 3166-1 alpha-2；Natural Earth 特殊 id 单独映射 */
    getFlagUrlForIso(iso3) {
      if (!iso3) return "";
      const overrides = {
        NE_174_Kosovo: "xk",
        NE_160_N_Cyprus: "cy",
        NE_167_Somaliland: "so",
        NE_21_Norway: "no",
        NE_43_France: "fr",
        // Natural Earth / GeoJSON 内部编码非 ISO alpha-3，需要额外映射
        PSX: "ps", // Palestine (ADM0_A3/GU_A3/BRK_A3 in your geojson)
        SDS: "ss", // 南苏丹：Natural Earth 为 SDS，ISO 为 SSD
        SAH: "eh" // 西撒哈拉：NE 为 SAH，ISO 为 ESH
      };
      let a2 = overrides[iso3];
      if (!a2 && /^[A-Za-z]{3}$/.test(iso3)) {
        a2 = countries.alpha3ToAlpha2(iso3.toUpperCase());
      }
      if (!a2) return "";
      return `https://flagcdn.com/w40/${String(a2).toLowerCase()}.png`;
    },

    /** 仅允许中文、英文字母及国名中常见字符（禁止数字、符号等，不可按 ISO 搜索） */
    onCountrySearchInput(e) {
      const raw = e.target.value || "";
      const cleaned = raw.replace(
        /[^\u4e00-\u9fffa-zA-Z\u00c0-\u024f\s\-'·()]/g,
        ""
      );
      this.countrySearch = cleaned;
    },

    initViewer() {
      const Cesium = this.Cesium;
      if (!Cesium) return;

      this.viewer = new Cesium.Viewer(this.$refs.globeContainer, {
        animation: false,
        timeline: false,
        baseLayerPicker: false,
        geocoder: false,
        // 系统级 2D/3D 切换由 Situation/index.vue 统一控制
        sceneModePicker: false,
        navigationHelpButton: false,
        fullscreenButton: false,
        infoBox: false,
        selectionIndicator: false,
        imageryProvider: new Cesium.SingleTileImageryProvider({
          url: "/images/world.jpg",
          rectangle: Cesium.Rectangle.fromDegrees(-180, -90, 180, 90)
        })
      });

      this.viewer.scene.globe.enableLighting = true;
      this.viewer.scene.skyAtmosphere.brightnessShift = -0.2;
      this.viewer.scene.backgroundColor = Cesium.Color.fromCssColorString("#041530");

      this._morphRemoveListener = () => {
        this.syncSceneModeFlags();
        this.syncCountryLayerVisibility();
      };
      this.viewer.scene.morphComplete.addEventListener(this._morphRemoveListener);

      // /* Viewer 默认 LEFT_CLICK 会更新 selectedEntity；
      //  * 导致已选中国家的多边形被 DataSource 恢复默认样式。国家与情报点由本组件自行处理。 */

      this.bindMouseEvents();
      this.loadCountriesGeoJson();
      this.syncSceneModeFlags();
      this.viewer.camera.flyHome(0);
      this.connectSituationSocket();
      this.applyLayerVisibility();
    },

    syncSceneModeFlags() {
      const Cesium = this.Cesium;
      if (!this.viewer || !Cesium) return;
      const mode = this.viewer.scene.mode;
      this.isScene3D = mode === Cesium.SceneMode.SCENE3D;
    },

    syncCountryLayerVisibility() {
      const Cesium = this.Cesium;
      if (!this.viewer || !Cesium || !this.countryDataSource) return;
      const show = this.viewer.scene.mode === Cesium.SceneMode.SCENE3D;
      this.countryDataSource.show = show;
      if (!show) {
        this.clearHoverVisual();
        this.countrySearch = "";
      }
    },

    /** Natural Earth GeoJSON 使用 `NAME` / `NAME_EN` 等字段；仅读 `name` 会得到空字符串 */
    getEntityCountryName(entity) {
      if (!entity || !entity.properties) return "";
      const p = entity.properties;
      const pick = (key) => {
        const raw = p[key];
        if (raw == null) return "";
        if (typeof raw === "string") return raw.trim();
        if (typeof raw.getValue === "function") {
          try {
            const v = raw.getValue();
            return v != null ? String(v).trim() : "";
          } catch (_e) {
            return "";
          }
        }
        return "";
      };
      return pick("NAME") || pick("name") || pick("NAME_EN") || "";
    },

    /** GeoJSON 国家编码优先从 properties 提取，避免依赖 Cesium 自动 id */
    getIsoFromEntity(entity) {
      if (!entity || !entity.properties) {
        return this.baseIsoFromEntityId(entity && entity.id);
      }
      const p = entity.properties;
      const candidates = ["ADM0_A3", "ISO_A3", "SOV_A3", "GU_A3", "BRK_A3"];
      for (let i = 0; i < candidates.length; i++) {
        const key = candidates[i];
        const val = p[key];
        const code =
          val && typeof val.getValue === "function" ? val.getValue() : val;
        if (typeof code === "string" && code.trim()) {
          return code.trim().toUpperCase();
        }
      }
      return this.baseIsoFromEntityId(entity.id);
    },

    /** 展示与检索用中文国名；无映射时回退 GeoJSON 英文 name */
    getCountryZhName(iso) {
      if (!iso) return "";
      const zh = getCountryNamesZh(iso);
      if (zh) return zh;
      const list = this.countryEntitiesByIso[iso];
      if (list && list[0]) {
        return this.getEntityCountryName(list[0]) || iso;
      }
      return iso;
    },

    /** GeoJSON / Natural Earth 英文国名（含台区合并后统一为 China） */
    getCountryEnName(iso) {
      if (iso === "CHN") return "China";
      const list = this.countryEntitiesByIso[iso];
      if (!list || !list[0]) return "";
      return this.getEntityCountryName(list[0]) || "";
    },

    /** 搜索框选中后展示：中国(China) */
    getCountrySearchDisplay(iso) {
      const zh = this.getCountryZhName(iso);
      const en = this.getCountryEnName(iso);
      if (en && en !== zh) {
        return `${zh}(${en})`;
      }
      return zh || en || iso;
    },

    async loadCountriesGeoJson() {
      const Cesium = this.Cesium;
      if (!this.viewer || !Cesium) return;

      try {
        await loadCountryNamesZh();
        const dataSource = await Cesium.GeoJsonDataSource.load(
          COUNTRIES_GEOJSON_URL,
          {
            stroke: Cesium.Color.fromCssColorString("#5eb3ff").withAlpha(0.65),
            fill: Cesium.Color.fromCssColorString("#1e6aa8").withAlpha(0.22),
            strokeWidth: 1,
            clampToGround: true
          }
        );

        this.countryDataSource = dataSource;
        this.viewer.dataSources.add(dataSource);

        const countryEntitiesByIso = {};
        const entities = dataSource.entities.values;

        for (let i = 0; i < entities.length; i++) {
          const entity = entities[i];
          if (!entity.polygon) continue;

          const rawIso = this.getIsoFromEntity(entity);
          if (!rawIso) continue;
          /* 台湾地区与合并为中国，拾取/高亮均视为 CHN */
          let iso = rawIso === "TWN" ? "CHN" : rawIso;
          /* Natural Earth 中南苏丹 ADM0_A3=SDS，与 ISO 3166-1 的 SSD 对齐（中文名/国旗） */
          if (iso === "SDS") iso = "SSD";
          if (iso === "SAH") iso = "ESH";

          entity.isCountryPolygon = true;
          entity._countryIso = iso;

          entity.polygon.arcType = Cesium.ArcType.GEODESIC;
          entity.polygon.granularity = Cesium.Math.toRadians(1.0);

          entity._countryDefaultFill = Cesium.Color.fromCssColorString(
            "#1e6aa8"
          ).withAlpha(0.22);
          entity._countryDefaultStroke = Cesium.Color.fromCssColorString(
            "#5eb3ff"
          ).withAlpha(0.65);
          entity._countryHoverFill = Cesium.Color.fromCssColorString(
            "#6be6ff"
          ).withAlpha(0.42);
          entity._countryHoverStroke = Cesium.Color.fromCssColorString(
            "#b8f0ff"
          ).withAlpha(0.9);
          entity._countrySearchFill = Cesium.Color.fromCssColorString(
            "#ffd54a"
          ).withAlpha(0.45);
          entity._countrySearchStroke = Cesium.Color.fromCssColorString(
            "#fff176"
          ).withAlpha(0.95);
          entity._countrySelectedFill = Cesium.Color.fromCssColorString(
            "#ff8800"
          ).withAlpha(0.58);
          entity._countrySelectedStroke = Cesium.Color.fromCssColorString(
            "#ffb84d"
          ).withAlpha(0.95);

          entity.polygon.material = new Cesium.ColorMaterialProperty(
            entity._countryDefaultFill
          );
          if (entity.polygon.outline !== false) {
            entity.polygon.outline = true;
            entity.polygon.outlineColor = new Cesium.ConstantProperty(
              entity._countryDefaultStroke
            );
          }

          if (!countryEntitiesByIso[iso]) countryEntitiesByIso[iso] = [];
          countryEntitiesByIso[iso].push(entity);
        }

        this.countryEntitiesByIso = countryEntitiesByIso;

        const countryList = Object.keys(countryEntitiesByIso)
          .map((iso) => {
            const first = countryEntitiesByIso[iso][0];
            return {
              id: iso,
              nameZh: this.getCountryZhName(iso),
              nameEn: this.getEntityCountryName(first) || iso
            };
          })
          .sort((a, b) => a.nameZh.localeCompare(b.nameZh, "zh-CN"));

        this.countryList = countryList;
        this.countriesReady = true;
        this.syncCountryLayerVisibility();
      } catch (e) {
        console.error("Failed to load countries GeoJSON", e);
      }
    },

    applyPolygonStyle(entity, kind) {
      const Cesium = this.Cesium;
      if (!entity || !entity.polygon || !Cesium) return;
      let fill;
      let stroke;
      if (kind === "selected") {
        fill = entity._countrySelectedFill;
        stroke = entity._countrySelectedStroke;
      } else if (kind === "search") {
        fill = entity._countrySearchFill;
        stroke = entity._countrySearchStroke;
      } else if (kind === "hover") {
        fill = entity._countryHoverFill;
        stroke = entity._countryHoverStroke;
      } else {
        fill = entity._countryDefaultFill;
        stroke = entity._countryDefaultStroke;
      }
      entity.polygon.material = new Cesium.ColorMaterialProperty(fill);
      if (entity.polygon.outline !== false) {
        entity.polygon.outlineColor = new Cesium.ConstantProperty(stroke);
      }
    },

    applyStyleForIso(iso, kind) {
      const list = this.countryEntitiesByIso[iso];
      if (!list) return;
      list.forEach((e) => this.applyPolygonStyle(e, kind));
    },

    clearSearchFocusStyle() {
      if (!this.searchFocusIso) return;
      const iso = this.searchFocusIso;
      if (this.selectedIso === iso) {
        this.applyStyleForIso(iso, "selected");
      } else {
        this.applyStyleForIso(iso, "default");
      }
      this.searchFocusIso = null;
    },

    clearHoverVisual() {
      if (!this.hoveredIso) {
        return;
      }
      const iso = this.hoveredIso;
      if (this.selectedIso === iso) {
        this.applyStyleForIso(iso, "selected");
      } else if (this.searchFocusIso === iso) {
        this.applyStyleForIso(iso, "search");
      } else {
        this.applyStyleForIso(iso, "default");
      }
      this.hoveredIso = null;
    },
    resolveCountryIsoByName(countryName) {
      const raw = String(countryName || "").trim();
      if (!raw) return "";
      const norm = raw.toLowerCase();
      const found = this.countryList.find((c) => {
        const zh = String(c?.nameZh || "").trim();
        const en = String(c?.nameEn || "").trim();
        return zh === raw || en.toLowerCase() === norm;
      });
      return found ? String(found.id || "") : "";
    },
    _hasCountryEntitiesForIso(isoKey) {
      const list = (this.countryEntitiesByIso || {})[isoKey];
      return Array.isArray(list) && list.length > 0;
    },

    _normalizeGeoIsoFromHint(iso) {
      let x = String(iso || "").trim().toUpperCase();
      if (!x) return "";
      if (x === "TWN") return "CHN";
      if (x === "SDS") return "SSD";
      if (x === "SAH") return "ESH";
      return x;
    },

    resolveIsoFromPanelHint(countryCode, countryName) {
      const codeRaw = String(countryCode || "").trim().toUpperCase();
      const nameHint = String(countryName || "").trim();

      if (codeRaw.length === 3 && /^[A-Z]{3}$/.test(codeRaw)) {
        const key = this._normalizeGeoIsoFromHint(codeRaw);
        if (this._hasCountryEntitiesForIso(key)) return key;
      }
      if (codeRaw.length === 2 && /^[A-Z]{2}$/.test(codeRaw)) {
        const a3 = countries.alpha2ToAlpha3(codeRaw);
        if (a3) {
          const key = this._normalizeGeoIsoFromHint(a3);
          if (this._hasCountryEntitiesForIso(key)) return key;
        }
      }
      return this.resolveCountryIsoByName(nameHint || codeRaw);
    },

    focusCountryFromPanel(payload) {
      const p = payload && typeof payload === "object" ? payload : {};
      const iso = this.resolveIsoFromPanelHint(
        p.countryCode,
        p.country || p.countryNameZh || p.countryName
      );
      if (!iso) return;
      this.selectedSituationDetail = null;
      this.selectCountryById(iso);
    },

    resolveCountryEntityFromPick(picked) {
      if (!picked) return null;
      const id = picked.id;
      if (!id) return null;
      if (id.isCountryPolygon && id.polygon) return id;
      return null;
    },

    applyFirstSearchMatch() {
      const first = this.filteredCountries[0];
      if (first) this.selectCountryById(first.id);
    },

    baseIsoFromEntityId(entityId) {
      if (entityId == null) return "";
      const s = String(entityId);
      const m = s.match(/^(.+)_(\d+)$/);
      return m ? m[1] : s;
    },

    getCountryNameForIso(iso) {
      return this.getCountrySearchDisplay(iso);
    },

    formatFlightValue(value, digits = 4) {
      const n = Number(value);
      return Number.isFinite(n) ? n.toFixed(digits) : "-";
    },

    formatFlightAltitude(value) {
      const n = Number(value);
      // 这里返回“数字字符串”，单位由字段名（几何高度(m)）提供
      return Number.isFinite(n) ? n.toFixed(1) : "-";
    },

    formatFlightSpeed(value, digits = 2) {
      const n = Number(value);
      return Number.isFinite(n) ? n.toFixed(digits) : "-";
    },

    formatFlightHeading(value, digits = 1) {
      const n = Number(value);
      return Number.isFinite(n) ? n.toFixed(digits) : "-";
    },

    formatFlightIdentifier(value) {
      const s = String(value || "").trim();
      return s || "-";
    },

    formatHistoryNumber(value, digits = 4) {
      const n = Number(value);
      return Number.isFinite(n) ? n.toFixed(digits) : "未知";
    },
    getSatelliteNoradKey(satellite, fallback = "") {
      return String(
        satellite?.norad_id ?? satellite?.noradId ?? satellite?.object_id ?? fallback
      ).trim();
    },
    onLayerVisibilityFromPanel(next) {
      this.layerVisibility = next;
      this.applyLayerVisibility();
    },
    applyLayerVisibility() {
      const v = this.layerVisibility || {};
      Object.values(this.pointEntities || {}).forEach((entity) => {
        if (entity) entity.show = !!v.intel;
      });
      Object.values(this.flightEntitiesByIcao || {}).forEach((entity) => {
        if (entity) entity.show = !!v.flight;
      });
      Object.values(this.shipEntitiesById || {}).forEach((entity) => {
        if (entity) entity.show = !!v.vessel;
      });
      Object.values(this.satelliteEntitiesById || {}).forEach((entity) => {
        if (entity) entity.show = !!v.satellite;
      });
    },
    refreshSatelliteVisualStates() {
      const Cesium = this.Cesium;
      if (!Cesium) return;
      const selected = String(this.selectedSatelliteNoradId || "").trim();
      const hovered = String(this.hoveredSatelliteNoradId || "").trim();

      Object.keys(this.satelliteEntitiesById || {}).forEach((noradId) => {
        const entity = this.satelliteEntitiesById[noradId];
        if (!entity || !entity.billboard) return;
        const isSelected = selected && selected === noradId;
        const isHovered = !isSelected && hovered && hovered === noradId;
        const raw = entity.rawSatellite || {};
        const warnKey = String(
          raw.object_id ?? raw.objectId ?? raw.norad_id ?? raw.noradId ?? noradId
        ).trim();
        const warnFlash =
          !!warnKey &&
          !!this.satelliteWarnFlashIdMap[warnKey] &&
          !!this.warnFlashPhase;
        entity.billboard.color = warnFlash
          ? Cesium.Color.fromCssColorString("#ff4444")
          : isSelected
            ? Cesium.Color.fromCssColorString("#FFD60A")
            : isHovered
              ? Cesium.Color.fromCssColorString("#FFE066")
              : Cesium.Color.WHITE;
        if (entity.label) {
          entity.label.fillColor = warnFlash
            ? Cesium.Color.fromCssColorString("#ff4444")
            : Cesium.Color.fromCssColorString(SATELLITE_NAME_COLOR);
          entity.label.outlineColor = warnFlash
            ? Cesium.Color.fromCssColorString("#2a0000")
            : Cesium.Color.BLACK;
        }
      });

    },
    applySatelliteWarnMessage(payload) {
      const p = payload && typeof payload === "object" ? payload : {};
      const objectId = String(p.objectId ?? "").trim();
      if (objectId) {
        this.$set(this.satelliteWarnFlashIdMap, objectId, true);
        this.syncWarnFlashTicker();
        this.refreshSatelliteVisualStates();
      }
    },
    syncWarnFlashTicker() {
      const ids = Object.keys(this.satelliteWarnFlashIdMap || {});
      if (!ids.length) {
        if (this._warnFlashTimer) {
          clearInterval(this._warnFlashTimer);
          this._warnFlashTimer = null;
        }
        return;
      }
      if (this._warnFlashTimer) return;
      this._warnFlashTimer = window.setInterval(() => {
        this.warnFlashPhase = !this.warnFlashPhase;
        this.refreshSatelliteVisualStates();
      }, 480);
    },
    clearSatelliteWarnFlashForId(id) {
      const sid = String(id || "").trim();
      if (!sid || !this.satelliteWarnFlashIdMap[sid]) return;
      this.$delete(this.satelliteWarnFlashIdMap, sid);
      this.syncWarnFlashTicker();
      this.refreshSatelliteVisualStates();
    },
    buildFlightDetail(flight) {
      if (!flight) return null;
      return {
        title: "目标详情",
        type: "flight",
        identifier: this.formatFlightIdentifier(flight.icao24 || flight.icao || flight.callsign),
        model: String(flight.model || flight.aircraft_model || "").trim() || "-",
        longitude: this.formatFlightValue(flight.longitude, 4),
        latitude: this.formatFlightValue(flight.latitude, 4),
        geomAltitude: this.formatFlightAltitude(flight.geoAltitude ?? flight.altitude),
        groundSpeed: this.formatFlightSpeed(
          flight.groundSpeed ??
            flight.ground_speed ??
            flight.velocity ??
            flight.speed ??
            flight.gs
        ),
        trueHeading: this.formatFlightHeading(
          flight.trueTrack ?? flight.heading ?? flight.headingDeg ?? flight.track
        )
      };
    },

    buildVesselDetail(vessel) {
      if (!vessel) return null;
      const vesselName = String(
        vessel.nameAis ??
          vessel.name_ais ??
          vessel.vesselName ??
          vessel.shipName ??
          vessel.name ??
          ""
      ).trim() || "未知";
      const navyType = String(vessel.navyType || "").trim() || "未知";
      const speed = Number(vessel.speedOverGround ?? vessel.speed_over_ground);
      const course = Number(vessel.courseOverGround ?? vessel.course_over_ground ?? vessel.cog);
      const country = String(vessel.country || "").trim() || "未知";
      return {
        title: "目标详情",
        type: "vessel",
        name: vesselName,
        navyType,
        longitude: this.formatHistoryNumber(vessel.longitude, 4),
        latitude: this.formatHistoryNumber(vessel.latitude, 4),
        speedOverGround: Number.isFinite(speed) ? speed.toFixed(2) : "未知",
        courseOverGround: Number.isFinite(course) ? course.toFixed(1) : "未知",
        country
      };
    },

    buildSatelliteDetail(satellite) {
      if (!satellite) return null;
      const objectId = String(satellite.object_id || "").trim() || "未知";
      const altKm = parseSatelliteAltitudeKm(satellite);
      const altitudeStr = Number.isFinite(altKm)
        ? `${altKm.toFixed(1)} km`
        : "—";
      const ot = String(satellite.objectType || "").trim();
      return {
        title: "目标详情",
        type: "satellite",
        objectType: ot || "卫星",
        objectId,
        name: (satellite.name || "").trim() || "未知",
        longitude: this.formatHistoryNumber(satellite.longitude, 4),
        latitude: this.formatHistoryNumber(satellite.latitude, 4),
        altitude: altitudeStr
      };
    },

    formatHistoryApiDateTime(value) {
      return normalizeHistoryApiDateTime(value);
    },

    openNativeDateTimePicker(event) {
      const input = event && event.target;
      if (!input || typeof input.showPicker !== "function") return;
      try {
        input.showPicker();
      } catch (e) {
        // Some browsers require user gesture timing; ignore silently.
      }
    },

    selectCountryById(id) {
      const Cesium = this.Cesium;
      const entities = this.countryEntitiesByIso[id];
      if (!entities || !entities.length || !this.viewer || !Cesium) return;

      if (this.searchFocusIso && this.searchFocusIso !== id) {
        this.clearSearchFocusStyle();
      }
      this.searchFocusIso = null;

      if (this.selectedIso && this.selectedIso !== id) {
        this.applyStyleForIso(this.selectedIso, "default");
      }
      this.selectedIso = id;
      this.applyStyleForIso(id, "selected");
      this.countrySearch = "";

      this.viewer.flyTo(entities, {
        duration: 1.4,
        offset: new Cesium.HeadingPitchRange(0, Cesium.Math.toRadians(-90), 0)
      });
    },

    /** 点击海洋等非国家区域时：去掉橙色选中与检索高亮 */
    clearCountrySelection() {
      if (this.searchFocusIso && this.searchFocusIso !== this.selectedIso) {
        this.applyStyleForIso(this.searchFocusIso, "default");
      }
      this.searchFocusIso = null;
      if (this.selectedIso) {
        this.applyStyleForIso(this.selectedIso, "default");
        this.selectedIso = null;
      }
    },

    bindMouseEvents() {
      const Cesium = this.Cesium;
      if (!Cesium) return;

      const handler = new Cesium.ScreenSpaceEventHandler(this.viewer.scene.canvas);
      this._screenSpaceHandler = handler;

      handler.setInputAction((movement) => {
        const picked = this.viewer.scene.pick(movement.endPosition);
        const countryEntity = this.resolveCountryEntityFromPick(picked);
        const id = picked && picked.id ? picked.id : null;
        const isIntel = id && id.isIntelligencePoint;
        const isSituationEntity = id && id.isSituationEntity;
        const hoverSatId =
          id && id.isSituationEntity
            ? String(id.noradId || id.rawSatellite?.norad_id || "").trim()
            : "";
        this.hoveredSatelliteNoradId = hoverSatId || null;
        this.refreshSatelliteVisualStates();

        this.viewer.container.style.cursor =
          isIntel || isSituationEntity || countryEntity ? "pointer" : "default";

        const hoverIso =
          countryEntity && countryEntity._countryIso
            ? countryEntity._countryIso
            : null;

        if (hoverIso !== this.hoveredIso) {
          this.clearHoverVisual();
          this.hoveredIso = hoverIso;
          if (
            hoverIso &&
            hoverIso !== this.selectedIso &&
            hoverIso !== this.searchFocusIso
          ) {
            this.applyStyleForIso(hoverIso, "hover");
          }
        }
      }, Cesium.ScreenSpaceEventType.MOUSE_MOVE);

      handler.setInputAction((movement) => {
        const picked = this.viewer.scene.pick(movement.position);
        const countryEntity = this.resolveCountryEntityFromPick(picked);
        const id = picked && picked.id ? picked.id : null;

        if (countryEntity && countryEntity._countryIso) {
          this.selectedSituationDetail = null;
          const iso = countryEntity._countryIso;
          if (this.selectedIso && this.selectedIso !== iso) {
            this.applyStyleForIso(this.selectedIso, "default");
          }
          this.selectedIso = iso;
          if (this.searchFocusIso && this.searchFocusIso !== iso) {
            this.clearSearchFocusStyle();
          } else {
            this.searchFocusIso = null;
          }
          this.applyStyleForIso(iso, "selected");
          this.countrySearch = "";
          return;
        }

        if (id && id.isIntelligencePoint) {
          this.selectedSituationDetail = null;
          this.selectedPoint = id.rawData;
          return;
        }

        if (id && id.isSituationEntity && id.situationType === "flight") {
          const detail = this.buildFlightDetail(id.rawFlight);
          this.selectedSituationDetail = detail;
          this.$emit(
            "situation-node-selected",
            detail && (detail.identifier || detail.model)
              ? detail.identifier || detail.model
              : "未知"
          );
          return;
        }

        if (id && id.isSituationEntity && id.situationType === "vessel") {
          const detail = this.buildVesselDetail(id.rawVessel);
          this.selectedSituationDetail = detail;
          this.$emit("situation-node-selected", detail && detail.name ? detail.name : "未知");
          return;
        }

        if (id && id.isSituationEntity && id.situationType === "satellite") {
          this.selectedSatelliteNoradId = String(
            id.noradId || id.rawSatellite?.norad_id || ""
          ).trim() || null;
          const warnObjectId = String(
            id.rawSatellite?.object_id ?? id.rawSatellite?.objectId ?? ""
          ).trim();
          if (warnObjectId) this.clearSatelliteWarnFlashForId(warnObjectId);
          if (this.selectedSatelliteNoradId) {
            this.clearSatelliteWarnFlashForId(this.selectedSatelliteNoradId);
          }
          const detail = this.buildSatelliteDetail(id.rawSatellite);
          this.selectedSituationDetail = detail;
          this.$emit("situation-node-selected", detail && detail.name ? detail.name : "未知");
          this.refreshSatelliteVisualStates();
          return;
        }

        this.clearCountrySelection();
        this.selectedSituationDetail = null;
        this.selectedSatelliteNoradId = null;
        this.refreshSatelliteVisualStates();
      }, Cesium.ScreenSpaceEventType.LEFT_CLICK);
    },

    renderIntelligencePoints(points) {
      const Cesium = this.Cesium;
      if (!this.viewer) return;
      if (!Cesium) return;

      this.pointEntities.forEach((entity) => this.viewer.entities.remove(entity));
      this.pointEntities = [];

      points.forEach((point, index) => {
        const [lon, lat] = parseCoordinates(point.coordinates);
        const entity = this.viewer.entities.add({
          id: `intel-${index}-${Date.now()}`,
          position: Cesium.Cartesian3.fromDegrees(lon, lat, 20000),
          billboard: {
            image: INTEL_ICON_URL,
            width: 32,
            height: 32,
            color: Cesium.Color.WHITE,
            scaleByDistance: new Cesium.NearFarScalar(1.5e2, 1.2, 8.0e6, 0.4)
          },
          label: {
            text: point.region || "情报点",
            font: "12px sans-serif",
            fillColor: Cesium.Color.fromCssColorString(INTEL_NAME_COLOR),
            outlineColor: Cesium.Color.BLACK,
            outlineWidth: 2,
            style: Cesium.LabelStyle.FILL_AND_OUTLINE,
             pixelOffset: new Cesium.Cartesian2(0, -28),
            // 始终参与深度测试，避免背面标签透过地球显示
            disableDepthTestDistance: 0
          },
          isIntelligencePoint: true,
          rawData: point
        });
        this.pointEntities.push(entity);
      });
      this.applyLayerVisibility();
    },

    connectSituationSocket() {
      if (this.situationMode !== "realtime") return;
      if (!this.viewer || !this.Cesium) return;
      if (this.situationSocket) return;

      const ws = createSatelliteSituationWebSocket({
        subscribeTypes: ["satellite", "aircraft", "ship"],
        onMessage: (data) => this.handleSituationWsMessage(data),
        onClose: () => {
          this.situationSocket = null;
          if (this.situationWsManualClose) {
            this.situationWsManualClose = false;
            return;
          }
          if (this.situationMode === "realtime") {
            if (this.situationWsReconnectTimer) {
              clearTimeout(this.situationWsReconnectTimer);
            }
            this.situationWsReconnectTimer = window.setTimeout(() => {
              this.situationWsReconnectTimer = null;
              this.connectSituationSocket();
            }, 2000);
          }
        }
      });
      if (ws) this.situationSocket = ws;
    },

    disconnectSituationSocket() {
      if (this.situationWsReconnectTimer) {
        clearTimeout(this.situationWsReconnectTimer);
        this.situationWsReconnectTimer = null;
      }
      if (!this.situationSocket) return;
      this.situationWsManualClose = true;
      const ws = this.situationSocket;
      ws.onopen = null;
      ws.onmessage = null;
      ws.onerror = null;
      ws.onclose = null;
      try {
        ws.close();
      } catch (e) {
        // ignore
      }
      this.situationSocket = null;
    },

    handleSituationWsMessage(rawText) {
      let payload;
      try {
        payload = JSON.parse(rawText);
      } catch (e) {
        console.warn("[SituationWS] invalid JSON message", rawText);
        return;
      }
      const messageType = String(payload?.messageType || "").trim().toUpperCase();
      if (messageType === "SHIP" || messageType === "VESSEL") {
        const rows = this.normalizeShipRows(payload);
        if (!rows.length) return;
        rows.forEach((ship, index) => this.upsertVesselEntity(ship, index));
        return;
      }
      if (messageType === "AIRCRAFT" || !messageType) {
        const rows = this.normalizeAircraftRows(payload);
        if (!rows.length) return;
        rows.forEach((flight) => {
          this.upsertFlightEntity(flight);
        });
        return;
      }
      if (messageType === "SATELLITE") {
        if (payload.messageLevel === "INFO" && Array.isArray(payload.content)) {
          const rows = normalizeWsSatelliteInfoItems(payload.content);
          rows.forEach((sat, index) => this.upsertSatelliteEntity(sat, index));
          return;
        }
        if (
          payload.messageLevel === "WARN" &&
          payload.content &&
          typeof payload.content === "object" &&
          !Array.isArray(payload.content)
        ) {
          const c = payload.content;
          const pickLine = (primary, ...alts) => {
            const xs = [primary, ...alts];
            for (let i = 0; i < xs.length; i += 1) {
              const v = xs[i];
              if (v != null && String(v).trim()) return String(v).trim();
            }
            return "";
          };
          this.applySatelliteWarnMessage({
            objectId: String(c.objectId ?? c.object_id ?? "").trim(),
            objectName: String(c.objectName ?? c.name ?? "").trim(),
            timestamp: payload.timestamp != null ? String(payload.timestamp) : "",
            messageId: payload.messageId != null ? String(payload.messageId) : "",
            tleLine1: pickLine(c.tleLine1, c.tle_line1, c.telLine1, c.tel_line1, c.tle1),
            tleLine2: pickLine(c.tleLine2, c.tle_line2, c.telLine2, c.tel_line2, c.tle2),
            oldTleLine1: pickLine(c.oldTleLine1, c.old_tle_line1, c.oldTelLine1, c.old_tel_line1),
            oldTleLine2: pickLine(c.oldTleLine2, c.old_tle_line2, c.oldTelLine2, c.old_tel_line2),
            newTleLine1: pickLine(c.newTleLine1, c.new_tle_line1, c.newTelLine1, c.new_tel_line1),
            newTleLine2: pickLine(c.newTleLine2, c.new_tle_line2, c.newTelLine2, c.new_tel_line2)
          });
        }
      }
    },

    normalizeAircraftRows(payload) {
      return normalizeSituationAircraftRows(payload);
    },

    normalizeAircraftRow(row) {
      return normalizeSituationAircraftRow(row);
    },

    normalizeShipRows(payload) {
      return normalizeSituationShipRows(payload);
    },

    normalizeShipRow(row) {
      return normalizeSituationShipRow(row);
    },

    upsertFlightEntity(flight) {
      const Cesium = this.Cesium;
      if (!this.viewer || !Cesium || !flight) return;

      const normalized = this.normalizeAircraftRow(flight);
      if (!normalized) return;
      const icao24 = String(normalized.icao24 || "").trim();
      if (!icao24) return;

      const lon = Number(normalized.longitude);
      const lat = Number(normalized.latitude);
      const altitude = Number(normalized.geoAltitude ?? normalized.altitude ?? 0);
      const heading = Number(
        normalized.trueTrack ?? normalized.heading ?? normalized.headingDeg ?? 0
      );

      if (!isFinite(lon) || !isFinite(lat)) return;

      const position = Cesium.Cartesian3.fromDegrees(lon, lat, Math.max(altitude, 0));
      const rotation = Cesium.Math.toRadians(heading);

      let entity = this.flightEntitiesByIcao[icao24];
      if (!entity) {
        entity = this.viewer.entities.add({
          id: `flight-${icao24}`,
          position,
          billboard: {
            image: AIRCRAFT_ICON_URL,
            width: 34,
            height: 34,
            color: Cesium.Color.WHITE,
            rotation,
            alignedAxis: Cesium.Cartesian3.UNIT_Z,
            scaleByDistance: new Cesium.NearFarScalar(1.0e3, 1.0, 8.0e6, 0.25)
          },
          label: {
            text: (normalized.callsign || icao24).trim(),
            font: "10px sans-serif",
            fillColor: Cesium.Color.fromCssColorString(FLIGHT_NAME_COLOR),
            outlineColor: Cesium.Color.BLACK,
            outlineWidth: 2,
            style: Cesium.LabelStyle.FILL_AND_OUTLINE,
            pixelOffset: new Cesium.Cartesian2(0, -20),
            // 始终参与深度测试，避免背面标签透过地球显示
            disableDepthTestDistance: 0
          },
          isSituationEntity: true,
          situationType: "flight",
          rawFlight: normalized
        });
        this.flightEntitiesByIcao[icao24] = entity;
        this.applyLayerVisibility();
        return;
      }

      entity.position = position;
      if (entity.billboard) {
        entity.billboard.rotation = rotation;
      }
      if (entity.label) {
        entity.label.text = (normalized.callsign || icao24).trim();
      }
      entity.rawFlight = normalized;
      this.applyLayerVisibility();
    },

    clearFlightEntities() {
      if (!this.viewer) {
        this.flightEntitiesByIcao = {};
        return;
      }
      Object.keys(this.flightEntitiesByIcao).forEach((icao24) => {
        const entity = this.flightEntitiesByIcao[icao24];
        if (entity) this.viewer.entities.remove(entity);
      });
      this.flightEntitiesByIcao = {};
    },

    upsertVesselEntity(vessel, index = 0) {
      const Cesium = this.Cesium;
      if (!this.viewer || !Cesium || !vessel) return;
      const lon = Number(vessel.longitude);
      const lat = Number(vessel.latitude);
      if (!isFinite(lon) || !isFinite(lat)) return;

      const mmsi = String(vessel.mmsi || "").trim();
      const key =
        String(vessel.id || "").trim() ||
        (mmsi ? mmsi : `vessel-${index}`);
      const name = String(
        vessel.nameAis ??
          vessel.name_ais ??
          vessel.vesselName ??
          vessel.shipName ??
          vessel.name ??
          mmsi ??
          `船舶-${index + 1}`
      ).trim();
      const heading = Number(vessel.course_over_ground || 0);
      const rotation = Cesium.Math.toRadians(heading);
      const position = Cesium.Cartesian3.fromDegrees(lon, lat, 1000);

      let entity = this.shipEntitiesById[key];
      if (!entity) {
        entity = this.viewer.entities.add({
          id: `vessel-${key}`,
          position,
          billboard: {
            image: NAVY_ICON_URL,
            width: 32,
            height: 32,
            color: Cesium.Color.WHITE,
            rotation,
            alignedAxis: Cesium.Cartesian3.UNIT_Z,
            scaleByDistance: new Cesium.NearFarScalar(1.0e3, 1.0, 8.0e6, 0.25)
          },
          label: {
            text: name,
            font: "10px sans-serif",
            fillColor: Cesium.Color.fromCssColorString(VESSEL_NAME_COLOR),
            outlineColor: Cesium.Color.BLACK,
            outlineWidth: 2,
            style: Cesium.LabelStyle.FILL_AND_OUTLINE,
            pixelOffset: new Cesium.Cartesian2(0, -20),
            // 始终参与深度测试，避免背面标签透过地球显示
            disableDepthTestDistance: 0
          },
          isSituationEntity: true,
          situationType: "vessel",
          rawVessel: vessel
        });
        this.shipEntitiesById[key] = entity;
        this.applyLayerVisibility();
        return;
      }

      entity.position = position;
      if (entity.billboard) {
        entity.billboard.rotation = rotation;
      }
      if (entity.label) {
        entity.label.text = name;
      }
      entity.rawVessel = vessel;
      this.applyLayerVisibility();
    },

    clearVesselEntities() {
      if (!this.viewer) {
        this.shipEntitiesById = {};
        return;
      }
      Object.keys(this.shipEntitiesById).forEach((key) => {
        const entity = this.shipEntitiesById[key];
        if (entity) this.viewer.entities.remove(entity);
      });
      this.shipEntitiesById = {};
    },

    upsertSatelliteEntity(satellite, index = 0) {
      const Cesium = this.Cesium;
      if (!this.viewer || !Cesium || !satellite) return;
      let lon = Number(satellite.longitude);
      let lat = Number(satellite.latitude);
      const altitudeKm = parseSatelliteAltitudeKm(satellite);
      const tle1 = String(satellite?.telLine1 ?? satellite?.tle1 ?? "").trim();
      const tle2 = String(satellite?.telLine2 ?? satellite?.tle2 ?? "").trim();
      if (tle1 && tle2) {
        const st = computeGroundState(tle1, tle2, new Date());
        if (st) {
          lon = st.lon;
          lat = st.lat;
        }
      }
      if (!isFinite(lon) || !isFinite(lat)) {
        return;
      }

      const noradId = this.getSatelliteNoradKey(satellite, `unknown-${index}`);
      const key = noradId;
      const name = (satellite.name || noradId || `卫星-${index + 1}`).trim();
      const altitudeMeter = Math.max(
        0,
        (Number.isFinite(altitudeKm) ? altitudeKm : 0) * 1000
      );
      const position = Cesium.Cartesian3.fromDegrees(lon, lat, altitudeMeter);
      const satIconUrl = situationSatelliteIconUrl(satellite);

      let entity = this.satelliteEntitiesById[key];
      if (!entity) {
        entity = this.viewer.entities.add({
          id: `satellite-${key}`,
          position,
          billboard: {
            image: satIconUrl,
            width: 28,
            height: 28,
            color: Cesium.Color.WHITE,
            scaleByDistance: new Cesium.NearFarScalar(1.0e3, 1.0, 2.5e7, 0.25)
          },
          label: {
            text: name,
            font: "10px sans-serif",
            fillColor: Cesium.Color.fromCssColorString(SATELLITE_NAME_COLOR),
            outlineColor: Cesium.Color.BLACK,
            outlineWidth: 2,
            style: Cesium.LabelStyle.FILL_AND_OUTLINE,
            pixelOffset: new Cesium.Cartesian2(0, -18),
            // 始终参与深度测试，避免背面标签透过地球显示
            disableDepthTestDistance: 0
          },
          isSituationEntity: true,
          situationType: "satellite",
          noradId,
          rawSatellite: satellite
        });
        this.satelliteEntitiesById[key] = entity;
        this.applyLayerVisibility();
        this.refreshSatelliteVisualStates();
        return;
      }

      entity.position = position;
      if (entity.billboard) {
        entity.billboard.image = satIconUrl;
      }
      if (entity.label) {
        entity.label.text = name;
      }
      entity.rawSatellite = satellite;
      entity.noradId = noradId;
      this.applyLayerVisibility();
      this.refreshSatelliteVisualStates();
    },

    clearSatelliteEntities() {
      this.satelliteWarnFlashIdMap = {};
      this.warnFlashPhase = false;
      if (this._warnFlashTimer) {
        clearInterval(this._warnFlashTimer);
        this._warnFlashTimer = null;
      }
      if (!this.viewer) {
        this.satelliteEntitiesById = {};
        this.selectedSatelliteNoradId = null;
        this.hoveredSatelliteNoradId = null;
        return;
      }
      Object.keys(this.satelliteEntitiesById).forEach((key) => {
        const entity = this.satelliteEntitiesById[key];
        if (entity) this.viewer.entities.remove(entity);
      });
      this.satelliteEntitiesById = {};
      this.selectedSatelliteNoradId = null;
      this.hoveredSatelliteNoradId = null;
    },

    async fetchAndRenderHistoryVessels() {
      const start = this.formatHistoryApiDateTime(this.historyStartTime);
      const end = this.formatHistoryApiDateTime(this.historyEndTime);
      try {
        const res = await getVesselsByTimeRange(start, end);
        const rows = Array.isArray(res && res.data) ? res.data : [];
        this.clearVesselEntities();
        rows.forEach((vessel, index) => this.upsertVesselEntity(vessel, index));
        return true;
      } catch (error) {
        console.error("[VesselAPI] 获取历史船舶失败", error);
        return false;
      }
    },

    async fetchAndRenderHistorySatellites() {
      const start = this.formatHistoryApiDateTime(this.historyStartTime);
      const end = this.formatHistoryApiDateTime(this.historyEndTime);
      try {
        const res = await getSatellitesByTimeRange(start, end);
        const rows = Array.isArray(res && res.data) ? res.data : [];
        this.clearSatelliteEntities();
        rows.forEach((satellite, index) =>
          this.upsertSatelliteEntity(satellite, index)
        );
        return true;
      } catch (error) {
        console.error("[SatelliteAPI] 获取历史卫星失败", error);
        return false;
      }
    },

    async historyPlayPause() {
      if (this.historyPlaying) {
        if (this.historyTimer) {
          clearInterval(this.historyTimer);
          this.historyTimer = null;
        }
        this.historyPlaying = false;
        return;
      }
      if (this.historyLoading) return;
      const start = this.formatHistoryApiDateTime(this.historyStartTime);
      const end = this.formatHistoryApiDateTime(this.historyEndTime);
      if (!start || !end) {
        window.alert("请输入有效的开始和结束时间");
        return;
      }
      if (new Date(end).getTime() <= new Date(start).getTime()) {
        window.alert("结束时间必须晚于开始时间");
        return;
      }

      this.historyLoading = true;
      const [vesselLoaded, satelliteLoaded] = await Promise.all([
        this.fetchAndRenderHistoryVessels(),
        this.fetchAndRenderHistorySatellites()
      ]);
      this.historyLoading = false;

      if (!vesselLoaded && !satelliteLoaded) {
        window.alert("历史态势数据加载失败");
        return;
      }
      this.historyPlaying = true;
      this.historyTimer = setInterval(() => {
        this.historyCurrentMinute += this.historySpeed;
        if (this.historyCurrentMinute >= this.historyMaxMinutes) {
          this.historyCurrentMinute = this.historyMaxMinutes;
          this.historyPlayPause();
        }
      }, 1000);
    },

    historyEnd() {
      if (this.historyTimer) {
        clearInterval(this.historyTimer);
        this.historyTimer = null;
      }
      this.historyPlaying = false;
      this.historyCurrentMinute = 0;
      this.clearVesselEntities();
      this.clearSatelliteEntities();
      this.selectedSituationDetail = null;
    },

    onProgressDragStart() {
      this._historyProgressDragging = true;
      if (this.historyPlaying) {
        clearInterval(this.historyTimer);
        this.historyTimer = null;
        this.historyPlaying = false;
      }
    },

    onProgressDragEnd() {
      this._historyProgressDragging = false;
    },

    syncHistoryClock() {
      const Cesium = this.Cesium;
      if (!this.viewer || !Cesium || this.situationMode !== "history") return;
      const start = new Date(this.historyStartTime);
      if (!isFinite(start.getTime())) return;
      const base = Cesium.JulianDate.fromDate(start);
      this.viewer.clock.currentTime = Cesium.JulianDate.addMinutes(
        base,
        Number(this.historyCurrentMinute || 0),
        new Cesium.JulianDate()
      );
    },

    /**
     * 外部“视角归位”按钮调用，用于 3D 返回初始视角。
     * @see src/components/Situation/index.vue
     */
    resetView() {
      if (!this.viewer || !this.viewer.camera) return;
      this.viewer.camera.flyHome(0);
    },
    focusPointView(coordinateText, zoom = 5) {
      const Cesium = this.Cesium;
      if (!this.viewer || !this.viewer.camera || !Cesium) return;
      const [lon, lat] = parseCoordinates(coordinateText);
      if (!Number.isFinite(lon) || !Number.isFinite(lat)) return;
      const h = zoom > 6 ? 900000 : zoom > 4 ? 1600000 : 2600000;
      this.viewer.camera.flyTo({
        destination: Cesium.Cartesian3.fromDegrees(lon, lat, h),
        duration: 0.8
      });
    },

    /** 父级调整态势区宽度后调用 */
    onContainerResize() {
      if (this.viewer) this.viewer.resize();
    }
  }
};
</script>

<style scoped>
.globe-scene {
  position: relative;
  width: 100%;
  height: 100%;
}
.globe-canvas {
  width: 100%;
  height: 100%;
}
</style>

