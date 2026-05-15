<template>
  <aside class="settings-column panel-scrollbar" aria-label="配置区">
    <div class="settings-column-head">
      <span class="settings-column-title">配置区</span>
    </div>

    <div v-if="model.phaseStripVisible" class="phase-strip">
      <template v-if="model.phase === 'idle'">
        <div class="idle-event-block">
          <div class="strip-title">录入事件</div>
          <div class="event-input-wrap">
            <input
              v-model.trim="model.eventDraft"
              class="event-input"
              type="text"
              :disabled="model.footerLocked"
              placeholder="请输入事件"
              @keydown.enter.exact.prevent="handlers.onFooterEnter"
            />
            <button
              type="button"
              class="mic-btn"
              :class="{ 'mic-btn--active': isListening }"
              :disabled="!speechSupported || model.footerLocked"
              :title="micTitle"
              :aria-label="micAriaLabel"
              :aria-pressed="isListening"
              @click="toggleSpeechInput"
            >
              <svg class="mic-icon" viewBox="0 0 24 24" aria-hidden="true">
                <path
                  fill="currentColor"
                  d="M12 14c1.66 0 3-1.34 3-3V5c0-1.66-1.34-3-3-3S9 3.34 9 5v6c0 1.66 1.34 3 3 3zm5.91-3c-.49 0-.9.36-.98.85C16.52 14.2 14.47 16 12 16s-4.52-1.8-4.93-4.15c-.08-.49-.49-.85-.98-.85-.61 0-1.09.54-1 1.14.49 3 2.89 5.35 5.91 5.78V20c0 .55.45 1 1 1s1-.45 1-1v-2.08c3.02-.43 5.42-2.78 5.91-5.78.1-.6-.39-1.14-1-1.14z"
                />
              </svg>
            </button>
          </div>
          <div v-if="sttError" class="stt-error">{{ sttError }}</div>
        </div>
        <div class="context-ref-box">
          <div class="context-ref-head">
            <span class="context-ref-title">添加上下文事件</span>
            <button
              type="button"
              class="history-ref-refresh"
              :disabled="model.busy || !model.relationEvents.length"
              @click="handlers.clearRelationEvents"
            >
              清空
            </button>
          </div>
          <div v-if="model.relationEvents.length" class="context-ref-list panel-scrollbar">
            <div v-for="(evt, idx) in model.relationEvents" :key="`${evt}-${idx}`" class="context-ref-item">
              <span class="context-ref-text" :title="evt">{{ evt }}</span>
              <button
                type="button"
                class="context-ref-remove"
                :disabled="model.busy"
                @click="handlers.removeRelationEvent(idx)"
              >
                ×
              </button>
            </div>
          </div>
          <div v-else class="history-ref-empty">点击关联拓扑图支节点后，将自动加入这里</div>
        </div>

        <div class="history-ref-box">
          <div class="history-ref-head">
            <div class="history-ref-title-row">
              <span class="history-ref-title">引入历史会话</span>
              <span class="history-help" tabindex="0" aria-label="历史会话功能说明">
                ?
                <span class="history-help-tip">
                  可选择历史会话作为本次讨论的上下文，支持多选，也可以不选择。
                </span>
              </span>
            </div>
            <button
              type="button"
              class="history-ref-refresh"
              :disabled="model.historyReferenceLoading || model.busy"
              @click="handlers.fetchHistoryReferenceCandidates"
            >
              刷新
            </button>
          </div>
          <div v-if="model.historyReferenceLoading" class="history-ref-loading">正在加载历史会话…</div>
          <div v-else-if="model.historyReferenceCandidates.length" class="history-ref-table-wrap panel-scrollbar">
            <table class="history-ref-table">
              <thead>
                <tr>
                  <th class="col-check">#</th>
                  <th class="col-id">会话ID</th>
                  <th>议题</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="it in model.historyReferenceCandidates" :key="it.chat_id">
                  <td class="col-check">
                    <input
                      v-model="model.selectedHistoryChatIds"
                      class="history-ref-checkbox"
                      type="checkbox"
                      :value="String(it.chat_id)"
                      :disabled="model.busy || model.historyReadonly"
                    />
                  </td>
                  <td class="col-id">
                    <span class="id-cell-text" :title="it.chat_id">{{ it.chat_id }}</span>
                  </td>
                  <td class="col-topic">
                    <span class="topic-cell-text" :title="it.displayTitle">
                      {{ it.displayTitle || "（无标题）" }}
                    </span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <div
            v-else-if="model.historyReferenceError"
            class="history-ref-error"
            role="button"
            tabindex="0"
            @click="handlers.fetchHistoryReferenceCandidates"
            @keydown.enter.prevent="handlers.fetchHistoryReferenceCandidates"
            @keydown.space.prevent="handlers.fetchHistoryReferenceCandidates"
          >
            {{ model.historyReferenceError }} 查询失败，点击刷新重新获取
          </div>
          <div v-else class="history-ref-empty">暂无可引用的历史会话</div>
        </div>
        <div class="strip-actions">
          <button
            class="btn btn-primary btn-next-step"
            type="button"
            :disabled="model.footerLocked || !model.canStartFromIdle"
            @click="handlers.startFromIdle"
          >
            下一步
          </button>
        </div>
      </template>

      <template v-else-if="model.phase === 'experts'">
        <div class="strip-title">选择参与讨论的专家（至少 2 位）</div>
        <div v-if="model.busy && model.experts.length === 0" class="stage-loading-card">
          <span class="loading-spinner" />
          <span>正在加载专家列表…</span>
        </div>
        <div class="persona-accord panel-scrollbar">
          <div class="persona-cards">
            <label
              v-for="p in model.experts"
              :key="model.personaKey(p)"
              class="persona-card"
              :class="{ on: model.selectedExpertMap[model.personaKey(p)] }"
            >
              <input v-model="model.selectedExpertMap[model.personaKey(p)]" type="checkbox" class="sr-only" />
              <span class="persona-name">{{ p.name }}</span>
            </label>
          </div>
        </div>
        <div class="strip-actions">
          <button
            type="button"
            class="btn btn-primary"
            :disabled="!model.canConfirmExperts || model.busy"
            @click="handlers.confirmExpertsAndSave"
          >
            确认专家并进入议题选择
          </button>
        </div>
      </template>

      <template v-else-if="model.phase === 'topics'">
        <div class="strip-title">会议议题</div>
        <div class="topic-mode-toggle" role="radiogroup" aria-label="议题来源">
          <label class="topic-mode-option" :class="{ on: model.topicPickMode === 'llm' }">
            <input v-model="model.topicPickMode" type="radio" class="sr-only" value="llm" @change="handlers.onTopicPickModeChange" />
            AI 生成议题
          </label>
          <label class="topic-mode-option" :class="{ on: model.topicPickMode === 'manual' }">
            <input
              v-model="model.topicPickMode"
              type="radio"
              class="sr-only"
              value="manual"
              @change="handlers.onTopicPickModeChange"
            />
            自定义议题
          </label>
        </div>

        <template v-if="model.topicPickMode === 'llm'">
          <div class="strip-actions strip-actions-inline strip-actions-topic">
            <button type="button" class="btn-topic-refresh" :disabled="model.busy" @click="handlers.runTopicGenerationStream">
              重新生成
            </button>
          </div>
          <div v-if="model.busy && model.topics.length === 0" class="stage-loading-card">
            <span class="loading-spinner" />
            <span>正在生成议题…</span>
          </div>
          <div class="topic-list">
            <label v-for="(t, idx) in model.topics" :key="idx" class="topic-row" :class="{ on: model.selectedTopicIndex === idx }">
              <input v-model.number="model.selectedTopicIndex" type="radio" :value="idx" />
              <span class="topic-text">{{ t }}</span>
            </label>
          </div>
          <div v-if="!model.busy && model.topics.length === 0" class="strip-actions strip-actions-inline">
            <button type="button" class="btn btn-outline" @click="handlers.runTopicGenerationStream">
              生成选题
            </button>
          </div>
        </template>

        <template v-else-if="model.topicPickMode === 'manual'">
          <textarea
            v-model.trim="model.manualDebateTopic"
            class="topic-manual-input"
            rows="4"
            placeholder="输入本次会议的核心议题…"
            :disabled="model.busy"
          />
          <div class="strip-actions strip-actions-inline">
            <button
              type="button"
              class="btn btn-outline"
              :disabled="!String(model.manualDebateTopic || '').trim() || model.busy"
              @click="handlers.confirmManualDebateTopic"
            >
              确认议题
            </button>
          </div>
          <p v-if="model.manualTopicSubmitted" class="strip-hint">议题已提交，可开始阐述观点。</p>
        </template>
        <template v-else>
          <p class="strip-hint">请先选择议题方式：手动录入或 AI 生成。</p>
        </template>

        <div class="strip-actions">
          <button
            type="button"
            class="btn btn-primary"
            :disabled="!model.canStartPositionPhase || model.busy"
            @click="handlers.startPositionPhase"
          >
            开始各方阐述观点
          </button>
        </div>
      </template>

      <template v-else-if="model.phase === 'position_done'">
        <div class="strip-title">观点阐述已完成</div>
        <p class="strip-hint">可调整交叉讨论参数后进入自由辩论环节。</p>
        <div class="param-row">
          <label class="param-label">
            本轮发言轮次
            <input v-model.number="model.debateTurns" class="param-input" type="number" min="1" max="20" />
          </label>
          <label class="param-label">
            回复链长度
            <input v-model.number="model.replyChainLength" class="param-input" type="number" min="1" max="20" />
          </label>
        </div>
        <div class="strip-actions">
          <button type="button" class="btn btn-primary" :disabled="model.busy" @click="handlers.startDebatePhase">
            进入交叉讨论
          </button>
        </div>
      </template>

      <template v-else-if="model.phase === 'round_done'">
        <div class="strip-title">本轮选题、观点与讨论已结束</div>
        <div class="strip-actions">
          <button type="button" class="btn btn-primary" :disabled="model.busy" @click="handlers.onAnotherRoundClick">
            再讨论一轮
          </button>
        </div>
      </template>
    </div>

    <div v-else-if="model.settingsSummaryVisible" class="settings-summary card-like">
      <div class="settings-summary-title">当前会议</div>
      <div class="settings-summary-block">
        <span class="k">事件:</span>
        <span class="v">{{ model.eventTextSummary }}</span>
      </div>
      <div v-if="model.selectedRolesList.length" class="settings-summary-block">
        <span class="k">专家:</span>
        <span class="v">{{ model.selectedRolesList.map((x) => x.name).join("、") }}</span>
      </div>
      <div v-if="model.currentDebateTopic()" class="settings-summary-block">
        <span class="k">议题:</span>
        <span class="v">{{ model.currentDebateTopic() }}</span>
      </div>
    </div>
  </aside>
