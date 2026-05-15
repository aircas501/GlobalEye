<template>
  <div class="settings-module settings-module--intel">
    <div class="settings-module__title">情报面板</div>
    <p class="intel-panel-hint">
      配置右侧情报栏网格：列数 1～2，行数 2～10；应用后立即生效。刷新页面后恢复默认布局。
    </p>
    <div class="intel-panel-dims">
      <label class="intel-dim-field">
        <span class="intel-dim-field__label">列数（宽）</span>
        <input
          v-model.number="intelDraftCols"
          class="intel-dim-input"
          type="number"
          :min="INTEL_PANEL_COL_MIN"
          :max="INTEL_PANEL_COL_MAX"
          @change="onIntelGridDimChange"
        />
      </label>
      <label class="intel-dim-field">
        <span class="intel-dim-field__label">行数（高）</span>
        <input
          v-model.number="intelDraftRows"
          class="intel-dim-input"
          type="number"
          :min="INTEL_PANEL_ROW_MIN"
          :max="INTEL_PANEL_ROW_MAX"
          @change="onIntelGridDimChange"
        />
      </label>
    </div>
    <div class="intel-panel-meta">
      格子总数：<strong>{{ intelCellCount }}</strong>
    </div>
    <div class="intel-panel-cell-list panel-scrollbar">
      <div
        v-for="(key, index) in intelDraftCells"
        :key="`cell-${index}`"
        class="intel-cell-row"
      >
        <span class="intel-cell-row__pos" :title="`第 ${cellRow(index)} 行，第 ${cellCol(index)} 列`">
          {{ cellRow(index) }}×{{ cellCol(index) }}
        </span>
        <select v-model="intelDraftCells[index]" class="intel-cell-select">
          <option value="">（空格）</option>
          <option
            v-for="opt in intelPanelMenuItemsForSettings"
            :key="opt.key"
            :value="opt.key"
          >
            {{ opt.label }}
          </option>
        </select>
      </div>
    </div>
  </div>
</template>

<script>
import {
  getDefaultIntelConfig,
  panelsToCells,
  buildPanelsFromCells,
  clampIntelCols,
  clampIntelRows,
  normalizeStoredPanels,
  INTEL_PANEL_COL_MIN,
  INTEL_PANEL_COL_MAX,
  INTEL_PANEL_ROW_MIN,
  INTEL_PANEL_ROW_MAX
} from "../../../utils/intelPanelConfig.js";
import { INTEL_PANEL_MENU_ITEMS } from "../../intelComponents/js/intelPanelMenuItems.js";

