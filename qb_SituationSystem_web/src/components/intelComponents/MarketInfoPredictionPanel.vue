<template>
  <section class="card app-panel">
    <div class="module-title">
      <span class="module-title-emoji" aria-hidden="true">{{ titleEmoji }}</span>市场信息预测
    </div>

    <div v-if="showSettingPanel" class="setting-panel">
      <div class="setting-title">新闻参数设置</div>
      <label class="setting-item">
        <span>新闻标题</span>
        <input v-model.trim="draftInput.title" type="text" placeholder="请输入新闻标题" />
      </label>
      <label class="setting-item">
        <span>新闻内容</span>
        <textarea
          v-model.trim="draftInput.content"
          rows="4"
          placeholder="请输入新闻内容"
        />
      </label>
      <label class="setting-item">
        <span>发布时间</span>
        <input v-model="draftInput.publishTime" type="datetime-local" />
      </label>
      <div v-if="settingError" class="setting-error">{{ settingError }}</div>
      <div class="setting-actions">
        <button type="button" class="setting-btn-primary" :disabled="loading" @click="analyzeFromSetting">
          {{ loading ? "分析中..." : "分析" }}
        </button>
        <button type="button" class="setting-btn-ghost" @click="cancelSetting">取消</button>
      </div>
    </div>

    <div class="panel-content">
      <PanelRetrievingState v-if="loading" />
      <div v-else-if="errorText" class="tip-error">{{ errorText }}</div>
      <template v-else>
        <div v-if="requestInput.title" class="news-title-sticky">
          <span class="news-title-label">新闻标题:</span>
          <span class="news-title-text">{{ requestInput.title }}</span>
        </div>
        <div v-if="!stocks.length && analysisSummary" class="analysis-summary-wrap">
          <div class="analysis-summary-title">分析结论</div>
          <div class="analysis-summary-content">{{ analysisSummary }}</div>
        </div>
        <div v-else-if="!stocks.length" class="empty-state">
          请先在设置中填写新闻，或右键热点事件卡片触发联动分析
        </div>
        <div v-else-if="stocks.length || marketImpactVisible" class="stock-list">
        <button
          v-for="item in stocks"
          :key="item.id"
          type="button"
          class="stock-card"
          :class="{ 'is-active': activeStock && activeStock.id === item.id }"
          @click="toggleStockCard(item)"
        >
          <div class="stock-line">
            <span class="stock-code">{{ item.stockCode }}</span>
            <span class="stock-name">{{ item.stockName }}</span>
            <span class="stock-relation">{{ item.relationTypeText }}</span>
          </div>
          <div class="stock-line stock-line--meta">
            <span :class="['direction', `direction--${item.impactDirection}`]">{{ item.directionText }}</span>
            <span :class="['change', `change--${item.impactDirection}`]">{{ item.changeText }}</span>
            <span class="confidence">置信度: {{ item.confidenceText }}</span>
          </div>

          <div v-if="activeStock && activeStock.id === item.id" class="stock-detail stock-detail--inline">
            <div class="detail-row">
              <span>影响程度:</span>
              <strong>{{ item.severityText }}</strong>
            </div>
            <div class="detail-row">
              <span>持续时间:</span>
              <strong>{{ item.durationText }}</strong>
            </div>
            <div class="detail-row">
              <span>关联度:</span>
              <strong>{{ item.relevanceText }}</strong>
            </div>
            <div class="detail-title">分析理由:</div>
            <div class="detail-reason">{{ item.reasoning || "--" }}</div>
            <div v-if="item.keyFactorsText" class="detail-factors">
              关键因素: {{ item.keyFactorsText }}
            </div>
          </div>
        </button>
        <div v-if="marketImpactVisible" class="stock-card market-impact-wrap market-impact-card">
          <div class="market-impact-title">整体市场影响</div>
          <div class="market-impact-line">
            <span class="market-impact-label-inline">市场情绪:</span>
            <span class="market-impact-value">{{ marketSentimentText }}</span>
          </div>
          <div class="market-impact-label">受影响板块:</div>
          <div v-if="affectedSectors.length" class="market-impact-sectors">
            <span v-for="(sector, idx) in affectedSectors" :key="`sector-${idx}`" class="sector-chip">
              {{ sector }}
            </span>
          </div>
          <div v-else class="market-impact-empty">暂无</div>
        </div>
        </div>
      </template>
    </div>
  </section>
