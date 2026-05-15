<template>
  <div
    ref="assistantShell"
    class="assistant-shell"
    :style="{ width: panelWidth + 'px', height: panelHeight + 'px' }"
  >
    <div
      class="assistant-resize-handle"
      title="拖动调整窗口大小"
      aria-label="拖动调整窗口大小"
      @pointerdown.prevent.stop="onResizePointerDown"
    />
    <div class="assistant card">
      <header class="assistant-head">
        <div class="assistant-head-main">
          <div class="assistant-title-line">
            <span class="assistant-title">智能讨论</span>
            <span
              class="status-badge"
              :class="channelConnected ? 'is-ok' : 'is-off'"
            >
              <span class="status-dot" aria-hidden="true" />
              {{ channelConnected ? "已连接" : "未连接" }}
            </span>
          </div>
          <div class="assistant-meta">
            <template v-if="showHistoryPanel">
              <span>用户ID: {{ userId }}</span>
              <span class="meta-chat">历史会话ID: {{ historyMetaChatId }}</span>
              <span class="meta-chat">历史议题: {{ historyMetaTopic }}</span>
            </template>
            <template v-else>
              <span>用户ID: {{ userId }}</span>
              <span v-if="chatId" class="meta-chat">会话ID: {{ chatId }}</span>
              <span class="meta-chat">会话名: {{ currentSessionTitle || "新会话" }}</span>
            </template>
          </div>
          <div v-if="!showHistoryPanel" class="step-rail" aria-label="讨论进度">
            <span
              v-for="(s, i) in stepLabels"
              :key="s.key"
              class="step-chip"
              :class="{
                done: stepIndex > i,
                current: stepIndex === i,
                wait: stepIndex < i
              }"
            >
              {{ s.label }}
            </span>
          </div>
        </div>
        <div class="assistant-head-actions">
          <button
            v-if="!showHistoryPanel"
            type="button"
            class="btn btn-outline"
            :class="{ 'btn-history-on': showHistoryPanel }"
            @click="toggleHistoryPanel"
          >
            历史会话
          </button>
          <button
            v-else
            type="button"
            class="btn btn-outline"
            @click="toggleHistoryPanel"
          >
            +新会话
          </button>
          <button
            v-if="!showHistoryPanel"
            type="button"
            class="btn btn-outline btn-refresh"
            title="重置会话"
            aria-label="重置会话"
            @click="resetSession"
          >
            ↻
          </button>
          <button
            type="button"
            class="btn btn-close"
            title="关闭"
            aria-label="关闭智能讨论"
            @click="$emit('close')"
          >
            ×
          </button>
        </div>
      </header>

      <div class="assistant-body">
        <div v-if="active && errorBanner" class="status-banner is-error">
          {{ errorBanner }}
        </div>

        <HistorySessionView
          v-if="showHistoryPanel"
          :model="historySessionModel"
          :handlers="historySessionHandlers"
        />
        <NewSessionView
          v-else
          :model="newSessionModel"
          :handlers="newSessionHandlers"
        />
      </div>
    </div>

    <!-- 再讨论一轮：二次确认 -->
    <div v-if="roundNextConfirmModalOpen" class="modal-backdrop" @click.self="roundNextConfirmModalOpen = false">
      <div class="modal-card card">
        <div class="modal-title">确认进入下一轮</div>
        <p class="modal-desc">将保留当前全部上下文并直接进入下一轮。<br>确定开始下一轮讨论吗？</p>
        <div class="modal-actions">
          <button type="button" class="btn btn-outline" @click="roundNextConfirmModalOpen = false">取消</button>
          <button type="button" class="btn btn-primary" :disabled="busy" @click="confirmContinueNextRound">
            确认
          </button>
        </div>
      </div>
    </div>

    <!-- 删除历史会话：二次确认 -->
    <div v-if="historyDeleteConfirmOpen" class="modal-backdrop" @click.self="cancelHistoryDelete">
      <div class="modal-card card">
        <div class="modal-title">删除历史会话</div>
        <p class="modal-desc">
          确定删除该会话吗？删除后无法恢复。<br>
          <span v-if="pendingHistoryDeleteItem" class="modal-delete-meta">
            ID：{{ pendingHistoryDeleteItem.chat_id }}<br>
            事件：{{ pendingHistoryDeleteItem.event || "（无事件描述）" }}
          </span>
        </p>
        <div class="modal-actions">
          <button type="button" class="btn btn-outline" @click="cancelHistoryDelete">取消</button>
          <button type="button" class="btn btn-primary" @click="confirmHistoryDelete">删除</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import {
  postDiscussionSend,
  postDiscussionSendStream,
  getPersonas,
  postSaveRoles,
  getChatHistoryList,
  getChatHistoryContext,
  postDelChatHistory,
  getDiscussionStreamUrl,
  isDiscussionSseStreamEnabled,
  parseDiscussionEventPayload
} from "../../api/discussion";
import NewSessionView from "./newSession/index.vue";
import HistorySessionView from "./historySession/index.vue";

const USER_ID = "admin";
const CHAT_MODE = "multi_role_meeting";

const DEMO_EVENT =
  "伊朗发动“真实承诺-4”行动，打击美军中东基地。";

