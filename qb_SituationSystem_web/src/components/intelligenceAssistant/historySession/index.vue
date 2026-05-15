<template>
  <div class="assistant-workspace">
    <aside class="settings-column panel-scrollbar" aria-label="历史会话列表">
      <HistorySessionList
        :sessions="model.historySessions"
        :active-chat-id="model.activeHistoryChatId"
        :loading="model.historyLoading || model.historyContextLoading"
        @refresh="handlers.fetchHistorySessions"
        @select="handlers.openHistorySession"
        @delete="handlers.deleteHistorySession"
      />
    </aside>
    <HistoryDiscussionPanel
      :messages="model.historyRecordMessages"
      :active-chat-id="model.activeHistoryChatId"
    />
  </div>
</template>

<script>
import HistorySessionList from "./component/HistorySessionList.vue";
import HistoryDiscussionPanel from "./component/HistoryDiscussionPanel.vue";

export default {
  name: "IntelligenceAssistantHistorySessionView",
  components: {
    HistorySessionList,
    HistoryDiscussionPanel
  },
  props: {
    model: { type: Object, required: true },
    handlers: { type: Object, required: true }
  }
};
</script>
