<template>
  <div class="history-panel">
    <div class="history-head">
      <span class="history-title">历史会话</span>
      <button type="button" class="history-refresh-btn" :disabled="loading" @click="$emit('refresh')">
        刷新
      </button>
    </div>

    <div v-if="loading" class="history-loading">加载中…</div>

    <div v-else class="history-list panel-scrollbar">
      <div v-if="!sessions.length" class="history-empty">暂无历史会话</div>
      <button
        v-for="item in sessions"
        :key="item.chat_id"
        type="button"
        class="history-card"
        :class="{ active: activeChatId === item.chat_id }"
        @click="$emit('select', item)"
      >
        <div class="history-card-id">{{ item.chat_id }}</div>
        <div class="history-card-event">{{ item.event || "（无事件描述）" }}</div>
        <div class="history-card-foot">
          <span class="history-card-time">{{ formatTs(item.timestamp) }}</span>
          <span class="history-card-del" @click.stop="$emit('delete', item)">删除</span>
        </div>
      </button>
    </div>
  </div>
</template>

<script>
export default {
  name: "HistorySessionList",
  props: {
    sessions: { type: Array, default: () => [] },
    activeChatId: { type: String, default: "" },
    loading: { type: Boolean, default: false }
  },
  methods: {
    formatTs(ts) {
      const n = Number(ts);
      if (!Number.isFinite(n) || n <= 0) return "";
      const d = new Date(Math.round(n * 1000));
      const y = d.getFullYear();
      const m = String(d.getMonth() + 1).padStart(2, "0");
      const day = String(d.getDate()).padStart(2, "0");
      const hh = String(d.getHours()).padStart(2, "0");
      const mm = String(d.getMinutes()).padStart(2, "0");
      return `${y}-${m}-${day} ${hh}:${mm}`;
    }
  }
};
</script>
