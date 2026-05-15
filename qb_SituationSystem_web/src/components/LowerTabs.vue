<template>
  <div class="lower-tabs-root">
    <!-- 右侧约半宽（可拖拽）区内 2×N 情报格；新闻/监控由 App 左侧底部承载 -->
    <div ref="panelGrid" class="panel-grid-viewport">
      <div class="panel-grid" :style="panelGridStyle">
    <div
      v-for="item in normalizedLayout"
      :key="item.id"
      class="grid-item"
      :class="{
        'is-dragging': dragState && dragState.id === item.id && dragState.active,
        'is-swap-target': dragState && dragState.targetId === item.id
      }"
      :style="getPanelStyle(item)"
      @mousedown.capture="onPanelMouseDown($event, item)"
    >
      <div
        class="grid-item-toolbar"
        :class="{ 'grid-item-toolbar--intel-slot': item.componentKey === 'ai-intel-slot' }"
      >
        <template v-if="item.componentKey === 'ai-intel-slot'">
          <button
            type="button"
            class="op-btn-close"
            title="将当前 .vue 代码保存到本地"
            @click="saveIntelSlotToLocal(item.id)"
          >
            <img class="op-btn-icon" src="/assets/svg/下载.svg" alt="保存到本地" />
          </button>
          <button
            type="button"
            class="op-btn-close"
            title="上传到服务器（尚未接入）"
            @click="onIntelSlotUploadPlaceholder"
          >
            <img class="op-btn-icon" src="/assets/svg/上传.svg" alt="上传到服务器" />
          </button>
          <button
            type="button"
            class="op-btn-close"
            title="关闭并移除此预览格"
            @click="removePanel(item.id)"
          >
            <img class="op-btn-icon" src="/assets/svg/%E5%85%B3%E9%97%AD.svg" alt="关闭" />
          </button>
        </template>
        <template v-else-if="item.componentKey === 'personal-plugin'">
          <button
            class="op-btn-refresh"
            type="button"
            title="重新加载组件"
            @click="refreshPanel(item.id)"
          >
            <img class="op-btn-icon" src="/assets/svg/%E5%88%B7%E6%96%B0.svg" alt="刷新" />
          </button>
          <button class="op-btn-close" type="button" title="删除组件" @click="removePanel(item.id)">
            <img class="op-btn-icon" src="/assets/svg/%E5%85%B3%E9%97%AD.svg" alt="关闭" />
          </button>
        </template>
        <template v-else>
          <button
            v-if="item.componentKey === 'rel-event' || item.componentKey === 'market-stock'"
            class="op-btn-setting"
            type="button"
            :title="item.componentKey === 'market-stock' ? '市场信息设置' : '图谱参数设置'"
            @click="openPanelSettings(item.id)"
          >
            <img class="op-btn-icon" src="/assets/svg/%E8%AE%BE%E7%BD%AE2.svg" alt="设置" />
          </button>
          <button
            class="op-btn-refresh"
            type="button"
            title="刷新组件"
            @click="refreshPanel(item.id)"
          >
            <img class="op-btn-icon" src="/assets/svg/%E5%88%B7%E6%96%B0.svg" alt="刷新" />
          </button>
          <button class="op-btn-close" type="button" title="删除组件" @click="removePanel(item.id)">
            <img class="op-btn-icon" src="/assets/svg/%E5%85%B3%E9%97%AD.svg" alt="关闭" />
          </button>
        </template>
      </div>
      <div
        class="resize-handle resize-handle-top"
        :class="{ 'is-enabled': canResize(item, 'top'), 'is-active': isActiveHandle(item, 'top') }"
        @mousedown.prevent="startResize($event, item, 'top')"
      />
      <div
        class="resize-handle resize-handle-right"
        :class="{ 'is-enabled': canResize(item, 'right'), 'is-active': isActiveHandle(item, 'right') }"
        @mousedown.prevent="startResize($event, item, 'right')"
      />
      <div
        class="resize-handle resize-handle-bottom"
        :class="{ 'is-enabled': canResize(item, 'bottom'), 'is-active': isActiveHandle(item, 'bottom') }"
        @mousedown.prevent="startResize($event, item, 'bottom')"
      />
      <div
        class="resize-handle resize-handle-left"
        :class="{ 'is-enabled': canResize(item, 'left'), 'is-active': isActiveHandle(item, 'left') }"
        @mousedown.prevent="startResize($event, item, 'left')"
      />
      <component
        :key="`${item.id}-${panelRefreshVersionMap[item.id] || 0}`"
        :is="resolveComponent(item.componentKey)"
        v-bind="getPanelProps(item)"
        @focus-situation-country="onFocusSituationCountry"
        @add-intel-point="onAddIntelPoint"
        @open-hot-news-detail="onOpenHotNewsDetail"
      />
    </div>

    <div
      v-for="cell in emptyCells"
      :key="`empty-${cell.x}-${cell.y}`"
      class="grid-empty-cell"
      :class="{
        'is-drop-target':
          dragState &&
          dragState.active &&
          dragState.emptyHighlight &&
          dragState.emptyHighlight.x === cell.x &&
          dragState.emptyHighlight.y === cell.y,
        'grid-empty-cell--picker-inline': isAddingCell(cell) && addMenuStep !== 'kind'
      }"
      :data-add-menu-open="isAddingCell(cell) ? '1' : undefined"
      :style="getGridItemStyle({ x: cell.x, y: cell.y, w: 1, h: 1 })"
    >
      <div
        v-if="!isAddingCell(cell) || addMenuStep === 'kind'"
        class="empty-cell-popover-anchor"
      >
        <button
          class="empty-cell-icon-btn"
          type="button"
          title="添加组件"
          aria-label="添加组件"
          :aria-expanded="isAddingCell(cell) && addMenuStep === 'kind' ? 'true' : 'false'"
          @click="openAddMenu(cell)"
        >
          <img class="empty-cell-icon-btn__img" src="/assets/svg/添加.svg" alt="" />
        </button>
        <div
          v-if="isAddingCell(cell) && addMenuStep === 'kind'"
          class="empty-cell-popover"
          role="dialog"
          aria-label="选择组件分支"
        >
          <div class="empty-cell-popover-list empty-cell-popover-list--kind">
            <button type="button" class="add-type-btn add-type-btn--compact" @click="addMenuStep = 'system'">
              <span class="add-type-btn__label">系统组件</span>
              <span class="add-type-btn__emoji" aria-hidden="true">📦</span>
            </button>
            <button type="button" class="add-type-btn add-type-btn--compact" @click="openPersonalPluginPicker(cell)">
              <span class="add-type-btn__label">自定义组件</span>
              <span class="add-type-btn__emoji" aria-hidden="true">🧩</span>
            </button>
          </div>
        </div>
      </div>
      <div v-else-if="isAddingCell(cell)" class="empty-cell-add-inline">
        <div v-if="addMenuStep === 'system'" class="empty-cell-add-inline__body">
          <div class="add-menu-list-inline add-menu-list-inline--with-back-fab">
            <button
              v-for="item in intelSystemAddMenuItems"
              :key="item.key"
              class="add-type-btn"
              type="button"
              @click="addPanelAt(cell, item.key)"
            >
              <span class="add-type-btn__label">{{ item.label }}</span>
              <span class="add-type-btn__emoji" aria-hidden="true">{{ panelTitleEmoji[item.key] }}</span>
            </button>
          </div>
          <button
            type="button"
            class="add-menu-back-fab"
            title="返回分支选择"
            aria-label="返回"
            @click="addMenuStep = 'kind'"
          >
            ←
          </button>
        </div>
        <div v-else-if="addMenuStep === 'personal'" class="plugin-picker-scope">
          <transition name="plugin-url-toast-fade">
            <div
              v-if="pluginToast.visible"
              class="plugin-url-toast plugin-url-toast--scoped"
              role="status"
            >
              {{ pluginToast.text }}
            </div>
          </transition>

          <div
            v-if="pluginUrlSetDialog.visible"
            class="plugin-url-modal-layer"
            role="dialog"
            aria-modal="true"
            aria-labelledby="plugin-url-set-title"
            @click.self="closePluginUrlSetDialog"
          >
            <div class="plugin-url-dialog plugin-url-dialog--set" @click.stop>
              <h3 id="plugin-url-set-title" class="plugin-url-dialog__title">设置 URL</h3>
              <p v-if="pluginUrlSetDialog.prefetching" class="plugin-url-dialog__hint">正在读取已绑定地址…</p>
              <label class="plugin-url-dialog__label">
                <span class="plugin-url-dialog__label-text">URL</span>
                <input
                  v-model="pluginUrlSetDialog.urlPrefix"
                  type="text"
                  class="plugin-url-dialog__input"
                  placeholder="例如 http://localhost:8081"
                  autocomplete="off"
                  :disabled="pluginUrlSetDialog.prefetching"
                  @keydown.enter.prevent="submitPluginUrlSet"
                />
              </label>
              <div class="plugin-url-dialog__footer">
                <button
                  type="button"
                  class="plugin-url-dialog__btn plugin-url-dialog__btn--ghost"
                  @click="closePluginUrlSetDialog"
                >
                  取消
                </button>
                <button
                  type="button"
                  class="plugin-url-dialog__btn plugin-url-dialog__btn--primary"
                  :disabled="pluginUrlSetDialog.submitting || pluginUrlSetDialog.prefetching"
                  @click="submitPluginUrlSet"
                >
                  确定
                </button>
              </div>
            </div>
          </div>

          <div
            v-if="pluginUrlDeleteDialog.visible"
            class="plugin-url-modal-layer"
            role="dialog"
            aria-modal="true"
            aria-labelledby="plugin-url-del-title"
            @click.self="closePluginUrlDeleteDialog"
          >
            <div class="plugin-url-dialog plugin-url-dialog--confirm" @click.stop>
              <h3 id="plugin-url-del-title" class="plugin-url-dialog__title">确认清除</h3>
              <p class="plugin-url-dialog__confirm-text">
                确定要清除 ID 为 <strong>{{ pluginUrlDeleteDialog.pluginId }}</strong> 组件的 URL 地址吗？
              </p>
              <div class="plugin-url-dialog__footer">
                <button
                  type="button"
                  class="plugin-url-dialog__btn plugin-url-dialog__btn--ghost"
                  @click="closePluginUrlDeleteDialog"
                >
                  取消
                </button>
                <button
                  type="button"
                  class="plugin-url-dialog__btn plugin-url-dialog__btn--danger"
                  :disabled="pluginUrlDeleteDialog.submitting"
                  @click="confirmPluginUrlDelete"
                >
                  确认
                </button>
              </div>
            </div>
          </div>

          <div class="empty-cell-add-inline__body empty-cell-add-inline__body--scroll">
          <div class="add-menu-list-inline add-menu-list-inline--scroll add-menu-list-inline--with-back-fab add-menu-list-inline--plugin-table-wrap">
            <div v-if="pluginsLoading" class="add-menu-status">加载组件列表…</div>
            <div v-else-if="pluginsError" class="add-menu-status add-menu-status--err">{{ pluginsError }}</div>
            <template v-else>
              <div v-if="!pluginsList.length" class="add-menu-status">暂无可用组件</div>
              <table v-else class="plugin-picker-table" @click.stop>
                <thead>
                  <tr>
                    <th class="plugin-picker-table__th plugin-picker-table__th--idx">#</th>
                    <th class="plugin-picker-table__th">组件 ID</th>
                    <th class="plugin-picker-table__th plugin-picker-table__th--actions">URL 操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr
                    v-for="(p, idx) in pluginsList"
                    :key="p.id"
                    class="plugin-picker-table__row"
                    :title="'点击添加组件至网格'"
                    @click="onPluginTableRowClick($event, cell, p)"
                  >
                    <td class="plugin-picker-table__td plugin-picker-table__td--idx">{{ idx + 1 }}</td>
                    <td class="plugin-picker-table__td plugin-picker-table__td--id">
                      <span class="plugin-picker-table__id-text">{{ p.id }}</span>
                      <span v-if="p.file_name" class="plugin-picker-table__file">{{ p.file_name }}</span>
                    </td>
                    <td class="plugin-picker-table__td plugin-picker-table__td--actions plugin-url-actions" @click.stop>
                      <div class="plugin-url-action-btns">
                        <button
                          type="button"
                          class="plugin-url-icon-btn"
                          title="设置组件 URL（先读取已绑定地址）"
                          aria-label="设置组件 URL"
                          @click="openPluginUrlSetDialog(p)"
                        >
                          <img class="plugin-url-icon-btn__img" src="/assets/svg/编辑.svg" alt="" />
                        </button>
                        <button
                          type="button"
                          class="plugin-url-icon-btn"
                          title="删除组件 URL"
                          aria-label="删除组件 URL"
                          @click="openPluginUrlDeleteDialog(p)"
                        >
                          <img class="plugin-url-icon-btn__img" src="/assets/svg/清除.svg" alt="" />
                        </button>
                      </div>
                    </td>
                  </tr>
                </tbody>
              </table>
            </template>
          </div>
          <button
            type="button"
            class="add-menu-back-fab"
            title="返回分支选择"
            aria-label="返回"
            @click="addMenuStep = 'kind'"
          >
            ←
          </button>
        </div>
        </div>
      </div>
    </div>

    </div>
  </div>
    <div v-if="hotNewsDetailOpen && hotNewsActive" class="panel-grid-modal-layer">
      <HotNewsDetailModal
        :news="hotNewsActive"
        @close="closeHotNewsDetail"
        @add-intel-point="onAddIntelPoint"
        @remove-intel-point="onRemoveIntelPoint"
        @focus-situation-point="onFocusSituationPoint"
        @reset-situation-view="onResetSituationView"
      />
    </div>
  </div>
