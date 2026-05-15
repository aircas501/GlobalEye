<template>
  <section class="card app-panel">
    <div class="module-title">
      <span class="module-title-emoji" aria-hidden="true">{{ titleEmoji }}</span>地区热点事件
    </div>
    <div class="inline-form">
      <span class="inline-label">地区:</span>
      <select v-model="selectedRegion" class="region-select">
        <option v-for="region in regions" :key="region" :value="region">
          {{ region }}
        </option>
      </select>
      <span class="inline-label">领域:</span>
      <select v-model="selectedTopic" class="topic-select" aria-label="领域选择">
        <option v-for="topic in topics" :key="topic" :value="topic">
          {{ topic }}
        </option>
      </select>
    </div>

    <div class="panel-content">
      <PanelRetrievingState v-if="loading" />
      <div v-else-if="errorText" class="panel-tip panel-tip--error" role="status" aria-live="polite">
        {{ errorText }}
      </div>
      <div v-else-if="eventList.length" class="news-wrap">
        <button
          v-for="eventItem in eventList"
          :key="eventItem.id"
          type="button"
          class="news-card"
          @click="onCardClick(eventItem)"
          @contextmenu.prevent="onCardContextMenu(eventItem)"
        >
          <div class="news-card__summary">{{ eventItem.name }}</div>
          <div class="news-card__time">{{ eventItem.startDateText }}</div>
        </button>
      </div>
      <PanelNoData v-else />
    </div>
  </section>
</template>

<script>
/**
 * 地区热点事件面板：
 * 按国家与领域拉取热点事件，并向外抛出“打开新闻详情”事件。
 */
import PanelNoData from "./common/PanelNoData.vue";
import PanelRetrievingState from "./common/PanelRetrievingState.vue";
import { PANEL_TITLE_EMOJI } from "./js/panelTitleEmoji.js";
import { getHotEventArticles, getHotEventsByLocation } from "../../api/hotEvents";

const REGION_OPTIONS = ["美国", "伊朗", "以色列", "俄罗斯", "乌克兰", "中国", "日本", "韩国", "朝鲜"];
const TOPIC_OPTIONS = ["军事", "政治", "经济"];

function pad2(value) {
  const n = Number(value);
  if (!Number.isFinite(n)) return "";
  return String(Math.trunc(n)).padStart(2, "0");
}

function formatDateText(createdAt, fallbackDate) {
  if (Array.isArray(createdAt) && createdAt.length >= 3) {
    const y = Number(createdAt[0]);
    const m = pad2(createdAt[1]);
    const d = pad2(createdAt[2]);
    if (Number.isFinite(y) && m && d) {
      return `${y}年${m}月${d}日`;
    }
  }

  const text = String(fallbackDate || "").trim();
  if (!text) return "未知时间";
  if (!text.includes("-")) return text;
  const [y, m, d] = text.split("-");
  if (!y || !m || !d) return text;
  return `${y}年${m}月${d}日`;
}