export default {
  name: "SettingsIntelPanelModule",
  props: {
    /** 与系统设置弹窗 visible 同步：打开时重置草稿 */
    dialogOpen: {
      type: Boolean,
      default: false
    },
    intelInitialConfig: {
      type: Object,
      default: null
    }
  },
  data() {
    const d = getDefaultIntelConfig();
    const cols = clampIntelCols(d.cols);
    const rows = clampIntelRows(d.rows);
    return {
      INTEL_PANEL_COL_MIN,
      INTEL_PANEL_COL_MAX,
      INTEL_PANEL_ROW_MIN,
      INTEL_PANEL_ROW_MAX,
      intelDraftCols: cols,
      intelDraftRows: rows,
      intelDraftCells: Array(cols * rows).fill(""),
      _intelPrevCols: cols,
      _intelPrevRows: rows
    };
  },
  computed: {
    intelPanelMenuItemsForSettings() {
      const base = INTEL_PANEL_MENU_ITEMS.map((it) => ({ ...it }));
      const seen = new Set(base.map((b) => b.key));
      const cells = Array.isArray(this.intelDraftCells) ? this.intelDraftCells : [];
      for (let i = 0; i < cells.length; i += 1) {
        const raw = cells[i] != null ? String(cells[i]).trim() : "";
        if (!raw.startsWith("personal-plugin|")) continue;
        if (seen.has(raw)) continue;
        seen.add(raw);
        const pid = raw.slice("personal-plugin|".length).trim() || "?";
        base.push({ key: raw, label: `个性化：${pid}` });
      }
      return base;
    },
    intelCellCount() {
      const c = clampIntelCols(this.intelDraftCols);
      const r = clampIntelRows(this.intelDraftRows);
      return Math.max(0, c * r);
    }
  },
  watch: {
    dialogOpen: {
      immediate: true,
      handler(v) {
        if (!v) return;
        this.resetDraftFromProps();
      }
    },
    intelCellCount: {
      handler(n) {
        this.$emit("intel-summary", { cellCount: n });
      },
      immediate: true
    }
  },
  methods: {
    resetDraftFromProps() {
      const base = this.resolveIntelBaseConfig();
      const cols = clampIntelCols(base.cols);
      const rows = clampIntelRows(base.rows);
      const panels = normalizeStoredPanels(cols, rows, base.panels);
      this.intelDraftCols = cols;
      this.intelDraftRows = rows;
      const cells = panelsToCells(cols, rows, panels);
      this.intelDraftCells = cells;
      this._intelPrevCols = cols;
      this._intelPrevRows = rows;
    },
    resolveIntelBaseConfig() {
      const snap = this.intelInitialConfig;
      if (
        snap &&
        typeof snap === "object" &&
        Array.isArray(snap.panels) &&
        Number.isFinite(Number(snap.cols)) &&
        Number.isFinite(Number(snap.rows))
      ) {
        const cols = clampIntelCols(snap.cols);
        const rows = clampIntelRows(snap.rows);
        return { cols, rows, panels: snap.panels };
      }
      return getDefaultIntelConfig();
    },
    cellCol(index) {
      const c = clampIntelCols(this.intelDraftCols);
      return (index % c) + 1;
    },
    cellRow(index) {
      const c = clampIntelCols(this.intelDraftCols);
      return Math.floor(index / c) + 1;
    },
    onIntelGridDimChange() {
      const prevC = clampIntelCols(this._intelPrevCols);
      const prevR = clampIntelRows(this._intelPrevRows);
      const oldCells = Array.isArray(this.intelDraftCells) ? this.intelDraftCells.slice() : [];
      const c = clampIntelCols(this.intelDraftCols);
      const r = clampIntelRows(this.intelDraftRows);
      this.intelDraftCols = c;
      this.intelDraftRows = r;
      const next = Array(c * r).fill("");
      const maxR = Math.min(r, prevR);
      const maxC = Math.min(c, prevC);
      for (let y = 0; y < maxR; y += 1) {
        for (let x = 0; x < maxC; x += 1) {
          const oi = y * prevC + x;
          const ni = y * c + x;
          next[ni] = oldCells[oi] != null ? String(oldCells[oi]) : "";
        }
      }
      this.intelDraftCells = next;
      this._intelPrevCols = c;
      this._intelPrevRows = r;
    },
    /** 供父级「应用」时调用 */
    getIntelApplyPayload() {
      const cols = clampIntelCols(this.intelDraftCols);
      const rows = clampIntelRows(this.intelDraftRows);
      this.intelDraftCols = cols;
      this.intelDraftRows = rows;
      let cells = Array.isArray(this.intelDraftCells) ? this.intelDraftCells.slice(0, cols * rows) : [];
      while (cells.length < cols * rows) cells.push("");
      const panels = buildPanelsFromCells(cols, rows, cells);
      return { cols, rows, panels };
    }
  }
};
</script>

<style scoped>
.settings-module {
  min-height: 0;
  padding: 16px 18px 12px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.settings-module__title {
  font-size: 14px;
  font-weight: 700;
  color: #9fdfff;
}

.settings-module--intel {
  padding-bottom: 4px;
}

.intel-panel-hint {
  margin: 0;
  font-size: 12px;
  line-height: 1.45;
  color: rgba(200, 230, 255, 0.72);
}

.intel-panel-dims {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 20px;
  align-items: flex-end;
}

.intel-dim-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
  color: rgba(210, 236, 255, 0.85);
}

.intel-dim-field__label {
  font-weight: 600;
  color: #9fdfff;
}

.intel-dim-input {
  width: 72px;
  height: 30px;
  padding: 0 8px;
  border-radius: 6px;
  border: 1px solid rgba(94, 179, 255, 0.45);
  background: rgba(4, 18, 40, 0.95);
  color: #e8f4ff;
  font-size: 14px;
}

.intel-panel-meta {
  font-size: 13px;
  color: rgba(210, 236, 255, 0.78);
}

.intel-panel-meta strong {
  color: #b8efff;
  font-weight: 700;
}

.intel-panel-cell-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding-right: 4px;
}

.intel-cell-row {
  display: grid;
  grid-template-columns: 52px minmax(0, 1fr);
  gap: 8px;
  align-items: center;
}

.intel-cell-row__pos {
  font-size: 11px;
  font-weight: 700;
  color: rgba(140, 210, 255, 0.85);
  font-variant-numeric: tabular-nums;
}

.intel-cell-select {
  width: 100%;
  min-width: 0;
  height: 30px;
  padding: 0 8px;
  border-radius: 6px;
  border: 1px solid rgba(94, 179, 255, 0.4);
  background: rgba(6, 26, 52, 0.95);
  color: #dceeff;
  font-size: 13px;
}
</style>
