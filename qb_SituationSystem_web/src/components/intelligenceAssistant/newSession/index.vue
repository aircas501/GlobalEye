<template>
  <div class="assistant-workspace">
    <ConfigPanel :model="model" :handlers="handlers" />
    <DiscussionPanel :model="model" />
  </div>
</template>

<script>
import ConfigPanel from "./component/ConfigPanel.vue";
import DiscussionPanel from "./component/DiscussionPanel.vue";

export default {
  name: "IntelligenceAssistantNewSessionView",
  components: {
    ConfigPanel,
    DiscussionPanel
  },
  props: {
    model: { type: Object, required: true },
    handlers: { type: Object, required: true }
  }
};
</script>
