<template>
  <section class="card app-panel ai-intel-live">
    <div class="module-title ai-intel-live__title">
      <div class="module-title-left">
        <span class="module-title-emoji" aria-hidden="true">{{ titleEmoji }}</span>
        情报组件预览
      </div>
    </div>
    <div class="ai-intel-live__body">
      <div v-if="!hasSource" class="ai-intel-live__placeholder">
        在「情报组件智能生成」对话框中输入需求并生成代码，功能效果将在此格内展示。
      </div>
      <div v-else-if="compileError" class="ai-intel-live__error" role="alert">
        {{ compileError }}
      </div>
      <div v-else class="ai-intel-live__mount-wrap">
        <component :is="previewCtor" :key="previewKey" class="ai-intel-live__mount" />
      </div>
    </div>
  </section>
</template>

<script>
/**
 * 情报组件预览格（网格键 ai-intel-slot）。
 * LowerTabs 注入 liveSfc（.vue 全文），经 compilePastedSfc 编译后动态挂载；与顶栏智能组件工厂解耦。
 */
import Vue from "vue";
import { PANEL_TITLE_EMOJI } from "../intelComponents/js/panelTitleEmoji.js";
import { compilePastedSfc } from "../../utils/compilePastedSfc.js";

export default {
  name: "AiIntelLiveMountPanel",
  props: {
    panelId: { type: String, default: "" },
    liveSfc: { type: String, default: "" }
  },
  data() {
    return {
      titleEmoji: PANEL_TITLE_EMOJI["ai-intel-slot"],
      previewCtor: null,
      previewKey: 0,
      compileError: "",
      _debounce: null
    };
  },
  computed: {
    hasSource() {
      return !!(this.liveSfc && String(this.liveSfc).trim());
    }
  },
  watch: {
    liveSfc: {
      immediate: true,
      handler() {
        this.scheduleCompile();
      }
    }
  },
  beforeDestroy() {
    clearTimeout(this._debounce);
    this.removeInjectedStyles();
    this.previewCtor = null;
  },
  methods: {
    /** 每个预览格独立注入 <style>，避免多格之间样式串扰 */
    styleMarker() {
      const id = (this.panelId || "slot").replace(/[^\w-]/g, "");
      return `ai-intel-live-${id}`;
    },
    removeInjectedStyles() {
      const sel = `style[data-ai-intel-live="${this.styleMarker()}"]`;
      const nodes = document.head.querySelectorAll(sel);
      nodes.forEach((n) => n.parentNode && n.parentNode.removeChild(n));
    },
    injectStyles(chunks) {
      this.removeInjectedStyles();
      const list = Array.isArray(chunks) ? chunks.filter(Boolean) : [];
      if (!list.length) return;
      const el = document.createElement("style");
      el.setAttribute("data-ai-intel-live", this.styleMarker());
      el.textContent = list.join("\n\n");
      document.head.appendChild(el);
    },
    scheduleCompile() {
      clearTimeout(this._debounce);
      this._debounce = setTimeout(() => {
        this._debounce = null;
        this.compileNow();
      }, 320);
    },
    compileNow() {
      const src = (this.liveSfc || "").trim();
      if (!src) {
        this.compileError = "";
        this.previewCtor = null;
        this.removeInjectedStyles();
        return;
      }
      this.compileError = "";
      try {
        const { Component, styles } = compilePastedSfc(Vue, this.liveSfc);
        this.injectStyles(styles);
        this.previewCtor = Component;
        this.previewKey += 1;
      } catch (e) {
        this.compileError = (e && e.message) || String(e);
        this.previewCtor = null;
      }
    }
  }
};
</script>

<style scoped>
.app-panel {
  height: 100%;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.ai-intel-live {
  box-shadow:
    inset 0 0 0 1px rgba(98, 206, 255, 0.35),
    0 0 14px rgba(62, 186, 255, 0.1);
  background: linear-gradient(165deg, rgba(10, 28, 52, 0.92), rgba(6, 20, 44, 0.9));
}

.ai-intel-live__title {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 8px;
  flex-wrap: wrap;
}

.module-title-left {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.ai-intel-live__body {
  flex: 1;
  min-height: 0;
  margin-top: 6px;
  border-radius: 6px;
  border: 1px dashed rgba(94, 179, 255, 0.28);
  background: rgba(4, 18, 42, 0.35);
  overflow: auto;
}

.ai-intel-live__placeholder {
  padding: 16px 12px;
  font-size: 11px;
  line-height: 1.5;
  color: rgba(180, 205, 230, 0.65);
  text-align: center;
}

.ai-intel-live__error {
  padding: 12px;
  font-size: 11px;
  line-height: 1.45;
  color: #ffb4b4;
}

.ai-intel-live__mount-wrap {
  padding: 6px;
  min-height: 48px;
}

.ai-intel-live__mount {
  min-height: 32px;
}
</style>