</template>

<script>
import HotEventsPanel from "./intelComponents/HotEventsPanel.vue";
import HotNewsDetailModal from "./intelComponents/overlays/HotNewsDetailModal.vue";
import RelatedEventDiscoveryPanel from "./intelComponents/RelatedEventDiscoveryPanel.vue";
import MarketInfoPredictionPanel from "./intelComponents/MarketInfoPredictionPanel.vue";
import AiIntelLiveMountPanel from "./smartComponentFactory/AiIntelLiveMountPanel.vue";
import PersonalizedPluginMountPanel from "./intelComponents/personalizedPlugins/PersonalizedPluginMountPanel.vue";
import { PANEL_TITLE_EMOJI } from "./intelComponents/js/panelTitleEmoji.js";
import { INTEL_PANEL_MENU_ITEMS } from "./intelComponents/js/intelPanelMenuItems.js";
import { fetchPluginsList, postPluginUrlMapping, getPluginUrlMapping, deletePluginUrlMapping } from "../api/pluginHttp.js";
import { PLUGIN_API_USERNAME } from "../api/pluginConstants.js";
import {
  DEFAULT_INTEL_GRID,
  DEFAULT_INTEL_PANELS,
  clampIntelCols,
  clampIntelRows,
  normalizeStoredPanels
} from "../utils/intelPanelConfig.js";

/** 与 `.panel-grid` 的 `gap` 一致（px），用于 JS 与 CSS 对齐 */
const PANEL_GRID_GAP = 10;