</template>

<script>
export default {
  name: "IntelligenceAssistantConfigPanel",
  props: {
    model: { type: Object, required: true },
    handlers: { type: Object, required: true }
  },
  data() {
    return {
      speechSupported: false,
      isListening: false,
      sttError: "",
      speechBaseText: "",
      speechFinalBuffer: "",
      recognition: null
    };
  },
  computed: {
    micTitle() {
      if (!this.speechSupported) return "当前浏览器不支持语音输入";
      if (this.model && this.model.footerLocked) return "当前步骤不可使用语音";
      return this.isListening ? "点击结束语音识别" : "点击开始语音输入（将请求麦克风权限）";
    },
    micAriaLabel() {
      if (!this.speechSupported) return "语音输入不可用";
      return this.isListening ? "结束语音识别" : "开始语音识别";
    }
  },
  methods: {
    getSpeechRecognitionCtor() {
      return typeof window !== "undefined"
        ? window.SpeechRecognition || window.webkitSpeechRecognition
        : null;
    },
    ensureRecognition() {
      if (this.recognition) return this.recognition;
      const Ctor = this.getSpeechRecognitionCtor();
      if (!Ctor) return null;
      const r = new Ctor();
      r.lang = "zh-CN";
      r.continuous = true;
      r.interimResults = true;
      r.onresult = (event) => {
        let interim = "";
        for (let i = event.resultIndex; i < event.results.length; i += 1) {
          const piece = event.results[i][0]?.transcript || "";
          if (event.results[i].isFinal) {
            this.speechFinalBuffer += piece;
          } else {
            interim += piece;
          }
        }
        const merged = `${this.speechBaseText}${this.speechFinalBuffer}${interim}`.trim();
        if (this.model) this.model.eventDraft = merged;
      };
      r.onerror = (event) => {
        const code = event?.error || "";
        if (code === "aborted" || code === "no-speech") return;
        const map = {
          "not-allowed": "麦克风权限被拒绝，请在浏览器设置中允许使用麦克风",
          "service-not-allowed": "语音服务不可用，请检查网络或浏览器设置",
          network: "语音识别需要网络，请检查连接后重试"
        };
        this.sttError = map[code] || "语音识别出错，请重试";
        this.isListening = false;
      };
      r.onend = () => {
        this.isListening = false;
      };
      this.recognition = r;
      return r;
    },
    toggleSpeechInput() {
      if (!this.speechSupported || (this.model && this.model.footerLocked)) return;
      if (this.isListening) {
        this.stopSpeechInput();
        return;
      }
      this.sttError = "";
      const r = this.ensureRecognition();
      if (!r) {
        this.sttError = "无法初始化语音识别";
        return;
      }
      const base = this.model && this.model.eventDraft != null ? String(this.model.eventDraft) : "";
      this.speechBaseText = base ? `${base} ` : "";
      this.speechFinalBuffer = "";
      try {
        r.start();
        this.isListening = true;
      } catch (e) {
        this.sttError = "无法启动语音识别，请稍后重试";
        this.isListening = false;
      }
    },
    stopSpeechInput() {
      if (!this.recognition) return;
      try {
        this.recognition.stop();
      } catch (e) {
        /* ignore */
      }
      this.isListening = false;
    }
  },
  mounted() {
    this.speechSupported = !!this.getSpeechRecognitionCtor();
  },
  beforeDestroy() {
    this.stopSpeechInput();
    if (this.recognition) {
      this.recognition.onresult = null;
      this.recognition.onerror = null;
      this.recognition.onend = null;
      this.recognition = null;
    }
  }
};
</script>

<style scoped>
.event-input-wrap {
  position: relative;
  min-width: 0;
}
.mic-btn {
  position: absolute;
  right: 6px;
  top: 50%;
  transform: translateY(-50%);
  width: 28px;
  height: 28px;
  padding: 0;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: rgba(140, 210, 255, 0.82);
  cursor: pointer;
  display: grid;
  place-items: center;
}
.mic-btn:hover:not(:disabled) {
  color: rgba(190, 235, 255, 0.98);
  background: rgba(30, 90, 150, 0.35);
}
.mic-btn:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}
.mic-btn--active {
  color: rgba(120, 255, 200, 0.95);
  background: rgba(0, 120, 90, 0.25);
}
.mic-icon {
  width: 16px;
  height: 16px;
  display: block;
}
.stt-error {
  margin-top: 6px;
  color: #ff9ea5;
  font-size: 12px;
}
</style>
