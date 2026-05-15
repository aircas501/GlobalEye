<template>
  <section class="card app-panel personal-plugin-live">
    <div class="module-title personal-plugin-live__title">
      <div class="module-title-left">
        <span class="module-title-emoji" aria-hidden="true">{{ titleEmoji }}</span>
        <span class="personal-plugin-live__name" :title="titleFull">{{ panelTitle }}</span>
      </div>
    </div>
    <div class="personal-plugin-live__body panel-scrollbar">
      <div v-if="!pluginIdTrim" class="personal-plugin-live__placeholder">未配置组件 ID</div>
      <div v-else-if="loading" class="personal-plugin-live__placeholder">正在加载组件…</div>
      <div v-else-if="loadError" class="personal-plugin-live__error" role="alert">{{ loadError }}</div>
      <div v-else-if="compileError" class="personal-plugin-live__error" role="alert">{{ compileError }}</div>
      <div v-else class="personal-plugin-live__mount-wrap">
        <component
          :is="previewCtor"
          :key="previewKey"
          class="personal-plugin-live__mount"
          :context="pluginContext"
        />
      </div>
    </div>
  </section>
</template>

<script>
/**
 * 个性化组件：GET /api/plugins/detail 拉取 SFC 片段，拼成 .vue 后走 compilePastedSfc 动态挂载。
 * 数据请求须走代理，勿对 `/data-table` 等同源路径直接 fetch（会打到 Vue devServer → HTML 404 → JSON 解析报错）。
 * - `props.context.proxyRequest(path, options)`：与同事 PluginDemo 一致，返回类 fetch 的 `{ json(), data, status }`。
 * - `window.__SIG_PLUGIN_CONTEXT__.request / proxyRequest`：同上，供未声明 props 的组件使用。
 */
import Vue from "vue";
import { PANEL_TITLE_EMOJI } from "../js/panelTitleEmoji.js";
import { compilePastedSfc } from "../../../utils/compilePastedSfc.js";
import { buildVueSourceFromPluginDetail } from "./buildVueFromPluginDetail.js";
import { fetchPluginDetail, postPluginProxy } from "../../../api/pluginHttp.js";
import { PLUGIN_API_USERNAME } from "../../../api/pluginConstants.js";