export default {
  name: "LowerTabs",
  components: {
    HotEventsPanel,
    HotNewsDetailModal,
    RelatedEventDiscoveryPanel,
    MarketInfoPredictionPanel,
    AiIntelLiveMountPanel,
    PersonalizedPluginMountPanel
  },
  data() {
    return {
      panelTitleEmoji: PANEL_TITLE_EMOJI,
      gridCols: DEFAULT_INTEL_GRID.cols,
      gridRows: DEFAULT_INTEL_GRID.rows,
      rightPanelLayout: DEFAULT_INTEL_PANELS.map((p) => ({ ...p })),
      panelRefreshVersionMap: {},
      addingCell: null,
      /** 空格子添加菜单：kind → 系统列表 / 自定义组件列表 */
      addMenuStep: "kind",
      pluginsList: [],
      pluginsLoading: false,
      pluginsError: "",
      /** 组件 URL 映射：顶部轻提示 */
      pluginToast: {
        visible: false,
        text: ""
      },
      _pluginToastTimer: null,
      pluginUrlSetDialog: {
        visible: false,
        pluginId: "",
        urlPrefix: "",
        submitting: false,
        /** 打开弹窗时先 GET /api/plugin-url 预填 */
        prefetching: false
      },
      pluginUrlDeleteDialog: {
        visible: false,
        pluginId: "",
        submitting: false
      },
      resizingState: null,
      dragState: null,
      bodyCursorBackup: "",
      bodySelectBackup: "",

      hotNewsDetailOpen: false,
      hotNewsActive: null,
      panelCellW: 0,
      panelCellH: 0,

      /**
       * 动态情报组件预览格（ai-intel-slot）的 .vue 全文；与顶栏智能组件工厂解耦，后续可对接云端下发。
       */
      intelSlotLiveSfc: {},
      /** 按 panelId 暂存需求描述 */
      intelGenPromptByPanelId: {},
      /** ResizeObserver → refresh 若同步改样式，会触发「loop undelivered」；用 rAF 拆到下一帧 */
      _panelGridResizeRaf: null
    };
  },
  mounted() {
    this._panelGridResizeObserver = new ResizeObserver(() => {
      this.schedulePanelGridResize();
    });
    this.$nextTick(() => {
      const el = this.$refs.panelGrid;
      if (el) this._panelGridResizeObserver.observe(el);
      this.refreshPanelCellSize();
    });
    this._closeAddMenuOnOutside = (e) => {
      if (!this.addingCell) return;
      const t = e.target;
      if (t && typeof t.closest === "function" && t.closest('[data-add-menu-open="1"]')) return;
      this.cancelAdd();
    };
    document.addEventListener("pointerdown", this._closeAddMenuOnOutside, false);
  },
  beforeDestroy() {
    if (this._closeAddMenuOnOutside) {
      document.removeEventListener("pointerdown", this._closeAddMenuOnOutside, false);
      this._closeAddMenuOnOutside = null;
    }
    if (this._panelGridResizeRaf != null) {
      cancelAnimationFrame(this._panelGridResizeRaf);
      this._panelGridResizeRaf = null;
    }
    if (this._panelGridResizeObserver) {
      this._panelGridResizeObserver.disconnect();
      this._panelGridResizeObserver = null;
    }
    if (this._pluginToastTimer) {
      clearTimeout(this._pluginToastTimer);
      this._pluginToastTimer = null;
    }
    window.removeEventListener("mousemove", this.onResizingMove);
    window.removeEventListener("mouseup", this.onResizingEnd);
    window.removeEventListener("mousemove", this.onPanelDragging);
    window.removeEventListener("mouseup", this.onPanelDragEnd);
    this.restoreDragEnvironment();
  },
  computed: {
    panelGridStyle() {
      const style = {};
      if (this.panelCellW > 0) style["--panel-cell-w"] = `${this.panelCellW}px`;
      if (this.panelCellH > 0) style["--panel-cell-h"] = `${this.panelCellH}px`;
      const c = Math.max(1, Number(this.gridCols) || 1);
      const r = Math.max(1, Number(this.gridRows) || 1);
      style.gridTemplateColumns = `repeat(${c}, var(--panel-cell-w, minmax(0, 1fr)))`;
      style.gridTemplateRows = `repeat(${r}, var(--panel-cell-h, 160px))`;
      return style;
    },
    normalizedLayout() {
      return (this.rightPanelLayout || []).map((item, idx) => {
        const x = this.clampCol(item.x, 1);
        const y = this.clampRow(item.y, 1);
        const w = this.clampCol(item.w, 1);
        const h = this.clampRow(item.h, 1);
        return {
          id: item.id || `${item.componentKey || "panel"}-${idx + 1}`,
          componentKey: item.componentKey || "hot",
          pluginId: item.pluginId,
          pluginFileName: item.pluginFileName,
          x,
          y,
          w: Math.min(w, this.gridCols - x + 1),
          h: Math.min(h, this.gridRows - y + 1)
        };
      });
    },
    emptyCells() {
      const cells = [];
      for (let y = 1; y <= this.gridRows; y += 1) {
        for (let x = 1; x <= this.gridCols; x += 1) {
          if (this.isAreaFree(x, y, 1, 1)) {
            cells.push({ x, y });
          }
        }
      }
      return cells;
    },
    intelSystemAddMenuItems() {
      return INTEL_PANEL_MENU_ITEMS;
    },
  },
  methods: {
    /** 供打开系统设置时同步「情报面板」表单（不写本地缓存） */
    getIntelPanelConfig() {
      return {
        cols: this.gridCols,
        rows: this.gridRows,
        panels: this.rightPanelLayout.map((p) => ({ ...p }))
      };
    },
    /**
     * 系统设置「应用」后同步网格（由 App 调用 ref）
     */
    syncIntelGridFromConfig(payload) {
      const p = payload && typeof payload === "object" ? payload : {};
      const cols = clampIntelCols(p.cols);
      const rows = clampIntelRows(p.rows);
      const panels = normalizeStoredPanels(cols, rows, p.panels);
      this.gridCols = cols;
      this.gridRows = rows;
      this.rightPanelLayout = panels.map((it) => ({ ...it }));
      this.cancelAdd();
      this.$nextTick(() => this.refreshPanelCellSize());
    },
    refreshPanelCellSize() {
      const { cellW, cellH } = this.getPanelCellMetrics();
      const w = Math.round(cellW * 100) / 100;
      const h = Math.round(cellH * 100) / 100;
      if (this.panelCellW === w && this.panelCellH === h) return;
      this.panelCellW = w;
      this.panelCellH = h;
    },
    schedulePanelGridResize() {
      if (this._panelGridResizeRaf != null) cancelAnimationFrame(this._panelGridResizeRaf);
      this._panelGridResizeRaf = requestAnimationFrame(() => {
        this._panelGridResizeRaf = null;
        this.refreshPanelCellSize();
      });
    },
    clampCol(value, fallback) {
      const num = Number(value);
      if (!Number.isFinite(num)) return fallback;
      return Math.max(1, Math.min(this.gridCols, Math.round(num)));
    },
    clampRow(value, fallback) {
      const num = Number(value);
      if (!Number.isFinite(num)) return fallback;
      return Math.max(1, Math.min(this.gridRows, Math.round(num)));
    },
    resolveComponent(componentKey) {
      const componentMap = {
        hot: "HotEventsPanel",
        "market-stock": "MarketInfoPredictionPanel",
        "rel-event": "RelatedEventDiscoveryPanel",
        "ai-intel-slot": "AiIntelLiveMountPanel",
        "personal-plugin": "PersonalizedPluginMountPanel"
      };
      return componentMap[componentKey] || "HotEventsPanel";
    },
    getPanelProps(item) {
      const componentKey = item && item.componentKey ? item.componentKey : "";
      const panelId = item && item.id ? item.id : "";
      const base = {
        componentKey,
        panelId
      };
      if (componentKey === "ai-intel-slot") {
        return {
          ...base,
          liveSfc: this.intelSlotLiveSfc[panelId] != null ? String(this.intelSlotLiveSfc[panelId]) : ""
        };
      }
      if (componentKey === "personal-plugin") {
        return {
          ...base,
          pluginId: item.pluginId != null ? String(item.pluginId).trim() : "",
          pluginFileName: item.pluginFileName != null ? String(item.pluginFileName).trim() : ""
        };
      }
      return base;
    },
    openPanelSettings(panelId) {
      const panel = this.rightPanelLayout.find((it) => it.id === panelId);
      const componentKey = panel && panel.componentKey ? panel.componentKey : "";
      const eventName =
        componentKey === "market-stock" ? "market-stock-open-setting" : "related-event-open-setting";
      window.dispatchEvent(new CustomEvent(eventName, { detail: { panelId: String(panelId || "") } }));
    },
    onFocusSituationCountry(payload) {
      this.$emit("focus-situation-country", payload);
    },
    onAddIntelPoint(payload) {
      this.$emit("add-intel-point", payload);
    },
    onRemoveIntelPoint(payload) {
      this.$emit("remove-intel-point", payload);
    },
    onFocusSituationPoint(payload) {
      this.$emit("focus-situation-point", payload);
    },
    onResetSituationView() {
      this.$emit("reset-situation-view");
    },
    onOpenHotNewsDetail(news) {
      this.hotNewsActive = news || null;
      this.hotNewsDetailOpen = !!news;
    },
    closeHotNewsDetail() {
      this.hotNewsDetailOpen = false;
      this.hotNewsActive = null;
    },
    getGridItemStyle(item) {
      const cardColumns = Number(item.w) >= 2 ? 2 : 1;
      return {
        gridColumn: `${item.x} / span ${item.w}`,
        gridRow: `${item.y} / span ${item.h}`,
        "--panel-card-columns": String(cardColumns)
      };
    },
    getPanelStyle(item) {
      const base = this.getGridItemStyle(item);
      if (!this.dragState || this.dragState.id !== item.id || !this.dragState.active) {
        return base;
      }
      const dx = this.dragState.currentX - this.dragState.startX;
      const dy = this.dragState.currentY - this.dragState.startY;
      return {
        ...base,
        transform: `translate3d(${dx}px, ${dy}px, 0)`,
        transformOrigin: "center center",
        zIndex: 50
      };
    },
    isAreaFree(x, y, w, h, ignoreId) {
      const xEnd = x + w - 1;
      const yEnd = y + h - 1;
      if (x < 1 || y < 1 || xEnd > this.gridCols || yEnd > this.gridRows) return false;
      return !this.normalizedLayout.some((panel) => {
        if (ignoreId && panel.id === ignoreId) return false;
        const panelXEnd = panel.x + panel.w - 1;
        const panelYEnd = panel.y + panel.h - 1;
        const overlapX = !(xEnd < panel.x || x > panelXEnd);
        const overlapY = !(yEnd < panel.y || y > panelYEnd);
        return overlapX && overlapY;
      });
    },
    getMaxResize(item, direction) {
      let step = 0;
      while (step < this.gridCols + this.gridRows) {
        const next = step + 1;
        let free = false;
        if (direction === "right") {
          free = this.isAreaFree(item.x + item.w, item.y, 1, item.h, item.id);
          if (free) item = { ...item, w: item.w + 1 };
        } else if (direction === "left") {
          free = this.isAreaFree(item.x - 1, item.y, 1, item.h, item.id);
          if (free) item = { ...item, x: item.x - 1, w: item.w + 1 };
        } else if (direction === "bottom") {
          free = this.isAreaFree(item.x, item.y + item.h, item.w, 1, item.id);
          if (free) item = { ...item, h: item.h + 1 };
        } else if (direction === "top") {
          free = this.isAreaFree(item.x, item.y - 1, item.w, 1, item.id);
          if (free) item = { ...item, y: item.y - 1, h: item.h + 1 };
        }
        if (!free) break;
        step = next;
      }
      return step;
    },
    getMaxShrink(item, direction) {
      if (direction === "left" || direction === "right") {
        return Math.max(0, Number(item.w || 1) - 1);
      }
      return Math.max(0, Number(item.h || 1) - 1);
    },
    canResize(item, direction) {
      return this.getMaxResize(item, direction) > 0 || this.getMaxShrink(item, direction) > 0;
    },
    isActiveHandle(item, direction) {
      return (
        !!this.resizingState &&
        this.resizingState.id === item.id &&
        this.resizingState.direction === direction
      );
    },
    /**
    * 单格尺寸：宽 = (视口宽 - gap) / 2，高 = (视口高 - gap) / 2，与原先 2×2 铺满侧栏时一致。
     */
    getPanelCellMetrics() {
      const el = this.$refs.panelGrid;
      if (!el) {
        return {
          cellW: 1,
          cellH: 1,
          gap: PANEL_GRID_GAP,
          padLeft: 0,
          innerWidth: 1
        };
      }
      const g = PANEL_GRID_GAP;
      const cs = window.getComputedStyle(el);
      const padL = parseFloat(cs.paddingLeft) || 0;
      const padR = parseFloat(cs.paddingRight) || 0;
      const innerW = Math.max(0, el.clientWidth - padL - padR);
      const h = el.clientHeight;
      const cols = Math.max(1, Number(this.gridCols) || 1);
      const cellW = Math.max(1, (innerW - (cols - 1) * g) / cols);
      const cellH = Math.max(1, (h - g) / 2);
      return { cellW, cellH, gap: g, padLeft: padL, innerWidth: innerW };
    },
    getGridCellSize() {
      const { cellW, cellH } = this.getPanelCellMetrics();
      return { cellW, cellH };
    },
    /** 沿一行/列轨道（含 gap）由偏移求格子序号 1..count */
    axisIndexFromOffset(rel, cellSize, count, gap) {
      if (cellSize <= 0) return 1;
      let acc = 0;
      for (let i = 1; i <= count; i += 1) {
        if (rel < acc + cellSize) return Math.min(count, Math.max(1, i));
        acc += cellSize + gap;
      }
      return count;
    },
    startResize(event, item, direction) {
      if (this.dragState) return;
      const maxExpandUnits = this.getMaxResize(item, direction);
      const maxShrinkUnits = this.getMaxShrink(item, direction);
      if (!maxExpandUnits && !maxShrinkUnits) return;
      const baseItem = this.rightPanelLayout.find((it) => it.id === item.id);
      if (!baseItem) return;
      this.resizingState = {
        id: item.id,
        direction,
        startX: event.clientX,
        startY: event.clientY,
        base: {
          x: Number(baseItem.x),
          y: Number(baseItem.y),
          w: Number(baseItem.w),
          h: Number(baseItem.h)
        },
        maxExpandUnits,
        maxShrinkUnits
      };
      this.applyDragEnvironment(direction);
      window.addEventListener("mousemove", this.onResizingMove);
      window.addEventListener("mouseup", this.onResizingEnd);
      this.cancelAdd();
    },
    quantizeUnits(rawUnits) {
      const threshold = 0.35;
      if (rawUnits > 0) return Math.floor(rawUnits + threshold);
      if (rawUnits < 0) return Math.ceil(rawUnits - threshold);
      return 0;
    },
    applyDragEnvironment(direction) {
      this.bodyCursorBackup = document.body.style.cursor || "";
      this.bodySelectBackup = document.body.style.userSelect || "";
      document.body.style.cursor =
        direction === "left" || direction === "right"
          ? "ew-resize"
          : direction === "top" || direction === "bottom"
            ? "ns-resize"
            : "grabbing";
      document.body.style.userSelect = "none";
    },
    restoreDragEnvironment() {
      document.body.style.cursor = this.bodyCursorBackup;
      document.body.style.userSelect = this.bodySelectBackup;
    },
    onResizingMove(event) {
      if (!this.resizingState) return;
      const target = this.rightPanelLayout.find((it) => it.id === this.resizingState.id);
      if (!target) return;
      const { cellW, cellH } = this.getGridCellSize();
      const dx = event.clientX - this.resizingState.startX;
      const dy = event.clientY - this.resizingState.startY;
      let rawUnits = 0;
      if (this.resizingState.direction === "right") {
        rawUnits = dx / cellW;
      } else if (this.resizingState.direction === "left") {
        rawUnits = -dx / cellW;
      } else if (this.resizingState.direction === "bottom") {
        rawUnits = dy / cellH;
      } else {
        rawUnits = -dy / cellH;
      }
      let units = this.quantizeUnits(rawUnits);
      units = Math.max(
        -this.resizingState.maxShrinkUnits,
        Math.min(this.resizingState.maxExpandUnits, units)
      );
      const base = this.resizingState.base;
      if (this.resizingState.direction === "right") {
        target.x = base.x;
        target.w = Math.max(1, base.w + units);
      } else if (this.resizingState.direction === "left") {
        target.x = base.x - units;
        target.w = Math.max(1, base.w + units);
      } else if (this.resizingState.direction === "bottom") {
        target.y = base.y;
        target.h = Math.max(1, base.h + units);
      } else {
        target.y = base.y - units;
        target.h = Math.max(1, base.h + units);
      }
    },
    onResizingEnd() {
      if (!this.resizingState) return;
      this.resizingState = null;
      window.removeEventListener("mousemove", this.onResizingMove);
      window.removeEventListener("mouseup", this.onResizingEnd);
      this.restoreDragEnvironment();
    },
    removePanel(id) {
      this.rightPanelLayout = this.rightPanelLayout.filter((item) => item.id !== id);
      if (Object.prototype.hasOwnProperty.call(this.panelRefreshVersionMap, id)) {
        this.$delete(this.panelRefreshVersionMap, id);
      }
      if (Object.prototype.hasOwnProperty.call(this.intelSlotLiveSfc, id)) {
        this.$delete(this.intelSlotLiveSfc, id);
      }
      if (Object.prototype.hasOwnProperty.call(this.intelGenPromptByPanelId, id)) {
        this.$delete(this.intelGenPromptByPanelId, id);
      }
      this.cancelAdd();
    },
    refreshPanel(id) {
      const panel = this.rightPanelLayout.find((it) => it.id === id);
      const componentKey = panel && panel.componentKey ? panel.componentKey : "";
      if (componentKey) {
        this.$emit("panel-reset", { id, componentKey });
      }
      if (componentKey === "hot") {
        this.$emit("clear-situation-country-selection");
      }
      const current = Number(this.panelRefreshVersionMap[id] || 0);
      this.$set(this.panelRefreshVersionMap, id, current + 1);
    },
    onPanelMouseDown(event, item) {
      if (this.resizingState) return;
      if (event.button !== 0) return;
      const target = event.target;
      if (!target || typeof target.closest !== "function") return;
      // 仅标题区域作为拖拽锚点
      if (!target.closest(".module-title")) return;
      event.preventDefault();
      this.dragState = {
        id: item.id,
        startX: event.clientX,
        startY: event.clientY,
        currentX: event.clientX,
        currentY: event.clientY,
        active: false,
        targetId: null,
        emptyHighlight: null
      };
      this.applyDragEnvironment("move");
      window.addEventListener("mousemove", this.onPanelDragging);
      window.addEventListener("mouseup", this.onPanelDragEnd);
      this.cancelAdd();
    },
    getGridCellAtPointer(clientX, clientY) {
      const el = this.$refs.panelGrid;
      if (!el) return null;
      const rect = el.getBoundingClientRect();
      if (clientX < rect.left || clientX > rect.right || clientY < rect.top || clientY > rect.bottom) {
        return null;
      }
      const { cellW, cellH, gap, padLeft, innerWidth } = this.getPanelCellMetrics();
      const relX = clientX - rect.left - padLeft;
      if (relX < 0 || relX >= innerWidth) return null;
      const relY = clientY - rect.top + el.scrollTop;
      const x = this.axisIndexFromOffset(relX, cellW, this.gridCols, gap);
      const y = this.axisIndexFromOffset(relY, cellH, this.gridRows, gap);
      return { x, y };
    },
    findPanelAtPointer(clientX, clientY, ignoreId) {
      const cell = this.getGridCellAtPointer(clientX, clientY);
      if (!cell) return null;
      const { x, y } = cell;
      return (
        this.normalizedLayout.find((panel) => {
          if (panel.id === ignoreId) return false;
          const xEnd = panel.x + panel.w - 1;
          const yEnd = panel.y + panel.h - 1;
          return x >= panel.x && x <= xEnd && y >= panel.y && y <= yEnd;
        }) || null
      );
    },
    onPanelDragging(event) {
      if (!this.dragState) return;
      const dx = event.clientX - this.dragState.startX;
      const dy = event.clientY - this.dragState.startY;
      this.dragState.currentX = event.clientX;
      this.dragState.currentY = event.clientY;
      if (!this.dragState.active && Math.hypot(dx, dy) < 4) return;
      this.dragState.active = true;
      const target = this.findPanelAtPointer(event.clientX, event.clientY, this.dragState.id);
      this.dragState.targetId = target ? target.id : null;
      if (this.dragState.targetId) {
        this.dragState.emptyHighlight = null;
      } else {
        const cell = this.getGridCellAtPointer(event.clientX, event.clientY);
        this.dragState.emptyHighlight = cell;
      }
    },
    swapPanelsById(idA, idB) {
      const panelA = this.rightPanelLayout.find((item) => item.id === idA);
      const panelB = this.rightPanelLayout.find((item) => item.id === idB);
      if (!panelA || !panelB) return;
      const snapshot = {
        x: panelA.x,
        y: panelA.y,
        w: panelA.w,
        h: panelA.h
      };
      panelA.x = panelB.x;
      panelA.y = panelB.y;
      panelA.w = panelB.w;
      panelA.h = panelB.h;
      panelB.x = snapshot.x;
      panelB.y = snapshot.y;
      panelB.w = snapshot.w;
      panelB.h = snapshot.h;
    },
    movePanelToDropCell(dragId, cellX, cellY) {
      const panel = this.rightPanelLayout.find((item) => item.id === dragId);
      if (!panel || cellX == null || cellY == null) return;
      const w = Number(panel.w);
      const h = Number(panel.h);
      if (!Number.isFinite(w) || !Number.isFinite(h) || w < 1 || h < 1) return;
      const x = Math.max(1, Math.min(cellX, this.gridCols - w + 1));
      const y = Math.max(1, Math.min(cellY, this.gridRows - h + 1));
      if (!this.isAreaFree(x, y, w, h, dragId)) return;
      if (panel.x === x && panel.y === y) return;
      panel.x = x;
      panel.y = y;
    },
    onPanelDragEnd() {
      if (!this.dragState) return;
      const wasActive = this.dragState.active;
      const targetId = this.dragState.targetId;
      const dragId = this.dragState.id;
      const dropX = this.dragState.currentX;
      const dropY = this.dragState.currentY;
      this.dragState = null;
      window.removeEventListener("mousemove", this.onPanelDragging);
      window.removeEventListener("mouseup", this.onPanelDragEnd);
      this.restoreDragEnvironment();
      if (!wasActive) return;
      if (targetId) {
        this.swapPanelsById(dragId, targetId);
      } else {
        const cell = this.getGridCellAtPointer(dropX, dropY);
        if (cell) this.movePanelToDropCell(dragId, cell.x, cell.y);
      }
    },
    openAddMenu(cell) {
      if (this.addingCell && this.addingCell.x === cell.x && this.addingCell.y === cell.y) {
        this.cancelAdd();
        return;
      }
      this.addMenuStep = "kind";
      this.pluginsList = [];
      this.pluginsError = "";
      this.pluginsLoading = false;
      this.addingCell = { x: cell.x, y: cell.y };
    },
    isAddingCell(cell) {
      return (
        !!this.addingCell &&
        this.addingCell.x === cell.x &&
        this.addingCell.y === cell.y
      );
    },
    addPanelAt(cell, componentKey, extras = {}) {
      if (!this.isAreaFree(cell.x, cell.y, 1, 1)) return;
      const ex = extras && typeof extras === "object" ? extras : {};
      const row = {
        id: `${componentKey}-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
        componentKey,
        x: cell.x,
        y: cell.y,
        w: 1,
        h: 1
      };
      if (ex.pluginId) row.pluginId = String(ex.pluginId).trim();
      if (ex.pluginFileName) row.pluginFileName = String(ex.pluginFileName).trim();
      this.rightPanelLayout.push(row);
      this.cancelAdd();
    },
    async openPersonalPluginPicker(cell) {
      if (cell && !this.isAddingCell(cell)) {
        this.openAddMenu(cell);
      }
      this.addMenuStep = "personal";
      this.pluginsLoading = true;
      this.pluginsError = "";
      this.pluginsList = [];
      try {
        const { data } = await fetchPluginsList();
        this.pluginsList = data && Array.isArray(data.plugins) ? data.plugins : [];
      } catch (e) {
        const msg =
          (e && e.response && e.response.data && e.response.data.detail) ||
          (e && e.message) ||
          String(e);
        this.pluginsError = String(msg);
      } finally {
        this.pluginsLoading = false;
      }
    },
    addPersonalPluginAt(cell, plugin) {
      const p = plugin && typeof plugin === "object" ? plugin : {};
      const id = p.id != null ? String(p.id).trim() : "";
      if (!id) return;
      this.addPanelAt(cell, "personal-plugin", {
        pluginId: id,
        pluginFileName: p.file_name != null ? String(p.file_name) : ""
      });
    },
    onPluginTableRowClick(ev, cell, plugin) {
      if (ev && typeof ev.target.closest === "function" && ev.target.closest(".plugin-url-actions")) {
        return;
      }
      this.addPersonalPluginAt(cell, plugin);
    },
    showPluginToast(text) {
      const t = text != null ? String(text) : "";
      if (!t) return;
      if (this._pluginToastTimer) {
        clearTimeout(this._pluginToastTimer);
        this._pluginToastTimer = null;
      }
      this.pluginToast = { visible: true, text: t };
      this._pluginToastTimer = setTimeout(() => {
        this.pluginToast.visible = false;
        this._pluginToastTimer = null;
      }, 2800);
    },
    async openPluginUrlSetDialog(plugin) {
      const p = plugin && typeof plugin === "object" ? plugin : {};
      const id = p.id != null ? String(p.id).trim() : "";
      if (!id) return;
      this.pluginUrlSetDialog.pluginId = id;
      this.pluginUrlSetDialog.urlPrefix = "";
      this.pluginUrlSetDialog.submitting = false;
      this.pluginUrlSetDialog.prefetching = true;
      this.pluginUrlSetDialog.visible = true;
      try {
        const { data } = await getPluginUrlMapping({ plugin_name: id });
        this.pluginUrlSetDialog.urlPrefix =
          data && data.url_prefix != null ? String(data.url_prefix) : "";
      } catch (e) {
        if (e && e.response && e.response.status === 404) {
          this.pluginUrlSetDialog.urlPrefix = "";
        } else {
          const msg =
            (e && e.response && e.response.data && (e.response.data.detail || e.response.data.message)) ||
            (e && e.message) ||
            String(e);
          window.alert(String(msg));
          this.closePluginUrlSetDialog();
        }
      } finally {
        this.pluginUrlSetDialog.prefetching = false;
      }
    },
    closePluginUrlSetDialog() {
      this.pluginUrlSetDialog.visible = false;
      this.pluginUrlSetDialog.prefetching = false;
    },
    async submitPluginUrlSet() {
      const d = this.pluginUrlSetDialog;
      if (!d.visible || d.submitting || d.prefetching) return;
      const url = String(d.urlPrefix || "").trim();
      if (!url) {
        window.alert("请输入 URL");
        return;
      }
      this.pluginUrlSetDialog.submitting = true;
      try {
        await postPluginUrlMapping({
          username: PLUGIN_API_USERNAME,
          plugin_name: d.pluginId,
          url_prefix: url
        });
        this.closePluginUrlSetDialog();
        this.showPluginToast("设置成功");
      } catch (e) {
        const msg =
          (e && e.response && e.response.data && (e.response.data.detail || e.response.data.message)) ||
          (e && e.message) ||
          String(e);
        window.alert(String(msg));
      } finally {
        this.pluginUrlSetDialog.submitting = false;
      }
    },
    openPluginUrlDeleteDialog(plugin) {
      const p = plugin && typeof plugin === "object" ? plugin : {};
      const id = p.id != null ? String(p.id).trim() : "";
      if (!id) return;
      this.pluginUrlDeleteDialog.visible = true;
      this.pluginUrlDeleteDialog.pluginId = id;
      this.pluginUrlDeleteDialog.submitting = false;
    },
    closePluginUrlDeleteDialog() {
      this.pluginUrlDeleteDialog.visible = false;
    },
    async confirmPluginUrlDelete() {
      const d = this.pluginUrlDeleteDialog;
      if (!d.visible || d.submitting) return;
      this.pluginUrlDeleteDialog.submitting = true;
      try {
        await deletePluginUrlMapping({ plugin_name: d.pluginId });
        this.closePluginUrlDeleteDialog();
        this.showPluginToast("清除成功");
      } catch (e) {
        const msg =
          (e && e.response && e.response.data && (e.response.data.detail || e.response.data.message)) ||
          (e && e.message) ||
          String(e);
        window.alert(String(msg));
      } finally {
        this.pluginUrlDeleteDialog.submitting = false;
      }
    },
    /** 上传入口占位：可点，暂不触发任何逻辑 */
    onIntelSlotUploadPlaceholder() {},
    saveIntelSlotToLocal(panelId) {
      const id = panelId != null ? String(panelId).trim() : "";
      if (!id) return;
      const text =
        this.intelSlotLiveSfc[id] != null ? String(this.intelSlotLiveSfc[id]) : "";
      if (!text.trim()) {
        window.alert("没有可保存的代码。");
        return;
      }
      const safeId = id.replace(/[^\w-]+/g, "-");
      const blob = new Blob([text], { type: "text/plain;charset=utf-8" });
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `ai-intel-${safeId}.vue`;
      a.rel = "noopener";
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
    },
    onIntelGenLiveSfc(payload) {
      const p = payload && typeof payload === "object" ? payload : {};
      const id = p.panelId != null ? String(p.panelId).trim() : "";
      if (!id) return;
      const sfc = p.sfc != null ? String(p.sfc) : "";
      this.$set(this.intelSlotLiveSfc, id, sfc);
    },
    onIntelGenPromptSave(payload) {
      const p = payload && typeof payload === "object" ? payload : {};
      const id = p.panelId != null ? String(p.panelId).trim() : "";
      if (!id) return;
      const prompt = p.prompt != null ? String(p.prompt) : "";
      this.$set(this.intelGenPromptByPanelId, id, prompt);
    },
    cancelAdd() {
      this.pluginUrlSetDialog.visible = false;
      this.pluginUrlSetDialog.prefetching = false;
      this.pluginUrlDeleteDialog.visible = false;
      this.addingCell = null;
      this.addMenuStep = "kind";
      /* 保留 pluginsList / pluginsError / pluginsLoading，避免关闭弹窗或收起菜单时列表被清空 */
    }
  }
};
</script>

<style scoped>
.lower-tabs-root {
  position: relative;
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.panel-grid-viewport {
  position: relative;
  flex: 1;
  min-height: 0;
  overflow-x: hidden;
  overflow-y: auto;
  /* 与面板内容留出间隙，滚动条落在右侧留白内 */
  padding-right: 10px;
  box-sizing: border-box;
  scrollbar-width: thin;
  scrollbar-color: rgba(200, 218, 235, 0.2) transparent;
  color-scheme: dark;
}

/* WebKit：细、淡；悬停仅略提高不透明度，色相与非悬停一致 */
.panel-grid-viewport::-webkit-scrollbar {
  width: 5px;
  height: 5px;
}
.panel-grid-viewport::-webkit-scrollbar-track {
  background: transparent;
}
.panel-grid-viewport::-webkit-scrollbar-thumb {
  border-radius: 3px;
  border: 1px solid transparent;
  background-clip: padding-box;
  background-color: rgba(200, 218, 235, 0.18);
}
.panel-grid-viewport::-webkit-scrollbar-thumb:hover {
  /* 与非悬停同色，仅略减透明（略实一点），避免发灰白 */
  background-color: rgba(200, 218, 235, 0.26);
}

/* 统一右侧面板内所有子组件滚动条风格，与最外层保持一致 */
.lower-tabs-root :deep(*) {
  scrollbar-width: thin;
  scrollbar-color: rgba(200, 218, 235, 0.18) transparent;
}

.lower-tabs-root :deep(*::-webkit-scrollbar) {
  width: 5px;
  height: 5px;
}

.lower-tabs-root :deep(*::-webkit-scrollbar-track) {
  background: transparent;
}

.lower-tabs-root :deep(*::-webkit-scrollbar-thumb) {
  border-radius: 3px;
  border: 1px solid transparent;
  background-clip: padding-box;
  background-color: rgba(200, 218, 235, 0.18);
}

.lower-tabs-root :deep(*::-webkit-scrollbar-thumb:hover) {
  background-color: rgba(200, 218, 235, 0.26);
}

.panel-grid {
  position: relative;
  width: 100%;
  box-sizing: border-box;
  display: grid;
  /* 列数、行数由内联 gridTemplate* 与 gridCols/gridRows 同步 */
  gap: 10px;
}

.panel-grid-modal-layer {
  position: absolute;
  inset: 0;
  z-index: 1200;
  pointer-events: none;
}

.panel-grid-modal-layer > * {
  pointer-events: auto;
}

.grid-item {
  position: relative;
  min-height: 0;
  transition: transform 0.12s ease, box-shadow 0.14s ease, filter 0.14s ease;
  will-change: transform;
}

.grid-item.is-dragging {
  box-shadow:
    0 0 0 1px rgba(125, 223, 255, 0.72),
    0 12px 28px rgba(49, 123, 196, 0.34),
    0 4px 10px rgba(62, 186, 255, 0.25);
  filter: brightness(1.06) saturate(1.06);
}

.grid-item.is-swap-target {
  box-shadow: inset 0 0 0 1px rgba(125, 223, 255, 0.8), inset 0 0 14px rgba(95, 198, 255, 0.28);
}

.grid-item-toolbar {
  position: absolute;
  right: 8px;
  top: 8px;
  z-index: 3;
  display: flex;
  gap: 6px;
}

.grid-item-toolbar--intel-slot {
  max-width: calc(100% - 16px);
  flex-wrap: wrap;
  justify-content: flex-end;
  align-items: center;
  gap: 6px;
}

.op-btn-refresh,
.op-btn-close,
.op-btn-setting {
  width: 19px;
  height: 19px;
  border: 1px solid rgba(107, 230, 255, 0.75);
  background: rgba(3, 28, 66, 0.9);
  border-radius: 50%;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  transition:
    box-shadow 0.18s ease,
    border-color 0.18s ease,
    background 0.18s ease,
    transform 0.18s ease;
}

.op-btn-refresh:hover,
.op-btn-close:hover,
.op-btn-setting:hover {
  border-color: rgba(150, 228, 255, 0.95);
  background: rgba(14, 52, 102, 0.98);
  box-shadow:
    0 0 0 1px rgba(125, 223, 255, 0.35),
    0 0 10px rgba(97, 202, 255, 0.42);
  transform: scale(1.06);
}

.op-btn-refresh:active,
.op-btn-close:active,
.op-btn-setting:active {
  transform: scale(1.02);
}

.op-btn-refresh:focus-visible,
.op-btn-close:focus-visible,
.op-btn-setting:focus-visible {
  outline: none;
  box-shadow:
    0 0 0 2px rgba(62, 186, 255, 0.55),
    0 0 12px rgba(97, 202, 255, 0.38);
}

.op-btn-icon {
  width: 11px;
  height: 11px;
  display: block;
  pointer-events: none;
  filter: brightness(0) invert(1);
  opacity: 0.96;
}

:deep(.module-title) {
  cursor: grab;
  user-select: none;
}

.grid-item.is-dragging :deep(.module-title) {
  cursor: grabbing;
}

.resize-handle {
  position: absolute;
  z-index: 5;
  opacity: 0;
  transition: opacity 0.18s ease, filter 0.18s ease;
  pointer-events: auto;
}

.resize-handle.is-enabled:hover,
.resize-handle.is-enabled.is-active {
  opacity: 1;
}

.resize-handle-top,
.resize-handle-bottom {
  left: 10px;
  right: 10px;
  height: 14px;
  cursor: ns-resize;
}

.resize-handle-top {
  top: 0;
}

.resize-handle-bottom {
  bottom: 0;
}

.resize-handle-left,
.resize-handle-right {
  top: 10px;
  bottom: 10px;
  width: 14px;
  cursor: ew-resize;
}

.resize-handle-left {
  left: 0;
}

.resize-handle-right {
  right: 0;
}

.resize-handle.is-enabled::after {
  content: "";
  position: absolute;
  inset: 0;
  border-radius: 8px;
}

.resize-handle-top.is-enabled::after,
.resize-handle-bottom.is-enabled::after {
  inset: 4px 0;
  background: linear-gradient(
    90deg,
    rgba(80, 180, 255, 0) 0%,
    rgba(98, 206, 255, 0.62) 18%,
    rgba(121, 223, 255, 0.7) 50%,
    rgba(98, 206, 255, 0.62) 82%,
    rgba(80, 180, 255, 0) 100%
  );
  box-shadow: 0 0 8px rgba(97, 202, 255, 0.45), inset 0 0 5px rgba(160, 235, 255, 0.55);
}

.resize-handle-left.is-enabled::after,
.resize-handle-right.is-enabled::after {
  inset: 0 4px;
  background: linear-gradient(
    180deg,
    rgba(80, 180, 255, 0) 0%,
    rgba(98, 206, 255, 0.62) 18%,
    rgba(121, 223, 255, 0.7) 50%,
    rgba(98, 206, 255, 0.62) 82%,
    rgba(80, 180, 255, 0) 100%
  );
  box-shadow: 0 0 8px rgba(97, 202, 255, 0.45), inset 0 0 5px rgba(160, 235, 255, 0.55);
}

.grid-empty-cell {
  min-height: 0;
  border: 1px dashed rgba(94, 179, 255, 0.45);
  border-radius: 8px;
  background: rgba(4, 24, 55, 0.25);
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 6px;
}

.grid-empty-cell.is-drop-target {
  border-color: rgba(125, 223, 255, 0.95);
  background: rgba(8, 45, 88, 0.55);
  box-shadow:
    inset 0 0 0 1px rgba(125, 223, 255, 0.5),
    0 0 12px rgba(62, 186, 255, 0.22);
}

.grid-empty-cell--picker-inline {
  align-items: stretch;
  justify-content: flex-start;
  padding: 8px;
  overflow: visible;
}

.empty-cell-add-inline {
  flex: 1 1 auto;
  min-height: 0;
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 0;
  box-sizing: border-box;
}

.empty-cell-add-inline__body {
  position: relative;
  flex: 1 1 auto;
  min-height: 0;
  width: 100%;
  display: flex;
  flex-direction: column;
}

.empty-cell-add-inline__body--scroll {
  min-height: 0;
}

.add-menu-list-inline {
  flex: 1 1 auto;
  min-height: 0;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px;
  overflow-y: auto;
  overflow-x: hidden;
  padding-right: 2px;
  scrollbar-width: thin;
  scrollbar-color: rgba(200, 218, 235, 0.28) transparent;
}

.add-menu-list-inline--with-back-fab {
  padding-bottom: 32px;
}

.add-menu-back-fab {
  position: absolute;
  right: 2px;
  bottom: 2px;
  z-index: 3;
  width: 26px;
  height: 26px;
  padding: 0;
  margin: 0;
  border-radius: 6px;
  font-size: 13px;
  line-height: 1;
  border: 1px solid rgba(120, 200, 255, 0.48);
  background: rgba(6, 26, 56, 0.94);
  color: rgba(200, 230, 255, 0.95);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.35);
  transition:
    border-color 0.15s ease,
    background 0.15s ease,
    box-shadow 0.15s ease;
}

.add-menu-back-fab:hover {
  border-color: rgba(160, 220, 255, 0.65);
  background: rgba(18, 48, 88, 0.95);
  box-shadow: 0 0 12px rgba(62, 186, 255, 0.22);
}

.add-menu-list-inline--scroll {
  grid-template-columns: 1fr;
}

.add-menu-list-inline::-webkit-scrollbar {
  width: 5px;
}

.add-menu-list-inline::-webkit-scrollbar-track {
  background: transparent;
}

.add-menu-list-inline::-webkit-scrollbar-thumb {
  border-radius: 3px;
  background-color: rgba(200, 218, 235, 0.24);
}

.add-menu-list-inline::-webkit-scrollbar-thumb:hover {
  background-color: rgba(200, 218, 235, 0.36);
}

.empty-cell-popover-anchor {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  min-height: 0;
}

.empty-cell-popover {
  position: absolute;
  left: calc(50% + 20px);
  top: calc(50% + 2px);
  transform: translateY(-50%);
  z-index: 25;
  min-width: 156px;
  max-width: min(280px, calc(100vw - 24px));
  max-height: min(200px, 40vh);
  padding: 8px;
  border-radius: 10px;
  border: 1px solid rgba(110, 200, 255, 0.52);
  background: linear-gradient(165deg, rgba(10, 36, 72, 0.98), rgba(5, 22, 48, 0.99));
  box-shadow:
    0 10px 32px rgba(0, 0, 0, 0.42),
    0 0 0 1px rgba(62, 186, 255, 0.12);
  display: flex;
  flex-direction: column;
  gap: 6px;
  box-sizing: border-box;
}

.empty-cell-popover-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-height: 0;
}

.empty-cell-popover-list--kind {
  flex: 0 0 auto;
}

.empty-cell-icon-btn {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  border: 1px solid rgba(107, 230, 255, 0.65);
  background: rgba(3, 28, 66, 0.8);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  flex-shrink: 0;
}

.empty-cell-icon-btn:hover {
  box-shadow: 0 0 10px rgba(97, 202, 255, 0.35);
}

.empty-cell-icon-btn__img {
  width: 20px;
  height: 20px;
  display: block;
  pointer-events: none;
  filter: brightness(0) invert(1);
  opacity: 0.92;
}

.add-type-btn--compact {
  padding: 7px 9px;
  font-size: 11px;
  border-radius: 7px;
}

.add-menu-status {
  font-size: 11px;
  line-height: 1.4;
  color: rgba(180, 210, 240, 0.78);
  padding: 6px 4px;
  text-align: center;
}

.add-menu-status--err {
  color: #ffb4b4;
}

.add-type-btn--plugin {
  flex-direction: column;
  align-items: stretch;
  gap: 2px;
}

.add-type-btn__sub {
  font-size: 10px;
  color: rgba(160, 200, 235, 0.68);
  font-family: ui-monospace, Consolas, Menlo, monospace;
}

.add-type-btn {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  width: 100%;
  text-align: left;
  border: 1px solid rgba(94, 179, 255, 0.65);
  background: rgba(3, 25, 58, 0.9);
  color: #cdeaff;
  border-radius: 6px;
  font-size: 12px;
  padding: 6px 8px;
  cursor: pointer;
}

.add-type-btn__label {
  flex: 1;
  min-width: 0;
}

.add-type-btn__emoji {
  flex-shrink: 0;
  font-size: 14px;
  line-height: 1;
  opacity: 0.92;
}

/* —— 自定义组件列表（表格）与 URL 映射弹窗（仅在本格 `.plugin-picker-scope` 内） —— */
.plugin-picker-scope {
  position: relative;
  flex: 1 1 auto;
  min-height: 0;
  width: 100%;
  display: flex;
  flex-direction: column;
  overflow: visible;
}

.plugin-picker-scope > .empty-cell-add-inline__body--scroll {
  flex: 1 1 auto;
  min-height: 0;
}

.plugin-url-toast {
  margin: 0 auto;
  max-width: min(420px, calc(100% - 16px));
  padding: 8px 14px;
  border-radius: 8px;
  font-size: 12px;
  font-weight: 600;
  text-align: center;
  color: #e8fff4;
  background: linear-gradient(135deg, rgba(24, 120, 72, 0.95), rgba(12, 72, 48, 0.98));
  border: 1px solid rgba(120, 255, 190, 0.45);
  box-shadow: 0 4px 18px rgba(0, 0, 0, 0.35);
  pointer-events: none;
}

.plugin-url-toast--scoped {
  position: absolute;
  top: 6px;
  left: 8px;
  right: 8px;
  z-index: 36;
  width: fit-content;
  max-width: calc(100% - 16px);
}

.plugin-url-toast-fade-enter-active,
.plugin-url-toast-fade-leave-active {
  transition: opacity 0.22s ease, transform 0.22s ease;
}
.plugin-url-toast-fade-enter,
.plugin-url-toast-fade-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}

.plugin-url-modal-layer {
  position: absolute;
  inset: 0;
  z-index: 34;
  background: rgba(2, 8, 20, 0.58);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 10px 8px;
  box-sizing: border-box;
}

.plugin-url-modal-layer--center {
  align-items: center;
  justify-content: center;
}

.plugin-url-dialog {
  position: relative;
  width: min(400px, 100%);
  max-width: 100%;
  border-radius: 10px;
  border: 1px solid rgba(110, 200, 255, 0.5);
  background: linear-gradient(165deg, rgba(10, 36, 72, 0.99), rgba(5, 20, 44, 0.99));
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.45);
  padding: 14px 16px 16px;
  color: rgba(210, 235, 255, 0.95);
}

.plugin-url-dialog__title {
  margin: 0 0 12px;
  font-size: 14px;
  font-weight: 700;
  color: #b8e8ff;
}

.plugin-url-dialog__hint {
  margin: -4px 0 10px;
  font-size: 11px;
  color: rgba(170, 200, 230, 0.78);
}

.plugin-url-dialog__label {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 14px;
}

.plugin-url-dialog__label-text {
  font-size: 11px;
  color: rgba(180, 210, 235, 0.85);
}

.plugin-url-dialog__input {
  width: 100%;
  box-sizing: border-box;
  padding: 8px 10px;
  border-radius: 6px;
  border: 1px solid rgba(94, 179, 255, 0.45);
  background: rgba(4, 18, 42, 0.85);
  color: #e8f4ff;
  font-size: 12px;
}

.plugin-url-dialog__input:focus {
  outline: none;
  border-color: rgba(160, 210, 255, 0.65);
}

.plugin-url-dialog__input:disabled {
  opacity: 0.65;
  cursor: wait;
}

.plugin-url-dialog__footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 4px;
}

.plugin-url-dialog__btn {
  min-width: 72px;
  padding: 6px 14px;
  border-radius: 6px;
  font-size: 12px;
  cursor: pointer;
  border: 1px solid transparent;
}

.plugin-url-dialog__btn--ghost {
  background: rgba(20, 40, 70, 0.6);
  border-color: rgba(120, 180, 230, 0.4);
  color: rgba(210, 230, 255, 0.9);
}

.plugin-url-dialog__btn--primary {
  background: linear-gradient(165deg, rgba(30, 100, 180, 0.95), rgba(16, 72, 140, 0.98));
  border-color: rgba(120, 200, 255, 0.55);
  color: #fff;
}

.plugin-url-dialog__btn--danger {
  background: linear-gradient(165deg, rgba(160, 48, 48, 0.95), rgba(110, 28, 32, 0.98));
  border-color: rgba(255, 140, 120, 0.45);
  color: #fff;
}

.plugin-url-dialog__btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.plugin-url-dialog__confirm-text {
  margin: 0 0 14px;
  font-size: 12px;
  line-height: 1.5;
  color: rgba(220, 230, 245, 0.9);
}

.add-menu-list-inline--plugin-table-wrap {
  display: block;
  padding-right: 4px;
}

.plugin-picker-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 11px;
  table-layout: fixed;
}

.plugin-picker-table__th {
  text-align: left;
  padding: 6px 8px;
  color: rgba(160, 200, 235, 0.88);
  font-weight: 700;
  border-bottom: 1px solid rgba(62, 140, 210, 0.45);
  background: rgba(4, 22, 48, 0.65);
}

.plugin-picker-table__th--idx {
  width: 36px;
  text-align: center;
}

.plugin-picker-table__th--actions {
  width: 76px;
  text-align: center;
}

.plugin-picker-table__row {
  cursor: pointer;
  transition: background 0.12s ease;
}

.plugin-picker-table__row:hover {
  background: rgba(20, 60, 110, 0.35);
}

.plugin-picker-table__td {
  padding: 6px 8px;
  vertical-align: middle;
  border-bottom: 1px solid rgba(50, 100, 150, 0.28);
  color: rgba(210, 235, 255, 0.92);
}

.plugin-picker-table__td--idx {
  text-align: center;
  color: rgba(170, 200, 230, 0.75);
  font-variant-numeric: tabular-nums;
}

.plugin-picker-table__td--id {
  word-break: break-all;
}

.plugin-picker-table__id-text {
  font-family: ui-monospace, Consolas, Menlo, monospace;
  font-size: 11px;
  color: #d8f0ff;
}

.plugin-picker-table__file {
  display: block;
  margin-top: 2px;
  font-size: 10px;
  color: rgba(150, 190, 230, 0.65);
}

.plugin-picker-table__td--actions {
  text-align: center;
}

.plugin-url-action-btns {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
}

.plugin-url-icon-btn {
  width: 28px;
  height: 28px;
  padding: 0;
  border-radius: 6px;
  border: 1px solid rgba(100, 180, 240, 0.45);
  background: rgba(6, 28, 58, 0.85);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.plugin-url-icon-btn:hover {
  border-color: rgba(140, 210, 255, 0.65);
  box-shadow: 0 0 8px rgba(62, 186, 255, 0.2);
}

.plugin-url-icon-btn__img {
  width: 14px;
  height: 14px;
  display: block;
  pointer-events: none;
  filter: brightness(0) invert(1);
  opacity: 0.9;
}
</style>
