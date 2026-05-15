<!--
  态势模块入口：在 2D(OpenLayers) 与 3D(Cesium) 之间切换，并转发国家选中等事件至 App。
-->
<template>
  <div class="situation-wrapper" :style="wrapperVars">
    <div class="view-mode-bar">
      <div class="view-mode-toggle" role="tablist" aria-label="2D/3D 切换">
        <button
          type="button"
          class="view-mode-btn"
          :class="{ 'is-active': viewMode === '2d' }"
          @click="switchTo2D"
        >
          2D
        </button>
        <button
          type="button"
          class="view-mode-btn"
          :class="{ 'is-active': viewMode === '3d' }"
          @click="switchTo3D"
        >
          3D
        </button>
      </div>

      <button
        v-if="viewMode === '2d'"
        type="button"
        class="view-reset-btn"
        @click="resetActiveView"
      >
        <img
          :src="remakePerspectiveIconUrl"
          class="view-reset-icon"
          alt="视角归位"
        />
      </button>

      <button
        v-if="viewMode === '2d'"
        type="button"
        class="view-reset-btn"
        :class="{ 'is-active': showLatLonGrid }"
        @click="showLatLonGrid = !showLatLonGrid"
        :title="showLatLonGrid ? '隐藏经纬网格' : '显示经纬网格'"
      >
        <img :src="latLonGridIconUrl" class="view-reset-icon" alt="经纬网格" />
      </button>
    </div>

    <OpenLayersScene
      v-if="viewMode === '2d'"
      ref="scene2d"
      :points="points"
      :showLatLonGrid="showLatLonGrid"
      @country-selected="forwardCountrySelected"
    />

    <GlobeScene
      v-else
      ref="scene3d"
      :points="points"
      @country-selected="forwardCountrySelected"
    />
  </div>
</template>

<script>
import OpenLayersScene from "./OpenLayersScene.vue";
import GlobeScene from "./GlobeScene.vue";

export default {
  name: "SituationIndex",
  components: {
    OpenLayersScene,
    GlobeScene
  },
  props: {
    points: { type: Array, default: () => [] }
  },
  data() {
    return {
      viewMode: "2d",
      showLatLonGrid: true
    };
  },
  computed: {
    remakePerspectiveIconUrl() {
      return "/assets/svg/RemakePerspective.svg";
    },
    latLonGridIconUrl() {
      return "/assets/svg/LatLonGrid.svg";
    },
    wrapperVars() {
      // 实时/历史切换为 absolute；2D/3D 拨杆在左上，故用 vw 右移避免重叠；Cesium 工具条同步偏移。
      return {
        "--situation-mode-switch-left": "min(18vw, 240px)",
        "--cesium-toolbar-left": "min(10vw, 160px)",
        "--cesium-toolbar-top": "1vh"
      };
    }
  },
  methods: {
    switchTo2D() {
      if (this.viewMode === "2d") return;
      this.viewMode = "2d";
    },
    switchTo3D() {
      if (this.viewMode === "3d") return;
      this.viewMode = "3d";
    },
    resetActiveView() {
      if (this.viewMode === "2d") {
        const inst = this.$refs.scene2d;
        if (inst && typeof inst.resetView === "function") inst.resetView();
        return;
      }
      const inst = this.$refs.scene3d;
      if (inst && typeof inst.resetView === "function") inst.resetView();
    },
    focusSituationPoint(payload) {
      const p = payload && typeof payload === "object" ? payload : {};
      const coordinates = String(p.coordinates || "").trim();
      const zoom = Number.isFinite(Number(p.zoom)) ? Number(p.zoom) : 5;
      if (!coordinates) return;
      if (this.viewMode === "2d") {
        this.$nextTick(() => {
          const inst = this.$refs.scene2d;
          if (inst && typeof inst.focusPointView === "function") {
            inst.focusPointView(coordinates, zoom);
          }
        });
        return;
      }
      this.$nextTick(() => {
        const inst = this.$refs.scene3d;
        if (inst && typeof inst.focusPointView === "function") {
          inst.focusPointView(coordinates, zoom);
        }
      });
    },
    resetToDefaultView() {
      this.$nextTick(() => {
        const inst = this.viewMode === "2d" ? this.$refs.scene2d : this.$refs.scene3d;
        if (inst && typeof inst.resetView === "function") inst.resetView();
      });
    },
    /** 仅清除国家选中/检索高亮，不改变当前视角（用于右侧面板刷新等） */
    clearSituationCountrySelection() {
      this.$nextTick(() => {
        const inst = this.viewMode === "2d" ? this.$refs.scene2d : this.$refs.scene3d;
        if (inst && typeof inst.clearCountrySelection === "function") {
          inst.clearCountrySelection();
        }
      });
    },
    /** 右侧面板携带 ISO / 国名时：与地图上点选国家一致（高亮 + 拉近） */
    focusCountryFromPanel(payload) {
      const p = payload && typeof payload === "object" ? payload : {};
      this.$nextTick(() => {
        const inst = this.viewMode === "2d" ? this.$refs.scene2d : this.$refs.scene3d;
        if (inst && typeof inst.focusCountryFromPanel === "function") {
          inst.focusCountryFromPanel(p);
        }
      });
    },

    /** App.vue 在拖拉分割条后调用，刷新 2D/3D 画布尺寸 */
    notifySituationResize() {
      this.$nextTick(() => {
        const s2 = this.$refs.scene2d;
        const s3 = this.$refs.scene3d;
        if (s2 && typeof s2.onContainerResize === "function") s2.onContainerResize();
        if (s3 && typeof s3.onContainerResize === "function") s3.onContainerResize();
      });
    },
    forwardCountrySelected(countryName) {
      this.$emit("country-selected", countryName);
    }
  }
};
</script>