export default {
  name: "IntelligenceAssistant",
  components: {
    NewSessionView,
    HistorySessionView
  },
  props: {
    active: { type: Boolean, default: false }
  },
  data() {
    return {
      userId: USER_ID,
      chatId: "",
      eventText: "",
      eventDraft: "",
      currentSessionTitle: "新会话",
      phase: "idle",
      busy: false,
      streamConnected: false,
      channelConnected: false,
      discussionEventSource: null,
      discussionStreamChatId: "",
      _pushDedupeAt: Object.create(null),
      errorBanner: "",
      threadMessages: [],
      panelWidth: 720,
      panelHeight: 640,
      resizeDrag: null,
      abortCtl: null,
      experts: [],
      selectedExpertMap: {},
      topics: [],
      llmTopicsCache: [],
      selectedTopicIndex: 0,
      /** 议题来源：llm 为模型生成列表；manual 为自定义并随 topic_generation 提交 debate_topic */
      topicPickMode: "",
      manualDebateTopic: "",
      manualTopicSubmitted: false,
      historySessions: [],
      historyLoading: false,
      historyContextLoading: false,
      historyReferenceLoading: false,
      historyReferenceError: "",
      selectedHistoryChatIds: [],
      relationEvents: [],
      showHistoryPanel: false,
      activeHistoryChatId: "",
      historyReadonly: false,
      debateTurns: 4,
      replyChainLength: 2,
      typingLabel: "",
      pendingRoleBuffer: null,
      streamingMsgLocalId: "",
      streamingSummaryLocalId: "",
      lastRoundMessageIds: [],
      roundNextConfirmModalOpen: false,
      historyDeleteConfirmOpen: false,
      pendingHistoryDeleteItem: null,
      stepLabels: [
        { key: "event", label: "事件录入" },
        { key: "experts", label: "专家选择" },
        { key: "topic", label: "议题确定" },
        { key: "position", label: "观点发表" },
        { key: "debate", label: "智能研讨" }
      ]
    };
  },
  computed: {
    newSessionModel() {
      return this;
    },
    newSessionHandlers() {
      return {
        fetchHistorySessions: this.fetchHistorySessions,
        openHistorySession: this.openHistorySession,
        deleteHistorySession: this.deleteHistorySession,
        fetchHistoryReferenceCandidates: this.fetchHistoryReferenceCandidates,
        onFooterEnter: this.onFooterEnter,
        startFromIdle: this.startFromIdle,
        fillDemoEvent: this.fillDemoEvent,
        confirmExpertsAndSave: this.confirmExpertsAndSave,
        onTopicPickModeChange: this.onTopicPickModeChange,
        runTopicGenerationStream: this.runTopicGenerationStream,
        confirmManualDebateTopic: this.confirmManualDebateTopic,
        startPositionPhase: this.startPositionPhase,
        startDebatePhase: this.startDebatePhase,
        onAnotherRoundClick: this.onAnotherRoundClick,
        removeRelationEvent: this.removeRelationEvent,
        clearRelationEvents: this.clearRelationEvents
      };
    },
    historySessionModel() {
      return this;
    },
    historySessionHandlers() {
      return {
        fetchHistorySessions: this.fetchHistorySessions,
        openHistorySession: this.openHistorySession,
        deleteHistorySession: this.deleteHistorySession
      };
    },
    chatIdShort() {
      const c = this.chatId || "";
      return c.length > 14 ? `${c.slice(0, 10)}…` : c;
    },
    activeHistorySessionItem() {
      const id = String(this.activeHistoryChatId || "").trim();
      if (!id) return null;
      const rows = Array.isArray(this.historySessions) ? this.historySessions : [];
      return rows.find((x) => String(x && x.chat_id) === id) || null;
    },
    historyMetaChatId() {
      const id = String(this.activeHistoryChatId || "").trim();
      return id || "暂无";
    },
    historyMetaTopic() {
      const item = this.activeHistorySessionItem;
      if (!item) return "暂无";
      const topic = String((item && item.topic) || "").trim();
      const event = String((item && item.event) || "").trim();
      return topic || event || "暂无";
    },
    selectedRolesList() {
      const list = Array.isArray(this.experts) ? this.experts : [];
      const map = this.selectedExpertMap || {};
      return list.filter((p) => map[this.personaKey(p)]);
    },
    phaseStripVisible() {
      return (
        ["idle", "experts", "topics", "position_done", "round_done"].indexOf(
          this.phase
        ) >= 0
      );
    },
    stepIndex() {
      const m = {
        idle: 0,
        experts: 1,
        topics: 2,
        position: 3,
        position_done: 3,
        debate: 4,
        round_done: 4
      };
      return m[this.phase] != null ? m[this.phase] : 0;
    },
    footerLocked() {
      if (this.busy) return true;
      if (this.historyReadonly) return true;
      if (this.phase !== "idle") return true;
      return false;
    },
    footerPlaceholder() {
      if (this.historyReadonly) return "历史会话为只读模式，当前不支持继续聊";
      if (this.phase === "idle") {
        return "输入突发事件或议题描述，Enter 或点击「开始讨论」…";
      }
      return "";
    },
    canStartFromIdle() {
      if (this.historyReadonly) return false;
      return this.phase === "idle" && !!String(this.eventDraft || "").trim();
    },
    historyReferenceCandidates() {
      const rows = Array.isArray(this.historySessions) ? this.historySessions : [];
      return rows.map((x) => ({
        chat_id: x && x.chat_id != null ? String(x.chat_id) : "",
        topic: x && x.topic != null ? String(x.topic) : "",
        event: x && x.event != null ? String(x.event) : "",
        displayTitle:
          (x && x.topic != null ? String(x.topic) : "").trim() ||
          (x && x.event != null ? String(x.event) : "").trim()
      })).filter((x) => x.chat_id);
    },
    memberStripVisible() {
      if (!this.selectedRolesList.length) return false;
      return (
        ["position", "position_done", "debate", "round_done"].indexOf(this.phase) >= 0
      );
    },
    /** 左侧「设置」列顶部说明 */
    settingsColumnHint() {
      const m = {
        idle: "事件描述",
        experts: "选择参与讨论的专家（至少 2 位）",
        topics: "确定议题（可输入或 AI 生成）",
        position_done: "讨论参数",
        round_done: "本轮已结束"
      };
      return m[this.phase] || "会议设置";
    },
    /** 发言进行中时左侧展示只读摘要 */
    settingsSummaryVisible() {
      return this.phase === "position" || this.phase === "debate";
    },
    eventTextSummary() {
      const t = String(this.eventText || "").replace(/\s+/g, " ").trim();
      if (!t) return "（未填写）";
      return t.length > 120 ? `${t.slice(0, 120)}…` : t;
    },
    canStartPositionPhase() {
      return Array.isArray(this.topics) && this.topics.length > 0;
    },
    canConfirmExperts() {
      return this.selectedRolesList.length >= 2;
    },
    visibleThreadMessages() {
      return (this.threadMessages || []).filter(
        (m) => m && (m.kind === "role" || m.kind === "debate_summary")
      );
    },
    historyRecordMessages() {
      return (this.threadMessages || [])
        .filter((m) => m && (m.kind === "role" || m.kind === "debate_summary"))
        .map((m) => {
          if (m.kind === "role") {
            return {
              ...m,
              avatarText: this.roleInitial(m.name),
              avatarToneClass: this.roleAvatarToneClass(m.name)
            };
          }
          // debate_summary: 只需要标题/内容，展示由右侧面板决定
          return {
            ...m,
            title: m.title || "研讨总结",
            content: m.content || ""
          };
        });
    },
    showSettingsLoading() {
      return this.busy && ["idle", "experts", "topics", "position_done", "round_done"].includes(this.phase);
    },
    settingsLoadingText() {
      const map = {
        idle: "准备中…",
        experts: "正在获取专家角色…",
        topics: "正在处理议题…",
        position_done: "正在提交参数…",
        round_done: "正在处理下一轮…"
      };
      return map[this.phase] || "处理中…";
    },
    discussionStatusText() {
      if (this.phase === "position") return "观点发表中...";
      if (this.phase === "debate") return "角色讨论中...";
      if (this.phase === "position_done") return "观点发表完毕";
      if (this.phase === "round_done") return "角色讨论已完毕";
      return "";
    },
    discussionStatusClass() {
      if (this.phase === "position" || this.phase === "debate") return "is-running";
      if (this.phase === "position_done" || this.phase === "round_done") return "is-done";
      return "";
    }
  },
  watch: {
    active(val) {
      if (!val) {
        this.abortAllStreams();
        this.closeDiscussionChannel();
      } else {
        this.ensureDiscussionSseIfNeeded();
      }
    }
  },
  mounted() {
    this.setInitialPanelSizeByViewport();
    this.clampPanelSize();
    window.addEventListener("resize", this.clampPanelSize);
    if (this.active) this.ensureDiscussionSseIfNeeded();

    // 允许其他模块通过 window 事件触发（用于“突发事件 -> 触发讨论”）
    // 事件名：intelligence-assistant-trigger-event
    // detail: { text: string } 或直接传 string
    this._externalTriggerHandler = (e) => {
      const detail = e && e.detail;
      const text =
        typeof detail === "string"
          ? detail
          : (detail && (detail.text || detail.event || detail.message)) || "";
      this.triggerWithEventText(text, { autoStart: true });
    };
    window.addEventListener("intelligence-assistant-trigger-event", this._externalTriggerHandler);
    this._relatedEventSelectedHandler = (e) => {
      const detail = e && e.detail ? e.detail : null;
      const text =
        typeof detail === "string"
          ? detail
          : (detail && (detail.title || detail.text || detail.event)) || "";
      this.addRelationEvent(text);
    };
    window.addEventListener("related-event-selected", this._relatedEventSelectedHandler);
    this.fetchHistoryReferenceCandidates();
  },
  beforeDestroy() {
    window.removeEventListener("resize", this.clampPanelSize);
    this.endResizeDrag();
    if (this._externalTriggerHandler) {
      window.removeEventListener(
        "intelligence-assistant-trigger-event",
        this._externalTriggerHandler
      );
      this._externalTriggerHandler = null;
    }
    if (this._relatedEventSelectedHandler) {
      window.removeEventListener("related-event-selected", this._relatedEventSelectedHandler);
      this._relatedEventSelectedHandler = null;
    }
    this.abortAllStreams();
    this.closeDiscussionChannel();
  },
  methods: {
    addRelationEvent(text) {
      const t = String(text || "").replace(/\s+/g, " ").trim();
      if (!t) return;
      if (!Array.isArray(this.relationEvents)) this.relationEvents = [];
      if (this.relationEvents.includes(t)) return;
      this.relationEvents.push(t);
      if (this.relationEvents.length > 20) this.relationEvents.splice(0, this.relationEvents.length - 20);
    },
    removeRelationEvent(idx) {
      if (!Array.isArray(this.relationEvents)) return;
      const i = Number(idx);
      if (!Number.isInteger(i) || i < 0 || i >= this.relationEvents.length) return;
      this.relationEvents.splice(i, 1);
    },
    clearRelationEvents() {
      this.relationEvents = [];
    },
    setInitialPanelSizeByViewport() {
      // 初始化尺寸按需求：width 40vw, height 80vh
      this.panelWidth = Math.round(window.innerWidth * 0.4);
      this.panelHeight = Math.round(window.innerHeight * 0.8);
    },
    personaKey(p) {
      return `${p && p.name != null ? String(p.name) : "?"}`;
    },
    roleInitial(name) {
      const s = String(name || "?").trim();
      return s.slice(0, 1);
    },
    genChatId() {
      const a = Math.random().toString(36).slice(2, 10);
      const b = Date.now().toString(36);
      return `chat_${b}_${a}`;
    },
    closeDiscussionChannel() {
      const es = this.discussionEventSource;
      if (es) {
        try {
          es.close();
        } catch {
          /* ignore */
        }
      }
      this.discussionEventSource = null;
      this.discussionStreamChatId = "";
      this.channelConnected = false;
    },
    openDiscussionChannel() {
      if (!this.chatId || typeof EventSource === "undefined") return;
      if (
        this.discussionEventSource &&
        this.discussionStreamChatId === this.chatId
      ) {
        return;
      }
      this.closeDiscussionChannel();
      let es;
      try {
        es = new EventSource(getDiscussionStreamUrl(this.userId, this.chatId));
      } catch {
        return;
      }
      this.discussionEventSource = es;
      this.discussionStreamChatId = this.chatId;
      es.onopen = () => {
        this.channelConnected = true;
      };
      const onEsPayload = (e) => {
        // 组件关闭/销毁后忽略旧连接推送
        if (this._isBeingDestroyed || this._isDestroyed) return;
        const raw = (e && e.data) || "";
        const t = String(raw).trim();
        if (!t) return;
        const evt = parseDiscussionEventPayload(t);
        if (evt) this.ingestPushEvent(evt);
      };
      es.onmessage = onEsPayload;
      /** 后端若使用 event: xxx 而非默认 message，需单独订阅 */
      ["countries", "sse", "update", "push", "send", "chat", "data"].forEach((name) => {
        try {
          es.addEventListener(name, onEsPayload);
        } catch {
          /* ignore */
        }
      });
      es.onerror = () => {
        this.channelConnected = !!(es && es.readyState === EventSource.OPEN);
      };
    },
    /** 打开助手时即建立 GET /stream（需 chat_id，无则先生成） */
    ensureDiscussionSseIfNeeded() {
      if (!isDiscussionSseStreamEnabled() || !this.active) return;
      if (!this.chatId) this.chatId = this.genChatId();
      this.openDiscussionChannel();
    },
    steadyEvtKey(evt) {
      try {
        return JSON.stringify(evt);
      } catch {
        return `${evt && evt.code}-${evt && evt.msg}`;
      }
    },
    ingestPushEvent(evt) {
      if (!evt || typeof evt !== "object") return;
      // 兜底：避免 EventSource 旧回调打到已重置/销毁的实例上
      if (!this._pushDedupeAt || typeof this._pushDedupeAt !== "object") {
        this._pushDedupeAt = Object.create(null);
      }
      const k = this.steadyEvtKey(evt);
      const now = Date.now();
      const prev = this._pushDedupeAt[k];
      if (prev != null && now - prev < 2000) return;
      this.$set(this._pushDedupeAt, k, now);
      const keys = Object.keys(this._pushDedupeAt);
      if (keys.length > 100) {
        for (let i = 0; i < keys.length - 70; i += 1) {
          this.$delete(this._pushDedupeAt, keys[i]);
        }
      }
      this.routePushEvent(evt);
    },
    routePushEvent(evt) {
      const code = String(evt && evt.code);
      const msg = String((evt && evt.msg) || "");
      if (code === "21000") {
        // 新版流程不再使用 “role_choose/countries” 推送；保留兼容但忽略即可
        return;
      }
      if (code === "21002") {
        this.onTopicGenEvent(evt);
        return;
      }
      if (code === "21001") {
        if (this.phase === "debate" || msg.indexOf("debate") >= 0) {
          this.onDebateEvent(evt);
        } else {
          this.onPositionEvent(evt);
        }
        return;
      }
      // 兼容：部分后端在 debate 阶段仍可能推 21003/21004
      if (code === "21003" || code === "21004") {
        if (this.phase === "debate" || msg.indexOf("debate") >= 0) this.onDebateEvent(evt);
        else this.onPositionEvent(evt);
        return;
      }
      if (code === "21005" || code === "21006" || code === "21011") {
        this.onDebateEvent(evt);
        return;
      }
    },
    abortAllStreams() {
      if (this.abortCtl) {
        try {
          this.abortCtl.abort();
        } catch {
          /* ignore */
        }
      }
      this.abortCtl = null;
      this.streamConnected = false;
      this.typingLabel = "";
    },
    pushSystem(text) {
      this.threadMessages.push({
        id: `sys-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
        kind: "system",
        content: text
      });
    },
    pushUser(text) {
      this.threadMessages.push({
        id: `user-${Date.now()}`,
        kind: "user",
        content: text
      });
    },
    pushRoleMsg(partial) {
      const id = `role-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
      this.threadMessages.push({
        id,
        kind: "role",
        name: partial.name,
        identity: partial.identity || "",
        country: partial.country || "",
        content: partial.content || "",
        targetName: partial.targetName || "",
        messageId: partial.messageId || ""
      });
      return id;
    },
    ensureStreamingSummaryMessage() {
      const localId = this.streamingSummaryLocalId;
      const existing = localId ? this.getMessageByLocalId(localId) : null;
      if (existing && existing.kind === "debate_summary") return localId;
      const id = `summary-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
      this.threadMessages.push({
        id,
        kind: "debate_summary",
        title: "研讨总结",
        content: ""
      });
      this.streamingSummaryLocalId = id;
      return id;
    },
    appendStreamingSummaryContent(part) {
      const chunk = String(part || "");
      if (!chunk) return;
      const localId = this.ensureStreamingSummaryMessage();
      const msg = this.getMessageByLocalId(localId);
      if (!msg || msg.kind !== "debate_summary") return;
      msg.content = `${msg.content || ""}${chunk}`;
    },
    /** 讨论/角色气泡头像：按姓名稳定映射到一组配色（同一人始终同色） */
    roleAvatarToneIndex(name) {
      const s = String(name || "").trim() || "?";
      let h = 2166136261;
      for (let i = 0; i < s.length; i += 1) {
        h ^= s.charCodeAt(i);
        h = Math.imul(h, 16777619) >>> 0;
      }
      return h % 8;
    },
    roleAvatarToneClass(name) {
      return `role-av-tone-${this.roleAvatarToneIndex(name)}`;
    },
    /** 辩论发言开始：建立 pending 与首条气泡，供后续 21005 流式正文追加 */
    beginDebateSpeakerFromPayload(data) {
      let speak = "";
      if (data && typeof data === "object") {
        speak = String(data.speak_name || "").trim();
      } else if (typeof data === "string") {
        speak = data.trim();
      }
      this.typingLabel = speak;
      const roles = this.selectedRolesList;
      const hit = roles.find((r) => r.name === speak);
      this.pendingRoleBuffer = {
        name: speak || "发言人",
        identity: hit ? hit.identity : "",
        country: hit ? hit.country : "",
        targetName: ""
      };
      this.streamingMsgLocalId = "";
      this.ensureStreamingRoleMessage(this.pendingRoleBuffer);
    },
    getMessageByLocalId(localId) {
      const id = String(localId || "");
      if (!id) return null;
      for (let i = this.threadMessages.length - 1; i >= 0; i -= 1) {
        const m = this.threadMessages[i];
        if (m && m.id === id) return m;
      }
      return null;
    },
    ensureStreamingRoleMessage(base) {
      const existing = this.getMessageByLocalId(this.streamingMsgLocalId);
      if (existing && existing.kind === "role") return existing;
      const localId = this.pushRoleMsg({
        name: base.name,
        identity: base.identity,
        country: base.country,
        targetName: base.targetName || "",
        content: ""
      });
      this.streamingMsgLocalId = localId;
      return this.getMessageByLocalId(localId);
    },
    appendStreamingContent(text) {
      const chunk = String(text || "");
      if (!chunk) return;
      const m = this.ensureStreamingRoleMessage(
        this.pendingRoleBuffer || { name: "发言人", identity: "", country: "", targetName: "" }
      );
      if (!m) return;
      const prev = String(m.content || "");
      const next = `${prev}${chunk}`;
      this.$set(m, "content", next);
    },
    finalizeStreamingMessageId(messageId) {
      const mid = String(messageId || "").trim();
      if (!mid) return;
      const m = this.getMessageByLocalId(this.streamingMsgLocalId);
      if (m && m.kind === "role") {
        this.$set(m, "messageId", mid);
        this.trackRoundMessage(m.name, mid, m.content);
      } else {
        // 兜底：如果本地气泡未命中，退化到“补丁最后一条角色消息”
        this.patchLastRoleMessageId(mid);
      }
      this.streamingMsgLocalId = "";
    },
    patchLastRoleMessageId(messageId) {
      for (let i = this.threadMessages.length - 1; i >= 0; i -= 1) {
        const m = this.threadMessages[i];
        if (m.kind === "role") {
          this.$set(m, "messageId", messageId);
          this.trackRoundMessage(m.name, messageId, m.content);
          return;
        }
      }
    },
    trackRoundMessage(speaker, messageId, content) {
      if (!messageId) return;
      const preview = String(content || "").replace(/\s+/g, " ").slice(0, 72);
      this.lastRoundMessageIds.push({
        messageId,
        speaker: speaker || "角色",
        preview: preview.length ? `${preview}${String(content).length > 72 ? "…" : ""}` : "（无预览）"
      });
    },
    clampPanelSize() {
      const maxW = Math.min(1200, window.innerWidth - 32);
      const maxH = window.innerHeight - 32;
      this.panelWidth = Math.min(Math.max(this.panelWidth, 360), maxW);
      this.panelHeight = Math.min(Math.max(this.panelHeight, 320), maxH);
    },
    onResizePointerDown(e) {
      if (e.button !== 0) return;
      const shell = this.$refs.assistantShell;
      if (!shell) return;
      const rect = shell.getBoundingClientRect();
      this.resizeDrag = {
        startX: e.clientX,
        startY: e.clientY,
        startW: rect.width,
        startH: rect.height
      };
      const move = (ev) => this.onResizePointerMove(ev);
      const up = () => {
        document.removeEventListener("pointermove", move);
        document.removeEventListener("pointerup", up);
        document.removeEventListener("pointercancel", up);
        this.resizeDrag = null;
        this._resizeMove = null;
        this._resizeUp = null;
        document.documentElement.style.cursor = "";
        document.documentElement.style.userSelect = "";
      };
      document.addEventListener("pointermove", move);
      document.addEventListener("pointerup", up);
      document.addEventListener("pointercancel", up);
      this._resizeMove = move;
      this._resizeUp = up;
      document.documentElement.style.cursor = "nwse-resize";
      document.documentElement.style.userSelect = "none";
    },
    onResizePointerMove(e) {
      if (!this.resizeDrag) return;
      const { startX, startY, startW, startH } = this.resizeDrag;
      const dx = e.clientX - startX;
      const dy = e.clientY - startY;
      const maxW = Math.min(1200, window.innerWidth - 32);
      const maxH = window.innerHeight - 32;
      let w = Math.round(startW - dx);
      let h = Math.round(startH - dy);
      w = Math.max(360, Math.min(maxW, w));
      h = Math.max(320, Math.min(maxH, h));
      this.panelWidth = w;
      this.panelHeight = h;
    },
    endResizeDrag() {
      const move = this._resizeMove;
      const up = this._resizeUp;
      if (move && up) {
        document.removeEventListener("pointermove", move);
        document.removeEventListener("pointerup", up);
        document.removeEventListener("pointercancel", up);
        this.resizeDrag = null;
        this._resizeMove = null;
        this._resizeUp = null;
        document.documentElement.style.cursor = "";
        document.documentElement.style.userSelect = "";
      }
    },
    resetSession() {
      this.abortAllStreams();
      this.chatId = "";
      this.eventText = "";
      this.eventDraft = "";
      this.currentSessionTitle = "新会话";
      this.phase = "idle";
      this.busy = false;
      this.errorBanner = "";
      this.threadMessages = [];
      this.activeHistoryChatId = "";
      this.historyReadonly = false;
      this.selectedHistoryChatIds = [];
      this.relationEvents = [];
      this.experts = [];
      this.selectedExpertMap = {};
      this.topics = [];
      this.selectedTopicIndex = 0;
      this.topicPickMode = "";
      this.manualDebateTopic = "";
      this.manualTopicSubmitted = false;
      this.lastRoundMessageIds = [];
      this.roundNextConfirmModalOpen = false;
      this.typingLabel = "";
      this.pendingRoleBuffer = null;
      this._pushDedupeAt = Object.create(null);
      this.closeDiscussionChannel();
      this.$nextTick(() => {
        this.ensureDiscussionSseIfNeeded();
        this.scrollToBottom();
      });
    },
    fillDemoEvent() {
      this.eventDraft = DEMO_EVENT;
    },
    onFooterEnter() {
      if (this.phase === "idle") this.startFromIdle();
    },
    async startFromIdle() {
      const text = String(this.eventDraft || "").trim();
      if (!text) return;
      this.errorBanner = "";
      this.showHistoryPanel = false;
      this.historyReadonly = false;
      this.activeHistoryChatId = "";
      this.currentSessionTitle = "新会话";
      this.threadMessages = [];
      this.lastRoundMessageIds = [];
      this.eventText = text;
      // 若已在打开时建立 SSE，则复用当前 chat_id，避免重复建立连接
      if (!this.chatId) this.chatId = this.genChatId();
      this.openDiscussionChannel();
      this.pushUser(text);
      this.eventDraft = "";
      this.phase = "experts";
      this.pushSystem(`已创建会话 ${this.chatIdShort}，正在获取专家角色列表…`);
      this.$nextTick(this.scrollToBottom);
      await this.loadExperts();
    },
    async fetchHistoryReferenceCandidates() {
      this.historyReferenceLoading = true;
      this.historyReferenceError = "";
      try {
        const res = await getChatHistoryList(this.userId);
        const code = String(res && res.code);
        if (code !== "21009") {
          throw new Error(res.msg || res.message || "获取历史会话失败");
        }
        this.historySessions = Array.isArray(res.data) ? res.data : [];
      } catch (e) {
        this.historySessions = [];
        this.historyReferenceError = e && e.message ? String(e.message) : String(e || "历史会话查询失败");
      } finally {
        this.historyReferenceLoading = false;
      }
    },
    baseSendBody(extra) {
      const text = this.eventText;
      const payload = extra || {};
      return {
        user_id: this.userId,
        chat_id: this.chatId,
        event: text,
        history_chat_ids: Array.isArray(payload.history_chat_ids) ? payload.history_chat_ids : [],
        chat_mode: CHAT_MODE,
        ...payload
      };
    },
    /**
     * 外部模块触发接口（window 事件）。
     * 若当前处于“idle”且组件处于打开状态，则自动进入角色选择流程。
     */
    triggerWithEventText(text, { autoStart } = {}) {
      const t = String(text || "").trim();
      if (!t) return;

      // 把草稿写入，确保用户可见并能点“开始讨论”
      this.eventDraft = t;

      // 仅在 idle 阶段自动开始，避免打断用户当前讨论
      if (this.phase !== "idle") return;
      if (!autoStart) return;
      if (!this.active) return;
      this.startFromIdle();
    },
    async loadExperts() {
      this.errorBanner = "";
      this.busy = true;
      try {
        const res = await getPersonas();
        const code = String(res && res.code);
        if (code !== "20000") {
          throw new Error(res.msg || res.message || "获取专家列表失败");
        }
        const list = Array.isArray(res.data) ? res.data : [];
        this.experts = list;
        const map = {};
        for (let i = 0; i < list.length; i += 1) {
          // 默认勾选前 2 位，满足“至少两位”
          map[this.personaKey(list[i])] = i < 2;
        }
        this.selectedExpertMap = map;
        this.pushSystem(`已加载 ${list.length} 位专家，请选择至少 2 位参会。`);
      } catch (e) {
        this.errorBanner = e.message || String(e);
        this.pushSystem(`获取专家列表失败：${this.errorBanner}`);
      } finally {
        this.busy = false;
        this.$nextTick(this.scrollToBottom);
      }
    },
    async confirmExpertsAndSave() {
      const roles = this.selectedRolesList;
      if (roles.length < 2) return;
      this.errorBanner = "";
      this.busy = true;
      try {
        const res = await postSaveRoles({
          user_id: this.userId,
          chat_id: this.chatId,
          roles,
          relation_event: Array.isArray(this.relationEvents) ? this.relationEvents.slice() : []
        });
        const code = String(res && res.code);
        if (code !== "21008") {
          throw new Error(res.msg || res.message || "保存角色失败");
        }
        this.pushSystem("角色已保存，请先选择议题方式（手动录入 / AI 生成）。");
        this.phase = "topics";
        this.topics = [];
        this.manualDebateTopic = "";
        this.manualTopicSubmitted = false;
        this.topicPickMode = "";
      } catch (e) {
        this.errorBanner = e.message || String(e);
        this.pushSystem(`保存角色失败：${this.errorBanner}`);
      } finally {
        this.busy = false;
        this.$nextTick(this.scrollToBottom);
      }
    },
    async runTopicGenerationStream(extraBody = {}) {
      this.busy = true;
      this.abortAllStreams();
      const ctl = new AbortController();
      this.abortCtl = ctl;
      this.streamConnected = true;
      try {
        const evt = await postDiscussionSend(
          this.baseSendBody({ stage: "topic_generation", ...extraBody }),
          ctl.signal
        );
        if (evt) {
          // topic_generation 改为“接口一次性返回”，直接处理并兼容不同返回结构
          this.onTopicGenEvent(evt);
        }
      } catch (e) {
        if (e.name === "AbortError") return;
        this.errorBanner = e.message || String(e);
        this.pushSystem(`选题生成失败：${this.errorBanner}`);
      } finally {
        this.streamConnected = false;
        this.busy = false;
        this.abortCtl = null;
        this.selectedTopicIndex = 0;
        this.$nextTick(this.scrollToBottom);
      }
    },
    onTopicGenEvent(evt, options = {}) {
      const allowWhenManual = !!(options && options.allowWhenManual);
      if (this.phase !== "topics") return;
      if (this.topicPickMode === "manual" && !allowWhenManual) return;

      const code = String(evt && evt.code);
      if (code !== "21002") return;

      // 文档：/api/send topic_generation 直接返回 {code:"21002", data:[...]}，
      // msg 字段可能不存在；这里按 data 是否为数组来兼容。
      const list = Array.isArray(evt.data) ? evt.data : [];
      const topics =
        list.length > 0
          ? list
          : evt.data && typeof evt.data === "object" && Array.isArray(evt.data.topics)
            ? evt.data.topics
            : [];

      if (!topics.length) return;
      this.topics = topics;
      if (this.topicPickMode === "llm") {
        this.llmTopicsCache = topics.slice();
      }
      this.selectedTopicIndex = 0;
      if (!this.currentSessionTitle || this.currentSessionTitle === "新会话") {
        this.currentSessionTitle = String(topics[0] || "新会话");
      }
      this.pushSystem(`已生成 ${topics.length} 条选题，请选择一条继续。`);
    },
    onTopicPickModeChange() {
      this.abortAllStreams();
      this.busy = false;
      this.streamConnected = false;
      this.abortCtl = null;
      this.errorBanner = "";
      if (this.topicPickMode === "manual") {
        this.llmTopicsCache = Array.isArray(this.topics) ? this.topics.slice() : [];
        this.topics = [];
        this.selectedTopicIndex = 0;
        this.manualTopicSubmitted = false;
        return;
      }
      if (this.topicPickMode === "llm") {
        if (Array.isArray(this.llmTopicsCache) && this.llmTopicsCache.length) {
          this.topics = this.llmTopicsCache.slice();
          this.selectedTopicIndex = 0;
        }
        return;
      }
    },
    async confirmManualDebateTopic() {
      const s = String(this.manualDebateTopic || "").trim();
      if (!s) return;
      this.errorBanner = "";
      this.busy = true;
      this.abortAllStreams();
      const ctl = new AbortController();
      this.abortCtl = ctl;
      this.streamConnected = true;
      try {
        const evt = await postDiscussionSend(
          this.baseSendBody({ stage: "topic_generation", debate_topic: s }),
          ctl.signal
        );
        if (evt) this.onTopicGenEvent(evt, { allowWhenManual: true });
        if (!this.topics.length) {
          this.topics = [s];
          this.selectedTopicIndex = 0;
          this.pushSystem("已采用自定义议题。");
        }
        this.manualTopicSubmitted = true;
      } catch (e) {
        if (e.name === "AbortError") return;
        this.errorBanner = e.message || String(e);
        this.pushSystem(`提交自定义议题失败：${this.errorBanner}`);
      } finally {
        this.streamConnected = false;
        this.busy = false;
        this.abortCtl = null;
        this.$nextTick(this.scrollToBottom);
      }
    },
    currentDebateTopic() {
      const t = this.topics[this.selectedTopicIndex];
      return t != null ? String(t) : "";
    },
    async startPositionPhase() {
      const debateTopic = this.currentDebateTopic();
      if (!debateTopic) return;
      const historyChatIds = Array.from(
        new Set(
          (this.selectedHistoryChatIds || [])
            .map((x) => String(x || "").trim())
            .filter(Boolean)
        )
      );
      this.lastRoundMessageIds = [];
      this.phase = "position";
      this.errorBanner = "";
      this.pushSystem("各角色正按选定选题依次阐述观点…");
      this.$nextTick(this.scrollToBottom);
      this.busy = true;
      this.abortAllStreams();
      const ctl = new AbortController();
      this.abortCtl = ctl;
      this.streamConnected = true;
      this.pendingRoleBuffer = null;
      this.streamingMsgLocalId = "";
      this.streamingSummaryLocalId = "";
      try {
        await postDiscussionSendStream(
          this.baseSendBody({
            stage: "position",
            debate_topic: debateTopic,
            history_chat_ids: historyChatIds
          }),
          (evt) => this.ingestPushEvent(evt),
          ctl.signal
        );
      } catch (e) {
        if (e.name === "AbortError") return;
        this.errorBanner = e.message || String(e);
        this.pushSystem(`观点阶段失败：${this.errorBanner}`);
      } finally {
        this.streamConnected = false;
        this.busy = false;
        this.abortCtl = null;
        this.typingLabel = "";
        this.pendingRoleBuffer = null;
        this.phase = "position_done";
        this.$nextTick(this.scrollToBottom);
      }
    },
    onPositionEvent(evt) {
      const code = String(evt && evt.code);
      if (code === "21001" && evt.msg === "role speak") {
        const name = String(evt.data || "").trim();
        this.typingLabel = name;
        this.pendingRoleBuffer = { name, identity: "", country: "" };
        this.streamingMsgLocalId = "";
        const roles = this.selectedRolesList;
        const hit = roles.find((r) => r.name === name);
        if (hit) {
          this.pendingRoleBuffer.identity = hit.identity;
          this.pendingRoleBuffer.country = hit.country;
        }
        // 21001 视为该角色一次发言开始：先创建一个气泡，后续 21003 只追加到此气泡
        this.ensureStreamingRoleMessage(this.pendingRoleBuffer);
        return;
      }
      if (code === "21003" && evt.msg === "role position") {
        const content = String(evt.data || "");
        // 21003 为流式分片：追加到同一个气泡里（避免“蹦字多气泡”）
        this.appendStreamingContent(content);
        this.typingLabel = "";
        this.$nextTick(this.scrollToBottom);
        return;
      }
      if (code === "21004" && evt.msg === "role position message id") {
        const mid = String(evt.data || "").trim();
        if (mid) this.finalizeStreamingMessageId(mid);
        this.pendingRoleBuffer = null;
        this.$nextTick(this.scrollToBottom);
      }
    },
    async startDebatePhase() {
      const debateTopic = this.currentDebateTopic();
      if (!debateTopic) return;
      this.phase = "debate";
      this.errorBanner = "";
      this.pushSystem(
        `进入交叉讨论（turns=${this.debateTurns}，reply_chain_length=${this.replyChainLength}）…`
      );
      this.$nextTick(this.scrollToBottom);
      this.busy = true;
      this.abortAllStreams();
      const ctl = new AbortController();
      this.abortCtl = ctl;
      this.streamConnected = true;
      this.pendingRoleBuffer = null;
      this.streamingMsgLocalId = "";
      try {
        await postDiscussionSendStream(
          this.baseSendBody({
            stage: "debate",
            debate_topic: debateTopic,
            turns: this.debateTurns,
            reply_chain_length: this.replyChainLength
          }),
          (evt) => this.ingestPushEvent(evt),
          ctl.signal
        );
      } catch (e) {
        if (e.name === "AbortError") return;
        this.errorBanner = e.message || String(e);
        this.pushSystem(`讨论阶段失败：${this.errorBanner}`);
      } finally {
        this.streamConnected = false;
        this.busy = false;
        this.abortCtl = null;
        this.typingLabel = "";
        this.pendingRoleBuffer = null;
        this.phase = "round_done";
        this.pushSystem("本轮讨论已结束。可选择「再讨论一轮」继续。");
        this.$nextTick(this.scrollToBottom);
      }
    },
    onDebateEvent(evt) {
      const code = String(evt && evt.code);
      const evtMsg = String((evt && evt.msg) || "").trim();
      // 新版：21005 + role debate speaker 先推发言人/被@对象，再推 21005 role debate 正文分片
      if (code === "21005" && evtMsg.toLowerCase() === "role debate speaker") {
        this.beginDebateSpeakerFromPayload(evt.data);
        return;
      }
      if (code === "21001" && evtMsg.indexOf("debate") >= 0) {
        this.beginDebateSpeakerFromPayload(evt.data);
        return;
      }
      // 讨论内容分片：优先匹配 21005，也兼容 21003（后端可能复用）
      if (
        (code === "21005" && evtMsg === "role debate") ||
        (code === "21003" && (evt.msg === "role debate" || evt.msg === "role position"))
      ) {
        const content = String(evt.data || "");
        // 21005 为流式分片：追加到同一个气泡里
        this.appendStreamingContent(content);
        this.typingLabel = "";
        this.$nextTick(this.scrollToBottom);
        return;
      }
      // 讨论消息 ID：优先匹配 21006，也兼容 21004（后端可能复用）
      if (
        (code === "21006" && evt.msg === "role debate message id") ||
        (code === "21004" && (evt.msg === "role debate message id" || evt.msg === "role position message id"))
      ) {
        const mid = String(evt.data || "").trim();
        if (mid) this.finalizeStreamingMessageId(mid);
        this.pendingRoleBuffer = null;
        this.$nextTick(this.scrollToBottom);
        return;
      }
      if (code === "21011" && evtMsg.toLowerCase() === "debate summary") {
        this.appendStreamingSummaryContent(evt.data);
        this.$nextTick(this.scrollToBottom);
      }
    },
    onAnotherRoundClick() {
      if (this.busy) return;
      this.roundNextConfirmModalOpen = true;
    },
    async confirmContinueNextRound() {
      this.roundNextConfirmModalOpen = false;
      this.lastRoundMessageIds = [];
      await this.continueNextRoundWithoutDelete();
    },
    async continueNextRoundWithoutDelete() {
      this.topicPickMode = "";
      this.manualDebateTopic = "";
      this.manualTopicSubmitted = false;
      this.phase = "topics";
      this.topics = [];
      this.selectedTopicIndex = 0;
      this.pushSystem("请先选择下一轮议题方式（手动录入 / AI 生成）。");
      this.$nextTick(this.scrollToBottom);
    },
    toggleHistoryPanel() {
      this.showHistoryPanel = !this.showHistoryPanel;
      if (this.showHistoryPanel) {
        this.fetchHistorySessions();
        return;
      }
      if (this.historyReadonly || this.phase === "history_view") {
        this.historyReadonly = false;
        this.activeHistoryChatId = "";
        this.chatId = "";
        this.threadMessages = [];
        this.phase = "idle";
        this.currentSessionTitle = "新会话";
      }
    },
    async fetchHistorySessions() {
      this.historyLoading = true;
      try {
        const res = await getChatHistoryList(this.userId);
        const code = String(res && res.code);
        if (code !== "21009") {
          throw new Error(res.msg || res.message || "获取历史会话失败");
        }
        this.historySessions = Array.isArray(res.data) ? res.data : [];
      } catch (e) {
        this.errorBanner = e.message || String(e);
      } finally {
        this.historyLoading = false;
      }
    },
    async openHistorySession(item) {
      const chatId = String(item && item.chat_id ? item.chat_id : "").trim();
      if (!chatId) return;
      this.historyContextLoading = true;
      try {
        const res = await getChatHistoryContext(this.userId, chatId);
        const code = String(res && res.code);
        if (code !== "21009") {
          throw new Error(res.msg || res.message || "获取历史会话详情失败");
        }
        const rows = Array.isArray(res.data) ? res.data : [];
        this.threadMessages = rows.map((x, idx) => {
          const speakName = x && x.speak_name ? String(x.speak_name) : "";
          const content = x && x.content != null ? String(x.content) : "";
          const targetName = x && x.target_name ? String(x.target_name) : "";
          const messageId = x && x.message_id ? String(x.message_id) : "";

          // 后端约定：summary_agent 对应“研讨总结”信息（非气泡展示）
          if (speakName.trim() === "summary_agent") {
            return {
              id: `history-${chatId}-${idx}`,
              kind: "debate_summary",
              title: "研讨总结",
              content
            };
          }

          return {
            id: `history-${chatId}-${idx}`,
            kind: "role",
            name: speakName.trim() || "角色",
            identity: "",
            country: "",
            content,
            targetName,
            messageId
          };
        });
        this.chatId = chatId;
        this.activeHistoryChatId = chatId;
        this.currentSessionTitle = String((item && item.topic) || "历史会话");
        this.historyReadonly = true;
        this.phase = "history_view";
      } catch (e) {
        this.errorBanner = e.message || String(e);
      } finally {
        this.historyContextLoading = false;
        this.$nextTick(this.scrollToBottom);
      }
    },
    deleteHistorySession(item) {
      const chatId = String(item && item.chat_id ? item.chat_id : "").trim();
      if (!chatId) return;
      this.pendingHistoryDeleteItem = item;
      this.historyDeleteConfirmOpen = true;
    },
    cancelHistoryDelete() {
      this.historyDeleteConfirmOpen = false;
      this.pendingHistoryDeleteItem = null;
    },
    async confirmHistoryDelete() {
      const item = this.pendingHistoryDeleteItem;
      this.cancelHistoryDelete();
      const chatId = String(item && item.chat_id ? item.chat_id : "").trim();
      if (!chatId) return;
      try {
        const res = await postDelChatHistory({ user_id: this.userId, chat_id: chatId });
        const code = String(res && res.code);
        if (code !== "21009") {
          throw new Error(res.msg || res.message || "删除历史会话失败");
        }
        this.historySessions = (this.historySessions || []).filter((x) => String(x.chat_id) !== chatId);
        if (this.activeHistoryChatId === chatId) {
          this.activeHistoryChatId = "";
          this.threadMessages = [];
          this.historyReadonly = false;
          this.phase = "idle";
          this.currentSessionTitle = "新会话";
        }
      } catch (e) {
        this.errorBanner = e.message || String(e);
      }
    },
    scrollToBottom() {
      const el = this.$refs.messageList;
      if (el) el.scrollTop = el.scrollHeight;
    }
  }
};
</script>

<style>
.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

.assistant-shell {
  position: relative;
  box-sizing: border-box;
  overflow: hidden;
  border-radius: 10px;
}

.assistant-resize-handle {
  position: absolute;
  left: 0;
  top: 0;
  z-index: 6;
  width: 18px;
  height: 18px;
  cursor: nwse-resize;
  touch-action: none;
  background:
    linear-gradient(
      135deg,
      transparent 45%,
      rgba(120, 200, 255, 0.55) 45%,
      rgba(120, 200, 255, 0.55) 48%,
      transparent 48%
    ),
    linear-gradient(
      135deg,
      transparent 58%,
      rgba(120, 200, 255, 0.35) 58%,
      rgba(120, 200, 255, 0.35) 61%,
      transparent 61%
    );
  border-radius: 10px 0 0 0;
}

.assistant.card {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  padding: 0;
  overflow: hidden;
  background: var(--bg-card);
  border: 1px solid var(--line-main);
  border-radius: 10px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.35);
}

.assistant-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 12px 10px 22px;
  border-bottom: 1px solid rgba(28, 111, 184, 0.45);
  flex-shrink: 0;
}

.member-strip {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px 8px 22px;
  border-bottom: 1px solid rgba(28, 111, 184, 0.35);
  background: rgba(4, 20, 44, 0.55);
  overflow-x: auto;
  max-width: 100%;
}

.member-strip-label {
  font-size: 11px;
  font-weight: 700;
  color: #9ecff0;
  flex-shrink: 0;
  letter-spacing: 0.5px;
}

.member-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  min-width: 0;
}

.member-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px 10px 3px 4px;
  border-radius: 999px;
  border: 1px solid rgba(60, 120, 180, 0.55);
  background: rgba(6, 32, 64, 0.65);
  font-size: 11px;
  color: #dff6ff;
  max-width: 220px;
}

.member-chip-av {
  width: 22px;
  height: 22px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 800;
  background: rgba(180, 120, 255, 0.15);
  color: #e6d4ff;
  flex-shrink: 0;
}

.member-chip-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.assistant-title-line {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.assistant-title {
  font-size: 15px;
  font-weight: 800;
  color: #e8f6ff;
  letter-spacing: 0.5px;
}

.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 600;
  padding: 3px 11px;
  border-radius: 999px;
  line-height: 1.2;
}

.status-badge.is-ok {
  border: 1px solid rgba(96, 222, 143, 0.65);
  color: #7ef0b8;
  background: rgba(8, 48, 32, 0.45);
}

.status-badge.is-off {
  border: 1px solid rgba(255, 109, 109, 0.55);
  color: #ffb4b4;
  background: rgba(56, 12, 12, 0.4);
}

.status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: currentColor;
  box-shadow: 0 0 8px currentColor;
}

.assistant-meta {
  margin-top: 6px;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  font-family: Consolas, "Courier New", ui-monospace, monospace;
  font-size: 11px;
  color: var(--font-muted);
  opacity: 0.92;
  word-break: break-all;
}

.meta-chat {
  flex: 1 1 100%;
  min-width: 0;
  max-width: 100%;
  opacity: 0.85;
}

.step-rail {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}

.step-chip {
  font-size: 11px;
  font-weight: 600;
  padding: 3px 8px;
  border-radius: 999px;
  border: 1px solid rgba(60, 120, 180, 0.45);
  color: var(--font-muted);
  background: rgba(6, 28, 56, 0.5);
}

.step-chip.done {
  border-color: rgba(96, 222, 143, 0.45);
  color: #9feec9;
}

.step-chip.current {
  border-color: rgba(123, 200, 255, 0.85);
  color: #dff6ff;
  background: rgba(12, 60, 110, 0.55);
}

.assistant-head-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.btn-outline {
  border: 1px solid rgba(47, 136, 202, 0.85);
  background: rgba(8, 40, 88, 0.65);
  color: #d4f0ff;
  border-radius: 6px;
  padding: 6px 12px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
}

.btn-outline:hover:not(:disabled) {
  border-color: rgba(123, 200, 255, 0.95);
  background: rgba(12, 52, 100, 0.85);
}

.btn-refresh {
  width: 32px;
  height: 32px;
  padding: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  line-height: 1;
}

.btn-history-on {
  border-color: rgba(123, 200, 255, 0.95);
  background: rgba(16, 78, 145, 0.8);
}

.btn-close {
  width: 32px;
  height: 32px;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid rgba(47, 136, 202, 0.85);
  background: rgba(8, 40, 88, 0.65);
  color: #d4f0ff;
  border-radius: 6px;
  font-size: 18px;
  line-height: 1;
  cursor: pointer;
}

.btn-close:hover {
  border-color: rgba(123, 200, 255, 0.95);
  color: #fff;
}

.btn-primary {
  border: 1px solid rgba(64, 180, 255, 0.55);
  background: linear-gradient(135deg, #1083d3, #0e4d9d);
  color: #fff;
  border-radius: 8px;
  padding: 8px 14px;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  box-shadow: 0 2px 12px rgba(16, 131, 211, 0.28);
}

.btn-primary:hover:not(:disabled) {
  filter: brightness(1.06);
}

.btn-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-ghost {
  border: 1px dashed rgba(123, 200, 255, 0.45);
  background: rgba(8, 40, 88, 0.35);
  color: #c8e9ff;
  border-radius: 8px;
  padding: 6px 12px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
}

.btn-ghost:hover {
  border-color: rgba(123, 200, 255, 0.85);
}

.assistant-body {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: rgba(3, 16, 38, 0.35);
}

.assistant-workspace {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: row;
  align-items: stretch;
}

.settings-column {
  width: 40%;
  min-width: 200px;
  max-width: 380px;
  min-height: 0;
  flex-shrink: 0;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 8px 10px 10px 12px;
  border-right: 1px solid rgba(28, 111, 184, 0.35);
  background: rgba(3, 20, 44, 0.5);
}

.settings-column-head {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding-bottom: 4px;
  border-bottom: 1px solid rgba(28, 111, 184, 0.25);
}

.settings-column-title {
  font-size: 12px;
  font-weight: 800;
  color: #9ecff0;
  letter-spacing: 0.5px;
}

.settings-column-sub {
  font-size: 11px;
  color: var(--font-muted);
  line-height: 1.35;
}

.settings-loading-inline {
  margin-top: 6px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  color: #9ecff0;
}

.settings-loading-text {
  opacity: 0.95;
}

.settings-column .member-strip {
  margin: 0;
  padding: 6px 8px;
  border-radius: 8px;
  border: 1px solid rgba(28, 111, 184, 0.3);
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
}

.discussion-column {
  flex: 1;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: rgba(2, 12, 28, 0.25);
}

.discussion-column-head {
  flex-shrink: 0;
  padding: 8px 12px;
  border-bottom: 1px solid rgba(28, 111, 184, 0.3);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.discussion-column-title {
  font-size: 12px;
  font-weight: 800;
  color: #b8e6ff;
  letter-spacing: 0.5px;
}

.discussion-status-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
  line-height: 1;
  white-space: nowrap;
}

.discussion-status-badge.is-running {
  color: #e9f8ff;
  border: 1px solid rgba(88, 193, 255, 0.72);
  background: linear-gradient(90deg, rgba(14, 85, 142, 0.5), rgba(13, 122, 194, 0.35));
}

.discussion-status-badge.is-done {
  color: #e8ffef;
  border: 1px solid rgba(88, 210, 130, 0.72);
  background: linear-gradient(90deg, rgba(22, 112, 64, 0.5), rgba(34, 147, 88, 0.32));
}

.discussion-status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #8fe2ff;
  box-shadow: 0 0 0 0 rgba(143, 226, 255, 0.7);
  animation: discussionStatusPulse 1.2s ease-out infinite;
}

@keyframes discussionStatusPulse {
  0% {
    box-shadow: 0 0 0 0 rgba(143, 226, 255, 0.65);
  }
  70% {
    box-shadow: 0 0 0 8px rgba(143, 226, 255, 0);
  }
  100% {
    box-shadow: 0 0 0 0 rgba(143, 226, 255, 0);
  }
}

.discussion-empty-hint {
  flex: 1;
  padding: 20px 16px;
  font-size: 13px;
  color: var(--font-muted);
  line-height: 1.55;
}

.discussion-empty-sub {
  margin-top: 8px;
  font-size: 12px;
  opacity: 0.88;
}

.discussion-column .message-list {
  flex: 1;
  min-height: 0;
}

.summary-row {
  margin: 10px 8px 4px;
  padding: 10px 12px;
  border: 1px solid rgba(94, 186, 255, 0.42);
  border-radius: 10px;
  background: linear-gradient(180deg, rgba(13, 64, 112, 0.42), rgba(8, 34, 68, 0.5));
}

.summary-title {
  font-size: 12px;
  font-weight: 800;
  color: #a7deff;
  letter-spacing: 0.3px;
  margin-bottom: 6px;
}

.summary-content {
  font-size: 13px;
  line-height: 1.65;
  color: #e9f7ff;
  white-space: pre-wrap;
  word-break: break-word;
}

.idle-input-block {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: auto;
  padding-top: 8px;
  border-top: 1px solid rgba(28, 111, 184, 0.25);
}

.settings-summary.card-like {
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid rgba(28, 111, 184, 0.4);
  background: rgba(5, 26, 52, 0.65);
}

.settings-summary-title {
  font-size: 12px;
  font-weight: 800;
  color: #9ecff0;
  margin-bottom: 8px;
}

.settings-summary-block {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 10px;
  font-size: 12px;
  line-height: 1.45;
}

.settings-summary-block:last-child {
  margin-bottom: 0;
}

.settings-summary-block .k {
  font-size: 10px;
  font-weight: 700;
  color: var(--font-muted);
  text-transform: uppercase;
  letter-spacing: 0.4px;
}

.settings-summary-block .v {
  color: #dff6ff;
  word-break: break-word;
}

.topic-mode-toggle {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin: 8px 0 10px;
}

.topic-mode-option {
  cursor: pointer;
  font-size: 11px;
  font-weight: 700;
  padding: 6px 10px;
  border-radius: 8px;
  border: 1px solid rgba(60, 120, 180, 0.55);
  color: #b8daf5;
  background: rgba(6, 32, 64, 0.45);
  user-select: none;
}

.topic-mode-option.on {
  border-color: rgba(123, 200, 255, 0.95);
  background: rgba(23, 90, 160, 0.45);
  color: #fff;
}

.topic-manual-input {
  width: 100%;
  box-sizing: border-box;
  min-height: 88px;
  resize: vertical;
  padding: 8px 10px;
  font-size: 12px;
  line-height: 1.45;
  color: var(--font-main);
  border: 1px solid rgba(45, 109, 165, 0.95);
  border-radius: 8px;
  background: #062a55;
}

.topic-manual-input:focus {
  outline: none;
  border-color: var(--accent);
}

.strip-actions-inline {
  margin-top: 6px;
}

.strip-actions-topic {
  margin-top: 0;
  margin-bottom: 8px;
}

.btn-topic-refresh {
  border: none;
  background: transparent;
  padding: 0;
  min-height: auto;
  font-size: 11px;
  line-height: 1.3;
  color: #7fc4ef;
  cursor: pointer;
}

.btn-topic-refresh:hover:not(:disabled) {
  color: #a6dcff;
  text-decoration: underline;
}

.btn-topic-refresh:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  text-decoration: none;
}

.settings-column .phase-strip {
  margin: 0;
  max-height: none;
  flex: 1;
  min-height: 0;
}

.status-banner {
  margin: 10px 12px 0;
  padding: 8px 14px;
  text-align: center;
  font-size: 12px;
  color: #b8daf5;
  border-radius: 999px;
  border: 1px solid rgba(200, 140, 80, 0.45);
  background: rgba(18, 36, 58, 0.85);
}

.status-banner.is-error {
  border-color: rgba(255, 109, 109, 0.55);
  color: #ffc9c9;
  background: rgba(56, 12, 12, 0.4);
}

.phase-strip {
  flex-shrink: 0;
  max-height: 38%;
  overflow: auto;
  margin: 8px 10px 0;
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid rgba(28, 111, 184, 0.4);
  background: rgba(5, 26, 52, 0.65);
}

.strip-title {
  font-size: 13px;
  font-weight: 700;
  color: #e8f6ff;
  margin-bottom: 6px;
}

.strip-hint {
  font-size: 12px;
  color: var(--font-muted);
  line-height: 1.45;
  margin: 0 0 8px;
}

.strip-actions {
  margin-top: 10px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.history-ref-box {
  margin-top: 10px;
  border: 1px solid rgba(45, 109, 165, 0.55);
  border-radius: 8px;
  background: rgba(4, 22, 44, 0.45);
  padding: 8px;
}

.history-ref-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.history-ref-title-row {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.history-ref-title {
  font-size: 12px;
  font-weight: 700;
  color: #dff6ff;
}

.history-help {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  border: 1px solid rgba(123, 200, 255, 0.65);
  color: #9ecff0;
  font-size: 11px;
  font-weight: 700;
  line-height: 1;
  cursor: help;
  flex-shrink: 0;
}

.history-help-tip {
  position: absolute;
  left: 50%;
  top: calc(100% + 6px);
  transform: translateX(-50%);
  min-width: 220px;
  max-width: 260px;
  padding: 7px 9px;
  border-radius: 8px;
  border: 1px solid rgba(45, 109, 165, 0.9);
  background: rgba(5, 24, 48, 0.96);
  color: #dff6ff;
  font-size: 11px;
  font-weight: 500;
  line-height: 1.45;
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.15s ease;
  z-index: 4;
}

.history-help:hover .history-help-tip,
.history-help:focus .history-help-tip,
.history-help:focus-within .history-help-tip {
  opacity: 1;
}

.history-ref-refresh {
  border: 1px solid rgba(47, 136, 202, 0.75);
  background: rgba(8, 40, 88, 0.55);
  color: #d4f0ff;
  border-radius: 6px;
  padding: 3px 10px;
  font-size: 12px;
  cursor: pointer;
}

.history-ref-refresh:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.history-ref-loading,
.history-ref-empty {
  margin-top: 8px;
  font-size: 12px;
  color: var(--font-muted);
}

.history-ref-error {
  margin-top: 8px;
  font-size: 12px;
  color: #ffb3b3;
  cursor: pointer;
  text-decoration: underline;
  text-underline-offset: 2px;
}

.history-ref-table-wrap {
  margin-top: 8px;
  max-height: 90px;
  overflow: auto;
}

.context-ref-box {
  margin-top: 8px;
  border: 1px solid rgba(45, 109, 165, 0.55);
  border-radius: 8px;
  background: rgba(4, 22, 44, 0.45);
  padding: 8px;
}

.context-ref-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.context-ref-title {
  font-size: 12px;
  font-weight: 700;
  color: #dff6ff;
}

.context-ref-list {
  margin-top: 8px;
  height: 8vh;
  overflow: auto;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.context-ref-item {
  display: flex;
  align-items: center;
  gap: 6px;
  border: 1px solid rgba(45, 109, 165, 0.45);
  border-radius: 6px;
  background: rgba(6, 30, 60, 0.55);
  padding: 5px 6px;
}

.context-ref-text {
  flex: 1;
  min-width: 0;
  font-size: 12px;
  color: #d6eeff;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.context-ref-remove {
  width: 20px;
  height: 20px;
  border: 1px solid rgba(123, 200, 255, 0.55);
  border-radius: 4px;
  background: rgba(8, 40, 88, 0.5);
  color: #d9efff;
  line-height: 1;
  cursor: pointer;
}

.history-ref-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
  table-layout: fixed;
}

.history-ref-table th,
.history-ref-table td {
  border-bottom: 1px solid rgba(45, 109, 165, 0.35);
  padding: 6px 6px;
  text-align: left;
  color: var(--font-main);
  word-break: break-all;
}

.history-ref-table th {
  font-weight: 700;
  color: #9ecff0;
}

.history-ref-table .col-check {
  width: 15%;
}

.history-ref-table .col-id {
  width: 35%;
}

.history-ref-table .col-topic {
  width: 50%;
}

.history-ref-checkbox {
  width: 14px;
  height: 14px;
  margin: 0;
  vertical-align: middle;
}

.id-cell-text {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: middle;
}

.topic-cell-text {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: middle;
}

.idle-event-block {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.event-input-wrap {
  position: relative;
  min-width: 0;
}

.event-input {
  width: 100%;
  box-sizing: border-box;
  height: 36px;
  border: 1px solid rgba(45, 109, 165, 0.95);
  border-radius: 8px;
  background: #062a55;
  color: var(--font-main);
  font-size: 13px;
  padding: 0 34px 0 10px;
}

.event-input::placeholder {
  color: rgba(160, 174, 192, 0.75);
}

.event-input:focus {
  outline: none;
  border-color: var(--accent);
}

.event-input:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}

.btn-next-step {
  width: 100%;
  justify-content: center;
  min-height: 34px;
  padding: 6px 12px;
  font-size: 12px;
  border-radius: 6px;
}

.history-panel {
  display: flex;
  flex-direction: column;
  min-height: 0;
  height: 100%;
  border: 1px solid rgba(45, 109, 165, 0.45);
  border-radius: 10px;
  background: rgba(4, 22, 44, 0.55);
  overflow: hidden;
}

.history-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 10px 10px 8px;
  border-bottom: 1px solid rgba(45, 109, 165, 0.45);
  background: rgba(6, 28, 56, 0.65);
}

.history-title {
  font-size: 13px;
  font-weight: 800;
  color: #dff6ff;
  letter-spacing: 0.2px;
}

.history-refresh-btn {
  border: 1px solid rgba(47, 136, 202, 0.78);
  background: rgba(8, 40, 88, 0.58);
  color: #d4f0ff;
  border-radius: 6px;
  padding: 4px 10px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
}

.history-refresh-btn:hover:not(:disabled) {
  border-color: rgba(123, 200, 255, 0.95);
  background: rgba(12, 52, 100, 0.82);
}

.history-refresh-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.history-loading,
.history-empty {
  padding: 14px 10px;
  font-size: 12px;
  color: var(--font-muted);
}

.history-list {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 8px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.history-card {
  width: 100%;
  border: 1px solid rgba(45, 109, 165, 0.5);
  border-radius: 8px;
  background: rgba(6, 30, 60, 0.6);
  color: var(--font-main);
  padding: 8px 9px;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.15s ease, background 0.15s ease, box-shadow 0.15s ease;
}

.history-card:hover {
  border-color: rgba(123, 200, 255, 0.88);
  background: rgba(10, 42, 78, 0.72);
}

.history-card.active {
  border-color: rgba(123, 200, 255, 0.95);
  background: rgba(14, 56, 102, 0.62);
  box-shadow: 0 0 0 1px rgba(23, 166, 255, 0.2);
}

.history-card-id {
  font-size: 12px;
  font-weight: 700;
  color: #dff6ff;
  line-height: 1.35;
  word-break: break-all;
}

.history-card-event {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.45;
  color: var(--font-muted);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.history-card-foot {
  margin-top: 8px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.history-card-time {
  font-size: 11px;
  color: #9ecff0;
  opacity: 0.9;
}

.history-card-del {
  font-size: 12px;
  color: #ffb4b4;
  cursor: pointer;
}

.history-card-del:hover {
  color: #ffd0d0;
  text-decoration: underline;
}

.chip-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.country-chip {
  cursor: pointer;
  font-size: 12px;
  font-weight: 600;
  padding: 6px 11px;
  border-radius: 999px;
  border: 1px solid rgba(60, 120, 180, 0.5);
  color: #c8e2f8;
  background: rgba(8, 36, 72, 0.55);
  user-select: none;
}

.country-chip.on {
  border-color: rgba(123, 200, 255, 0.95);
  background: rgba(23, 90, 160, 0.45);
  color: #fff;
}

.persona-accord {
  max-height: 200px;
  overflow: auto;
  margin-top: 6px;
}

.persona-group-title {
  font-size: 12px;
  font-weight: 700;
  color: #9ecff0;
  margin: 8px 0 6px;
}

.persona-cards {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.persona-card {
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 34px;
  padding: 0 14px;
  border-radius: 6px;
  border: 1px solid rgba(45, 109, 165, 0.75);
  background: rgba(4, 24, 48, 0.65);
  transition: all 0.16s ease;
}

.persona-card.on {
  border-color: rgba(76, 196, 120, 0.95);
  background: rgba(31, 130, 72, 0.38);
  box-shadow: 0 0 0 1px rgba(67, 209, 122, 0.24);
}

.persona-name {
  font-size: 13px;
  font-weight: 700;
  color: #e9f8ff;
  line-height: 1;
}

.stage-loading-card {
  margin-bottom: 8px;
  padding: 8px 10px;
  border-radius: 8px;
  border: 1px solid rgba(45, 109, 165, 0.55);
  background: rgba(4, 24, 48, 0.55);
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #c8e9ff;
}

.loading-spinner {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  border: 2px solid rgba(158, 207, 240, 0.25);
  border-top-color: #7ec8ff;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.topic-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 6px;
}

.topic-row {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  cursor: pointer;
  padding: 8px 10px;
  border-radius: 8px;
  border: 1px solid rgba(45, 109, 165, 0.55);
  background: rgba(4, 22, 44, 0.55);
}

.topic-row.on {
  border-color: rgba(123, 200, 255, 0.9);
}

.topic-text {
  font-size: 12px;
  line-height: 1.45;
  color: var(--font-main);
}

.param-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 8px;
}

.param-label {
  font-size: 12px;
  color: #c8e9ff;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.param-input {
  width: 88px;
  padding: 4px 8px;
  border-radius: 6px;
  border: 1px solid rgba(45, 109, 165, 0.95);
  background: #062a55;
  color: var(--font-main);
  font-size: 13px;
}

.message-list {
  flex: 1;
  min-height: 100px;
  min-width: 0;
  overflow-x: hidden;
  overflow-y: auto;
  padding: 10px 10px 12px;
}

.chat-row {
  margin-bottom: 12px;
}

.system-pill {
  display: table;
  margin: 0 auto;
  max-width: 94%;
  padding: 6px 12px;
  font-size: 12px;
  line-height: 1.45;
  color: #b8daf5;
  border-radius: 999px;
  border: 1px solid rgba(80, 130, 180, 0.45);
  background: rgba(12, 40, 72, 0.55);
  text-align: center;
}

.system-pill.is-wait {
  font-style: italic;
  opacity: 0.9;
}

.chat-row-inner {
  display: flex;
  gap: 10px;
  align-items: flex-end;
}

.chat-row-inner.user {
  flex-direction: row-reverse;
}

.avatar {
  flex-shrink: 0;
  width: 34px;
  height: 34px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 800;
  border: 1px solid rgba(123, 200, 255, 0.35);
}

.user-av {
  background: rgba(23, 166, 255, 0.2);
  color: #bfe9ff;
}

.role-av {
  background: rgba(180, 120, 255, 0.12);
  color: #e6d4ff;
}

.avatar.role-av.role-av-tone-0 {
  background: rgba(120, 86, 220, 0.28);
  color: #ebe4ff;
  border-color: rgba(168, 140, 255, 0.55);
}

.avatar.role-av.role-av-tone-1 {
  background: rgba(40, 160, 220, 0.28);
  color: #c8efff;
  border-color: rgba(90, 190, 255, 0.55);
}

.avatar.role-av.role-av-tone-2 {
  background: rgba(46, 190, 140, 0.28);
  color: #c8ffe8;
  border-color: rgba(100, 230, 180, 0.5);
}

.avatar.role-av.role-av-tone-3 {
  background: rgba(230, 150, 60, 0.28);
  color: #ffecc8;
  border-color: rgba(255, 190, 120, 0.55);
}

.avatar.role-av.role-av-tone-4 {
  background: rgba(230, 90, 140, 0.26);
  color: #ffd6e6;
  border-color: rgba(255, 140, 180, 0.5);
}

.avatar.role-av.role-av-tone-5 {
  background: rgba(100, 200, 200, 0.26);
  color: #d4ffff;
  border-color: rgba(120, 230, 230, 0.5);
}

.avatar.role-av.role-av-tone-6 {
  background: rgba(200, 120, 80, 0.28);
  color: #ffe0d0;
  border-color: rgba(255, 170, 130, 0.5);
}

.avatar.role-av.role-av-tone-7 {
  background: rgba(140, 140, 220, 0.28);
  color: #e4e8ff;
  border-color: rgba(170, 180, 255, 0.55);
}

.chat-main {
  min-width: 0;
  flex: 1;
}

.chat-name {
  font-size: 11px;
  color: var(--font-muted);
  margin-bottom: 4px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: baseline;
}

.name-strong {
  font-weight: 800;
  color: #dff6ff;
}

.name-sub {
  opacity: 0.85;
}

.bubble {
  display: inline-block;
  max-width: 100%;
  padding: 9px 12px;
  border-radius: 12px;
  font-size: 13px;
  line-height: 1.55;
  color: var(--font-main);
  white-space: pre-wrap;
  word-break: break-word;
}

.user-bubble {
  border: 1px solid rgba(79, 180, 255, 0.4);
  background: rgba(23, 166, 255, 0.14);
  border-bottom-right-radius: 4px;
}

.role-bubble {
  border: 1px solid #225f99;
  background: rgba(5, 30, 60, 0.72);
  border-bottom-left-radius: 4px;
}

.typing-row {
  padding: 4px 0 8px 44px;
}

.typing-dots {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #9ecff0;
}

.typing-name {
  font-weight: 700;
  color: #dff6ff;
}

.typing-ellipsis {
  opacity: 0.9;
}

.typing-dots-bar {
  display: inline-flex;
  align-items: center;
  gap: 3px;
}

.dot {
  display: inline-block;
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: #7ec8ff;
  animation: bounce 1.2s infinite ease-in-out;
}

@keyframes bounce {
  0%,
  80%,
  100% {
    transform: translateY(0);
    opacity: 0.4;
  }
  40% {
    transform: translateY(-4px);
    opacity: 1;
  }
}

.assistant-foot {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px 12px 12px;
  border-top: 1px solid rgba(28, 111, 184, 0.45);
  flex-shrink: 0;
}

.foot-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.foot-quick {
  display: flex;
  align-items: center;
  gap: 10px;
}

.foot-quick-hint {
  font-size: 11px;
  color: var(--font-muted);
}

.btn-compact {
  padding: 6px 10px;
  font-size: 12px;
}

.assistant-textarea {
  width: 100%;
  box-sizing: border-box;
  min-height: 72px;
  resize: vertical;
  max-height: 160px;
  padding: 8px 10px;
  font-size: 13px;
  line-height: 1.45;
  color: var(--font-main);
  border: 1px solid rgba(45, 109, 165, 0.95);
  border-radius: 8px;
  background: #062a55;
  box-shadow: inset 0 0 0 1px rgba(23, 166, 255, 0.12);
}

.assistant-textarea::placeholder {
  color: rgba(160, 174, 192, 0.75);
}

.assistant-textarea:focus {
  outline: none;
  border-color: var(--accent);
  box-shadow:
    inset 0 0 0 1px rgba(23, 166, 255, 0.2),
    0 0 0 1px rgba(23, 166, 255, 0.25);
}

.assistant-textarea:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}

.btn-send {
  min-width: 100px;
  padding: 10px 18px;
  border: 1px solid rgba(64, 180, 255, 0.5);
  border-radius: 8px;
  font-size: 14px;
  font-weight: 700;
  color: #fff;
  cursor: pointer;
  background: linear-gradient(135deg, #1083d3, #0e4d9d);
  box-shadow: 0 2px 12px rgba(16, 131, 211, 0.35);
}

.btn-send:hover:not(:disabled) {
  filter: brightness(1.06);
}

.btn-send:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.modal-backdrop {
  position: fixed;
  inset: 0;
  z-index: 12010;
  background: rgba(0, 8, 20, 0.55);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
}

.modal-card {
  width: min(420px, 100%);
  max-height: min(70vh, 520px);
  display: flex;
  flex-direction: column;
  padding: 16px 18px;
  border-radius: 12px;
  border: 1px solid var(--line-main);
  background: var(--bg-card);
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.45);
}

.modal-title {
  font-size: 15px;
  font-weight: 800;
  color: #e8f6ff;
}

.modal-desc {
  font-size: 12px;
  color: var(--font-muted);
  line-height: 1.45;
  margin: 8px 0 10px;
}

.modal-delete-meta {
  display: block;
  margin-top: 8px;
  color: rgba(205, 228, 248, 0.88);
  word-break: break-all;
}

.modal-list {
  flex: 1;
  min-height: 120px;
  overflow: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.filter-row {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  font-size: 12px;
  color: var(--font-main);
  padding: 8px;
  border-radius: 8px;
  border: 1px solid rgba(45, 109, 165, 0.45);
  background: rgba(4, 22, 44, 0.45);
}

.filter-preview {
  line-height: 1.4;
}

.modal-empty {
  font-size: 12px;
  color: var(--font-muted);
  padding: 12px;
  text-align: center;
}

.modal-actions {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.modal-actions-spread {
  flex-wrap: wrap;
  justify-content: flex-end;
}
</style>