</template>

<script>
import { analyzeMarketsFromNews } from "@/api/newsMarketAnalysis";
import PanelRetrievingState from "./common/PanelRetrievingState.vue";
import { PANEL_TITLE_EMOJI } from "./js/panelTitleEmoji.js";

const RELATION_TYPE_MAP = {
  DIRECT_MENTION: "直接提及",
  INDIRECT_INDUSTRY: "行业关联",
  SUPPLY_CHAIN: "产业链",
  POLICY_IMPACT: "政策影响",
  REGIONAL_IMPACT: "地域影响",
  MARKET_SENTIMENT: "市场情绪"
};

const IMPACT_SEVERITY_MAP = {
  HIGH: "🔴 重大",
  MODERATE: "🟠 中等",
  LOW: "🟢 轻微"
};

const DURATION_MAP = {
  SHORT_TERM: "短期 (1-3天)",
  MEDIUM_TERM: "中期 (1-4周)",
  LONG_TERM: "长期 (1个月以上)"
};

const MARKET_SENTIMENT_MAP = {
  POSITIVE: "😊 积极",
  NEGATIVE: "😟 消极",
  NEUTRAL: "😐 中性",
  SLIGHTLY_POSITIVE: "🙂 略积极",
  SLIGHTLY_NEGATIVE: "😕 略消极"
};

