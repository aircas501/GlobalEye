<template>
  <div
    class="detail-mask"
    role="dialog"
    aria-modal="true"
    aria-label="新闻详情"
    @mousedown.self="onRequestClose"
  >
    <div class="detail-card">
      <div class="detail-topbar">
        <div class="detail-title">{{ eventTitle }}</div>
        <button class="detail-close" type="button" @click="onRequestClose" aria-label="关闭">
          ×
        </button>
      </div>

      <div class="detail-section detail-section--articles">
        <div class="detail-section-title">相关新闻</div>
        <div class="detail-section-body">
          <PanelRetrievingState v-if="articlesLoading" />
          <div v-else-if="articlesError" class="detail-tip detail-tip--error" role="status" aria-live="polite">
            {{ articlesError }}
          </div>
          <div v-else-if="articles.length" class="news-table-wrap">
            <table class="news-table">
              <thead>
                <tr>
                  <th>#</th>
                  <th>标题</th>
                  <th>来源</th>
                  <th>权威等级</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="(row, idx) in articles"
                  :key="row.id"
                  :class="{ 'is-active': selectedArticle && selectedArticle.id === row.id }"
                  @click="onSelectArticle(row)"
                >
                  <td>{{ idx + 1 }}</td>
                  <td :title="row.title">{{ row.title }}</td>
                  <td>{{ row.source || "--" }}</td>
                  <td>
                    <span :class="getAuthorityLevelClass(row.authorityLevel)">
                      {{ row.authorityLevel || "--" }}
                    </span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <div v-else class="detail-tip">暂无相关新闻</div>
        </div>
      </div>

      <div class="detail-section detail-section--content">
        <div class="detail-section-title">新闻详情</div>
        <div class="detail-section-body">
          <PanelRetrievingState v-if="contentLoading" />
          <div v-else-if="contentError" class="detail-tip detail-tip--error" role="status" aria-live="polite">
            {{ contentError }}
          </div>
          <div v-else-if="parsedSegments.length" class="detail-text">
            <span v-for="(seg, idx) in parsedSegments" :key="`seg-${idx}`">
              <span v-if="seg.type === 'text'">{{ seg.text }}</span>
              <button
                v-else
                type="button"
                class="detail-link"
                @click="onTargetClick(seg)"
              >
                {{ seg.text }}
              </button>
            </span>
          </div>
          <div v-else class="detail-placeholder">请先在上方选择一条新闻</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { getHotEventArticles, getParsedArticleContent } from "../../../api/hotEvents";
import PanelRetrievingState from "../common/PanelRetrievingState.vue";

function parseTargetSegments(rawContent) {
  const content = String(rawContent || "");
  if (!content) return [];
  const result = [];
  const regex = /<target="([^"]+)">([\s\S]*?)<\/target>/g;
  let lastIndex = 0;
  let matched = regex.exec(content);
  while (matched) {
    const [all, coordinateText, name] = matched;
    const start = matched.index;
    if (start > lastIndex) {
      result.push({ type: "text", text: content.slice(lastIndex, start) });
    }
    result.push({
      type: "target",
      text: String(name || "").trim() || "未知目标",
      coordinateText: String(coordinateText || "").trim()
    });
    lastIndex = start + all.length;
    matched = regex.exec(content);
  }
  if (lastIndex < content.length) {
    result.push({ type: "text", text: content.slice(lastIndex) });
  }
  return result;
}

function toLatLonText(lonLatText) {
  const raw = String(lonLatText || "").trim();
  if (!raw) return "";
  const parts = raw.split(",");
  if (parts.length < 2) return "";
  const lon = Number(parts[0]);
  const lat = Number(parts[1]);
  if (!Number.isFinite(lon) || !Number.isFinite(lat)) return "";
  return `${lat},${lon}`;
}

