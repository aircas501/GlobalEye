<!--
  态势模块 · 2D 画布：OpenLayers 地图、国家面、实时/历史态势图层与交互。
  业务逻辑较重部分保留在本文件；浮层 UI 拆至 overlays/ 与 css/situationSceneShared.css。
-->
<template>
  <div class="ol-scene">
    <div ref="mapContainer" class="ol-map"></div>
    <div
      v-if="mouseFocusLonLat"
      class="mouse-lonlat-overlay"
      aria-hidden="true"
    >
      经度：{{ mouseFocusLonLat.lon.toFixed(3) }}　纬度：{{
        mouseFocusLonLat.lat.toFixed(3)
      }}
    </div>

    <SituationModeSwitch v-model="situationMode" name="situationModeOl" />

    <SituationLayerPanel
      :layer-visibility="layerVisibility"
      @update:layerVisibility="onLayerVisibilityFromPanel"
    />

    <SituationCountryPanel
      :visible="countriesReady"
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
import { calculateSatelliteCoverage } from "@/api/satelliteCoverage";
import { createSatelliteSituationWebSocket } from "@/api/SituationWebSocket";
import { normalizeWsSatelliteInfoItems } from "@/utils/satelliteWsAdapter";
import { parseSatelliteAltitudeKm } from "@/utils/satelliteAltitude";
import { splitTrackAtDateline } from "@/utils/geoTrackSegments";
import { hexToRgba, clampNumber } from "@/utils/colorUtils";
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

import Map from "ol/Map";
import View from "ol/View";
import TileLayer from "ol/layer/Tile";
import VectorLayer from "ol/layer/Vector";
import VectorSource from "ol/source/Vector";
import XYZ from "ol/source/XYZ";
import { fromLonLat, transformExtent, toLonLat } from "ol/proj";
import GeoJSON from "ol/format/GeoJSON";
import Feature from "ol/Feature";
import { Point, LineString, Polygon } from "ol/geom";
import { Style, Fill, Stroke, Icon, Text, Circle } from "ol/style";

const APP_BASE_URL = process.env.BASE_URL || "/";
const COUNTRIES_GEOJSON_URL = `${APP_BASE_URL}data/countries.geojson`;

const rgba = hexToRgba;

// UI 颜色（2D 态势点）
// 说明：SVG 本体统一“黑色”，颜色由文字 + 轻微阴影表达；Hover/Selected 仍保持黄色不变

// 交互态：Hover / Selected / 聚焦高亮外圈
const HOVER_YELLOW = "#FFE066";
const SELECTED_YELLOW = "#FFD60A";
const FOCUS_SCALE = 1.3;
const ICON_BASE_SIZE_PX = 200; // 与各 svg 的 width/height 对齐（用于计算外圈半径）

// 覆盖范围展示（右键触发）
const COVERAGE_ICON_ORANGE = "#FF8C00";
const COVERAGE_ICON_ORANGE_RGBA = "rgba(255, 140, 0, 0.95)";
const COVERAGE_RING_FILL = "rgba(255, 165, 165, 0.28)";
const COVERAGE_RING_STROKE = "rgba(255, 165, 165, 0.95)";
const COVERAGE_RING_LINE_DASH = [10, 6];

/** 非选中卫星星下点轨迹 */
// 比卫星名称更淡一点即可（同色系）
const SAT_TRACK_COLOR = rgba(SATELLITE_NAME_COLOR, 0.32);
const SAT_TRACK_WIDTH = 2;
/** 选中卫星轨迹高亮 */
const SAT_TRACK_HOVER_COLOR = rgba(HOVER_YELLOW, 0.95);
const SAT_TRACK_SELECTED_COLOR = rgba(SELECTED_YELLOW, 0.98);
// 不加粗：选中和非选中统一线宽
const SAT_TRACK_SELECTED_WIDTH = SAT_TRACK_WIDTH;
/**
 * 2D 地图中态势目标图标需要随 zoom 等比放大/缩小
 * （OpenLayers 默认 Icon 尺寸不会随 zoom 自动变化，这里手动刷新样式）
 */
const ICON_SCALE_ZOOM_BASE = 2; // 对齐初始 zoom=2 时的默认图标大小
const ICON_SCALE_FACTOR_MIN = 0.7; // 缩小时下限，避免点太小难点
const ICON_SCALE_FACTOR_MAX = 1.8; // 放大时上限，避免遮挡过多
const DEFAULT_TILE_URL =
  process.env.VUE_APP_2D_TILE_URL ||
  // 默认使用标准 OSM（浅色）
  "https://tile.openstreetmap.org/{z}/{x}/{y}.png";
const LOCAL_TILE_URL = `${APP_BASE_URL}tiles/{z}/{x}/{y}.png`;
const LOCAL_TILE_MIN_ZOOM = 1;
const LOCAL_TILE_MAX_ZOOM = 6;
const LOCAL_FLAG_BASE_URL = `${APP_BASE_URL}w2560`;
const rawUseLocalTile = String(process.env.VUE_APP_2D_USE_LOCAL_TILE || "")
  .trim()
  .toLowerCase();
// 生产默认走本地瓦片；仅在显式配置 false 时关闭
const USE_LOCAL_TILE =
  process.env.NODE_ENV === "production"
    ? rawUseLocalTile !== "false"
    : rawUseLocalTile === "true";
  //第三方（深色）
  // "https://basemaps.cartocdn.com/dark_all/{z}/{x}/{y}.png'";

// 情报点图标来自公共资源（SVG 本体黑色化，由文字颜色表现类别）

/**
 * 2D 态势主组件：OSM/瓦片底图、国家 GeoJSON、图层开关、实时/历史数据与地图交互。
 * 父级可调用：resetView()、onContainerResize()（见 Situation/index.vue）。
 */