export default {
  name: "MarketInfoPredictionPanel",
  components: { PanelRetrievingState },
  props: {
    panelId: { type: String, default: "" }
  },
  data() {
    return {
      titleEmoji: PANEL_TITLE_EMOJI["market-stock"],
      showSettingPanel: false,
      settingError: "",
      loading: false,
      errorText: "",
      requestInput: {
        title: "",
        content: "",
        publishTime: ""
      },
      draftInput: {
        title: "",
        content: "",
        publishTime: ""
      },
      analysisSummary: "",
      marketSentimentText: "",
      affectedSectors: [],
      stocks: [],
      activeStock: null
    };
  },
  computed: {
    marketImpactVisible() {
      return !!this.marketSentimentText || this.affectedSectors.length > 0;
    }
  },
  methods: {
    toggleStockCard(item) {
      if (!item || !item.id) return;
      if (this.activeStock && this.activeStock.id === item.id) {
        this.activeStock = null;
        return;
      }
      this.activeStock = item;
    },
    onExternalOpenSetting(evt) {
      const targetPanelId = String(evt?.detail?.panelId || "");
      if (targetPanelId && this.panelId && targetPanelId !== this.panelId) return;
      this.openSettingPanel();
    },
    onExternalAnalyzeRequest(evt) {
      const payload = evt?.detail || {};
      const title = String(payload.title || "").trim();
      const content = String(payload.content || "").trim();
      const publishTime = String(payload.publishTime || "").trim();
      if (!title && !content) return;
      const now = new Date();
      const y = now.getFullYear();
      const m = String(now.getMonth() + 1).padStart(2, "0");
      const d = String(now.getDate()).padStart(2, "0");
      const hh = String(now.getHours()).padStart(2, "0");
      const mm = String(now.getMinutes()).padStart(2, "0");
      this.requestInput = {
        title: title || "热点新闻",
        content: content || title || "无摘要",
        publishTime: publishTime || `${y}-${m}-${d}T${hh}:${mm}`
      };
      this.draftInput = { ...this.requestInput };
      this.analyzeWithPayload(this.requestInput);
    },
    openSettingPanel() {
      this.settingError = "";
      this.draftInput = { ...this.requestInput };
      this.showSettingPanel = true;
    },
    async analyzeFromSetting() {
      const title = String(this.draftInput.title || "").trim();
      const content = String(this.draftInput.content || "").trim();
      const publishTime = String(this.draftInput.publishTime || "").trim();
      if (!title || !content || !publishTime) {
        this.settingError = "标题、内容、发布时间均为必填";
        return;
      }
      const payload = { title, content, publishTime };
      this.requestInput = { ...payload };
      this.draftInput = { ...payload };
      this.settingError = "";
      this.showSettingPanel = false;
      await this.analyzeWithPayload(payload);
    },
    cancelSetting() {
      this.settingError = "";
      this.showSettingPanel = false;
    },
    async analyzeWithPayload(payload) {
      const title = String(payload?.title || "").trim();
      const content = String(payload?.content || "").trim();
      const publishTime = String(payload?.publishTime || "").trim();
      if (!title || !content || !publishTime) {
        this.errorText = "请先在设置中完整填写新闻标题、内容、发布时间";
        return;
      }
      this.loading = true;
      this.errorText = "";
      this.analysisSummary = "";
      this.marketSentimentText = "";
      this.affectedSectors = [];
      try {
        const response = await analyzeMarketsFromNews({ title, content, publishTime });
        const list = Array.isArray(response?.affectedStocks) ? response.affectedStocks : [];
        this.stocks = list.map((item, idx) => this.normalizeStock(item, idx));
        this.analysisSummary = String(response?.analysisSummary || "").trim();
        const sentimentRaw = String(response?.marketImpact?.marketSentiment || "").trim();
        this.marketSentimentText = MARKET_SENTIMENT_MAP[sentimentRaw] || sentimentRaw;
        this.affectedSectors = Array.isArray(response?.marketImpact?.affectedSectors)
          ? response.marketImpact.affectedSectors
              .map((item) => String(item || "").trim())
              .filter(Boolean)
          : [];
        this.activeStock = null;
      } catch (error) {
        this.stocks = [];
        this.activeStock = null;
        this.analysisSummary = "";
        this.marketSentimentText = "";
        this.affectedSectors = [];
        this.errorText = error?.message || "市场信息预测失败，请稍后重试";
      } finally {
        this.loading = false;
      }
    },
    normalizeStock(item, idx) {
      const impactDirection = String(item?.impactDirection || "NEUTRAL").trim();
      const predictedChangePercent = Number(item?.predictedChangePercent);
      const confidence = Number(item?.confidence);
      const relevance = Number(item?.relevanceScore);
      const change = Number.isFinite(predictedChangePercent) ? predictedChangePercent : 0;
      return {
        id: `${item?.stockCode || "stock"}-${idx}`,
        stockCode: String(item?.stockCode || "--"),
        stockName: String(item?.stockName || "--"),
        relationTypeText: RELATION_TYPE_MAP[item?.relationType] || String(item?.relationType || "--"),
        impactDirection,
        directionText:
          impactDirection === "POSITIVE"
            ? "↑"
            : impactDirection === "NEGATIVE"
              ? "↓"
              : "--",
        changeText: `${change >= 0 ? "+" : ""}${change.toFixed(2)}%`,
        confidenceText: `${Math.round((Number.isFinite(confidence) ? confidence : 0) * 100)}%`,
        severityText: IMPACT_SEVERITY_MAP[item?.impactSeverity] || String(item?.impactSeverity || "--"),
        durationText: DURATION_MAP[item?.duration] || String(item?.duration || "--"),
        relevanceText: `${Math.round((Number.isFinite(relevance) ? relevance : 0) * 100)}%`,
        reasoning: String(item?.reasoning || "").trim(),
        keyFactorsText: Array.isArray(item?.keyFactors) ? item.keyFactors.filter(Boolean).join("；") : ""
      };
    }
  },
  mounted() {
    window.addEventListener("market-stock-open-setting", this.onExternalOpenSetting);
    window.addEventListener("market-stock-analyze-request", this.onExternalAnalyzeRequest);
  },
  beforeDestroy() {
    window.removeEventListener("market-stock-open-setting", this.onExternalOpenSetting);
    window.removeEventListener("market-stock-analyze-request", this.onExternalAnalyzeRequest);
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
.setting-panel {
  margin-top: 8px;
  border: 1px solid rgba(95, 185, 255, 0.35);
  border-radius: 8px;
  background: rgba(4, 24, 55, 0.7);
  padding: 10px;
}
.setting-title {
  font-size: 12px;
  font-weight: 700;
  color: rgba(220, 238, 255, 0.94);
  margin-bottom: 10px;
}
.setting-item {
  display: grid;
  grid-template-columns: 68px 1fr;
  gap: 8px;
  align-items: center;
}
.setting-item + .setting-item {
  margin-top: 8px;
}
.setting-item span {
  color: rgba(194, 225, 250, 0.88);
  font-size: 12px;
}
.setting-item input,
.setting-item textarea {
  border-radius: 6px;
  border: 1px solid rgba(95, 185, 255, 0.45);
  background: rgba(5, 26, 57, 0.76);
  color: rgba(220, 238, 255, 0.96);
  padding: 6px 8px;
  outline: none;
}
.setting-item input {
  height: 30px;
}
.setting-item textarea {
  resize: vertical;
  min-height: 72px;
}
.setting-actions {
  margin-top: 10px;
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
.setting-btn-ghost,
.setting-btn-primary {
  height: 28px;
  min-width: 56px;
  border-radius: 6px;
  cursor: pointer;
}
.setting-btn-ghost {
  border: 1px solid rgba(95, 185, 255, 0.45);
  color: #cfe9ff;
  background: rgba(7, 46, 93, 0.45);
}
.setting-btn-primary {
  border: 1px solid rgba(95, 185, 255, 0.7);
  color: #e2f5ff;
  background: rgba(19, 107, 182, 0.4);
}
.setting-error,
.tip-error {
  margin-top: 8px;
  color: #ff9ea5;
  font-size: 12px;
}
.panel-content {
  flex: 1;
  min-height: 0;
  margin-top: 8px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
.news-title-sticky {
  flex: 0 0 auto;
  margin-bottom: 6px;
  border: 1px solid rgba(95, 185, 255, 0.35);
  border-radius: 8px;
  background: rgba(4, 24, 55, 0.95);
  padding: 5px 8px;
}
.news-title-label {
  color: rgba(159, 212, 255, 0.95);
  font-size: 11px;
  font-weight: 700;
}
.news-title-text {
  margin-left: 4px;
  color: rgba(223, 239, 255, 0.95);
  font-size: 11px;
  font-weight: 600;
  line-height: 1.35;
  word-break: break-word;
}
.empty-state {
  height: 100%;
  display: grid;
  place-items: center;
  color: rgba(205, 234, 255, 0.7);
  border: 1px dashed rgba(94, 179, 255, 0.35);
  border-radius: 8px;
  background: rgba(4, 24, 55, 0.25);
  padding: 0 12px;
  text-align: center;
}
.analysis-summary-wrap {
  margin-top: 8px;
  border: 1px solid rgba(95, 185, 255, 0.35);
  border-radius: 8px;
  background: rgba(4, 24, 55, 0.4);
  padding: 10px;
  max-height: 100%;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.analysis-summary-title {
  color: rgba(159, 212, 255, 0.95);
  font-size: 12px;
  font-weight: 700;
}
.analysis-summary-content {
  margin-top: 8px;
  color: rgba(223, 239, 255, 0.92);
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  overflow-y: auto;
  max-height: 220px;
  padding-right: 4px;
  scrollbar-width: thin;
  scrollbar-color: rgba(200, 218, 235, 0.18) transparent;
}
.analysis-summary-content::-webkit-scrollbar {
  width: 5px;
  height: 5px;
}
.analysis-summary-content::-webkit-scrollbar-track {
  background: transparent;
}
.analysis-summary-content::-webkit-scrollbar-thumb {
  border-radius: 3px;
  border: 1px solid transparent;
  background-clip: padding-box;
  background-color: rgba(200, 218, 235, 0.18);
}
.analysis-summary-content::-webkit-scrollbar-thumb:hover {
  background-color: rgba(200, 218, 235, 0.26);
}
.market-impact-wrap {
  margin-top: 0;
  border: 1px solid rgba(95, 185, 255, 0.35);
  border-radius: 8px;
  background: rgba(4, 24, 55, 0.4);
  padding: 10px;
}
.market-impact-card {
  cursor: default;
}
.market-impact-title {
  color: rgba(159, 212, 255, 0.95);
  font-size: 13px;
  font-weight: 800;
}
.market-impact-line {
  margin-top: 8px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.market-impact-label-inline,
.market-impact-label {
  color: rgba(214, 233, 252, 0.9);
  font-size: 12px;
  font-weight: 700;
}
.market-impact-label {
  margin-top: 8px;
}
.market-impact-value {
  color: rgba(223, 239, 255, 0.95);
  font-size: 12px;
  font-weight: 700;
}
.market-impact-sectors {
  margin-top: 8px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.sector-chip {
  border: 1px solid rgba(95, 185, 255, 0.35);
  border-radius: 999px;
  padding: 4px 10px;
  color: rgba(214, 233, 252, 0.95);
  font-size: 12px;
  background: rgba(13, 45, 86, 0.5);
}
.market-impact-empty {
  margin-top: 8px;
  color: rgba(186, 196, 208, 0.8);
  font-size: 12px;
}
.stock-list {
  margin-top: 8px;
  flex: 1;
  min-height: 0;
  overflow: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-right: 4px;
}
.stock-card {
  border: 1px solid rgba(95, 185, 255, 0.38);
  border-radius: 8px;
  background: linear-gradient(180deg, rgba(6, 40, 86, 0.72) 0%, rgba(4, 23, 52, 0.78) 100%);
  color: rgba(223, 239, 255, 0.92);
  padding: 10px;
  text-align: left;
  cursor: pointer;
  transition: all 0.2s ease;
}
.stock-card.is-active {
  border-color: rgba(125, 223, 255, 0.82);
  box-shadow: 0 0 0 1px rgba(125, 223, 255, 0.2) inset;
}
.stock-line {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.stock-line--meta {
  margin-top: 6px;
}
.stock-code {
  font-weight: 800;
}
.stock-name {
  color: rgba(214, 233, 252, 0.9);
}
.stock-relation {
  color: rgba(159, 212, 255, 0.95);
}
.direction {
  font-size: 14px;
  font-weight: 800;
}
.direction--POSITIVE,
.change--POSITIVE {
  color: #ff6a75;
}
.direction--NEGATIVE,
.change--NEGATIVE {
  color: #53d890;
}
.direction--NEUTRAL,
.change--NEUTRAL {
  color: #9aa8b9;
}
.confidence {
  color: rgba(190, 203, 218, 0.9);
}
.stock-detail {
  margin-top: 4px;
  border: 1px solid rgba(95, 185, 255, 0.32);
  border-radius: 8px;
  background: rgba(8, 33, 66, 0.55);
  padding: 10px;
}
.stock-detail--inline {
  margin-top: 10px;
}
.detail-row {
  display: flex;
  justify-content: space-between;
  color: rgba(214, 233, 252, 0.9);
  font-size: 12px;
}
.detail-row + .detail-row {
  margin-top: 6px;
}
.detail-title {
  margin-top: 10px;
  color: rgba(159, 212, 255, 0.95);
  font-size: 12px;
  font-weight: 700;
}
.detail-reason {
  margin-top: 6px;
  color: rgba(223, 239, 255, 0.92);
  font-size: 12px;
  line-height: 1.55;
  white-space: pre-wrap;
}
.detail-factors {
  margin-top: 8px;
  color: rgba(188, 201, 216, 0.92);
  font-size: 12px;
}
</style>