export default {
  name: "HotNewsDetailModal",
  components: { PanelRetrievingState },
  props: {
    news: { type: Object, default: null }
  },
  data() {
    return {
      articlesLoading: false,
      articlesError: "",
      articles: [],
      selectedArticle: null,
      contentLoading: false,
      contentError: "",
      parsedSegments: [],
      addedPointIds: []
    };
  },
  computed: {
    eventTitle() {
      return this.news?.eventName || "热点事件详情";
    }
  },
  watch: {
    news: {
      immediate: true,
      handler() {
        this.loadArticlesByEvent();
      }
    }
  },
  methods: {
    onRequestClose() {
      this.clearAddedIntelPoints();
      this.$emit("reset-situation-view");
      this.$emit("close");
    },
    clearAddedIntelPoints() {
      if (!Array.isArray(this.addedPointIds) || !this.addedPointIds.length) return;
      this.addedPointIds.forEach((id) => {
        this.$emit("remove-intel-point", { id });
      });
      this.addedPointIds = [];
    },
    async loadArticlesByEvent() {
      const eventName = String(this.news?.eventName || "").trim();
      const topic = String(this.news?.topic || "").trim();
      this.articles = [];
      this.selectedArticle = null;
      this.parsedSegments = [];
      this.contentError = "";
      if (!eventName) return;

      this.articlesLoading = true;
      this.articlesError = "";
      try {
        const response = await getHotEventArticles(eventName, topic);
        const rows = Array.isArray(response?.articles) ? response.articles : [];
        this.articles = rows.map((row, index) => ({
          ...row,
          id: row?.id != null ? row.id : `${eventName}-${index + 1}`,
          title: String(row?.title || "").trim() || "未命名新闻",
          source: String(row?.source || "").trim(),
          authorityLevel: String(row?.authorityLevel || "").trim(),
          mysqlId: row?.mysqlId
        }));
      } catch (error) {
        this.articles = [];
        this.articlesError = error?.message || "相关新闻加载失败";
      } finally {
        this.articlesLoading = false;
      }
    },
    async onSelectArticle(row) {
      if (!row) return;
      this.selectedArticle = row;
      const mysqlId = row?.mysqlId;
      if (mysqlId == null || String(mysqlId).trim() === "") {
        this.parsedSegments = [];
        this.contentError = "该新闻缺少 MySQL ID，无法加载正文";
        return;
      }

      this.contentLoading = true;
      this.contentError = "";
      this.parsedSegments = [];
      try {
        const response = await getParsedArticleContent(mysqlId);
        const content = String(response?.parsedContent || "").trim();
        this.parsedSegments = parseTargetSegments(content);
      } catch (error) {
        this.parsedSegments = [];
        this.contentError = error?.message || "新闻正文加载失败";
      } finally {
        this.contentLoading = false;
      }
    },
    onTargetClick(seg) {
      if (!seg) return;
      const coordinates = toLatLonText(seg.coordinateText);
      if (!coordinates) return;
      const articleId = this.selectedArticle?.mysqlId ?? this.selectedArticle?.id ?? "unknown";
      const pointId = `hotnews-${articleId}-${seg.coordinateText}-${seg.text}`;
      this.$emit("add-intel-point", {
        id: pointId,
        region: seg.text,
        coordinates,
        pulse: {
          color: "rgba(255, 36, 36, 1)",
          startedAt: Date.now(),
          period: 920,
          radius: 5,
          width: 2.6,
          alpha: 0.72
        }
      });
      this.$emit("focus-situation-point", {
        coordinates,
        zoom: 5
      });
      if (!this.addedPointIds.includes(pointId)) {
        this.addedPointIds.push(pointId);
      }
    },
    getAuthorityLevelClass(level) {
      const text = String(level || "").trim().toUpperCase();
      if (text === "A") return "authority-level authority-level--a";
      if (text === "B") return "authority-level authority-level--b";
      if (text === "C") return "authority-level authority-level--c";
      return "authority-level authority-level--other";
    }
  }
};
</script>

<style scoped>
.detail-mask {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 12px;
  background: rgba(0, 0, 0, 0.35);
  z-index: 999;
}

.detail-card {
  width: min(920px, 96%);
  height: min(78vh, 760px);
  max-height: calc(100% - 24px);
  overflow: hidden;
  border-radius: 10px;
  border: 1px solid rgba(95, 185, 255, 0.6);
  background: rgba(3, 24, 56, 0.96);
  box-shadow:
    0 18px 42px rgba(0, 0, 0, 0.55),
    0 0 0 1px rgba(125, 223, 255, 0.18) inset;
  padding: 12px 12px 14px;
  display: flex;
  flex-direction: column;
  scrollbar-width: thin;
  scrollbar-color: rgba(160, 170, 185, 0.85) rgba(255, 255, 255, 0.06);
}

.detail-card::-webkit-scrollbar {
  width: 8px;
}

