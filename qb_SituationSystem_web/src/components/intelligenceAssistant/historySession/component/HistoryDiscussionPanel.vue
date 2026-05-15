<template>
  <main class="discussion-column" aria-label="研讨记录">
    <div class="discussion-column-head">
      <span class="discussion-column-title">研讨记录</span>
    </div>
    <div v-if="!activeChatId" class="discussion-empty-hint panel-scrollbar">请选择左侧历史会话查看研讨记录</div>
    <div v-else-if="!messages.length" class="discussion-empty-hint panel-scrollbar">该历史会话暂无研讨记录</div>
    <div v-else class="message-list panel-scrollbar" ref="messageList">
      <div v-for="msg in messages" :key="msg.id">
        <template v-if="msg.kind === 'role'">
          <div class="chat-row kind-role">
            <div class="chat-row-inner">
              <div class="avatar role-av" :class="msg.avatarToneClass" :title="msg.country || ''">
                {{ msg.avatarText }}
              </div>
              <div class="chat-main">
                <div class="chat-name">
                  <span class="name-strong">{{ msg.name }}</span>
                  <span v-if="msg.identity" class="name-sub">{{ msg.identity }}</span>
                </div>
                <div class="bubble role-bubble">{{ msg.content }}</div>
              </div>
            </div>
          </div>
        </template>
        <template v-else-if="msg.kind === 'debate_summary'">
          <div class="summary-row">
            <div class="summary-title">{{ msg.title || "研讨总结" }}</div>
            <div class="summary-content">{{ msg.content }}</div>
          </div>
        </template>
      </div>
    </div>
  </main>
</template>

<script>
export default {
  name: "IntelligenceAssistantHistoryDiscussionPanel",
  props: {
    messages: { type: Array, default: () => [] },
    activeChatId: { type: String, default: "" }
  },
  watch: {
    messages() {
      this.$nextTick(this.scrollToBottom);
    }
  },
  methods: {
    scrollToBottom() {
      const el = this.$refs.messageList;
      if (el) el.scrollTop = el.scrollHeight;
    }
  }
};
</script>