export default {
  name: "OpenLayersScene",
  components: {
    SituationCountryPanel,
    SituationFloatingCards,
    SituationHistoryPanel,
    SituationLayerPanel,
    SituationModeSwitch
  },
  props: {
    points: { type: Array, default: () => [] },
    showLatLonGrid: { type: Boolean, default: true }
  },
  data() {
    return {
      map: null,
      view: null,

      countriesLayer: null,
      countriesSource: null,
      countriesReady: false,
      countryFeaturesByIso: {},
      countryList: [],
      countrySearch: "",

      hoveredIso: null,
      selectedIso: null,
      searchFocusIso: null,

      _flagImgFailed: false,
      _flagFallbackTried: false,

      selectedPoint: null,
      selectedSituationDetail: null,
      mouseFocusLonLat: null,

      // Situation layers
      intelSource: null,
      flightSource: null,
      vesselSource: null,
      satelliteSource: null,
      satelliteTrackSource: null,
      satelliteCoverageSource: null,
      gridSource: null,

      intelLayer: null,
      flightLayer: null,
      vesselLayer: null,
      satelliteTrackLayer: null,
      satelliteLayer: null,
      satelliteCoverageLayer: null,
      gridLayer: null,

      flightFeaturesByIcao: {},
      shipFeaturesById: {},
      satelliteFeaturesById: {},
      intelFeaturesById: {},

      /** 态势图层显隐（默认全显示） */
      layerVisibility: {
        intel: true,
        flight: true,
        vessel: true,
        satellite: true
      },

      /** 选中卫星 NORAD ID（字符串），用于轨迹高亮 */
      selectedSatelliteNoradId: null,

      /** 右键展示的覆盖范围卫星 NORAD ID（字符串），用于画覆盖圈 + 图标变橙色 */
      satelliteCoverageNoradId: null,

      /** 覆盖范围请求状态：避免实时更新时接口过度并发 */
      _satelliteCoverageReqSeq: 0,
      _satelliteCoverageInFlight: false,
      _satelliteCoveragePendingPayload: null,
      _satelliteCoverageRecalcTimer: null,
      _satelliteCoverageLastLonLat: null,
      _satelliteCoverageLastTelLines: null,
      _satelliteCoverageLastEpoch: null,

      /** 2D 目标点选中态（用于飞机/舰船/告警点外圈高亮） */
      selectedTargetKind: null, // "intel" | "flight" | "vessel" | "satellite"
      selectedTargetKey: null, // kind 对应的唯一 key
      /** 2D 目标点悬停态（用于外圈高亮） */
      hoveredTargetKind: null,
      hoveredTargetKey: null,
      /** 悬停卫星/轨迹时，用于同步卫星点与轨迹高亮 */
      hoveredSatelliteNoradId: null,

      situationMode: "realtime",
      satelliteSituationSocket: null,
      satelliteWsManualClose: false,
      satelliteWsReconnectTimer: null,
      /** WebSocket 模式下每颗卫星已走过的星下点 [lon,lat][]（EPSG:4326） */
      satelliteGroundTrackPoints: {},
      /** 异动卫星：卫星 ID（与 objectId 一致）→ 图标红色闪烁，点击卫星可关闭 */
      satelliteWarnFlashIdMap: {},
      warnFlashPhase: false,
      _warnFlashTimer: null,

      /** 情报点雷达波动画 */
      _intelPulseTimer: null,

      // 用于节流刷新态势图标样式（随 zoom 变化）
      _iconStyleRefreshTimer: null,

      // History UI/state
      historyStartTime: "2026-03-20T00:00:00",
      historyEndTime: "2026-03-21T00:00:00",
      historySpeed: 1,
      historyCurrentMinute: 0,
      historyPlaying: false,
      historyTimer: null,
      _historyProgressDragging: false,
      historyLoading: false,

      // Country styles (cached)
      countryStyles: null,
      countryLabelStyleCache: {}
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
          if (c.nameZh && c.nameZh.includes(q)) return true;
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
      // 2D 不使用 Cesium Clock，这里仅保留 UI 行为一致
    },
    historyMaxMinutes(newVal) {
      if (this.historyCurrentMinute > newVal) this.historyCurrentMinute = newVal;
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
    selectedPoint(newVal) {
      if (!newVal && this.selectedTargetKind === "intel") {
        this.selectedTargetKind = null;
        this.selectedTargetKey = null;
        this.refreshIconStylesByZoom();
      }
    },
    selectedSituationDetail(newVal) {
      if (!newVal && this.selectedTargetKind && this.selectedTargetKind !== "intel") {
        if (this.selectedTargetKind === "satellite") {
          this.selectedSatelliteNoradId = null;
        }
        this.selectedTargetKind = null;
        this.selectedTargetKey = null;
        this.refreshIconStylesByZoom();
      }
    },
    situationMode(newMode) {
      if (newMode === "realtime") {
        this.historyEnd();
        this.clearVesselFeatures();
        this.clearSatelliteFeatures();
        this.startSatelliteRealtimeLoop();
      } else {
        this.stopSatelliteRealtimeLoop();
        this.clearFlightFeatures();
        this.clearVesselFeatures();
        this.clearSatelliteFeatures();
        this.selectedSituationDetail = null;
        this.selectedSatelliteNoradId = null;
        this.selectedPoint = null;
        this.selectedTargetKind = null;
        this.selectedTargetKey = null;
        this.hoveredTargetKind = null;
        this.hoveredTargetKey = null;
        this.hoveredSatelliteNoradId = null;
      }
    },
    showLatLonGrid(newVal) {
      if (this.gridLayer) this.gridLayer.setVisible(!!newVal);
    },
    selectedSatelliteNoradId() {
      if (this.satelliteTrackLayer) this.satelliteTrackLayer.changed();
      this.updateSatellitePointStyles();
    }
  },
  mounted() {
    this.initMap();
    this.loadCountriesGeoJson();
    this.startSatelliteRealtimeLoop();
    // 切换 2D/3D 后组件重新挂载：watch(points) 不会触发，因此需要显式渲染一次
    this.renderIntelligencePoints(this.points);
    // Note: 初始默认 realtime，不需要等待 situationMode watcher
  },
  beforeDestroy() {
    this.historyEnd();
    this.stopSatelliteRealtimeLoop();
    this.clearFlightFeatures();
    this.clearVesselFeatures();

    // 清理实时数据
    this.clearSatelliteFeatures();
    this.clearIntelFeatures();
    this.stopIntelPulseTicker();

    if (this.map) {
      this.map.setTarget(null);
      this.map = null;
    }
  },
  methods: {
    stopIntelPulseTicker() {
      if (this._intelPulseTimer) {
        clearInterval(this._intelPulseTimer);
        this._intelPulseTimer = null;
      }
    },
    syncIntelPulseTicker() {
      const anyPulse = Object.values(this.intelFeaturesById || {}).some((f) => {
        const raw = f && f.get ? f.get("rawData") || {} : {};
        return !!raw.pulse;
      });

      if (!anyPulse) {
        this.stopIntelPulseTicker();
        return;
      }
      if (this._intelPulseTimer) return;

      this._intelPulseTimer = window.setInterval(() => {
        Object.values(this.intelFeaturesById || {}).forEach((f) => {
          const raw = f && f.get ? f.get("rawData") || {} : {};
          if (!raw.pulse) return;
          this.updateIntelFeatureStyle(f);
        });
      }, 70);
    },
    onFlagImgError() {
      if (!this._flagFallbackTried) {
        this._flagFallbackTried = true;
        return;
      }
      this._flagImgFailed = true;
    },

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
        SDS: "ss", // 南苏丹：NE 用 SDS，ISO 为 SSD
        SAH: "eh" // 西撒哈拉：NE 用 SAH，ISO 为 ESH
      };
      let a2 = overrides[iso3];
      if (!a2 && /^[A-Za-z]{3}$/.test(iso3)) {
        a2 = countries.alpha3ToAlpha2(iso3.toUpperCase());
      }
      if (!a2) return "";
      return `${LOCAL_FLAG_BASE_URL}/${String(a2).toLowerCase()}.png`;
    },

    getFallbackFlagUrl(iso3) {
      // 回退时仍使用本地 alpha2 路径（避免空图片反复触发 error）
      if (!iso3) return "";
      const u = String(iso3).toUpperCase();
      if (u === "SDS") return `${LOCAL_FLAG_BASE_URL}/ss.png`;
      if (u === "SAH") return `${LOCAL_FLAG_BASE_URL}/eh.png`;
      let a2 = countries.alpha3ToAlpha2(u);
      if (!a2) return "";
      return `${LOCAL_FLAG_BASE_URL}/${String(a2).toLowerCase()}.png`;
    },

    onCountrySearchInput(e) {
      const raw = e.target.value || "";
      const cleaned = raw.replace(/[^\u4e00-\u9fffa-zA-Z\u00c0-\u024f\s\-'·()]/g, "");
      this.countrySearch = cleaned;
    },

    initCountryStyles() {
      // 默认态不渲染国家区块颜色，让 OSM 底图完整呈现
      const countryDefaultFill = "rgba(0, 0, 0, 0)";
      const countryDefaultStroke = "rgba(0, 0, 0, 0)";
      const countryHoverFill = rgba("#6be6ff", 0.42);
      const countryHoverStroke = rgba("#b8f0ff", 0.9);
      const countrySearchFill = rgba("#ffd54a", 0.45);
      const countrySearchStroke = rgba("#fff176", 0.95);
      const countrySelectedFill = rgba("#ff8800", 0.58);
      const countrySelectedStroke = rgba("#ffb84d", 0.95);

      return {
        default: new Style({
          fill: new Fill({ color: countryDefaultFill }),
          stroke: new Stroke({ color: countryDefaultStroke, width: 1 })
        }),
        hover: new Style({
          fill: new Fill({ color: countryHoverFill }),
          stroke: new Stroke({ color: countryHoverStroke, width: 1.2 })
        }),
        search: new Style({
          fill: new Fill({ color: countrySearchFill }),
          stroke: new Stroke({ color: countrySearchStroke, width: 1.2 })
        }),
        selected: new Style({
          fill: new Fill({ color: countrySelectedFill }),
          stroke: new Stroke({ color: countrySelectedStroke, width: 1.4 })
        })
      };
    },

    getCountryLabelStyle(feature) {
      // 取消“国家名称标注”，避免全球视角下文字过密。
      return null;
    },

    initMap() {
      this.countryStyles = this.initCountryStyles();
      const baseTileUrl = USE_LOCAL_TILE ? LOCAL_TILE_URL : DEFAULT_TILE_URL;
      const baseLayer = new TileLayer({
        opacity: 0.96,
        source: new XYZ({
          url: baseTileUrl,
          crossOrigin: "anonymous",
          wrapX: true,
          minZoom: USE_LOCAL_TILE ? LOCAL_TILE_MIN_ZOOM : 0,
          maxZoom: USE_LOCAL_TILE ? LOCAL_TILE_MAX_ZOOM : 20
        })
      });

      this.gridSource = new VectorSource();
      this.gridLayer = new VectorLayer({
        source: this.gridSource,
        style: new Style({
          stroke: new Stroke({
            color: "rgba(255, 255, 255, 0.18)",
            width: 1
          })
        })
      });
      this.buildLatLonGrid();
      if (this.gridLayer) this.gridLayer.setVisible(!!this.showLatLonGrid);

      this.countriesSource = new VectorSource();
      this.countriesLayer = new VectorLayer({
        source: this.countriesSource,
        style: (feature) => {
          const iso = feature.get("iso");
          let baseStyle = this.countryStyles.default;
          if (iso && iso === this.selectedIso) baseStyle = this.countryStyles.selected;
          else if (iso && iso === this.searchFocusIso) baseStyle = this.countryStyles.search;
          else if (iso && iso === this.hoveredIso) baseStyle = this.countryStyles.hover;

          const labelStyle = this.getCountryLabelStyle(feature);
          return labelStyle ? [baseStyle, labelStyle] : baseStyle;
        }
      });

      this.intelSource = new VectorSource();
      this.intelLayer = new VectorLayer({ source: this.intelSource });

      this.flightSource = new VectorSource();
      this.flightLayer = new VectorLayer({ source: this.flightSource });

      this.vesselSource = new VectorSource();
      this.vesselLayer = new VectorLayer({ source: this.vesselSource });

      this.satelliteTrackSource = new VectorSource();
      this.satelliteTrackLayer = new VectorLayer({
        source: this.satelliteTrackSource,
        style: (feature) => {
          const id = String(feature.get("noradId") || "");
          const isSelected =
            this.selectedSatelliteNoradId &&
            this.selectedSatelliteNoradId === id;
          const isHovered =
            this.hoveredSatelliteNoradId && this.hoveredSatelliteNoradId === id;
          return new Style({
            stroke: new Stroke({
              color: isSelected
                ? SAT_TRACK_SELECTED_COLOR
                : isHovered
                  ? SAT_TRACK_HOVER_COLOR
                  : SAT_TRACK_COLOR,
              width: isSelected ? SAT_TRACK_SELECTED_WIDTH : SAT_TRACK_WIDTH
            })
          });
        }
      });

      this.satelliteSource = new VectorSource();
      this.satelliteLayer = new VectorLayer({ source: this.satelliteSource });

      this.satelliteCoverageSource = new VectorSource();
      this.satelliteCoverageLayer = new VectorLayer({
        source: this.satelliteCoverageSource
      });

      this.view = new View({
        center: fromLonLat([0, 0]),
        zoom: 2
      });

      this.map = new Map({
        target: this.$refs.mapContainer,
        layers: [
          baseLayer,
          this.gridLayer,
          this.countriesLayer,
          this.intelLayer,
          this.flightLayer,
          this.vesselLayer,
          this.satelliteTrackLayer,
          this.satelliteLayer,
          this.satelliteCoverageLayer
        ],
        view: this.view,
        controls: []
      });

      // 国家中文标注按缩放阈值显示，需要在缩放变化后重绘样式
      this.map.on("moveend", () => this.refreshCountryLayerStyle());

      // 2D 态势图标随 zoom 等比缩放：用 resolution 变化触发刷新
      if (this.view) {
        this.view.on("change:resolution", () => {
          this.queueIconStyleRefresh();
        });
      }
      this.bindMapEvents();
    },

    resetView() {
      if (!this.map || !this.view) return;
      this.view.animate(
        { center: fromLonLat([0, 0]), duration: 420 },
        { zoom: 2, duration: 320 }
      );
    },
    /**
     * 聚焦到指定情报点：目标位于中心，并拉近到指定 zoom。
     * @param {string} coordinateText "lat,lon"
     * @param {number} zoom 目标缩放级别
     */
    focusPointView(coordinateText, zoom = 5) {
      if (!this.map || !this.view) return;
      const [lon, lat] = parseCoordinates(coordinateText);
      if (!Number.isFinite(lon) || !Number.isFinite(lat)) return;
      const targetCenter = fromLonLat([lon, lat]);
      const targetZoom = Math.max(2, Math.min(12, Number(zoom) || 5));
      this.view.animate(
        { center: targetCenter, duration: 460 },
        { zoom: targetZoom, duration: 380 }
      );
    },

    /** 父级调整态势区宽度后调用，避免地图画布尺寸不更新 */
    onContainerResize() {
      if (this.map) this.map.updateSize();
    },

    applyLayerVisibility() {
      const v = this.layerVisibility;
      if (this.intelLayer) this.intelLayer.setVisible(!!v.intel);
      if (this.flightLayer) this.flightLayer.setVisible(!!v.flight);
      if (this.vesselLayer) this.vesselLayer.setVisible(!!v.vessel);
      if (this.satelliteLayer) this.satelliteLayer.setVisible(!!v.satellite);
      if (this.satelliteTrackLayer) this.satelliteTrackLayer.setVisible(!!v.satellite);
      if (this.satelliteCoverageLayer) {
        this.satelliteCoverageLayer.setVisible(
          !!v.satellite && !!this.satelliteCoverageNoradId
        );
      }
      if (this.gridLayer) this.gridLayer.setVisible(!!this.showLatLonGrid);
    },

    startSatelliteRealtimeLoop() {
      if (this.situationMode !== "realtime") return;
      this.stopSatelliteRealtimeLoop();
      this.connectSatelliteSituationSocket();
    },

    stopSatelliteRealtimeLoop() {
      this.disconnectSatelliteSituationSocket();
    },

    connectSatelliteSituationSocket() {
      if (this.situationMode !== "realtime") return;
      if (this.satelliteSituationSocket) return;

      const ws = createSatelliteSituationWebSocket({
        subscribeTypes: ["satellite", "aircraft", "ship"],
        onMessage: (data) => this.handleSatelliteSituationWsMessage(data),
        onClose: () => {
          this.satelliteSituationSocket = null;
          if (this.satelliteWsManualClose) {
            this.satelliteWsManualClose = false;
            return;
          }
          if (this.situationMode === "realtime") {
            if (this.satelliteWsReconnectTimer) {
              clearTimeout(this.satelliteWsReconnectTimer);
            }
            this.satelliteWsReconnectTimer = window.setTimeout(() => {
              this.satelliteWsReconnectTimer = null;
              this.connectSatelliteSituationSocket();
            }, 2000);
          }
        }
      });
      if (ws) this.satelliteSituationSocket = ws;
    },

    disconnectSatelliteSituationSocket() {
      if (this.satelliteWsReconnectTimer) {
        clearTimeout(this.satelliteWsReconnectTimer);
        this.satelliteWsReconnectTimer = null;
      }
      if (!this.satelliteSituationSocket) return;
      this.satelliteWsManualClose = true;
      const ws = this.satelliteSituationSocket;
      ws.onopen = null;
      ws.onmessage = null;
      ws.onerror = null;
      ws.onclose = null;
      try {
        ws.close();
      } catch (e) {
        // ignore
      }
      this.satelliteSituationSocket = null;
    },

    handleSatelliteSituationWsMessage(rawText) {
      let msg;
      try {
        msg = JSON.parse(rawText);
      } catch (e) {
        console.warn("[SatelliteWS] invalid JSON", rawText);
        return;
      }
      const messageType = String(msg.messageType || "").trim().toUpperCase();
      if (messageType === "AIRCRAFT") {
        this.handleAircraftSituationWsMessage(msg);
        return;
      }
      if (messageType === "SHIP" || messageType === "VESSEL") {
        this.handleShipSituationWsMessage(msg);
        return;
      }
      if (messageType && messageType !== "SATELLITE") return;

      if (msg.messageLevel === "INFO" && Array.isArray(msg.content)) {
        const rows = normalizeWsSatelliteInfoItems(msg.content);
        rows.forEach((row, index) => this.upsertSatelliteFeature(row, index));
        rows.forEach((row) => {
          const id = String(row.norad_id || "").trim();
          if (id) this.appendWsTrackPoint(id, row.longitude, row.latitude);
        });
        this.renderSatelliteTracks();
        return;
      }

      if (
        msg.messageLevel === "WARN" &&
        msg.content &&
        typeof msg.content === "object" &&
        !Array.isArray(msg.content)
      ) {
        const c = msg.content;
        const objectId = String(c.objectId ?? c.object_id ?? "").trim();
        const objectName = String(c.objectName ?? c.name ?? "").trim();
        const timestamp =
          msg.timestamp != null ? String(msg.timestamp) : "";
        const messageId =
          msg.messageId != null ? String(msg.messageId) : "";
        const pickLine = (primary, ...alts) => {
          const xs = [primary, ...alts];
          for (let i = 0; i < xs.length; i += 1) {
            const v = xs[i];
            if (v != null && String(v).trim()) return String(v).trim();
          }
          return "";
        };
        this.applySatelliteWarnMessage({
          objectId,
          objectName,
          timestamp,
          messageId,
          tleLine1: pickLine(
            c.tleLine1,
            c.tle_line1,
            c.telLine1,
            c.tel_line1,
            c.tle1
          ),
          tleLine2: pickLine(
            c.tleLine2,
            c.tle_line2,
            c.telLine2,
            c.tel_line2,
            c.tle2
          ),
          oldTleLine1: pickLine(
            c.oldTleLine1,
            c.old_tle_line1,
            c.oldTelLine1,
            c.old_tel_line1
          ),
          oldTleLine2: pickLine(
            c.oldTleLine2,
            c.old_tle_line2,
            c.oldTelLine2,
            c.old_tel_line2
          ),
          newTleLine1: pickLine(
            c.newTleLine1,
            c.new_tle_line1,
            c.newTelLine1,
            c.new_tel_line1
          ),
          newTleLine2: pickLine(
            c.newTleLine2,
            c.new_tle_line2,
            c.newTelLine2,
            c.new_tel_line2
          )
        });
      }
    },

    handleAircraftSituationWsMessage(msg) {
      const rows = this.normalizeAircraftRows(msg);
      if (!rows.length) return;
      rows.forEach((flight) => this.upsertFlightFeature(flight));
    },

    handleShipSituationWsMessage(msg) {
      const rows = this.normalizeShipRows(msg);
      if (!rows.length) return;
      rows.forEach((ship, index) => this.upsertVesselFeature(ship, index));
    },

    normalizeAircraftRows(msg) {
      return normalizeSituationAircraftRows(msg || {});
    },

    normalizeAircraftRow(row) {
      return normalizeSituationAircraftRow(row);
    },

    normalizeShipRows(msg) {
      return normalizeSituationShipRows(msg || {});
    },

    normalizeShipRow(row) {
      return normalizeSituationShipRow(row);
    },

    appendWsTrackPoint(id, lon, lat) {
      if (!id || !Number.isFinite(lon) || !Number.isFinite(lat)) return;
      const bucket = this.satelliteGroundTrackPoints;
      const arr = bucket[id] ? bucket[id].slice() : [];
      const last = arr[arr.length - 1];
      if (last && last[0] === lon && last[1] === lat) return;
      arr.push([lon, lat]);
      const maxPts = 800;
      if (arr.length > maxPts) arr.splice(0, arr.length - maxPts);
      this.$set(this.satelliteGroundTrackPoints, id, arr);
    },

    renderWsGroundTrackFeatures() {
      if (!this.satelliteTrackSource) return;
      const bucket = this.satelliteGroundTrackPoints || {};
      Object.keys(bucket).forEach((id) => {
        const pts = bucket[id];
        if (!pts || pts.length < 2) return;
        const lonLat = pts.map(([lo, la]) => ({ lon: lo, lat: la }));
        const split = splitTrackAtDateline(lonLat);
        split.forEach((seg) => {
          if (!seg || seg.length < 2) return;
          const coords3857 = seg.map((p) => fromLonLat([p.lon, p.lat]));
          const line = new LineString(coords3857);
          const f = new Feature({ geometry: line });
          f.set("kind", "satellite_track");
          f.set("noradId", String(id));
          this.satelliteTrackSource.addFeature(f);
        });
      });
    },

    applySatelliteWarnMessage(payload) {
      const { objectId } = payload || {};
      if (objectId) {
        this.$set(this.satelliteWarnFlashIdMap, objectId, true);
        this.syncWarnFlashTicker();
        const f = this.satelliteFeaturesById[objectId];
        if (f) this.updateSatelliteFeatureStyle(f);
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
        Object.keys(this.satelliteWarnFlashIdMap || {}).forEach((sid) => {
          const feat = this.satelliteFeaturesById[sid];
          if (feat) this.updateSatelliteFeatureStyle(feat);
        });
      }, 480);
    },

    clearSatelliteWarnFlashForId(id) {
      const sid = String(id || "").trim();
      if (!sid || !this.satelliteWarnFlashIdMap[sid]) return;
      this.$delete(this.satelliteWarnFlashIdMap, sid);
      this.syncWarnFlashTicker();
      const f = this.satelliteFeaturesById[sid];
      if (f) this.updateSatelliteFeatureStyle(f);
    },

    renderSatelliteTracks() {
      if (!this.satelliteTrackSource) return;
      this.satelliteTrackSource.clear();
      this.renderWsGroundTrackFeatures();
      if (this.satelliteTrackLayer) this.satelliteTrackLayer.changed();
    },

    clearSatelliteTrackFeatures() {
      if (!this.satelliteTrackSource) return;
      this.satelliteTrackSource.clear();
    },

    /**
     * 构建 10 度经纬网格（EPSG:3857 下绘制）。
     * 说明：由于 WebMercator 无法表示 ±90°，纬线在 [-85.05112878, 85.05112878] 上做夹紧，
     * 并只在 [-80, 80] 生成 10 度纬线，避免极区数值发散导致的异常。
     */
    buildLatLonGrid() {
      if (!this.gridSource) return;
      this.gridSource.clear();

      const MERCATOR_MAX_LAT = 85.05112878;
      const lonStep = 10; // 10 度经线
      const latStep = 10; // 10 度纬线
      const sampleStep = 5; // 折线采样间隔（度）

      const clampLat = (lat) =>
        Math.max(-MERCATOR_MAX_LAT, Math.min(MERCATOR_MAX_LAT, lat));

      // 经线：lon 固定，lat 从 -85.05 到 +85.05 采样
      for (let lon = -180; lon < 180; lon += lonStep) {
        const coords = [];
        for (let lat = -MERCATOR_MAX_LAT; lat <= MERCATOR_MAX_LAT; lat += sampleStep) {
          coords.push(fromLonLat([lon, clampLat(lat)]));
        }
        const line = new LineString(coords);
        const f = new Feature({ geometry: line });
        f.set("gridType", "lon");
        f.set("gridValue", lon);
        this.gridSource.addFeature(f);
      }

      // 纬线：lat 固定，lon 从 -180 到 +180 采样
      // 这里按业务要求“10 度网格”，在 [-80, 80] 生成纬线，避免极区问题。
      for (let lat = -80; lat <= 80; lat += latStep) {
        const coords = [];
        for (let lon = -180; lon < 180; lon += sampleStep) {
          coords.push(fromLonLat([lon, clampLat(lat)]));
        }
        const line = new LineString(coords);
        const f = new Feature({ geometry: line });
        f.set("gridType", "lat");
        f.set("gridValue", lat);
        this.gridSource.addFeature(f);
      }
    },

    getSatelliteIconUrl(satellite) {
      return situationSatelliteIconUrl(satellite);
    },

    /** 图层面板子组件回调：合并显隐并刷新 OL 图层 */
    onLayerVisibilityFromPanel(next) {
      this.layerVisibility = next;
      this.applyLayerVisibility();
    },

    /**
     * 计算态势图标缩放因子（随 2D zoom 变化）
     * @returns {number}
     */
    getIconScaleFactor() {
      if (!this.view || !this.view.getZoom) return 1;
      const zoom = this.view.getZoom();
      // 按需求：zoom <= 5 时略微放大
      // zoom  > 5 时再更大（用于 5 级别后的二次放大）
      return zoom > 5 ? 1.4 : 1.2;
    },

    getScaledIconScale(baseScale) {
      return baseScale * this.getIconScaleFactor();
    },

    getScaledOffsetY(baseOffsetY) {
      return baseOffsetY * this.getIconScaleFactor();
    },

    queueIconStyleRefresh() {
      if (this._iconStyleRefreshTimer) return;
      this._iconStyleRefreshTimer = window.setTimeout(() => {
        this._iconStyleRefreshTimer = null;
        this.refreshIconStylesByZoom();
      }, 80);
    },

    /**
     * 获取外圈高亮（Hover / Selected）的颜色
     */
    getFocusColor(isSelected, isHovered) {
      if (isSelected) return SELECTED_YELLOW;
      if (isHovered) return HOVER_YELLOW;
      return null;
    },

    getCategoryGlowStyles(categoryColor, iconScale) {
      // 阴影改为完全依赖 SVG 自身轮廓，不再额外绘制圆圈。
      // 这里保留函数签名以兼容调用方，直接返回空数组即可。
      return [];
    },

    /**
     * 用多圈模拟 Glow + 外圈高亮（OpenLayers 的 Icon/Circle 渲染不支持 shadowBlur，这里用透明描边做“发光”效果）
     * @param {string} focusColor HEX 颜色，如 #FFD60A
     * @param {number} iconScale Icon 当前 scale
     * @param {boolean} isSelected 用于区分 hover/selected 的强度
     */
    getCircleGlowAndRingStyles(focusColor, iconScale, isSelected) {
      const ringRadius = (ICON_BASE_SIZE_PX * iconScale) / 2;
      const glowSpread = isSelected ? 8 : 7;
      const glowStrokeWidth = isSelected ? 6 : 5;
      const ringStrokeWidth = isSelected ? 2.8 : 2.4; // 2~3
      const ringRadiusOuter = ringRadius + 2.5;

      const glowColor = rgba(focusColor, isSelected ? 0.34 : 0.25);

      return [
        new Style({
          image: new Circle({
            radius: ringRadius + glowSpread,
            stroke: new Stroke({ color: glowColor, width: glowStrokeWidth })
          })
        }),
        new Style({
          image: new Circle({
            radius: ringRadiusOuter,
            stroke: new Stroke({ color: focusColor, width: ringStrokeWidth })
          })
        })
      ];
    },

    updateIntelFeatureStyle(feature) {
      if (!feature) return;
      const label = feature.get("label") || "情报点";
      const key = String(feature.get("targetKey") || feature.getId?.() || "");
      const raw = feature.get("rawData") || {};

      const isSelected =
        this.selectedTargetKind === "intel" && this.selectedTargetKey === key;
      const isHovered =
        this.hoveredTargetKind === "intel" && this.hoveredTargetKey === key;
      const focused = isSelected || isHovered;
      const focusColor = this.getFocusColor(isSelected, isHovered);

      const factor = this.getIconScaleFactor();
      const baseIconScale = 0.1 * factor;
      const iconScale = focused ? baseIconScale * FOCUS_SCALE : baseIconScale;
      const offsetY =
        this.getScaledOffsetY(-28) * (focused ? 1.05 : 1);

      const styles = [];

      // 雷达波（红色扩散）
      if (raw && raw.pulse) {
        const pulse = raw.pulse && typeof raw.pulse === "object" ? raw.pulse : {};
        const startedAt = Number(pulse.startedAt) || Date.now();
        const period = Number(pulse.period) > 300 ? Number(pulse.period) : 1100; // ms
        const t = (Date.now() - startedAt) % period;
        const p01 = Math.max(0, Math.min(1, t / period));
        const extraRadius = Number.isFinite(Number(pulse.radius)) ? Number(pulse.radius) : 22;
        const ringBase = (ICON_BASE_SIZE_PX * iconScale) / 2 + 8;
        const ringRadius = ringBase + p01 * 22;
        const dynamicRadius = ringBase + p01 * Math.max(10, extraRadius);
        const maxAlpha = Number.isFinite(Number(pulse.alpha)) ? Number(pulse.alpha) : 0.6;
        const alpha = Math.max(0.08, Math.min(1, maxAlpha)) * (1 - p01);
        const width = Number.isFinite(Number(pulse.width)) ? Number(pulse.width) : 2.2;
        const color = pulse.color ? String(pulse.color) : "rgba(255, 60, 60, 1)";

        styles.push(
          new Style({
            image: new Circle({
              radius: dynamicRadius,
              stroke: new Stroke({
                color: color.replace(/rgba\(([^,]+),([^,]+),([^,]+),([^)]+)\)/, `rgba($1,$2,$3,${alpha})`),
                width: Math.max(1.2, width)
              })
            })
          })
        );
      }

      if (focused && focusColor) {
        styles.push(
          ...this.getCircleGlowAndRingStyles(focusColor, iconScale, isSelected)
        );
      }

      styles.push(
        new Style({
          image: new Icon({
            src: INTEL_ICON_URL,
            scale: iconScale,
            anchor: [0.5, 0.5],
            anchorXUnits: "fraction",
            anchorYUnits: "fraction"
          }),
          text: new Text({
            text: String(label),
            font: "12px sans-serif",
            fill: new Fill({ color: focused ? focusColor : INTEL_NAME_COLOR }),
            stroke: new Stroke({
              color: focused ? focusColor : "#000000",
              width: 2
            }),
            offsetY
          })
        })
      );

      feature.setStyle(styles);
    },

    updateFlightFeatureStyle(feature) {
      if (!feature) return;
      const label = feature.get("label") || "";
      const key = String(feature.get("targetKey") || feature.getId?.() || "");
      const raw = feature.get("rawData") || {};

      const isSelected =
        this.selectedTargetKind === "flight" && this.selectedTargetKey === key;
      const isHovered =
        this.hoveredTargetKind === "flight" && this.hoveredTargetKey === key;
      const focused = isSelected || isHovered;
      const focusColor = this.getFocusColor(isSelected, isHovered);

      const factor = this.getIconScaleFactor();
      const baseIconScale = 0.065 * factor;
      const iconScale = focused ? baseIconScale * FOCUS_SCALE : baseIconScale;
      const offsetY =
        this.getScaledOffsetY(-20) * (focused ? 1.05 : 1);

      const headingDeg = Number(raw.heading || raw.headingDeg || 0);
      const rotation = (headingDeg * Math.PI) / 180;

      const styles = [];
      if (focused && focusColor) {
        styles.push(
          ...this.getCircleGlowAndRingStyles(focusColor, iconScale, isSelected)
        );
      }

      styles.push(
        new Style({
          image: new Icon({
            src: AIRCRAFT_ICON_URL,
            scale: iconScale,
            anchor: [0.5, 0.5],
            rotation
          }),
          text: new Text({
            text: String(label),
            font: "10px sans-serif",
            fill: new Fill({ color: focused ? focusColor : FLIGHT_NAME_COLOR }),
            stroke: new Stroke({
              color: focused ? focusColor : "#000000",
              width: 2
            }),
            offsetY
          })
        })
      );

      feature.setStyle(styles);
    },

    updateVesselFeatureStyle(feature) {
      if (!feature) return;
      const label = feature.get("label") || "";
      const key = String(feature.get("targetKey") || feature.getId?.() || "");

      const isSelected =
        this.selectedTargetKind === "vessel" && this.selectedTargetKey === key;
      const isHovered =
        this.hoveredTargetKind === "vessel" && this.hoveredTargetKey === key;
      const focused = isSelected || isHovered;
      const focusColor = this.getFocusColor(isSelected, isHovered);

      const factor = this.getIconScaleFactor();
      const baseIconScale = 0.075 * factor;
      const iconScale = focused ? baseIconScale * FOCUS_SCALE : baseIconScale;
      const offsetY =
        this.getScaledOffsetY(-20) * (focused ? 1.05 : 1);

      const styles = [];
      if (focused && focusColor) {
        styles.push(
          ...this.getCircleGlowAndRingStyles(focusColor, iconScale, isSelected)
        );
      }

      styles.push(
        new Style({
          image: new Icon({
            src: NAVY_ICON_URL,
            scale: iconScale,
            anchor: [0.5, 0.5]
          }),
          text: new Text({
            text: String(label),
            font: "10px sans-serif",
            fill: new Fill({ color: focused ? focusColor : VESSEL_NAME_COLOR }),
            stroke: new Stroke({
              color: focused ? focusColor : "#000000",
              width: 2
            }),
            offsetY
          })
        })
      );

      feature.setStyle(styles);
    },

    updateSatelliteFeatureStyle(feature) {
      if (!feature) return;
      const raw = feature.get("rawData") || {};
      const noradRaw = raw.norad_id ?? raw.noradId ?? "";
      const noradId = String(noradRaw).trim();
      const flashKey = String(
        raw.object_id ?? raw.objectId ?? noradId
      ).trim();
      const warnFlash =
        !!flashKey &&
        this.satelliteWarnFlashIdMap[flashKey] &&
        this.warnFlashPhase;

      const isSelected =
        this.selectedSatelliteNoradId &&
        noradId === String(this.selectedSatelliteNoradId).trim();
      const isHovered =
        this.hoveredSatelliteNoradId &&
        noradId === String(this.hoveredSatelliteNoradId).trim();

      const isCoverageActive =
        !!this.satelliteCoverageNoradId &&
        noradId === String(this.satelliteCoverageNoradId).trim();

      const focused = isSelected || isHovered;
      const focusColor = this.getFocusColor(isSelected, isHovered);
      const effectiveFocusColor = isCoverageActive ? COVERAGE_ICON_ORANGE : focusColor;

      const label = feature.get("label") || `卫星-${noradId || ""}`;
      const iconUrl = this.getSatelliteIconUrl(raw);

      const factor = this.getIconScaleFactor();
      const baseIconScale = 0.065 * factor;
      const iconScale = focused ? baseIconScale * FOCUS_SCALE : baseIconScale;
      const offsetY =
        this.getScaledOffsetY(-18) * (focused ? 1.05 : 1);

      const labelColor = warnFlash
        ? "#ff4444"
        : isCoverageActive
          ? COVERAGE_ICON_ORANGE
          : focused
            ? focusColor
            : SATELLITE_NAME_COLOR;
      const labelStroke = warnFlash
        ? "#2a0000"
        : isCoverageActive
          ? COVERAGE_ICON_ORANGE
          : focused
            ? focusColor
            : "#000000";

      const styles = [];
      if (focused && focusColor) {
        styles.push(
          ...this.getCircleGlowAndRingStyles(
            effectiveFocusColor,
            iconScale,
            isSelected
          )
        );
      }

      const iconOptions = {
        src: iconUrl,
        scale: iconScale,
        anchor: [0.5, 0.5]
      };
      if (warnFlash) iconOptions.color = "rgba(255,55,55,0.92)";
      else if (isCoverageActive) iconOptions.color = COVERAGE_ICON_ORANGE_RGBA;

      styles.push(
        new Style({
          image: new Icon(iconOptions),
          text: new Text({
            text: String(label),
            font: "10px sans-serif",
            fill: new Fill({
              color: labelColor
            }),
            stroke: new Stroke({
              color: labelStroke,
              width: 2
            }),
            offsetY
          })
        })
      );

      feature.setStyle(styles);
    },

    /**
     * 根据当前 zoom 刷新所有态势图标样式（包含卫星标签颜色逻辑）
     */
    refreshIconStylesByZoom() {
      // 情报点（2D Icon + Text）
      if (this.intelSource) {
        this.intelSource.getFeatures().forEach((feature) =>
          this.updateIntelFeatureStyle(feature)
        );
      }

      // 飞机
      Object.values(this.flightFeaturesByIcao || {}).forEach((feature) => {
        if (!feature) return;
        this.updateFlightFeatureStyle(feature);
      });

      // 轮船
      Object.values(this.shipFeaturesById || {}).forEach((feature) => {
        if (!feature) return;
        this.updateVesselFeatureStyle(feature);
      });

      // 卫星（包括聚焦时文字颜色）
      this.updateSatellitePointStyles();
    },

    /**
     * 根据 Hover/Selected 状态刷新卫星点位样式（外圈高亮 + Glow + 放大）
     */
    updateSatellitePointStyles() {
      if (!this.satelliteFeaturesById) return;

      Object.keys(this.satelliteFeaturesById).forEach((key) => {
        const feature = this.satelliteFeaturesById[key];
        if (!feature || !feature.get) return;
        this.updateSatelliteFeatureStyle(feature);
      });
    },

    refreshCountryLayerStyle() {
      if (this.countriesLayer) this.countriesLayer.changed();
    },

    async loadCountriesGeoJson() {
      try {
        await loadCountryNamesZh();
        const res = await fetch(COUNTRIES_GEOJSON_URL);
        const json = await res.json();
        const format = new GeoJSON();
        const features = format.readFeatures(json, {
          dataProjection: "EPSG:4326",
          featureProjection: "EPSG:3857"
        });

        const byIso = {};
        const countryList = [];

        for (let i = 0; i < features.length; i++) {
          const f = features[i];
          const iso = this.getIsoFromFeature(f);
          if (!iso) continue;
          // Natural Earth 中南苏丹为 ADM0_A3=SDS，ISO 3166-1 三字码为 SSD（否则中文名/国旗映射不到）
          let normalizedIso = iso === "TWN" ? "CHN" : iso;
          if (normalizedIso === "SDS") normalizedIso = "SSD";
          if (normalizedIso === "SAH") normalizedIso = "ESH";
          f.set("iso", normalizedIso);
          f.set("isCountry", true);
          // NAME 用于构建英文名/兜底
          const props = f.getProperties() || {};
          f.set("countryNameEn", props.NAME || props.ADMIN || normalizedIso);
          f.set("countryNameZh", this.getCountryZhName(normalizedIso));

          if (!byIso[normalizedIso]) byIso[normalizedIso] = [];
          byIso[normalizedIso].push(f);
        }

        // 列表用于搜索建议
        Object.keys(byIso).forEach((iso) => {
          const list = byIso[iso];
          if (!list || !list.length) return;
          const first = list[0];
          const en = iso === "CHN" ? "China" : String(first.get("countryNameEn") || iso);
          countryList.push({
            id: iso,
            nameZh: this.getCountryZhName(iso),
            nameEn: en
          });
        });

        countryList.sort((a, b) => a.nameZh.localeCompare(b.nameZh, "zh-CN"));

        this.countryFeaturesByIso = byIso;
        this.countryList = countryList;
        this.countriesSource.clear();
        this.countriesSource.addFeatures(features);

        this.countriesReady = true;
        this.refreshCountryLayerStyle();
      } catch (e) {
        console.error("Failed to load countries GeoJSON", e);
      }
    },

    getIsoFromFeature(feature) {
      if (!feature) return "";
      const p = feature.getProperties ? feature.getProperties() : feature.properties || {};
      const candidates = ["ADM0_A3", "ISO_A3", "SOV_A3", "GU_A3", "BRK_A3"];
      for (let i = 0; i < candidates.length; i++) {
        const key = candidates[i];
        const val = p[key];
        if (typeof val === "string" && val.trim()) return val.trim().toUpperCase();
      }
      // GeoJSON Feature 在 OLayer 中有时没有可靠 id，用属性兜底
      const id = feature.getId ? feature.getId() : feature.id;
      if (typeof id === "string" && id.trim()) return id.trim().toUpperCase();
      return "";
    },

    getCountryZhName(iso) {
      if (!iso) return "";
      const zh = getCountryNamesZh(iso);
      if (zh) return zh;
      return iso;
    },

    getCountryEnName(iso) {
      if (!iso) return "";
      if (iso === "CHN") return "China";
      const list = this.countryFeaturesByIso[iso];
      if (list && list[0]) return String(list[0].get("countryNameEn") || "");
      return "";
    },

    getCountrySearchDisplay(iso) {
      const zh = this.getCountryZhName(iso);
      const en = this.getCountryEnName(iso);
      if (en && en !== zh) return `${zh}(${en})`;
      return zh || en || iso;
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
    _hasCountryFeaturesForIso(isoKey) {
      const list = (this.countryFeaturesByIso || {})[isoKey];
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
        if (this._hasCountryFeaturesForIso(key)) return key;
      }
      if (codeRaw.length === 2 && /^[A-Z]{2}$/.test(codeRaw)) {
        const a3 = countries.alpha2ToAlpha3(codeRaw);
        if (a3) {
          const key = this._normalizeGeoIsoFromHint(a3);
          if (this._hasCountryFeaturesForIso(key)) return key;
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
      this.selectedSatelliteNoradId = null;
      this.selectedPoint = null;
      this.selectedTargetKind = null;
      this.selectedTargetKey = null;
      this.selectCountryById(iso);
      this.refreshIconStylesByZoom();
    },

    applyFirstSearchMatch() {
      const first = this.filteredCountries[0];
      if (first) this.selectCountryById(first.id);
    },

    selectCountryById(id) {
      if (!id) return;
      const entities = this.countryFeaturesByIso[id];
      if (!entities || !entities.length || !this.map) return;

      if (this.searchFocusIso && this.searchFocusIso !== id) {
        this.clearSearchFocusStyle();
      }
      this.searchFocusIso = null;

      this.selectedIso = id;
      this.countrySearch = "";
      this.refreshCountryLayerStyle();

      const geom = entities[0].getGeometry();
      if (geom) {
        const extent = geom.getExtent();
        this.view.fit(extent, { padding: [20, 20, 20, 20], duration: 800, maxZoom: 4 });
      }
    },

    clearSearchFocusStyle() {
      if (!this.searchFocusIso) return;
      this.searchFocusIso = null;
      this.refreshCountryLayerStyle();
    },

    clearHoverVisual() {
      if (!this.hoveredIso) return;
      this.hoveredIso = null;
      this.refreshCountryLayerStyle();
    },

    clearCountrySelection() {
      if (this.searchFocusIso && this.searchFocusIso !== this.selectedIso) {
        this.searchFocusIso = null;
      }
      this.searchFocusIso = null;
      if (this.selectedIso) {
        this.selectedIso = null;
      }
      this.refreshCountryLayerStyle();
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

    buildFlightDetail(flight) {
      if (!flight) return null;
      return {
        title: "目标详情",
        type: "flight",
        identifier: this.formatFlightIdentifier(flight.icao24 || flight.icao || flight.callsign),
        model: String(flight.model || flight.aircraft_model || "").trim() || "-",
        longitude: this.formatFlightValue(flight.longitude, 4),
        latitude: this.formatFlightValue(flight.latitude, 4),
        // 几何高度：优先 geoAltitude，兜底 altitude
        geomAltitude: this.formatFlightAltitude(flight.geoAltitude ?? flight.altitude),
        // 地速(m/s)：优先 groundSpeed/velocity/speed（后端字段以实际为准）
        groundSpeed: this.formatFlightSpeed(
          flight.groundSpeed ??
            flight.ground_speed ??
            flight.velocity ??
            flight.speed ??
            flight.gs
        ),
        // 真航向(°)：优先 trueTrack，兜底 heading/headingDeg
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
        // ignore silently
      }
    },

    bindMapEvents() {
      if (!this.map) return;

      this.map.on("pointermove", (e) => {
        if (!this.map) return;
        if (e.dragging) {
          this.mouseFocusLonLat = null;
          return;
        }

        const pixel = e.pixel;

        // 当前鼠标焦点经纬度（EPSG:3857 => EPSG:4326）
        const lonLat = toLonLat(e.coordinate);
        if (
          lonLat &&
          Number.isFinite(lonLat[0]) &&
          Number.isFinite(lonLat[1])
        ) {
          this.mouseFocusLonLat = { lon: lonLat[0], lat: lonLat[1] };
        } else {
          this.mouseFocusLonLat = null;
        }

        // Cursor / hover for points (points 优先于国家面命中)
        const prevHovered = {
          kind: this.hoveredTargetKind,
          key: this.hoveredTargetKey,
          noradId: this.hoveredSatelliteNoradId
        };

        let hasPoint = false;
        let hoveredKind = null;
        let hoveredKey = null;
        let hoveredNoradId = null;

        this.map.forEachFeatureAtPixel(
          pixel,
          (feature) => {
            const kind = feature && feature.get ? feature.get("kind") : null;
            if (!kind) return false;

            hasPoint = true;
            hoveredKind = kind;

            if (kind === "intel" || kind === "flight" || kind === "vessel") {
              hoveredKey = String(feature.get("targetKey") || feature.getId?.());
            } else if (kind === "satellite") {
              const raw = feature.get("rawData") || {};
              const noradRaw = raw.norad_id ?? raw.noradId ?? "";
              hoveredNoradId = String(noradRaw).trim();
              hoveredKey = hoveredNoradId || String(feature.getId?.() || "");
            } else if (kind === "satellite_track") {
              hoveredNoradId = String(feature.get("noradId") || "").trim();
              hoveredKey = hoveredNoradId;
            }

            return true;
          },
          {
            layerFilter: (layer) =>
              layer === this.intelLayer ||
              layer === this.flightLayer ||
              layer === this.vesselLayer ||
              layer === this.satelliteTrackLayer ||
              layer === this.satelliteLayer
          }
        );

        // 如果没有点命中，再尝试国家面（避免点被国家面“抢”）
        let countryIso = null;
        if (!hasPoint) {
          this.map.forEachFeatureAtPixel(
            pixel,
            (feature) => {
              if (feature && feature.get && feature.get("isCountry")) {
                countryIso = feature.get("iso");
                return true;
              }
              return false;
            },
            { layerFilter: (layer) => layer === this.countriesLayer }
          );
        }

        const nextHoveredKind = hoveredKind;
        const nextHoveredKey = hoveredKey;
        const nextHoveredNoradId = hoveredNoradId;

        const noradChanged = prevHovered.noradId !== nextHoveredNoradId;

        this.hoveredTargetKind = nextHoveredKind;
        this.hoveredTargetKey = nextHoveredKey;
        this.hoveredSatelliteNoradId = nextHoveredNoradId;

        if (
          prevHovered.kind !== nextHoveredKind ||
          prevHovered.key !== nextHoveredKey ||
          noradChanged
        ) {
          const updatePrev = () => {
            if (!prevHovered.kind) return;
            if (prevHovered.kind === "intel") {
              const f = this.intelFeaturesById[prevHovered.key];
              if (f) this.updateIntelFeatureStyle(f);
            } else if (prevHovered.kind === "flight") {
              const f = this.flightFeaturesByIcao[prevHovered.key];
              if (f) this.updateFlightFeatureStyle(f);
            } else if (prevHovered.kind === "vessel") {
              const f = this.shipFeaturesById[prevHovered.key];
              if (f) this.updateVesselFeatureStyle(f);
            } else if (
              prevHovered.kind === "satellite" ||
              prevHovered.kind === "satellite_track"
            ) {
              if (!prevHovered.noradId) return;
              const f = this.satelliteFeaturesById[prevHovered.noradId];
              if (f) this.updateSatelliteFeatureStyle(f);
            }
          };

          const updateNext = () => {
            if (!nextHoveredKind) return;
            if (nextHoveredKind === "intel") {
              const f = this.intelFeaturesById[nextHoveredKey];
              if (f) this.updateIntelFeatureStyle(f);
            } else if (nextHoveredKind === "flight") {
              const f = this.flightFeaturesByIcao[nextHoveredKey];
              if (f) this.updateFlightFeatureStyle(f);
            } else if (nextHoveredKind === "vessel") {
              const f = this.shipFeaturesById[nextHoveredKey];
              if (f) this.updateVesselFeatureStyle(f);
            } else if (
              nextHoveredKind === "satellite" ||
              nextHoveredKind === "satellite_track"
            ) {
              if (!nextHoveredNoradId) return;
              const f = this.satelliteFeaturesById[nextHoveredNoradId];
              if (f) this.updateSatelliteFeatureStyle(f);
            }
          };

          updatePrev();
          updateNext();

          if (noradChanged && this.satelliteTrackLayer) {
            this.satelliteTrackLayer.changed();
          }
        }

        const container = this.$refs.mapContainer;
        if (container) {
          container.style.cursor = countryIso || hasPoint ? "pointer" : "default";
        }

        if (countryIso !== this.hoveredIso) {
          this.hoveredIso = countryIso;
          this.refreshCountryLayerStyle();
        }
      });

      this.map.on("singleclick", (e) => {
        const pixel = e.pixel;

        // 0) 点命中探测（points 优先于国家面，避免“难点”）
        let hasPointAtPixel = false;
        this.map.forEachFeatureAtPixel(
          pixel,
          (feature) => {
            const kind = feature && feature.get ? feature.get("kind") : null;
            if (
              kind === "intel" ||
              kind === "flight" ||
              kind === "vessel" ||
              kind === "satellite" ||
              kind === "satellite_track"
            ) {
              hasPointAtPixel = true;
              return true;
            }
            return false;
          },
          {
            layerFilter: (layer) =>
              layer === this.intelLayer ||
              layer === this.flightLayer ||
              layer === this.vesselLayer ||
              layer === this.satelliteTrackLayer ||
              layer === this.satelliteLayer,
            hitTolerance: 10
          }
        );

        // 1) Country click（仅在无点命中时生效）
        let countryFeature = null;
        this.map.forEachFeatureAtPixel(
          pixel,
          (feature) => {
            if (feature && feature.get && feature.get("isCountry")) {
              countryFeature = feature;
              return true;
            }
            return false;
          },
          { layerFilter: (layer) => layer === this.countriesLayer }
        );

        if (countryFeature && !hasPointAtPixel) {
          const iso = countryFeature.get("iso");
          this.selectedSituationDetail = null;
          this.selectedSatelliteNoradId = null;
          this.selectedPoint = null;
          this.selectedTargetKind = null;
          this.selectedTargetKey = null;
          this.hoveredTargetKind = null;
          this.hoveredTargetKey = null;
          this.hoveredSatelliteNoradId = null;
          if (this.selectedIso && this.selectedIso !== iso) {
            // style is derived from state; just overwrite
          }
          this.selectedIso = iso;
          this.searchFocusIso = null;
          this.countrySearch = "";
          this.refreshCountryLayerStyle();
          this.refreshIconStylesByZoom();
          return;
        }

        // 2) Intelligence points
        let intelFeature = null;
        this.map.forEachFeatureAtPixel(
          pixel,
          (feature) => {
            if (feature && feature.get && feature.get("kind") === "intel") {
              intelFeature = feature;
              return true;
            }
            return false;
          },
          { layerFilter: (layer) => layer === this.intelLayer }
        );
        if (intelFeature) {
          this.selectedSituationDetail = null;
          this.selectedSatelliteNoradId = null;
          this.selectedPoint = intelFeature.get("rawData");
          this.selectedTargetKind = "intel";
          this.selectedTargetKey = String(
            intelFeature.get("targetKey") || intelFeature.getId?.() || ""
          );
          this.hoveredSatelliteNoradId = null;
          this.refreshIconStylesByZoom();
          return;
        }

        // 2b) 卫星轨迹（点击选中并展示详情）
        let trackFeature = null;
        this.map.forEachFeatureAtPixel(
          pixel,
          (feature) => {
            if (feature && feature.get && feature.get("kind") === "satellite_track") {
              trackFeature = feature;
              return true;
            }
            return false;
          },
          {
            layerFilter: (layer) => layer === this.satelliteTrackLayer,
            hitTolerance: 10
          }
        );
        if (trackFeature) {
          const nid = String(trackFeature.get("noradId") || "");
          if (nid) this.clearSatelliteWarnFlashForId(nid);
          this.selectedSatelliteNoradId = nid || null;
          this.selectedPoint = null;
          this.selectedTargetKind = nid ? "satellite" : null;
          this.selectedTargetKey = nid || null;
          const pointFeat = nid ? this.satelliteFeaturesById[nid] : null;
          const raw = pointFeat && pointFeat.get ? pointFeat.get("rawData") : null;
          if (raw) {
            const detail = this.buildSatelliteDetail(raw);
            this.selectedSituationDetail = detail;
            this.$emit(
              "situation-node-selected",
              detail && detail.name ? detail.name : "未知"
            );
          }
          this.refreshIconStylesByZoom();
          return;
        }

        // 3) Flight/Vessel/Satellite
        let entityFeature = null;
        this.map.forEachFeatureAtPixel(
          pixel,
          (feature) => {
            const kind = feature && feature.get ? feature.get("kind") : null;
            if (
              kind === "flight" ||
              kind === "vessel" ||
              kind === "satellite"
            ) {
              entityFeature = feature;
              return true;
            }
            return false;
          },
          {
            layerFilter: (layer) =>
              layer === this.flightLayer ||
              layer === this.vesselLayer ||
              layer === this.satelliteLayer
          }
        );
        if (entityFeature) {
          const kind = entityFeature.get("kind");
          const raw = entityFeature.get("rawData");

          if (kind === "flight") {
            this.selectedSatelliteNoradId = null;
            this.selectedPoint = null;
            this.selectedTargetKind = "flight";
            this.selectedTargetKey = String(
              entityFeature.get("targetKey") || raw?.icao24 || ""
            );
            const detail = this.buildFlightDetail(raw);
            this.selectedSituationDetail = detail;
            this.$emit(
              "situation-node-selected",
              detail && (detail.identifier || detail.model)
                ? detail.identifier || detail.model
                : "未知"
            );
            this.refreshIconStylesByZoom();
            return;
          }

          if (kind === "vessel") {
            this.selectedSatelliteNoradId = null;
            this.selectedPoint = null;
            this.selectedTargetKind = "vessel";
            this.selectedTargetKey = String(
              entityFeature.get("targetKey") || ""
            );
            const detail = this.buildVesselDetail(raw);
            this.selectedSituationDetail = detail;
            this.$emit(
              "situation-node-selected",
              detail && detail.name ? detail.name : "未知"
            );
            this.refreshIconStylesByZoom();
            return;
          }

          if (kind === "satellite") {
            const nid = String(raw.norad_id ?? raw.noradId ?? "").trim();
            if (nid) this.clearSatelliteWarnFlashForId(nid);
            this.selectedSatelliteNoradId = nid || null;
            this.selectedPoint = null;
            this.selectedTargetKind = nid ? "satellite" : null;
            this.selectedTargetKey = nid || null;
            const detail = this.buildSatelliteDetail(raw);
            this.selectedSituationDetail = detail;
            this.$emit(
              "situation-node-selected",
              detail && detail.name ? detail.name : "未知"
            );
            this.refreshIconStylesByZoom();
            return;
          }
        }

        // 4) Ocean / empty area
        this.clearCountrySelection();
        this.selectedSituationDetail = null;
        this.selectedSatelliteNoradId = null;
        this.selectedPoint = null;
        this.selectedTargetKind = null;
        this.selectedTargetKey = null;
        this.refreshIconStylesByZoom();
      });

      // 右键菜单：对“卫星最新星下点（SVG）”触发覆盖范围展示
      this.map.on("contextmenu", (e) => {
        try {
          // 阻止浏览器默认菜单
          if (e?.preventDefault) e.preventDefault();
          if (e?.stopPropagation) e.stopPropagation();
          if (e?.originalEvent?.preventDefault) e.originalEvent.preventDefault();

          const pixel =
            (e && e.pixel ? e.pixel : null) ||
            (e && e.coordinate ? this.map.getPixelFromCoordinate(e.coordinate) : null);
          if (!pixel) return;

          let satFeature = null;
          this.map.forEachFeatureAtPixel(
            pixel,
            (feature) => {
              const kind = feature && feature.get ? feature.get("kind") : null;
              if (kind === "satellite") {
                satFeature = feature;
                return true;
              }
              return false;
            },
            {
              layerFilter: (layer) => layer === this.satelliteLayer,
              hitTolerance: 10
            }
          );

          if (!satFeature) return;

          const raw = satFeature.get("rawData") || {};
          const noradId = String(raw.norad_id ?? raw.noradId ?? "").trim();
          const telLine1 = String(raw.telLine1 ?? raw.tle1 ?? "").trim();
          const telLine2 = String(raw.telLine2 ?? raw.tle2 ?? "").trim();
          const epoch = raw.epoch ?? "";

          if (!noradId || !telLine1 || !telLine2 || !epoch) return;

          // 再次右键同一颗卫星：取消展示
          if (
            this.satelliteCoverageNoradId &&
            String(this.satelliteCoverageNoradId).trim() === noradId
          ) {
            this.clearSatelliteCoverage();
            return;
          }

          // 切换到新卫星：先取消旧展示，再开始刷新
          this.clearSatelliteCoverage();
          this.satelliteCoverageNoradId = noradId;
          this._satelliteCoverageLastLonLat = {
            lon: Number(raw.longitude),
            lat: Number(raw.latitude)
          };
          this._satelliteCoverageLastTelLines = { telLine1, telLine2 };
          this._satelliteCoverageLastEpoch = epoch;

          this.updateSatellitePointStyles();
          this.applyLayerVisibility();

          // 立即拉取一次覆盖范围并渲染
          this.refreshSatelliteCoverage({
            ...raw,
            telLine1,
            telLine2,
            epoch
          });
        } catch (err) {
          console.warn("[SatelliteCoverage] contextmenu failed", err);
        }
      });
    },

    clearIntelFeatures() {
      if (this.intelSource) this.intelSource.clear();
      this.intelFeaturesById = {};
      this.syncIntelPulseTicker();

      // 数据刷新导致告警点重建时，清掉可能指向旧 feature 的选中/悬停态
      if (this.selectedTargetKind === "intel") {
        this.selectedPoint = null;
        this.selectedTargetKind = null;
        this.selectedTargetKey = null;
      }
      if (this.hoveredTargetKind === "intel") {
        this.hoveredTargetKind = null;
        this.hoveredTargetKey = null;
      }
    },

    renderIntelligencePoints(points) {
      if (!this.intelSource) return;
      this.clearIntelFeatures();

      (points || []).forEach((point, index) => {
        const [lon, lat] = parseCoordinates(point.coordinates);
        const coords = fromLonLat([lon, lat]);
        const feature = new Feature({
          geometry: new Point(coords)
        });
        feature.setId(`intel-${index}`);
        feature.set("kind", "intel");
        feature.set("rawData", point);
        feature.set("label", point.region || "情报点");
        feature.set("targetKey", String(feature.getId()));

        this.intelSource.addFeature(feature);
        this.intelFeaturesById[String(feature.getId())] = feature;
        // 首次创建就应用当前 Hover / Selected（如果存在）
        this.updateIntelFeatureStyle(feature);
      });
      this.syncIntelPulseTicker();
    },

    upsertFlightFeature(flight) {
      if (!this.map || !this.flightSource || !flight) return;

      const normalized = this.normalizeAircraftRow(flight);
      if (!normalized) return;

      const icao24 = String(normalized.icao24 || "").trim();
      if (!icao24) return;

      const lon = Number(normalized.longitude);
      const lat = Number(normalized.latitude);
      const altitude = Number(normalized.geoAltitude ?? normalized.altitude ?? 0);
      const headingDeg = Number(
        normalized.trueTrack ?? normalized.heading ?? normalized.headingDeg ?? 0
      );

      if (!isFinite(lon) || !isFinite(lat)) return;

      const coords = fromLonLat([lon, lat]);
      const rotation = (headingDeg * Math.PI) / 180;

      const entity = this.flightFeaturesByIcao[icao24];
      const name = (normalized.callsign || icao24).trim() || icao24;
      if (!entity) {
        const feature = new Feature({
          geometry: new Point(coords)
        });
        feature.setId(`flight-${icao24}`);
        feature.set("kind", "flight");
        feature.set("rawData", normalized);
        feature.set("label", name);
        feature.set("targetKey", icao24);
        feature.set("rotation", rotation);

        this.flightSource.addFeature(feature);
        this.flightFeaturesByIcao[icao24] = feature;
        this.updateFlightFeatureStyle(feature);
        return;
      }

      entity.setGeometry(new Point(coords));
      entity.set("rawData", normalized);
      entity.set("label", name);
      entity.set("rotation", rotation);
      entity.set("targetKey", icao24);
      this.updateFlightFeatureStyle(entity);
    },

    clearFlightFeatures() {
      if (!this.flightSource) {
        this.flightFeaturesByIcao = {};
        return;
      }
      this.flightSource.clear();
      this.flightFeaturesByIcao = {};
    },

    upsertVesselFeature(vessel, index = 0) {
      if (!this.vesselSource || !vessel) return;

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

      const coords = fromLonLat([lon, lat]);

      const entity = this.shipFeaturesById[key];
      if (!entity) {
        const feature = new Feature({
          geometry: new Point(coords)
        });
        feature.setId(`vessel-${key}`);
        feature.set("kind", "vessel");
        feature.set("rawData", vessel);
        feature.set("label", name);
        feature.set("targetKey", key);
        this.vesselSource.addFeature(feature);
        this.shipFeaturesById[key] = feature;
        this.updateVesselFeatureStyle(feature);
        return;
      }

      entity.setGeometry(new Point(coords));
      entity.set("rawData", vessel);
      entity.set("label", name);
      entity.set("targetKey", key);
      this.updateVesselFeatureStyle(entity);
    },

    clearVesselFeatures() {
      if (!this.vesselSource) {
        this.shipFeaturesById = {};
        return;
      }
      this.vesselSource.clear();
      this.shipFeaturesById = {};
    },

    upsertSatelliteFeature(satellite, index = 0) {
      if (!this.satelliteSource || !satellite) return;

      const lon = Number(satellite.longitude);
      const lat = Number(satellite.latitude);
      if (!isFinite(lon) || !isFinite(lat)) return;

      const objectIdStr = String(
        satellite.object_id ?? satellite.objectId ?? ""
      ).trim();
      const noradRaw = satellite.norad_id ?? satellite.noradId;
      const noradId = String(noradRaw != null ? noradRaw : "").trim();
      const key = objectIdStr || noradId || `unknown-${index}`;
      const name = (satellite.name || key || `卫星-${index + 1}`).trim();

      const coords = fromLonLat([lon, lat]);

      const entity = this.satelliteFeaturesById[key];
      if (!entity) {
        const feature = new Feature({
          geometry: new Point(coords)
        });
        feature.setId(`satellite-${key}`);
        feature.set("kind", "satellite");
        feature.set("rawData", satellite);
        feature.set("label", name);
        this.satelliteSource.addFeature(feature);
        this.satelliteFeaturesById[key] = feature;
        this.updateSatelliteFeatureStyle(feature);
        this.maybeRecalculateSatelliteCoverageOnUpdate(satellite);

        return;
      }

      entity.setGeometry(new Point(coords));
      entity.set("rawData", satellite);
      entity.set("label", name);
      this.updateSatelliteFeatureStyle(entity);
      this.maybeRecalculateSatelliteCoverageOnUpdate(satellite);
    },

    clearSatelliteFeatures() {
      // 卫星列表清空时，也同步取消“覆盖范围展示”
      this.clearSatelliteCoverage();
      this.clearSatelliteTrackFeatures();
      this.satelliteGroundTrackPoints = {};
      this.satelliteWarnFlashIdMap = {};
      this.warnFlashPhase = false;
      if (this._warnFlashTimer) {
        clearInterval(this._warnFlashTimer);
        this._warnFlashTimer = null;
      }
      if (!this.satelliteSource) {
        this.satelliteFeaturesById = {};
        return;
      }
      this.satelliteSource.clear();
      this.satelliteFeaturesById = {};
    },

    clearSatelliteCoverage() {
      this.satelliteCoverageNoradId = null;
      this._satelliteCoverageInFlight = false;
      this._satelliteCoveragePendingPayload = null;
      this._satelliteCoverageRecalcTimer &&
        clearTimeout(this._satelliteCoverageRecalcTimer);
      this._satelliteCoverageRecalcTimer = null;
      this._satelliteCoverageLastLonLat = null;
      this._satelliteCoverageLastTelLines = null;
      this._satelliteCoverageLastEpoch = null;
      // invalidate in-flight responses：确保序号永远是数字，避免 NaN 之后所有比较都被拦截
      this._satelliteCoverageReqSeq = Number(this._satelliteCoverageReqSeq) || 0;
      this._satelliteCoverageReqSeq += 1;

      if (this.satelliteCoverageSource) this.satelliteCoverageSource.clear();

      // 卫星图标恢复非覆盖高亮态
      this.updateSatellitePointStyles();
      this.applyLayerVisibility();
    },

    normalizeCoverageDateTime(dateTime) {
      const s = String(dateTime ?? "").trim();
      if (!s) return "";
      if (/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/.test(s)) return `${s}.000`;
      if (/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{1,3}$/.test(s)) {
        const parts = s.split(".");
        return `${parts[0]}.${String(parts[1]).padEnd(3, "0").slice(0, 3)}`;
      }
      if (s.includes("T")) return s.replace("T", " ").replace(/Z$/, "");
      return s;
    },

    renderSatelliteCoverageFromResponse(resp) {
      const points = resp?.boundaryPoints?.points;
      if (!Array.isArray(points) || points.length < 2) {
        if (this.satelliteCoverageSource) this.satelliteCoverageSource.clear();
        return;
      }

      if (this.satelliteCoverageSource) this.satelliteCoverageSource.clear();
      if (!this.satelliteCoverageSource) return;

      const noradId = String(this.satelliteCoverageNoradId || "").trim();
      // OpenLayers 画线：经度跨越日界线时需要拆段，避免线段横穿整张图。
      const lonLatPoints = points
        .map((p) => {
          const lon = Number(p?.longitude);
          const lat = Number(p?.latitude);
          if (!isFinite(lon) || !isFinite(lat)) return null;
          return { lon, lat };
        })
        .filter(Boolean);

      if (lonLatPoints.length < 2) return;

      // 闭合处理：只有在没有拆段时才需要补首尾；拆段时补首尾会导致错误跨越。
      const segments = splitTrackAtDateline(lonLatPoints);

      const stylePolygon = new Style({
        fill: new Fill({ color: COVERAGE_RING_FILL }),
        stroke: new Stroke({
          color: COVERAGE_RING_STROKE,
          width: 4,
          lineDash: COVERAGE_RING_LINE_DASH
        })
      });

      const styleLine = () =>
        new Style({
          stroke: new Stroke({
            color: COVERAGE_RING_STROKE,
            width: 4,
            lineDash: COVERAGE_RING_LINE_DASH
          })
        });

      // 优先画 Polygon：确保透明填充可见，排除“线太细/看不见”的可能
      if (segments.length === 1) {
        const seg = segments[0];
        if (seg && seg.length >= 3) {
          const coords = seg.map((pt) => fromLonLat([pt.lon, pt.lat]));
          if (coords.length >= 3) {
            // 闭合环
            const first = coords[0];
            const last = coords[coords.length - 1];
            if (first && last && (first[0] !== last[0] || first[1] !== last[1])) {
              coords.push(first);
            }

            const poly = new Polygon([coords]);
            const feat = new Feature({ geometry: poly });
            feat.set("kind", "satellite_coverage");
            feat.set("noradId", noradId);
            feat.setId(`satellite-coverage-${noradId}-poly`);
            feat.setStyle(stylePolygon);
            this.satelliteCoverageSource.addFeature(feat);

            this.satelliteCoverageLayer &&
              this.satelliteCoverageLayer.changed();
            return;
          }
        }
      }

      segments.forEach((seg, idx) => {
        if (!seg || seg.length < 2) return;
        const coords = seg.map((pt) => fromLonLat([pt.lon, pt.lat]));
        if (coords.length < 2) return;

        // 尽可能闭合：单段情况下补上首尾，形成连续外圈
        // 多段拆分时不额外闭合，避免跨越拆段后出现“错误回线”

        const line = new LineString(coords);
        const feat = new Feature({ geometry: line });
        feat.set("kind", "satellite_coverage");
        feat.set("noradId", noradId);
        feat.setId(`satellite-coverage-${noradId}-${idx}`);
        feat.setStyle(styleLine());
        this.satelliteCoverageSource.addFeature(feat);
      });

      // 强制刷新矢量图层
      this.satelliteCoverageLayer && this.satelliteCoverageLayer.changed();

    },

    async refreshSatelliteCoverage(satellitePayload) {
      if (!this.satelliteCoverageNoradId) return;
      if (!satellitePayload) return;

      const noradRaw = satellitePayload.norad_id ?? satellitePayload.noradId;
      const noradId = String(noradRaw ?? "").trim();
      if (!noradId || noradId !== String(this.satelliteCoverageNoradId).trim()) return;

      const tleLine1 = satellitePayload.telLine1 ?? satellitePayload.tle1 ?? "";
      const tleLine2 = satellitePayload.telLine2 ?? satellitePayload.tle2 ?? "";
      if (!tleLine1 || !tleLine2) return;

      const epoch = satellitePayload.epoch ?? satellitePayload.dateTime ?? "";
      const dateTime = this.normalizeCoverageDateTime(epoch);
      if (!dateTime) return;

      // 确保序号永远可比较（避免出现 NaN 导致 return）
      this._satelliteCoverageReqSeq = Number(this._satelliteCoverageReqSeq) || 0;
      const reqSeq = (this._satelliteCoverageReqSeq += 1);
      this._satelliteCoverageInFlight = true;

      try {
        const resp = await calculateSatelliteCoverage({
          tleLine1,
          tleLine2,
          dateTime
        });

        // 切换/取消时可能出现并发返回：只采最新序号
        if (reqSeq !== this._satelliteCoverageReqSeq) return;

        // 不强依赖 resp.success 的类型（有的后端返回字符串 "true"）
        if (resp && resp.boundaryPoints && Array.isArray(resp.boundaryPoints.points)) {
          this.renderSatelliteCoverageFromResponse(resp);
        }
      } catch (e) {
        console.warn("[SatelliteCoverage] refresh failed", e);
      } finally {
        this._satelliteCoverageInFlight = false;

        if (
          this._satelliteCoveragePendingPayload &&
          this.satelliteCoverageNoradId &&
          String(this._satelliteCoveragePendingPayload.norad_id ?? this._satelliteCoveragePendingPayload.noradId ?? "").trim() ===
            String(this.satelliteCoverageNoradId).trim()
        ) {
          const next = this._satelliteCoveragePendingPayload;
          this._satelliteCoveragePendingPayload = null;
          this.refreshSatelliteCoverage(next);
        }
      }
    },

    scheduleSatelliteCoverageRecalc(satellitePayload) {
      if (!satellitePayload) return;
      if (this._satelliteCoverageInFlight) {
        this._satelliteCoveragePendingPayload = satellitePayload;
        return;
      }

      // 对实时位置更新做节流：短时间只请求一次
      if (this._satelliteCoverageRecalcTimer) {
        clearTimeout(this._satelliteCoverageRecalcTimer);
      }
      this._satelliteCoverageRecalcTimer = window.setTimeout(() => {
        this._satelliteCoverageRecalcTimer = null;
        this.refreshSatelliteCoverage(satellitePayload);
      }, 250);
    },

    maybeRecalculateSatelliteCoverageOnUpdate(satellitePayload) {
      if (!this.satelliteCoverageNoradId) return;
      if (!satellitePayload) return;

      const noradRaw = satellitePayload.norad_id ?? satellitePayload.noradId;
      const noradId = String(noradRaw ?? "").trim();
      if (!noradId || noradId !== String(this.satelliteCoverageNoradId).trim()) return;

      const lon = Number(satellitePayload.longitude);
      const lat = Number(satellitePayload.latitude);
      if (!isFinite(lon) || !isFinite(lat)) return;

      const last = this._satelliteCoverageLastLonLat;
      const changed =
        !last ||
        Math.abs(last.lon - lon) > 1e-6 ||
        Math.abs(last.lat - lat) > 1e-6;
      if (!changed) return;

      this._satelliteCoverageLastLonLat = { lon, lat };
      this.scheduleSatelliteCoverageRecalc(satellitePayload);
    },

    async fetchAndRenderHistoryVessels() {
      const start = this.formatHistoryApiDateTime(this.historyStartTime);
      const end = this.formatHistoryApiDateTime(this.historyEndTime);
      try {
        const res = await getVesselsByTimeRange(start, end);
        const rows = Array.isArray(res && res.data) ? res.data : [];
        this.clearVesselFeatures();
        rows.forEach((vessel, index) => this.upsertVesselFeature(vessel, index));
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
        this.clearSatelliteFeatures();
        rows.forEach((satellite, index) =>
          this.upsertSatelliteFeature(satellite, index)
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
      this.clearVesselFeatures();
      this.clearSatelliteFeatures();
      this.selectedSituationDetail = null;
      this.selectedSatelliteNoradId = null;
      this.selectedPoint = null;
      this.selectedTargetKind = null;
      this.selectedTargetKey = null;
      this.hoveredTargetKind = null;
      this.hoveredTargetKey = null;
      this.hoveredSatelliteNoradId = null;
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
    }
  }
};
</script>

<style src="./css/situationOlOverlays.css"></style>
<style scoped>
.ol-scene {
  position: relative;
  width: 100%;
  height: 100%;
}

.ol-map {
  width: 100%;
  height: 100%;
}
</style>

