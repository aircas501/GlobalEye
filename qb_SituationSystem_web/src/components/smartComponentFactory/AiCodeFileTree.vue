<template>
  <div class="ai-code-file-tree">
    <div class="tree-header">{{ rootName }}</div>
    <div class="tree-body panel-scrollbar">
      <AiCodeTreeNode
        v-for="entry in tree"
        :key="entry.path"
        :node="entry"
        :depth="0"
        @open-file="$emit('open-file', $event)"
      />
    </div>
  </div>
</template>

<script>
import AiCodeTreeNode, { scanDir } from "./AiCodeTreeNode.vue";

export default {
  name: "AiCodeFileTree",
  components: { AiCodeTreeNode },
  props: {
    /**
     * FileSystemDirectoryHandle 不是「纯 Object」，不能用 type: Object，否则会触发 Vue 校验告警。
     */
    handle: {
      default: null,
      validator(v) {
        if (v == null) return true;
        return typeof v === "object" && typeof v.getFileHandle === "function";
      }
    }
  },
  data() {
    return {
      rootName: "project",
      tree: []
    };
  },
  watch: {
    handle: {
      immediate: true,
      handler() {
        this.refresh();
      }
    }
  },
  methods: {
    async refresh() {
      if (!this.handle) {
        this.tree = [];
        this.rootName = "project";
        return;
      }
      this.rootName = this.handle.name || "project";
      this.tree = await scanDir(this.handle);
    }
  }
};
</script>

<style scoped>
.ai-code-file-tree {
  height: 100%;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.tree-header {
  padding: 8px 10px;
  font-weight: 600;
  font-size: 12px;
  border-bottom: 1px solid rgba(80, 150, 220, 0.22);
  flex-shrink: 0;
  color: rgba(200, 230, 255, 0.95);
}
.tree-body {
  flex: 1;
  overflow-y: auto;
  min-height: 0;
  padding: 4px 0;
}
</style>