.detail-card::-webkit-scrollbar-track {
  background: rgba(255, 255, 255, 0.06);
  border-radius: 8px;
}

.detail-card::-webkit-scrollbar-thumb {
  background: rgba(160, 170, 185, 0.85);
  border-radius: 8px;
  border: 1px solid rgba(255, 255, 255, 0.08);
}

.detail-card::-webkit-scrollbar-thumb:hover {
  background: rgba(178, 186, 198, 0.95);
}

.detail-topbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.detail-title {
  font-size: 14px;
  font-weight: 900;
  color: rgba(223, 239, 255, 0.98);
  line-height: 1.35;
}

.detail-close {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  border: 1px solid rgba(95, 185, 255, 0.55);
  background: rgba(2, 22, 45, 0.55);
  color: rgba(215, 236, 255, 0.92);
  cursor: pointer;
  flex: 0 0 auto;
}

.detail-section {
  margin-top: 12px;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.detail-section--articles {
  flex: 0 0 42%;
}

.detail-section--content {
  flex: 1 1 58%;
}

.detail-section-body {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

.detail-section-title {
  font-size: 12px;
  font-weight: 900;
  color: rgba(159, 212, 255, 0.95);
  margin-bottom: 6px;
}

.detail-text {
  white-space: pre-wrap;
  height: 100%;
  overflow: auto;
  padding: 10px;
  border: 1px solid rgba(95, 185, 255, 0.22);
  border-radius: 8px;
  background: rgba(6, 24, 45, 0.45);
  font-size: 12px;
  line-height: 1.7;
  color: rgba(223, 239, 255, 0.92);
}

.detail-placeholder {
  height: 100%;
  border: 1px solid rgba(95, 185, 255, 0.22);
  border-radius: 8px;
  background: rgba(6, 24, 45, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  color: rgba(170, 180, 196, 0.88);
  font-size: 12px;
}

.detail-link {
  border: none;
  padding: 0;
  background: transparent;
  color: #7de0ff;
  font-weight: 900;
  cursor: pointer;
  text-decoration: underline;
  text-underline-offset: 2px;
}

.detail-link:hover {
  color: #b8f0ff;
}

.detail-tip {
  margin-top: 6px;
  border: 1px solid rgba(95, 185, 255, 0.32);
  border-radius: 8px;
  padding: 8px 10px;
  background: rgba(6, 24, 45, 0.45);
  color: rgba(205, 234, 255, 0.9);
  font-size: 12px;
}

.detail-tip--error {
  border-color: rgba(255, 117, 117, 0.5);
  color: rgba(255, 205, 205, 0.95);
}

.news-table-wrap {
  margin-top: 0;
  height: 100%;
  overflow: auto;
  border: 1px solid rgba(95, 185, 255, 0.25);
  border-radius: 8px;
}

.news-table {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
  font-size: 12px;
  color: rgba(223, 239, 255, 0.92);
}

.news-table th,
.news-table td {
  border-bottom: 1px solid rgba(95, 185, 255, 0.2);
  padding: 8px 10px;
  text-align: center;
  vertical-align: middle;
}

.news-table th {
  position: sticky;
  top: 0;
  z-index: 1;
  background: rgba(8, 32, 66, 0.96);
  color: rgba(159, 212, 255, 0.95);
  font-weight: 800;
}

.news-table th:nth-child(1),
.news-table td:nth-child(1) {
  width: 8%;
  text-align: center;
}

.news-table th:nth-child(2),
.news-table td:nth-child(2) {
  width: 60%;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.news-table th:nth-child(3),
.news-table td:nth-child(3) {
  width: 16%;
}

.news-table th:nth-child(4),
.news-table td:nth-child(4) {
  width: 16%;
}

.news-table tbody tr {
  cursor: pointer;
  transition: background 0.15s ease;
}

.news-table tbody tr:hover {
  background: rgba(62, 160, 255, 0.16);
}

.news-table tbody tr.is-active {
  background: rgba(62, 160, 255, 0.26);
}

.authority-level {
  font-weight: 800;
}

.authority-level--a {
  color: #ff4d4f;
}

.authority-level--b {
  color: #ff9f43;
}

.authority-level--c {
  color: #ffd666;
}

.authority-level--other {
  color: rgba(170, 180, 196, 0.92);
}
</style>