<style src="./css/situationSceneShared.css"></style>
<style scoped>
.situation-wrapper {
  position: relative;
  width: 100%;
  height: 100%;
}

.view-mode-bar {
  position: absolute;
  left: clamp(0.4rem, 0.75vw, 12px);
  top: 1vh;
  z-index: 5;
  display: flex;
  align-items: center;
  gap: clamp(0.4rem, 0.75vw, 12px);
}

.view-mode-toggle {
  display: flex;
  align-items: center;
  border: 1px solid rgba(41, 182, 255, 0.55);
  background: rgba(2, 22, 45, 0.55);
  padding: clamp(2px, 0.35vh, 4px);
  border-radius: clamp(4px, 0.5vw, 6px);
  box-shadow: 0 0 0 1px rgba(41, 182, 255, 0.18) inset;
}

.view-mode-btn {
  min-width: min(12vw, 44px);
  height: clamp(1.5rem, 3.5vh, 28px);
  border: none;
  background: transparent;
  color: #d7ecff;
  font-weight: 700;
  cursor: pointer;
  border-radius: 4px;
}

.view-mode-btn.is-active {
  background: rgba(41, 182, 255, 0.22);
  box-shadow: 0 0 0 1px rgba(41, 182, 255, 0.35) inset;
}

.view-reset-btn {
  height: clamp(1.65rem, 3.8vh, 30px);
  padding: 0 clamp(0.4rem, 0.75vw, 12px);
  border-radius: clamp(4px, 0.5vw, 6px);
  border: 1px solid rgba(41, 182, 255, 0.55);
  background: rgba(2, 22, 45, 0.55);
  color: #d7ecff;
  font-weight: 700;
  cursor: pointer;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.65);
}

.view-reset-icon {
  width: clamp(14px, 1.15vw, 18px);
  height: clamp(14px, 1.15vw, 18px);
  display: block;
}

.view-reset-btn.is-active {
  background: rgba(41, 182, 255, 0.18);
  border-color: rgba(41, 182, 255, 0.85);
  box-shadow: 0 0 0 1px rgba(41, 182, 255, 0.35) inset;
}
</style>