export default {
  name: "PersonalizedPluginMountPanel",
  props: {
    panelId: { type: String, default: "" },
    pluginId: { type: String, default: "" },
    /** 来自网格的展示名（可选） */
    pluginFileName: { type: String, default: "" }
  },
  data() {
    return {
      titleEmoji: PANEL_TITLE_EMOJI["personal-plugin"] || "🧩",
      loading: false,
      loadError: "",
      compileError: "",
      previewCtor: null,
      previewKey: 0,
      detailMeta: null
    };
  },
  computed: {
    pluginIdTrim() {
      return (this.pluginId || "").trim();
    },
    panelTitle() {
      const fn = (this.pluginFileName || "").trim();
      if (fn) return fn.replace(/\.vue$/i, "");
      return this.pluginIdTrim || "个性化组件";
    },
    titleFull() {
      return this.pluginIdTrim ? `组件 ID：${this.pluginIdTrim}` : "";
    },
    /**
     * 透传给动态组件（与同事 PluginRenderer 的 context 一致）。
     * 组件内：`const res = await this.context.proxyRequest('/data-table'); const rows = await res.json();`
     */
    pluginContext() {
      const vm = this;
      return {
        pluginId: this.pluginIdTrim,
        username: PLUGIN_API_USERNAME,
        proxyRequest(path, options) {
          return vm.pluginProxyFetchLike(path, options);
        }
      };
    }
  },
  watch: {
    pluginIdTrim: {
      immediate: true,
      handler() {
        this.scheduleLoad();
      }
    }
  },
  beforeDestroy() {
    this.clearPluginRuntime();
    this.removeInjectedStyles();
    this.previewCtor = null;
  },
  methods: {
    styleMarker() {
      const id = (this.panelId || "pp").replace(/[^\w-]/g, "");
      return `personal-plugin-live-${id}`;
    },
    removeInjectedStyles() {
      const sel = `style[data-personal-plugin-live="${this.styleMarker()}"]`;
      const nodes = document.head.querySelectorAll(sel);
      nodes.forEach((n) => n.parentNode && n.parentNode.removeChild(n));
    },
    injectStyles(chunks) {
      this.removeInjectedStyles();
      const list = Array.isArray(chunks) ? chunks.filter(Boolean) : [];
      if (!list.length) return;
      const el = document.createElement("style");
      el.setAttribute("data-personal-plugin-live", this.styleMarker());
      el.textContent = list.join("\n\n");
      document.head.appendChild(el);
    },
    clearPluginRuntime() {
      const cur = window.__SIG_PLUGIN_CONTEXT__;
      const pid = (this.panelId || "").trim();
      if (cur && cur._ownerPanelId === pid) {
        try {
          delete window.__SIG_PLUGIN_CONTEXT__;
        } catch {
          window.__SIG_PLUGIN_CONTEXT__ = undefined;
        }
      }
    },
    installPluginRuntime() {
      const panelId = (this.panelId || "").trim();
      const plugin_name = this.pluginIdTrim;
      const vm = this;
      window.__SIG_PLUGIN_CONTEXT__ = {
        _ownerPanelId: panelId,
        username: PLUGIN_API_USERNAME,
        plugin_name,
        /** @param {string} path @param {{ method?: string, headers?: object, body?: unknown }} [init] */
        request: (path, init) => vm.postPluginProxyCore(path, init),
        /** 与同事 demo 中 fetch('/api/plugin-proxy') 的用法一致，便于 `await res.json()` */
        proxyRequest: (path, options) => vm.pluginProxyFetchLike(path, options)
      };
    },
    /**
     * POST /api/plugin-proxy，返回服务端 JSON（原始 axios data）。
     * @param {string} path 如 `/data-table`
     * @param {{ method?: string, headers?: object, body?: unknown }} [init]
     */
    async postPluginProxyCore(path, init = {}) {
      const url = String(path || "").trim();
      if (!url) throw new Error("缺少请求 path");
      const method = (init.method && String(init.method).toUpperCase()) || "GET";
      const { data } = await postPluginProxy({
        url,
        method,
        username: PLUGIN_API_USERNAME,
        plugin_name: this.pluginIdTrim,
        headers: init.headers && typeof init.headers === "object" ? init.headers : {},
        body: init.body != null ? init.body : null
      });
      return data;
    },
    /**
     * 将代理结果包装成类 Response 对象（含 `json()`），与同事 PluginDemo 中 proxyRequest 行为对齐。
     */
    async pluginProxyFetchLike(path, options = {}) {
      const raw = await this.postPluginProxyCore(path, options);
      const status = raw && typeof raw.status === "number" ? raw.status : 200;
      const body = raw && Object.prototype.hasOwnProperty.call(raw, "body") ? raw.body : raw;
      if (status < 200 || status >= 300) {
        throw new Error(`代理请求失败: 目标服务返回 HTTP ${status}`);
      }
      return {
        ok: true,
        status,
        json: () => Promise.resolve(body),
        data: body
      };
    },
    scheduleLoad() {
      this.$nextTick(() => this.loadAndCompile());
    },
    async loadAndCompile() {
      this.clearPluginRuntime();
      this.removeInjectedStyles();
      this.previewCtor = null;
      this.compileError = "";
      this.loadError = "";
      this.detailMeta = null;
      if (!this.pluginIdTrim) {
        this.loading = false;
        return;
      }
      this.loading = true;
      try {
        const { data } = await fetchPluginDetail(this.pluginIdTrim);
        this.detailMeta = data;
        const vueSource = buildVueSourceFromPluginDetail(data || {});
        // 调试：每次拉取详情并拼接后输出完整 .vue 文本
        console.log(
          `[PersonalizedPlugin] 拼接后的完整 .vue（plugin_id=${this.pluginIdTrim}，panel=${this.panelId}，${vueSource.length} 字符）`
        );
        console.log(vueSource);
        this.installPluginRuntime();
        try {
          const { Component, styles } = compilePastedSfc(Vue, vueSource);
          this.injectStyles(styles);
          this.previewCtor = Component;
          this.previewKey += 1;
        } catch (ce) {
          this.compileError = (ce && ce.message) || String(ce);
          this.previewCtor = null;
        }
      } catch (e) {
        const msg =
          (e && e.response && e.response.data && e.response.data.detail) ||
          (e && e.message) ||
          String(e);
        this.loadError = String(msg);
        this.previewCtor = null;
      } finally {
        this.loading = false;
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

.personal-plugin-live {
  box-shadow:
    inset 0 0 0 1px rgba(98, 206, 255, 0.35),
    0 0 14px rgba(62, 186, 255, 0.1);
  background: linear-gradient(165deg, rgba(10, 28, 52, 0.92), rgba(6, 20, 44, 0.9));
}

.personal-plugin-live__title {
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
  min-width: 0;
}

.personal-plugin-live__name {
  font-size: 12px;
  font-weight: 700;
  color: rgba(210, 238, 255, 0.95);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.personal-plugin-live__body {
  flex: 1;
  min-height: 0;
  margin-top: 6px;
  border-radius: 6px;
  border: 1px dashed rgba(94, 179, 255, 0.28);
  background: rgba(4, 18, 42, 0.35);
  overflow: auto;
}

.personal-plugin-live__placeholder {
  padding: 16px 12px;
  font-size: 11px;
  line-height: 1.5;
  color: rgba(180, 205, 230, 0.65);
  text-align: center;
}

.personal-plugin-live__error {
  padding: 12px;
  font-size: 11px;
  line-height: 1.45;
  color: #ffb4b4;
}

.personal-plugin-live__mount-wrap {
  padding: 6px;
  min-height: 48px;
}

.personal-plugin-live__mount {
  min-height: 32px;
}
</style>
