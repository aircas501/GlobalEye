<template>
  <main class="discussion-column" aria-label="讨论区">
    <div class="discussion-column-head">
      <span class="discussion-column-title">研讨区</span>
      <span v-if="model.discussionStatusText" class="discussion-status-badge" :class="model.discussionStatusClass">
        <span v-if="model.discussionStatusClass === 'is-running'" class="discussion-status-dot" aria-hidden="true" />
        {{ model.discussionStatusText }}
      </span>
    </div>
    <div v-if="!model.visibleThreadMessages.length" class="discussion-empty-hint panel-scrollbar" />
    <div v-else class="message-list panel-scrollbar" ref="messageList">
      <div v-for="msg in model.visibleThreadMessages" :key="msg.id" class="chat-row" :class="'kind-' + msg.kind">
        <template v-if="msg.kind === 'role'">
          <div class="chat-row-inner">
            <div class="avatar role-av" :class="model.roleAvatarToneClass(msg.name)" :title="msg.country || ''">
              {{ model.roleInitial(msg.name) }}
            </div>
            <div class="chat-main">
              <div class="chat-name">
                <span class="name-strong">{{ msg.name }}</span>
                <span v-if="msg.identity" class="name-sub">{{ msg.identity }}</span>
              </div>
              <div class="bubble role-bubble">{{ msg.content }}</div>
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

      <div v-if="model.typingLabel" class="typing-row">
        <div class="typing-dots" aria-live="polite">
          <span class="typing-name">{{ model.typingLabel }}</span>
          <span class="typing-ellipsis">正在输入</span>
          <span class="typing-dots-bar">
            <span class="dot" style="animation-delay: 0s" />
            <span class="dot" style="animation-delay: 0.15s" />
            <span class="dot" style="animation-delay: 0.3s" />
          </span>
        </div>
      </div>
    </div>
  </main>
</template>

<script>
export default {
  name: "IntelligenceAssistantDiscussionPanel",
  props: {
    model: { type: Object, required: true }
  },
  watch: {
    "model.visibleThreadMessages.length"() {
      this.$nextTick(this.scrollToBottom);
    },
    "model.typingLabel"() {
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
