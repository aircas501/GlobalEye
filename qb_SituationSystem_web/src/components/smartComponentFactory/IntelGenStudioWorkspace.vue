<template>
  <div class="intel-gen-workspace ai-code-gen-root">
    <header class="intel-gen-workspace__toolbar">
      <div class="intel-gen-workspace__toolbar-left">
        <div class="intel-gen-workspace__title-block">
          <span class="intel-gen-workspace__brand">智能组件工厂</span>
          <div
            ref="helpWrap"
            class="igw-help"
            @mouseenter="helpHover = true"
            @mouseleave="helpHover = false"
          >
            <button
              type="button"
              class="igw-help__trigger"
              aria-label="功能操作手册"
              :aria-expanded="helpPopoverOpen"
              aria-controls="igw-help-manual"
              @click.stop="toggleHelpClick"
            >
              <span class="igw-help__trigger-mark" aria-hidden="true">?</span>
            </button>
            <div
              v-show="helpPopoverOpen"
              id="igw-help-manual"
              class="igw-help__popover"
              role="region"
              aria-label="功能操作手册"
            >
              <div class="igw-help__popover-title">功能操作手册</div>
              <ol class="igw-help__steps">
                <li>点击文件夹图标按钮，选择代码存放路径；</li>
                <li>在页面右下角输入组件制作需求；</li>
                <li>
                  等待 AI 自动生成代码，生成完成后核查 Vue 文件是否符合使用需求；若满足要求，点击右上角上传按钮，将
                  Vue 文件上传至云端；
                </li>
                <li>如有配套后端工程，需自行构建服务环境，启动后端服务；</li>
                <li>
                  返回系统主页，进入右侧组件面板，点击「+」号，选择自定义组件；进入组件列表，配置后端服务地址后点击确定保存；
                </li>
                <li>
                  验证插件运行状态，若运行异常，可返回智能组件工厂重新描述问题及优化需求，重新生成调整；重新生成
                  Vue 文件后请再次点击上传按钮，将新版组件覆盖至云端。
                </li>
              </ol>
            </div>
          </div>
        </div>
        <button
          type="button"
          class="igw-toolbar-icon-btn"
          :disabled="!folderSupported"
          :title="folderHandle ? `当前项目：${projectName}，点击更换文件夹` : '选择本地项目文件夹'"
          @click="selectFolder"
        >
          <img
            class="igw-toolbar-icon-btn__img"
            src="/assets/svg/打开文件夹.svg"
            alt=""
            aria-hidden="true"
          />
        </button>
      </div>
      <div class="intel-gen-workspace__toolbar-right">
        <span
          class="igw-link-pill"
          :class="connected ? 'igw-link-pill--on' : 'igw-link-pill--off'"
          :title="backendStatusTooltip"
          role="status"
          :aria-label="connected ? '服务已连接' : '服务未连接'"
        >
          <span class="igw-link-pill__dot" aria-hidden="true" />
          <span class="igw-link-pill__text">{{ connected ? "已连接" : "未连接" }}</span>
        </span>
        <button
          type="button"
          class="igw-toolbar-icon-btn"
          title="重新进行连通性测试"
          aria-label="重新连接"
          @click="refreshHealth"
        >
          <img class="igw-toolbar-icon-btn__img" src="/assets/svg/重连.svg" alt="" aria-hidden="true" />
        </button>
        <button type="button" class="igw-toolbar-icon-btn" title="重置会话" aria-label="新会话" @click="onNewChat">
          <img class="igw-toolbar-icon-btn__img" src="/assets/svg/新会话.svg" alt="" aria-hidden="true" />
        </button>
        <button type="button" class="igw-toolbar-icon-btn" title="退出智能组件工厂" aria-label="关闭" @click="onCloseClick">
          <img class="igw-toolbar-icon-btn__img" src="/assets/svg/关闭.svg" alt="" aria-hidden="true" />
        </button>
      </div>
    </header>

    <div class="intel-gen-workspace__main">
      <aside class="intel-gen-workspace__col intel-gen-workspace__col--tree">
        <AiCodeFileTree
          v-if="folderHandle"
          ref="fileTree"
          :handle="folderHandle"
          @open-file="openFile"
        />
        <div v-else class="intel-gen-workspace__placeholder">请选择项目文件夹以启用智能构建工程</div>
      </aside>

      <section class="intel-gen-workspace__col intel-gen-workspace__col--editor">
        <div class="intel-gen-workspace__editor-head">
          <span class="intel-gen-workspace__file-label" :title="activeFileLabel">{{ activeFileLabel }}</span>
          <div class="intel-gen-workspace__editor-actions">
            <button
              v-if="isProjectBuffer && editorVueBasename"
              type="button"
              class="igw-toolbar-icon-btn igw-toolbar-icon-btn--editor"
              :disabled="!canUploadVueToServer || uploadingPlugin"
              :title="uploadingPlugin ? '上传中…' : uploadVueButtonTitle"
              :aria-busy="uploadingPlugin || undefined"
              aria-label="上传到服务器"
              @click="uploadCurrentVueToServer"
            >
              <img class="igw-toolbar-icon-btn__img" src="/assets/svg/上传.svg" alt="" aria-hidden="true" />
            </button>
            <button
              v-if="isProjectBuffer"
              type="button"
              class="igw-toolbar-icon-btn igw-toolbar-icon-btn--editor"
              title="关闭文件"
              aria-label="返回编辑区"
              @click="returnToScratch"
            >
              <img class="igw-toolbar-icon-btn__img" src="/assets/svg/关闭.svg" alt="" aria-hidden="true" />
            </button>
          </div>
        </div>
        <div ref="cmHost" class="intel-gen-workspace__cm-host" />
      </section>

      <aside class="intel-gen-workspace__col intel-gen-workspace__col--chat">
        <div ref="msgScroll" class="intel-gen-workspace__messages panel-scrollbar">
          <div v-for="(msg, i) in messages" :key="i">
            <div v-if="msg.role === 'user'" class="igw-msg igw-msg--user">
              <div class="igw-bubble igw-bubble--user">{{ msg.content }}</div>
            </div>
            <div v-else-if="msg.role === 'tool-result'" class="igw-msg">
              <AiCodeToolCallCard :tool="msg.tool" :args="msg.args" :result="msg.result" />
            </div>
            <div v-else-if="msg.role === 'assistant'" class="igw-msg igw-msg--assistant">
              <div class="igw-bubble igw-bubble--ai" v-html="renderMd(msg.content)" />
            </div>
            <div v-else-if="msg.role === 'bash-prompt'" class="igw-msg">
              <div class="igw-bash-card">
                <div class="igw-bash-title">请在终端手动执行：</div>
                <pre class="igw-bash-cmd">{{ msg.command }}</pre>
              </div>
            </div>
          </div>
          <div v-if="streaming" class="igw-msg igw-msg--assistant">
            <div class="igw-bubble igw-bubble--ai igw-bubble--stream">{{ streamText }}</div>
          </div>
          <div ref="msgEnd" />
        </div>
        <div class="intel-gen-workspace__input">
          <div class="intel-gen-workspace__input-wrap">
            <textarea
              v-model="input"
              class="intel-gen-workspace__input-field panel-scrollbar"
              rows="3"
              placeholder="描述需求或修改指令…（Enter 发送，Shift+Enter 换行）"
              :disabled="running"
              @keydown.enter.exact.prevent="send"
            />
            <button
              type="button"
              class="igw-input-send-btn"
              :disabled="running || !input.trim()"
              title="发送（Enter）"
              aria-label="发送"
              @click="send"
            >
              <img class="igw-input-send-btn__img" src="/assets/svg/上.svg" alt="" aria-hidden="true" />
            </button>
          </div>
          <div v-if="running" class="intel-gen-workspace__input-actions">
            <button type="button" class="igw-btn igw-btn--danger" @click="cancel">停止</button>
          </div>
          <p v-if="chatError" class="intel-gen-workspace__chat-err" role="alert">{{ chatError }}</p>
        </div>
      </aside>
    </div>
  </div>
