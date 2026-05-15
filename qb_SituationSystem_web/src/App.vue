<template>
  <div class="app-root">
    <header class="top-bar">
      <div class="top-bar-left">
        <img class="system-logo" src="/assets/png/logo1.jpg" alt="系统 Logo" />
        <div class="system-title-row">
          <div class="system-title">全球之眼分析系统</div>
          <div class="system-version" aria-label="BETA v1.0.0">
            <span class="system-version-chip system-version-chip--stage">BETA</span>
            <span class="system-version-chip system-version-chip--ver">v1.0.0</span>
          </div>
        </div>
      </div>
      <div class="top-bar-right">
        <button
          type="button"
          class="top-aicode-btn"
          :class="{ 'is-active': aiCodeGeneratorOpen }"
          title="智能组件工厂"
          aria-label="打开智能组件工厂"
          @click="toggleAiCodeGenerator"
        >
          <img src="/assets/svg/AI生成.svg" alt="" class="top-aicode-btn__icon" aria-hidden="true" />
        </button>
        <button
          type="button"
          class="top-setting-btn"
          title="打开设置"
          aria-label="打开设置"
          @click="openSettingsDialog"
        >
          <img src="/assets/svg/设置.svg" alt="" class="top-setting-btn__icon" aria-hidden="true" />
        </button>
        <div
          class="clock clock--beijing"
          role="timer"
          aria-live="polite"
          :aria-label="`北京时间 ${nowText}`"
        >
          <span class="clock__prefix">北京时间</span>
          <span class="clock__colon" aria-hidden="true">:</span>
          <span class="clock__time">{{ nowText }}</span>
        </div>
      </div>
    </header>

    <main ref="mainLayout" class="main-layout">
      <div class="main-layout__left" :style="mainLayoutLeftStyle">
        <div class="main-layout__situation">
          <SituationIndex
            ref="situationIndex"
            :points="intelPoints"
            @country-selected="handleCountrySelected"
          />
        </div>
        <div class="main-layout__dock">
          <div class="main-layout__dock-cell main-layout__dock-cell--news">
            <LiveNewsPanel />
          </div>
          <div class="main-layout__dock-cell main-layout__dock-cell--monitor">
            <LiveMonitorPanel />
          </div>
        </div>
      </div>

      <div
        class="main-layout__splitter"
        :class="{ 'is-dragging': isDraggingMainSplit }"
        title="拖拽调整左右栏宽度"
        aria-label="拖拽调整左右栏宽度"
        @mousedown.prevent="onMainSplitMouseDown"
      >
        <span class="main-layout__splitter-hint" aria-hidden="true">
          <!-- 竖向握把：表示左右拖拽，避免单向箭头误解 -->
          <svg class="main-layout__splitter-grip" viewBox="0 0 10 18" width="10" height="18" fill="none">
            <line x1="2" y1="3" x2="2" y2="15" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
            <line x1="5" y1="3" x2="5" y2="15" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
            <line x1="8" y1="3" x2="8" y2="15" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
          </svg>
        </span>
      </div>

      <aside class="main-layout__right" :style="mainLayoutRightStyle">
        <LowerTabs
          ref="lowerTabs"
          @panel-reset="onPanelReset"
          @add-intel-point="onAddIntelPoint"
          @remove-intel-point="onRemoveIntelPoint"
          @focus-situation-point="onFocusSituationPoint"
          @reset-situation-view="onResetSituationView"
          @clear-situation-country-selection="onClearSituationCountrySelection"
          @focus-situation-country="onFocusSituationCountry"
        />
      </aside>
    </main>

    <div v-if="aiCodeGeneratorOpen" class="ai-code-gen-overlay" role="dialog" aria-modal="true" aria-label="智能组件工厂">
      <IntelGenStudioWorkspace class="ai-code-gen-overlay__fill" :visible="aiCodeGeneratorOpen" @close="closeAiCodeGenerator" />
    </div>

    <SystemSettingsDialog
      :visible="settingsDialogVisible"
      :intel-initial-config="intelPanelSettingsSnapshot"
      @close="closeSettingsDialog"
      @apply="applySettings"
    />

    <div
      class="assistant-float-wrap"
      :style="{ left: `${assistantAnchor.x}px`, top: `${assistantAnchor.y}px` }"
    >
      <button
        type="button"
        class="assistant-ball"
        :class="{ 'is-open': assistantOpen }"
        :title="assistantOpen ? '收起智能讨论' : '打开智能讨论'"
        @mousedown="onAssistantDragStart"
        @click="toggleAssistant"
      >
        <span class="assistant-robot-icon" aria-hidden="true">
          <svg viewBox="0 0 24 24" width="26" height="26" fill="none" xmlns="http://www.w3.org/2000/svg">
            <rect x="5" y="7" width="14" height="12" rx="2" stroke="currentColor" stroke-width="1.6"/>
            <circle cx="9.5" cy="12" r="1.2" fill="currentColor"/>
            <circle cx="14.5" cy="12" r="1.2" fill="currentColor"/>
            <path d="M10 15.5h4" stroke="currentColor" stroke-width="1.4" stroke-linecap="round"/>
            <path d="M12 4v3" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/>
            <circle cx="12" cy="3" r="1.2" fill="currentColor"/>
          </svg>
        </span>
      </button>
      <div v-show="assistantOpen" class="assistant-popover">
        <IntelligenceAssistant
          :active="assistantOpen"
          @close="assistantOpen = false"
        />
      </div>
    </div>
  </div>
