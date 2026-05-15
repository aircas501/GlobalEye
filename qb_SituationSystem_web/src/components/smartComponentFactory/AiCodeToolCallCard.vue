<template>
  <div class="tool-card">
    <div class="tool-card__head">
      <span class="tool-card__name">{{ tool }}</span>
    </div>
    <pre v-if="argsStr" class="tool-card__args">{{ argsStr }}</pre>
    <pre class="tool-card__result">{{ result }}</pre>
  </div>
</template>

<script>
export default {
  name: "AiCodeToolCallCard",
  props: {
    tool: { type: String, default: "" },
    args: { type: Object, default: () => ({}) },
    result: { type: String, default: "" }
  },
  computed: {
    argsStr() {
      try {
        const s = JSON.stringify(this.args, null, 2);
        return s && s !== "{}" ? s : "";
      } catch {
        return "";
      }
    }
  }
};
</script>

<style scoped>
.tool-card {
  border-radius: 8px;
  border: 1px solid rgba(94, 179, 255, 0.35);
  background: rgba(6, 22, 48, 0.85);
  overflow: hidden;
  font-size: 11px;
}
.tool-card__head {
  padding: 6px 8px;
  background: rgba(12, 40, 72, 0.9);
  border-bottom: 1px solid rgba(80, 150, 220, 0.2);
}
.tool-card__name {
  font-weight: 700;
  color: rgba(140, 220, 255, 0.95);
}
.tool-card__args,
.tool-card__result {
  margin: 0;
  padding: 8px;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: ui-monospace, "Cascadia Code", Consolas, Menlo, monospace;
  line-height: 1.4;
  color: rgba(200, 220, 240, 0.88);
  max-height: 200px;
  overflow: auto;
}
.tool-card__args {
  border-bottom: 1px solid rgba(60, 100, 140, 0.35);
  color: rgba(180, 200, 220, 0.75);
  max-height: 120px;
}
</style>