</template>

<script>
/**
 * 智能组件工厂：本地目录 + 代码编辑 + aicode 对话；与右侧情报预览格解耦。
 */
import CodeMirror from "codemirror/lib/codemirror.js";
import "codemirror/lib/codemirror.css";
import "codemirror/mode/xml/xml.js";
import "codemirror/mode/javascript/javascript.js";
import "codemirror/mode/css/css.js";
import "codemirror/mode/htmlmixed/htmlmixed.js";
import "codemirror/addon/edit/matchbrackets.js";
import "codemirror/addon/selection/active-line.js";
import "codemirror/theme/material-darker.css";

import AiCodeFileTree from "./AiCodeFileTree.vue";
import AiCodeToolCallCard from "./AiCodeToolCallCard.vue";
import { aicodeGet, aicodePost } from "../../api/aicodeHttp.js";
import { connectAicodeSse, disconnectAicodeSse } from "../../api/aicodeSse.js";
import { uploadPluginVue } from "../../api/pluginHttp.js";
import { PLUGIN_API_USERNAME } from "../../api/pluginConstants.js";
import { executeTool } from "../../utils/executeToolFilesystem.js";

export default {
  name: "IntelGenStudioWorkspace",
  components: { AiCodeFileTree, AiCodeToolCallCard },
  props: {
    visible: { type: Boolean, default: false }
  },
  data() {
    return {
      folderSupported: typeof window !== "undefined" && "showDirectoryPicker" in window,
      folderHandle: null,
      connected: false,
      /** 健康检查失败时的简要原因（悬停「后端不可达」可看完整说明） */
      healthError: "",
      localCode: "",
      activeFilePath: "",
      /** 正在查看本地项目文件时，中间区展示该文件内容，与本地编辑草稿切换 */
      isProjectBuffer: false,
      storedIntelCodeForReturn: "",
      _cm: null,
      _cmSyncing: false,
      messages: [],
      input: "",
      running: false,
      streaming: false,
      streamText: "",
      chatId: "",
      chatError: "",
      /** 上传组件 .vue 到 /api/plugins/upload */
      uploadingPlugin: false,
      /** 操作手册：悬停显示；点击切换（便于触屏）；点击外部关闭 */
      helpHover: false,
      helpClickHeld: false
    };
  },
  computed: {
    helpPopoverOpen() {
      return this.helpHover || this.helpClickHeld;
    },
    projectName() {
      return (this.folderHandle && this.folderHandle.name) || "";
    },
    activeFileLabel() {
      if (this.isProjectBuffer && this.activeFilePath) return this.activeFilePath;
      return "未选择文件";
    },
    /** 说明：与 SSE 是否在对话中建立无关；此处只检测 GET /api/aicode/health */
    backendStatusTooltip() {
      const sse = "连通性状态";
      if (this.connected) {
        return `${sse} 当前：已连接。`;
      }
      const err = this.healthError ? ` 失败原因：${this.healthError}` : "";
      return `${sse} 当前：未连接。${err}`;
    },
    /** 当前打开的项目文件是否为 .vue（仅看路径 basename） */
    editorVueBasename() {
      if (!this.isProjectBuffer || !this.activeFilePath) return "";
      const p = String(this.activeFilePath).trim().replace(/\\/g, "/");
      const base = p.split("/").pop() || "";
      return /\.vue$/i.test(base) ? base : "";
    },
    /** 与接口文档一致：须含 `<template>`；内容为编辑器当前 UTF-8 文本 */
    canUploadVueToServer() {
      if (!this.editorVueBasename) return false;
      const code = this.localCode != null ? String(this.localCode) : "";
      return /<template[\s>]/i.test(code);
    },
    uploadVueButtonTitle() {
      if (!this.editorVueBasename) return "";
      if (!this.canUploadVueToServer) {
        return "仅支持 .vue 且须包含 <template> 区块（UTF-8），符合校验后方可上传";
      }
      return `上传「${this.editorVueBasename}」至云端组件列表，用户 ${PLUGIN_API_USERNAME}`;
    }
  },
  watch: {
    visible: {
      immediate: true,
      handler(v) {
        if (v) {
          this.isProjectBuffer = false;
          this.activeFilePath = "";
          this.storedIntelCodeForReturn = "";
          this.localCode = "";
          this.input = "";
          this.chatError = "";
          this.healthError = "";
          /** 界面打开即请求健康检查（含首次挂载 visible 已为 true 的情况） */
          this.refreshHealth();
          this.$nextTick(() => {
            this.ensureCodeMirror();
          });
        } else {
          this.teardownSession();
          this.destroyCodeMirror();
          this.helpHover = false;
          this.helpClickHeld = false;
        }
      }
    },
    localCode() {
      this.syncCmFromModel();
    }
  },
  created() {
    this._editingIntelFromCm = false;
    this._onDocHelpClose = (e) => {
      if (!this.helpClickHeld) return;
      const el = this.$refs.helpWrap;
      if (el && !el.contains(e.target)) this.helpClickHeld = false;
    };
    document.addEventListener("mousedown", this._onDocHelpClose, true);
  },
  methods: {
    toggleHelpClick() {
      this.helpClickHeld = !this.helpClickHeld;
    },
    async refreshHealth() {
      this.healthError = "";
      const healthAxiosCfg = { validateStatus: () => true };
      try {
        const res = await aicodeGet("/api/aicode/health", undefined, healthAxiosCfg);

        const code = res && typeof res.status === "number" ? res.status : 0;
        const payload = res && res.data;
        const raw = payload && typeof payload === "object" && !Array.isArray(payload) ? payload : {};
        const st = raw.status != null ? String(raw.status).trim() : "";
        /** 仅 HTTP 200 且 JSON 内 status 为 ok 视为已连接；其余含 404/500/502 均为未连接 */
        this.connected = code === 200 && st === "ok";

        if (!this.connected) {
          if (code !== 200) {
            this.healthError = code ? `HTTP ${code}` : "无有效 HTTP 状态";
          } else {
            this.healthError = st ? `status=${st}` : "响应非 ok";
          }
        }
      } catch (e) {
        this.connected = false;
        const ax = e && e.response;
        const code = ax && typeof ax.status === "number" ? ax.status : 0;
        const msg =
          (ax && ax.data && typeof ax.data === "object" && (ax.data.message || ax.data.detail)) ||
          (e && e.message) ||
          "请求失败";
        this.healthError = code ? `HTTP ${code} ${msg}` : String(msg);
      }
    },
    async selectFolder() {
      if (!this.folderSupported) return;
      try {
        const handle = await window.showDirectoryPicker({ mode: "readwrite" });
        this.folderHandle = handle;
        this.activeFilePath = "";
      } catch (e) {
        if (e && e.name !== "AbortError") console.error(e);
      }
    },
    async resolveFileHandle(filePath) {
      if (!this.folderHandle) return null;
      const parts = String(filePath).replace(/\\/g, "/").split("/");
      let current = this.folderHandle;
      for (const part of parts) {
        if (!part) continue;
        try {
          current = await current.getDirectoryHandle(part);
        } catch {
          try {
            current = await current.getFileHandle(part);
          } catch {
            return null;
          }
        }
      }
      return current;
    },
    async openFile(filePath) {
      if (!this.folderHandle || !filePath) return;
      if (!this.isProjectBuffer) {
        this.storedIntelCodeForReturn = this.localCode;
      }
      this.isProjectBuffer = true;
      this.activeFilePath = String(filePath);
      const h = await this.resolveFileHandle(filePath);
      if (!h || h.kind !== "file") return;
      const file = await h.getFile();
      const text = await file.text();
      this._editingIntelFromCm = true;
      this.localCode = text;
      this.$nextTick(() => {
        this._editingIntelFromCm = false;
        this.ensureCodeMirror();
        this.syncCmFromModel();
        if (this._cm) this._cm.refresh();
      });
    },
    returnToScratch() {
      this.isProjectBuffer = false;
      this.activeFilePath = "";
      this._editingIntelFromCm = true;
      this.localCode =
        this.storedIntelCodeForReturn != null ? String(this.storedIntelCodeForReturn) : "";
      this.$nextTick(() => {
        this._editingIntelFromCm = false;
      });
    },
    async uploadCurrentVueToServer() {
      if (!this.canUploadVueToServer || this.uploadingPlugin) return;
      const name = this.editorVueBasename;
      const text = this.localCode != null ? String(this.localCode) : "";
      const blob = new Blob([text], { type: "text/plain;charset=utf-8" });
      const file = new File([blob], name, { type: "text/plain" });
      this.uploadingPlugin = true;
      try {
        const { data } = await uploadPluginVue(file, PLUGIN_API_USERNAME);
        const hint = data && typeof data === "object" ? JSON.stringify(data, null, 2) : String(data || "ok");
        window.alert(`上传成功。\n${hint}`);
      } catch (e) {
        const ax = e && e.response;
        const detail = ax && ax.data && (ax.data.detail || ax.data.message);
        const msg = detail || (e && e.message) || String(e);
        window.alert(`上传失败：${msg}`);
      } finally {
        this.uploadingPlugin = false;
      }
    },
    onCloseClick() {
      this.$emit("close");
    },
    onNewChat() {
      this.chatId = "";
      this.messages = [];
      this.chatError = "";
      disconnectAicodeSse();
      this.running = false;
      this.streaming = false;
      this.streamText = "";
    },
    teardownSession() {
      disconnectAicodeSse();
      this.running = false;
      this.streaming = false;
      this.streamText = "";
      this.chatId = "";
      this.messages = [];
      this.input = "";
      this.chatError = "";
    },
    resetChatRunning() {
      this.running = false;
      this.streaming = false;
      this.streamText = "";
      disconnectAicodeSse();
    },
    endChatTurnFromStream() {
      this.resetChatRunning();
      this.$nextTick(() => this.refreshProjectFileTree());
    },
    refreshProjectFileTree() {
      if (!this.folderHandle) return;
      const ft = this.$refs.fileTree;
      if (ft && typeof ft.refresh === "function") {
        ft.refresh().catch((e) => {
          console.warn("[IntelGenStudioWorkspace] 刷新文件树失败:", e);
        });
      }
    },
    scrollChatBottom() {
      this.$nextTick(() => {
        const el = this.$refs.msgEnd;
        if (el && typeof el.scrollIntoView === "function") {
          el.scrollIntoView({ behavior: "smooth" });
        }
      });
    },
    renderMd(text) {
      const raw = text != null ? String(text) : "";
      return raw
        .replace(/```(\w*)\n([\s\S]*?)```/g, "<pre><code>$2</code></pre>")
        .replace(/`([^`]+)`/g, "<code>$1</code>")
        .replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>")
        .replace(/\n/g, "<br>");
    },
    ensureCodeMirror() {
      if (!this.visible || !this.$refs.cmHost) return;
      if (this._cm) {
        const next = this.localCode || "";
        if (this._cm.getValue() !== next) {
          const info = this._cm.getScrollInfo();
          this._cmSyncing = true;
          this._cm.setValue(next);
          this._cmSyncing = false;
          this._cm.scrollTo(info.left, info.top);
        }
        this.$nextTick(() => this._cm.refresh());
        return;
      }
      this._cm = CodeMirror(this.$refs.cmHost, {
        value: this.localCode || "",
        mode: "htmlmixed",
        theme: "material-darker",
        lineNumbers: true,
        indentUnit: 2,
        tabSize: 2,
        lineWrapping: true,
        matchBrackets: true,
        styleActiveLine: true,
        autofocus: false
      });
      this._cm.on("change", () => {
        if (this._cmSyncing) return;
        this._cmSyncing = true;
        this.localCode = this._cm.getValue();
        this.$nextTick(() => {
          this._cmSyncing = false;
        });
      });
      this.$nextTick(() => this._cm.refresh());
    },
    syncCmFromModel() {
      if (this._cmSyncing || !this._cm) return;
      const next = this.localCode || "";
      if (this._cm.getValue() !== next) {
        const info = this._cm.getScrollInfo();
        this._cmSyncing = true;
        this._cm.setValue(next);
        this._cmSyncing = false;
        this._cm.scrollTo(info.left, info.top);
      }
    },
    destroyCodeMirror() {
      if (!this._cm) return;
      const wrap = this._cm.getWrapperElement();
      if (wrap && wrap.parentNode) wrap.parentNode.removeChild(wrap);
      this._cm = null;
    },
    async send() {
      const text = this.input.trim();
      if (!text || this.running) return;
      this.input = "";
      this.running = true;
      this.streaming = true;
      this.streamText = "";
      this.chatError = "";
      this.messages.push({ role: "user", content: text });

      try {
        let cid = this.chatId;
        if (!cid) {
          const res = await aicodePost("/api/aicode/chat/new", {});
          cid = res.data && res.data.chat_id ? String(res.data.chat_id) : "";
          this.chatId = cid;
        }
        if (!cid) throw new Error("未返回 chat_id");

        await aicodePost("/api/aicode/chat/send", {
          chat_id: cid,
          instruction: text,
          mode: "modify"
        });

        connectAicodeSse(cid, {
          onToolCall: async (data) => {
            try {
              const tool = data && data.tool;
              const args = (data && data.args) || {};
              const result = await executeTool(this.folderHandle, tool, args);
              await aicodePost("/api/aicode/chat/tool/result", { chat_id: cid, result });
              this.messages.push({ role: "tool-result", tool, args, result });
              this.scrollChatBottom();
            } catch (e) {
              const errMsg = `❌ 工具执行失败: ${e && e.message ? e.message : String(e)}`;
              await aicodePost("/api/aicode/chat/tool/result", { chat_id: cid, result: errMsg });
              this.messages.push({
                role: "tool-result",
                tool: data && data.tool,
                args: (data && data.args) || {},
                result: errMsg
              });
              this.scrollChatBottom();
            }
          },
          onToolResult: () => {},
          onBashPrompt: (data) => {
            const command = data && data.command != null ? String(data.command) : "";
            this.messages.push({ role: "bash-prompt", command });
            this.scrollChatBottom();
          },
          onLlmReply: (data) => {
            const content = data && data.content != null ? String(data.content) : "";
            this.streamText = content;
            if (data && data.is_final) {
              this.streaming = false;
              this.messages.push({ role: "assistant", content });
              this.streamText = "";
            }
            this.scrollChatBottom();
          },
          onDone: () => {
            this.endChatTurnFromStream();
          },
          onError: (data) => {
            const msg = data && data.message != null ? String(data.message) : "未知错误";
            this.messages.push({ role: "assistant", content: `❌ ${msg}` });
            this.endChatTurnFromStream();
          }
        });
      } catch (e) {
        this.chatError = (e && e.message) || String(e);
        this.messages.push({
          role: "assistant",
          content: `❌ 请求失败: ${(e && e.message) || String(e)}`
        });
        this.endChatTurnFromStream();
      }
    },
    async cancel() {
      if (this.chatId) {
        try {
          await aicodePost("/api/aicode/chat/cancel", { chat_id: this.chatId });
        } catch {
          /* ignore */
        }
      }
      this.endChatTurnFromStream();
    }
  },
  beforeDestroy() {
    if (this._onDocHelpClose) {
      document.removeEventListener("mousedown", this._onDocHelpClose, true);
    }
    this.teardownSession();
    this.destroyCodeMirror();
  }
};
</script>

<style scoped>
.ai-code-gen-root.intel-gen-workspace {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  border: none;
  overflow: hidden;
  color: var(--font-main, #d8eeff);
  font-size: 12px;
  background: linear-gradient(
    165deg,
    rgba(7, 34, 79, 0.96) 0%,
    rgba(3, 16, 38, 0.98) 55%,
    rgba(3, 10, 26, 0.99) 100%
  );
  box-shadow: inset 0 0 0 1px rgba(28, 111, 184, 0.35);
}

.intel-gen-workspace__toolbar {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px;
  border-bottom: 1px solid var(--line-main, #1c6fb8);
  background: linear-gradient(90deg, rgba(4, 23, 52, 0.98), rgba(8, 40, 88, 0.92));
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.22);
}
.intel-gen-workspace__toolbar-left,
.intel-gen-workspace__toolbar-right {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  min-width: 0;
}
.intel-gen-workspace__title-block {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.intel-gen-workspace__brand {
  font-weight: 800;
  letter-spacing: 0.04em;
  font-size: 15px;
  background: linear-gradient(90deg, #8be9ff, #6ab6ff 50%, #c5f3ff 92%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  white-space: nowrap;
}

.igw-help {
  position: relative;
  flex-shrink: 0;
}
.igw-help__trigger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  margin: 0;
  padding: 0;
  border-radius: 50%;
  border: 1px solid rgba(120, 200, 255, 0.45);
  background: rgba(8, 36, 68, 0.55);
  color: rgba(200, 235, 255, 0.92);
  font-size: 12px;
  font-weight: 700;
  line-height: 1;
  cursor: help;
  transition:
    border-color 0.15s ease,
    box-shadow 0.15s ease,
    background 0.15s ease,
    color 0.15s ease;
}
.igw-help__trigger:hover,
.igw-help__trigger:focus-visible {
  outline: none;
  border-color: rgba(160, 230, 255, 0.75);
  background: rgba(24, 56, 100, 0.65);
  box-shadow: 0 0 10px rgba(62, 186, 255, 0.25);
  color: #fff;
}
.igw-help__trigger-mark {
  transform: translateY(-0.5px);
}
.igw-help__popover {
  position: absolute;
  left: 0;
  top: calc(100% + 6px);
  z-index: 200;
  width: min(440px, calc(100vw - 48px));
  max-height: min(72vh, 520px);
  overflow: auto;
  padding: 12px 14px 14px;
  border-radius: 10px;
  border: 1px solid rgba(100, 200, 255, 0.42);
  background: linear-gradient(165deg, rgba(12, 40, 82, 0.98), rgba(4, 18, 42, 0.99));
  box-shadow:
    0 12px 40px rgba(0, 0, 0, 0.45),
    0 0 0 1px rgba(0, 0, 0, 0.2),
    inset 0 1px 0 rgba(255, 255, 255, 0.06);
  color: rgba(220, 238, 255, 0.95);
  font-size: 12px;
  line-height: 1.55;
}
.igw-help__popover::before {
  content: "";
  position: absolute;
  left: 0;
  right: 0;
  top: -8px;
  height: 8px;
}
.igw-help__popover-title {
  font-weight: 700;
  font-size: 13px;
  letter-spacing: 0.06em;
  color: #9ee7ff;
  margin-bottom: 10px;
  padding-bottom: 8px;
  border-bottom: 1px solid rgba(28, 111, 184, 0.55);
}
.igw-help__steps {
  margin: 0;
  padding-left: 1.35em;
}
.igw-help__steps li {
  margin-bottom: 0.65em;
}
.igw-help__steps li:last-child {
  margin-bottom: 0;
}

.igw-btn {
  border-radius: 6px;
  border: 1px solid rgba(100, 200, 255, 0.42);
  background: rgba(8, 36, 68, 0.45);
  color: rgba(220, 235, 250, 0.95);
  font-size: 11px;
  padding: 6px 12px;
  cursor: pointer;
  transition: border-color 0.15s ease, box-shadow 0.15s ease, background 0.15s ease;
}
.igw-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.igw-btn--folder {
  max-width: 240px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.igw-btn--primary {
  border-color: rgba(186, 140, 255, 0.55);
  background: linear-gradient(135deg, rgba(72, 36, 120, 0.75), rgba(24, 60, 110, 0.75));
  color: #f4ecff;
}
.igw-btn--danger {
  border-color: rgba(255, 120, 120, 0.45);
  color: #ffb4b4;
}
.igw-btn--ghost {
  background: transparent;
}
.igw-btn--close {
  border-color: rgba(255, 160, 120, 0.4);
}
.igw-btn--tiny {
  padding: 4px 9px;
  font-size: 10px;
}
.igw-btn:hover:not(:disabled) {
  border-color: rgba(130, 220, 255, 0.65);
  box-shadow: 0 0 12px rgba(62, 186, 255, 0.18);
}

.intel-gen-workspace__toolbar .igw-toolbar-icon-btn {
  width: 30px;
  height: 30px;
  border-radius: 7px;
}
.intel-gen-workspace__toolbar .igw-toolbar-icon-btn__img {
  width: 18px;
  height: 18px;
}
.intel-gen-workspace__toolbar .igw-link-pill {
  gap: 6px;
  padding: 4px 10px 4px 8px;
  font-size: 10px;
}
.intel-gen-workspace__toolbar .igw-link-pill__dot {
  width: 6px;
  height: 6px;
}

.igw-toolbar-icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  padding: 0;
  border-radius: 8px;
  border: 1px solid rgba(100, 200, 255, 0.42);
  background: rgba(8, 36, 68, 0.45);
  cursor: pointer;
  flex-shrink: 0;
  transition:
    border-color 0.15s ease,
    box-shadow 0.15s ease,
    background 0.15s ease,
    opacity 0.15s ease;
}
.igw-toolbar-icon-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.igw-toolbar-icon-btn:hover:not(:disabled) {
  border-color: rgba(130, 220, 255, 0.65);
  background: rgba(24, 48, 92, 0.55);
  box-shadow: 0 0 12px rgba(62, 186, 255, 0.2);
}
.igw-toolbar-icon-btn__img {
  width: 20px;
  height: 20px;
  display: block;
  pointer-events: none;
  filter: brightness(0) invert(1);
  opacity: 0.9;
}
.igw-toolbar-icon-btn--editor {
  width: 30px;
  height: 30px;
  border-radius: 7px;
}
.igw-toolbar-icon-btn--editor .igw-toolbar-icon-btn__img {
  width: 18px;
  height: 18px;
}

.igw-link-pill {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 5px 12px 5px 10px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.02em;
  white-space: nowrap;
  flex-shrink: 0;
  border: 1px solid transparent;
}
.igw-link-pill__dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex-shrink: 0;
}
.igw-link-pill--on {
  color: #7fefc8;
  border-color: rgba(120, 230, 200, 0.72);
  background: rgba(4, 28, 42, 0.65);
  text-shadow: 0 0 10px rgba(100, 255, 200, 0.25);
}
.igw-link-pill--on .igw-link-pill__dot {
  background: #5cffb0;
  box-shadow: 0 0 8px rgba(100, 255, 180, 0.75);
}
.igw-link-pill--off {
  color: rgba(255, 170, 150, 0.92);
  border-color: rgba(255, 120, 100, 0.42);
  background: rgba(36, 12, 16, 0.35);
}
.igw-link-pill--off .igw-link-pill__dot {
  background: rgba(255, 120, 100, 0.85);
  box-shadow: 0 0 6px rgba(255, 100, 80, 0.35);
}

.intel-gen-workspace__main {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: row;
}

.intel-gen-workspace__col {
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
.intel-gen-workspace__col--tree {
  flex: 0 0 22%;
  max-width: 280px;
  border-right: 1px solid rgba(28, 111, 184, 0.45);
  background: rgba(4, 18, 42, 0.55);
}
.intel-gen-workspace__col--editor {
  flex: 1 1 auto;
  background: rgba(3, 12, 28, 0.35);
}
.intel-gen-workspace__col--chat {
  flex: 0 0 30%;
  max-width: 400px;
  border-left: 1px solid rgba(28, 111, 184, 0.45);
  background: rgba(4, 14, 34, 0.72);
}

.intel-gen-workspace__placeholder {
  padding: 16px 10px;
  line-height: 1.55;
  color: rgba(141, 199, 243, 0.65);
  text-align: center;
  font-size: 11px;
}

.intel-gen-workspace__editor-head {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 10px;
  border-bottom: 1px solid rgba(28, 111, 184, 0.4);
  font-size: 11px;
  background: rgba(4, 18, 42, 0.5);
}
.intel-gen-workspace__file-label {
  flex: 1;
  min-width: 0;
  font-family: ui-monospace, Consolas, Menlo, monospace;
  color: rgba(200, 235, 255, 0.92);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.intel-gen-workspace__editor-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}

.intel-gen-workspace__cm-host {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}
.intel-gen-workspace__cm-host :deep(.CodeMirror) {
  height: 100% !important;
  font-size: 11px;
  font-family: ui-monospace, "Cascadia Code", Consolas, Menlo, monospace;
}
.intel-gen-workspace__cm-host :deep(.CodeMirror-scroll) {
  max-height: 100%;
}

.intel-gen-workspace__messages {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 10px 10px 12px;
}
.igw-msg {
  margin-bottom: 10px;
}
.igw-bubble {
  padding: 8px 10px;
  border-radius: 8px;
  line-height: 1.45;
  font-size: 11px;
  word-break: break-word;
}
.igw-bubble--user {
  background: rgba(14, 99, 156, 0.88);
  color: #fff;
  max-width: 100%;
}
.igw-bubble--ai {
  background: rgba(7, 34, 79, 0.92);
  border: 1px solid rgba(28, 111, 184, 0.5);
  color: rgba(220, 235, 250, 0.94);
}
.igw-bubble--ai :deep(pre) {
  background: rgba(0, 0, 0, 0.35);
  padding: 8px;
  border-radius: 6px;
  overflow-x: auto;
  margin: 6px 0;
}
.igw-bubble--ai :deep(code) {
  background: rgba(255, 255, 255, 0.06);
  padding: 1px 4px;
  border-radius: 3px;
}
.igw-bubble--stream {
  animation: igw-pulse 1.4s ease-in-out infinite;
}
@keyframes igw-pulse {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.65;
  }
}

.igw-bash-card {
  border-radius: 8px;
  border: 1px solid rgba(200, 170, 90, 0.45);
  background: rgba(20, 18, 8, 0.5);
  padding: 8px;
}
.igw-bash-title {
  font-size: 11px;
  color: rgba(255, 220, 140, 0.9);
  margin-bottom: 6px;
}
.igw-bash-cmd {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
  font-family: ui-monospace, Consolas, Menlo, monospace;
  font-size: 11px;
  color: rgba(230, 240, 255, 0.9);
}

.intel-gen-workspace__input {
  flex-shrink: 0;
  padding: 10px 10px 12px;
  border-top: 1px solid rgba(28, 111, 184, 0.4);
  background: rgba(3, 10, 26, 0.9);
}
.intel-gen-workspace__input-wrap {
  position: relative;
}
.intel-gen-workspace__input-field {
  width: 100%;
  box-sizing: border-box;
  border-radius: 8px;
  border: 1px solid rgba(94, 179, 255, 0.35);
  background: rgba(4, 20, 48, 0.75);
  color: #e8f4ff;
  font-size: 11px;
  padding: 8px 36px 32px 10px;
  resize: none;
  font-family: inherit;
  display: block;
  min-height: 4.5em;
  overflow-y: auto;
}
.intel-gen-workspace__input-field:focus {
  outline: none;
  border-color: rgba(186, 140, 255, 0.5);
  box-shadow: 0 0 0 1px rgba(62, 186, 255, 0.12);
}
.intel-gen-workspace__input-field:disabled {
  opacity: 0.72;
  cursor: not-allowed;
}
.igw-input-send-btn {
  position: absolute;
  right: 8px;
  bottom: 8px;
  width: 26px;
  height: 26px;
  padding: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  border: 1px solid rgba(130, 200, 255, 0.45);
  background: linear-gradient(165deg, rgba(72, 56, 140, 0.55), rgba(24, 48, 92, 0.75));
  cursor: pointer;
  transition:
    border-color 0.15s ease,
    box-shadow 0.15s ease,
    opacity 0.15s ease,
    transform 0.12s ease;
}
.igw-input-send-btn:hover:not(:disabled) {
  border-color: rgba(160, 220, 255, 0.7);
  box-shadow: 0 0 14px rgba(120, 100, 255, 0.25);
}
.igw-input-send-btn:active:not(:disabled) {
  transform: scale(0.96);
}
.igw-input-send-btn:disabled {
  opacity: 0.35;
  cursor: not-allowed;
  box-shadow: none;
}
.igw-input-send-btn__img {
  width: 14px;
  height: 14px;
  display: block;
  pointer-events: none;
  filter: brightness(0) invert(1);
  opacity: 0.95;
}
.intel-gen-workspace__input-actions {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}
.intel-gen-workspace__chat-err {
  margin: 8px 0 0;
  font-size: 10px;
  color: #ffb4b4;
}
</style>
