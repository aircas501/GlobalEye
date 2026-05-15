<template>
  <div
    v-if="visible"
    class="settings-dialog-mask"
    role="presentation"
    @click.self="$emit('close')"
  >
    <section class="settings-dialog" role="dialog" aria-modal="true" aria-label="系统设置">
      <header class="settings-dialog__head">
        <div class="settings-dialog__title">系统设置</div>
        <button
          type="button"
          class="settings-dialog__close"
          aria-label="关闭设置弹窗"
          @click="$emit('close')"
        >
          ×
        </button>
      </header>

      <div class="settings-dialog__body panel-scrollbar">
        <div class="settings-tab-panel" role="region" aria-label="情报面板设置">
          <SettingsIntelPanelModule
            ref="intelModule"
            :dialog-open="visible"
            :intel-initial-config="intelInitialConfig"
            @intel-summary="onIntelSummary"
          />
        </div>
      </div>

      <footer class="settings-dialog__foot">
        <div class="settings-dialog__stats">
          当前页：情报格 <strong>{{ intelFooterCellCount }}</strong>
        </div>
        <button type="button" class="settings-dialog__apply" @click="onApply">
          应用
        </button>
      </footer>
    </section>
  </div>
</template>

<script>
import SettingsIntelPanelModule from "./settings-modules/SettingsIntelPanelModule.vue";
import { DEFAULT_INTEL_GRID } from "../../utils/intelPanelConfig.js";

export default {
  name: "SystemSettingsDialog",
  components: {
    SettingsIntelPanelModule
  },
  props: {
    visible: {
      type: Boolean,
      default: false
    },
    intelInitialConfig: {
      type: Object,
      default: null
    }
  },
  data() {
    return {
      intelFooterCellCount: 0
    };
  },
  methods: {
    onIntelSummary({ cellCount }) {
      const n = Number(cellCount);
      this.intelFooterCellCount = Number.isFinite(n) ? n : 0;
    },
    onApply() {
      const intelRef = this.$refs.intelModule;
      const intelPanel =
        intelRef && typeof intelRef.getIntelApplyPayload === "function"
          ? intelRef.getIntelApplyPayload()
          : null;
      const fallback = {
        cols: DEFAULT_INTEL_GRID.cols,
        rows: DEFAULT_INTEL_GRID.rows,
        panels: []
      };
      this.$emit("apply", {
        intelPanel: intelPanel && typeof intelPanel === "object" ? intelPanel : fallback
      });
    }
  }
};
</script>

<style scoped>
.settings-dialog-mask {
  position: fixed;
  inset: 0;
  z-index: 11000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  background: rgba(2, 10, 22, 0.68);
  backdrop-filter: blur(2px);
}

.settings-dialog {
  width: min(860px, 90vw);
  height: min(76vh, 760px);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border-radius: 12px;
  border: 1px solid rgba(80, 170, 230, 0.48);
  background: linear-gradient(180deg, rgba(7, 29, 58, 0.98), rgba(3, 17, 36, 0.98));
  box-shadow:
    0 14px 36px rgba(0, 0, 0, 0.48),
    0 0 0 1px rgba(0, 0, 0, 0.26);
}

.settings-dialog__head {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 18px 10px;
  border-bottom: 1px solid rgba(70, 145, 205, 0.22);
}

.settings-dialog__title {
  font-size: 16px;
  font-weight: 800;
  color: #aee9ff;
  letter-spacing: 0.04em;
}

.settings-dialog__close {
  width: 30px;
  height: 30px;
  border: none;
  border-radius: 6px;
  background: rgba(14, 48, 82, 0.65);
  color: #b7ddf2;
  font-size: 20px;
  line-height: 1;
  cursor: pointer;
}

.settings-dialog__close:hover {
  background: rgba(22, 68, 112, 0.82);
  color: #e8f6ff;
}

.settings-dialog__body {
  flex: 1;
  min-height: 0;
  overflow-x: hidden;
  overflow-y: auto;
}

.settings-tab-panel {
  min-height: 0;
  padding: 14px 18px 8px;
}

.settings-dialog__foot {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  padding: 12px 18px 16px;
  border-top: 1px solid rgba(70, 145, 205, 0.24);
  flex-shrink: 0;
}

.settings-dialog__stats {
  color: rgba(210, 236, 255, 0.72);
  font-size: 13px;
}

.settings-dialog__stats strong {
  color: #b8efff;
  font-weight: 700;
}

.settings-dialog__apply {
  min-width: 92px;
  height: 34px;
  border: 1px solid rgba(106, 202, 255, 0.52);
  border-radius: 6px;
  background: linear-gradient(135deg, rgba(20, 116, 196, 0.95), rgba(11, 61, 125, 0.95));
  color: #e8f8ff;
  font-size: 14px;
  cursor: pointer;
}

.settings-dialog__apply:hover {
  filter: brightness(1.08);
}
</style>