export default {
  name: "HotEventsPanel",
  components: { PanelNoData, PanelRetrievingState },
  data() {
    return {
      titleEmoji: PANEL_TITLE_EMOJI.hot,
      regions: REGION_OPTIONS,
      topics: TOPIC_OPTIONS,
      selectedRegion: REGION_OPTIONS[0],
      selectedTopic: TOPIC_OPTIONS[0],
      loading: false,
      errorText: "",
      eventList: []
    };
  },
  watch: {
    selectedRegion: {
      immediate: true,
      handler(newVal, oldVal) {
        this.fetchEvents();
        if (oldVal === undefined) return;
        const name = String(newVal || "").trim();
        if (name) {
          this.$emit("focus-situation-country", { country: name });
        }
      }
    },
    selectedTopic() {
      this.fetchEvents();
    }
  },
  methods: {
    /** 拉取热点事件并规范化列表字段，供卡片渲染。 */
    async fetchEvents() {
      const locationName = String(this.selectedRegion || "").trim();
      const topic = String(this.selectedTopic || "").trim();
      if (!locationName) {
        this.eventList = [];
        return;
      }
      this.loading = true;
      this.errorText = "";
      try {
        const response = await getHotEventsByLocation(locationName, topic);
        const rows = Array.isArray(response?.events) ? response.events : [];
        this.eventList = rows.map((row, index) => ({
          ...row,
          id: row?.id != null ? row.id : `${locationName}-${index + 1}`,
          name: String(row?.name || "").trim() || "未命名事件",
          startDateText: formatDateText(row?.createdAt, row?.startDate)
        }));
      } catch (error) {
        this.eventList = [];
        this.errorText = error?.message || "热点事件加载失败";
      } finally {
        this.loading = false;
      }
    },
    /** 点击卡片后通知父层打开“新闻详情”弹窗。 */
    onCardClick(eventItem) {
      if (!eventItem) return;
      this.$emit("open-hot-news-detail", {
        eventId: eventItem.id,
        eventName: eventItem.name,
        locationName: this.selectedRegion,
        topic: this.selectedTopic
      });
    },
    toDatetimeLocal(raw) {
      if (Array.isArray(raw) && raw.length >= 3) {
        const y = Number(raw[0]);
        const m = String(Number(raw[1]) || 1).padStart(2, "0");
        const d = String(Number(raw[2]) || 1).padStart(2, "0");
        const hh = String(Number(raw[3]) || 0).padStart(2, "0");
        const mm = String(Number(raw[4]) || 0).padStart(2, "0");
        if (Number.isFinite(y)) return `${y}-${m}-${d}T${hh}:${mm}`;
      }
      const dt = raw ? new Date(raw) : null;
      if (!dt || Number.isNaN(dt.getTime())) return "";
      const y = dt.getFullYear();
      const m = String(dt.getMonth() + 1).padStart(2, "0");
      const d = String(dt.getDate()).padStart(2, "0");
      const hh = String(dt.getHours()).padStart(2, "0");
      const mm = String(dt.getMinutes()).padStart(2, "0");
      return `${y}-${m}-${d}T${hh}:${mm}`;
    },
    async onCardContextMenu(eventItem) {
      if (!eventItem) return;
      const eventName = String(eventItem.name || "").trim();
      const topic = String(this.selectedTopic || "").trim();
      if (!eventName) return;
      try {
        const resp = await getHotEventArticles(eventName, topic);
        const first = Array.isArray(resp?.articles) && resp.articles.length ? resp.articles[0] : null;
        if (!first) return;
        const title = String(first?.title || "").trim();
        const content = String(first?.summary || first?.content || first?.title || "").trim();
        const publishTime = this.toDatetimeLocal(first?.createdAt) || this.toDatetimeLocal(new Date());
        if (!title && !content) return;
        window.dispatchEvent(
          new CustomEvent("market-stock-analyze-request", {
            detail: { title: title || "热点新闻", content, publishTime }
          })
        );
      } catch (_error) {
        // 右键联动失败不阻断热点详情流程
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

.inline-form {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.inline-label {
  flex: 0 0 auto;
  font-size: 12px;
  color: rgba(205, 234, 255, 0.9);
  white-space: nowrap;
}

/* 下拉框限制最大宽度，避免在宽格子里被 flex 拉得过宽 */
.region-select,
.topic-select {
  min-width: 0;
  box-sizing: border-box;
}

.region-select {
  flex: 0 1 auto;
  width: auto;
  max-width: min(300px, 100%);
}

.topic-select {
  flex: 0 1 auto;
  width: auto;
  max-width: min(200px, 100%);
}

.panel-content {
  flex: 1;
  min-height: 0;
  margin-top: 10px;
  padding-right: 4px;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
  scrollbar-width: thin;
  scrollbar-color: rgba(160, 170, 185, 0.85) rgba(255, 255, 255, 0.06);
}

.panel-content::-webkit-scrollbar {
  width: 8px;
}

.panel-content::-webkit-scrollbar-track {
  background: rgba(255, 255, 255, 0.06);
  border-radius: 8px;
}

.panel-content::-webkit-scrollbar-thumb {
  background: rgba(160, 170, 185, 0.85);
  border-radius: 8px;
  border: 1px solid rgba(255, 255, 255, 0.08);
}

.panel-content::-webkit-scrollbar-thumb:hover {
  background: rgba(178, 186, 198, 0.95);
}

.news-wrap {
  flex: 1 1 auto;
  min-height: 0;
  display: flex;
  flex-direction: column;
  align-content: start;
  gap: 8px;
}

.news-card {
  text-align: left;
  border: 1px solid rgba(95, 185, 255, 0.38);
  border-radius: 8px;
  padding: 10px 12px;
  background:
    linear-gradient(180deg, rgba(6, 40, 86, 0.72) 0%, rgba(4, 23, 52, 0.78) 100%);
  cursor: pointer;
  color: rgba(223, 239, 255, 0.92);
  box-shadow:
    0 0 0 1px rgba(125, 223, 255, 0.12) inset,
    0 10px 22px rgba(0, 0, 0, 0.18);
}

.news-card:hover {
  border-color: rgba(125, 223, 255, 0.78);
  background:
    linear-gradient(180deg, rgba(8, 52, 110, 0.7) 0%, rgba(6, 32, 70, 0.85) 100%);
}

.news-card__summary {
  font-size: 13px;
  font-weight: 800;
  line-height: 1.6;
  color: rgba(235, 246, 255, 0.94);
}

.news-card__time {
  margin-top: 6px;
  display: flex;
  justify-content: flex-start;
  font-size: 12px;
  font-weight: 600;
  color: rgba(185, 196, 210, 0.9);
}

.panel-tip {
  margin: 12px 0;
  border: 1px solid rgba(95, 185, 255, 0.35);
  border-radius: 8px;
  padding: 10px 12px;
  background: rgba(2, 22, 45, 0.55);
  color: rgba(205, 234, 255, 0.9);
  font-size: 12px;
}

.panel-tip--error {
  border-color: rgba(255, 117, 117, 0.5);
  color: rgba(255, 205, 205, 0.95);
}
</style>