</template>

<script>
/**
 * 应用壳层：顶栏、主布局（左侧态势+新闻/监控 | 右侧情报网格）、可拖浮动的情报助手。
 */
import SituationIndex from "./components/Situation/index.vue";
import IntelligenceAssistant from "./components/intelligenceAssistant/index.vue";
import LowerTabs from "./components/LowerTabs.vue";
import LiveNewsPanel from "./components/intelComponents/LiveNewsPanel.vue";
import LiveMonitorPanel from "./components/intelComponents/LiveMonitorPanel.vue";
import SystemSettingsDialog from "./components/system/SystemSettingsDialog.vue";
import IntelGenStudioWorkspace from "./components/smartComponentFactory/IntelGenStudioWorkspace.vue";

export default {
  name: "App",
  components: {
    SituationIndex,
    IntelligenceAssistant,
    LowerTabs,
    LiveNewsPanel,
    LiveMonitorPanel,
    SystemSettingsDialog,
    IntelGenStudioWorkspace
  },
  data() {
    return {
      nowText: "",
      clockTimer: null,
      intelPoints: [],
      assistantOpen: false,
      assistantAnchor: { x: 0, y: 0 },
      isDraggingAssistant: false,
      assistantDragMoved: false,
      dragOffset: { x: 0, y: 0 },
      assistantDockSide: "right",

      settingsDialogVisible: false,
      /** 打开设置时由右栏快照注入「情报面板」草稿；刷新页面后不保留 */
      intelPanelSettingsSnapshot: null,

      /** 主布局左栏宽度（px）；null 表示首帧用 50%/50% 占位，测量后再写入像素 */
      mainSplitLeftPx: null,
      isDraggingMainSplit: false,
      _mainSplitDragStartX: 0,
      _mainSplitDragStartLeftPx: 0,

      /** 顶栏智能组件工厂：全屏覆盖主工作区 */
      aiCodeGeneratorOpen: false
    };
  },
  computed: {
    mainLayoutLeftStyle() {
      if (this.mainSplitLeftPx == null) {
        return { flex: "0 0 50%", maxWidth: "50%", minWidth: 0 };
      }
      const w = Math.max(200, Math.round(this.mainSplitLeftPx));
      return { flex: `0 0 ${w}px`, maxWidth: `${w}px`, width: `${w}px`, minWidth: 0 };
    },
    mainLayoutRightStyle() {
      if (this.mainSplitLeftPx == null) {
        return { flex: "0 0 50%", maxWidth: "50%", minWidth: 0 };
      }
      return { flex: "1 1 auto", minWidth: 0, maxWidth: "none" };
    }
  },
  mounted() {
    this.updateClock();
    this.clockTimer = setInterval(this.updateClock, 1000);
    // 每次初始显示都放在页面右下角
    this.setAssistantInitialPosition();
    this.$nextTick(() => {
      this.initMainSplitFromLayout();
      this.notifySituationResize();
    });
    window.addEventListener("mousemove", this.onAssistantDragging);
    window.addEventListener("mouseup", this.onAssistantDragEnd);
    window.addEventListener("resize", this.onWindowResize);
  },
  beforeDestroy() {
    if (this.clockTimer) clearInterval(this.clockTimer);
    window.removeEventListener("mousemove", this.onAssistantDragging);
    window.removeEventListener("mouseup", this.onAssistantDragEnd);
    window.removeEventListener("resize", this.onWindowResize);
    window.removeEventListener("mousemove", this.onMainSplitMouseMove);
    window.removeEventListener("mouseup", this.onMainSplitMouseUp);
  },
  methods: {
    restoreAssistantAnchor() {
      try {
        const raw = localStorage.getItem("sig.assistantAnchor");
        if (!raw) return false;
        const j = JSON.parse(raw);
        if (!j || !Number.isFinite(j.x) || !Number.isFinite(j.y)) return false;
        // 恢复后做一次边界夹紧与吸边，避免分辨率变化导致按钮跑出视口
        const margin = 20;
        const maxX = window.innerWidth - 70;
        const maxY = window.innerHeight - 70;
        this.assistantAnchor = {
          x: Math.min(maxX, Math.max(margin, Math.round(j.x))),
          y: Math.min(maxY, Math.max(margin, Math.round(j.y)))
        };
        this.snapAssistantToEdge();
        return true;
      } catch {
        return false;
      }
    },
    persistAssistantAnchor() {
      try {
        localStorage.setItem("sig.assistantAnchor", JSON.stringify(this.assistantAnchor));
      } catch {
        /* ignore */
      }
    },
    setAssistantInitialPosition() {
      // 初始放在右下角，避开浏览器边缘与滚动条区域
      const margin = 20;
      this.assistantAnchor.x = Math.max(margin, window.innerWidth - 78);
      this.assistantAnchor.y = Math.max(margin, window.innerHeight - 78);
      this.assistantDockSide = "bottom";
    },
    updateClock() {
      // 北京时间固定按 UTC+8 计算，格式：xxxx年xx月xx日 xx:xx:xx
      const now = new Date();
      const utc = now.getTime() + now.getTimezoneOffset() * 60 * 1000;
      const bj = new Date(utc + 8 * 60 * 60 * 1000);
      const yyyy = bj.getFullYear();
      const mm = String(bj.getMonth() + 1).padStart(2, "0");
      const dd = String(bj.getDate()).padStart(2, "0");
      const hh = String(bj.getHours()).padStart(2, "0");
      const mi = String(bj.getMinutes()).padStart(2, "0");
      const ss = String(bj.getSeconds()).padStart(2, "0");
      this.nowText = `${yyyy}年${mm}月${dd}日 ${hh}:${mi}:${ss}`;
    },
    updateIntelPoints(points) {
      this.intelPoints = points;
    },
    onAddIntelPoint(payload) {
      const p = payload && typeof payload === "object" ? payload : {};
      const coordinates = String(p.coordinates || "").trim();
      if (!coordinates) return;
      const region = String(p.region || "").trim() || "情报点";
      const id = p.id != null ? String(p.id).trim() : "";

      const next = Array.isArray(this.intelPoints) ? this.intelPoints.slice() : [];
      if (id) {
        for (let i = next.length - 1; i >= 0; i -= 1) {
          const pid = next[i] && next[i].id != null ? String(next[i].id).trim() : "";
          if (pid === id) next.splice(i, 1);
        }
      }
      next.unshift({
        ...p,
        id,
        region,
        coordinates
      });
      // 避免无限增长
      if (next.length > 200) next.splice(200);
      this.intelPoints = next;
    },
    onRemoveIntelPoint(payload) {
      const p = payload && typeof payload === "object" ? payload : {};
      const id = p.id != null ? String(p.id).trim() : "";
      if (!id) return;
      const list = Array.isArray(this.intelPoints) ? this.intelPoints : [];
      this.intelPoints = list.filter((it) => {
        const pid = it && it.id != null ? String(it.id).trim() : "";
        return pid !== id;
      });
    },
    onFocusSituationPoint(payload) {
      const idx = this.$refs.situationIndex;
      if (!idx || typeof idx.focusSituationPoint !== "function") return;
      idx.focusSituationPoint(payload);
    },
    onResetSituationView() {
      const idx = this.$refs.situationIndex;
      if (!idx || typeof idx.resetToDefaultView !== "function") return;
      idx.resetToDefaultView();
    },
    onClearSituationCountrySelection() {
      const idx = this.$refs.situationIndex;
      if (!idx || typeof idx.clearSituationCountrySelection !== "function") return;
      idx.clearSituationCountrySelection();
    },
    onFocusSituationCountry(payload) {
      const idx = this.$refs.situationIndex;
      if (!idx || typeof idx.focusCountryFromPanel !== "function") return;
      idx.focusCountryFromPanel(payload);
    },
    handleCountrySelected() {},
    onPanelReset() {},
    openSettingsDialog() {
      const lt = this.$refs.lowerTabs;
      if (lt && typeof lt.getIntelPanelConfig === "function") {
        this.intelPanelSettingsSnapshot = lt.getIntelPanelConfig();
      } else {
        this.intelPanelSettingsSnapshot = null;
      }
      this.settingsDialogVisible = true;
    },
    closeSettingsDialog() {
      this.settingsDialogVisible = false;
      this.intelPanelSettingsSnapshot = null;
    },
    applySettings(payload) {
      const data = payload && typeof payload === "object" ? payload : {};
      if (data.intelPanel && typeof data.intelPanel === "object") {
        const lt = this.$refs.lowerTabs;
        if (lt && typeof lt.syncIntelGridFromConfig === "function") {
          lt.syncIntelGridFromConfig(data.intelPanel);
        }
      }
      this.closeSettingsDialog();
    },
    toggleAssistant() {
      if (this.assistantDragMoved) return;
      this.assistantOpen = !this.assistantOpen;
    },
    onAssistantDragStart(event) {
      this.isDraggingAssistant = true;
      this.assistantDragMoved = false;
      this.dragOffset.x = event.clientX - this.assistantAnchor.x;
      this.dragOffset.y = event.clientY - this.assistantAnchor.y;
    },
    onAssistantDragging(event) {
      if (!this.isDraggingAssistant) return;
      this.assistantDragMoved = true;
      const margin = 20;
      const maxX = window.innerWidth - 70;
      const maxY = window.innerHeight - 70;
      this.assistantAnchor.x = Math.min(
        maxX,
        Math.max(margin, event.clientX - this.dragOffset.x)
      );
      this.assistantAnchor.y = Math.min(
        maxY,
        Math.max(margin, event.clientY - this.dragOffset.y)
      );
    },
    onAssistantDragEnd() {
      if (!this.isDraggingAssistant) return;
      this.isDraggingAssistant = false;
      // 拖拽结束后自动吸附到最近的上下左右边缘
      this.snapAssistantToEdge();
      this.persistAssistantAnchor();
      setTimeout(() => {
        this.assistantDragMoved = false;
      }, 0);
    },
    snapAssistantToEdge() {
      const margin = 20;
      const left = this.assistantAnchor.x - margin;
      const right = Math.max(margin, window.innerWidth - 78) - this.assistantAnchor.x;
      const top = this.assistantAnchor.y - margin;
      const bottom = Math.max(margin, window.innerHeight - 78) - this.assistantAnchor.y;
      const minDist = Math.min(left, right, top, bottom);
      if (minDist === left) {
        this.assistantAnchor.x = margin;
        this.assistantDockSide = "left";
      } else if (minDist === right) {
        this.assistantAnchor.x = Math.max(margin, window.innerWidth - 78);
        this.assistantDockSide = "right";
      } else if (minDist === top) {
        this.assistantAnchor.y = margin;
        this.assistantDockSide = "top";
      } else {
        this.assistantAnchor.y = Math.max(margin, window.innerHeight - 78);
        this.assistantDockSide = "bottom";
      }
    },
    onWindowResize() {
      const margin = 20;
      const maxX = window.innerWidth - 70;
      const maxY = window.innerHeight - 70;
      this.assistantAnchor.x = Math.min(maxX, Math.max(margin, this.assistantAnchor.x));
      this.assistantAnchor.y = Math.min(maxY, Math.max(margin, this.assistantAnchor.y));
      this.snapAssistantToEdge();
      const main = this.$refs.mainLayout;
      if (main && this.mainSplitLeftPx != null) {
        this.clampMainSplitLeft(main.clientWidth);
      }
      this.notifySituationResize();
    },
    /** 主布局中间分割条宽度（与 theme 中 .main-layout__splitter 一致） */
    mainSplitterWidth() {
      return 8;
    },
    initMainSplitFromLayout() {
      const el = this.$refs.mainLayout;
      if (!el) return;
      const cw = el.clientWidth;
      if (!cw) return;
      const split = this.mainSplitterWidth();
      const gapApprox = 10;
      if (this.mainSplitLeftPx == null) {
        this.mainSplitLeftPx = Math.round(Math.max(0, cw - split - 2 * gapApprox) * 0.5);
      }
      this.clampMainSplitLeft(cw);
    },
    clampMainSplitLeft(innerW) {
      const split = this.mainSplitterWidth();
      const minLeft = 240;
      const minRight = 260;
      if (!innerW || innerW <= split + minLeft + minRight) return;
      const maxLeft = innerW - split - minRight;
      const minLeftClamped = Math.min(minLeft, Math.max(200, maxLeft - 1));
      this.mainSplitLeftPx = Math.min(maxLeft, Math.max(minLeftClamped, Math.round(this.mainSplitLeftPx)));
    },
    onMainSplitMouseDown(e) {
      if (this.mainSplitLeftPx == null) this.initMainSplitFromLayout();
      this.isDraggingMainSplit = true;
      this._mainSplitDragStartX = e.clientX;
      this._mainSplitDragStartLeftPx = this.mainSplitLeftPx;
      document.body.style.userSelect = "none";
      document.body.style.cursor = "col-resize";
      window.addEventListener("mousemove", this.onMainSplitMouseMove, { passive: true });
      window.addEventListener("mouseup", this.onMainSplitMouseUp, { passive: true });
    },
    onMainSplitMouseMove(e) {
      if (!this.isDraggingMainSplit) return;
      const el = this.$refs.mainLayout;
      if (!el) return;
      const inner = el.clientWidth;
      const dx = e.clientX - this._mainSplitDragStartX;
      this.mainSplitLeftPx = this._mainSplitDragStartLeftPx + dx;
      this.clampMainSplitLeft(inner);
      this.notifySituationResize();
    },
    onMainSplitMouseUp() {
      if (!this.isDraggingMainSplit) return;
      this.isDraggingMainSplit = false;
      document.body.style.userSelect = "";
      document.body.style.cursor = "";
      window.removeEventListener("mousemove", this.onMainSplitMouseMove);
      window.removeEventListener("mouseup", this.onMainSplitMouseUp);
      this.notifySituationResize();
    },
    notifySituationResize() {
      this.$nextTick(() => {
        const idx = this.$refs.situationIndex;
        if (idx && typeof idx.notifySituationResize === "function") {
          idx.notifySituationResize();
        }
      });
    },
    toggleAiCodeGenerator() {
      this.aiCodeGeneratorOpen = !this.aiCodeGeneratorOpen;
    },
    closeAiCodeGenerator() {
      this.aiCodeGeneratorOpen = false;
    }
  }
};
</script>

<style scoped>
.ai-code-gen-overlay {
  position: fixed;
  z-index: 119850;
  /* 与顶栏错开一点，四周留白 + 圆角卡片感 */
  left: 0.5vw;
  right: 0.5vw;
  bottom: clamp(10px, 1.2vh, 18px);
  top: calc(clamp(52px, 7.2vh, 72px) + clamp(8px, 1vh, 12px));
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  min-height: 0;
  border-radius: 10px;
  overflow: hidden;
  border: 1px solid rgba(28, 111, 184, 0.55);
  box-shadow:
    0 0 0 1px rgba(62, 186, 255, 0.12),
    0 18px 48px rgba(0, 10, 28, 0.55);
  background: linear-gradient(165deg, rgba(7, 28, 58, 0.98), rgba(3, 16, 38, 0.98));
}

.ai-code-gen-overlay__fill {
  flex: 1;
  min-height: 0;
  min-width: 0;
}
</style>
