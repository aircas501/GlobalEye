<template>
  <div>
    <div
      class="tree-row"
      :style="{ paddingLeft: depth * 14 + 6 + 'px' }"
      :title="node.path"
      @click="onClick"
    >
      <AiCodeFileIcon
        class="tree-icon"
        :name="node.name"
        :is-dir="node.isDir"
        :expanded="expanded"
        :loading="loading && node.isDir"
      />
      <span class="tree-name">{{ node.name }}</span>
      <span v-if="node.isDir && !expanded && node.children.length" class="tree-count">
        ({{ node.children.length }})
      </span>
    </div>
    <template v-if="node.isDir && expanded">
      <AiCodeTreeNode
        v-for="child in node.children"
        :key="child.path"
        :node="child"
        :depth="depth + 1"
        @open-file="$emit('open-file', $event)"
      />
    </template>
  </div>
</template>

<script>
import AiCodeFileIcon from "./AiCodeFileIcon.vue";

const SKIP_DIRS = new Set([
  "node_modules",
  "__pycache__",
  ".git",
  "target",
  "dist",
  ".idea",
  "venv",
  ".vscode"
]);

async function scanDir(dirHandle, prefix = "") {
  const entries = [];
  for await (const [name, h] of dirHandle.entries()) {
    const isDir = h.kind === "directory";
    if (isDir && SKIP_DIRS.has(name)) continue;
    const path = prefix ? `${prefix}/${name}` : name;
    const children = isDir ? await scanDir(h, path) : [];
    entries.push({
      name,
      path,
      isDir,
      children,
      _handle: isDir ? h : null
    });
  }
  entries.sort((a, b) => {
    if (a.isDir !== b.isDir) return a.isDir ? -1 : 1;
    return a.name.localeCompare(b.name);
  });
  return entries;
}

export { scanDir };

export default {
  name: "AiCodeTreeNode",
  components: { AiCodeFileIcon },
  props: {
    node: { type: Object, required: true },
    depth: { type: Number, default: 0 }
  },
  data() {
    return {
      expanded: false,
      loading: false
    };
  },
  methods: {
    async onClick() {
      if (!this.node.isDir) {
        this.$emit("open-file", this.node.path);
        return;
      }
      if (this.expanded) {
        this.expanded = false;
        return;
      }
      if (this.node.children.length > 0) {
        this.expanded = true;
        return;
      }
      if (this.node._handle && this.node.children.length === 0) {
        this.loading = true;
        try {
          this.node.children = await scanDir(this.node._handle, this.node.path);
        } finally {
          this.loading = false;
        }
        this.expanded = this.node.children.length > 0;
        return;
      }
      this.expanded = true;
    }
  }
};
</script>

<style scoped>
.tree-row {
  display: flex;
  align-items: center;
  padding: 3px 6px;
  cursor: pointer;
  font-size: 12px;
  white-space: nowrap;
  color: rgba(210, 225, 245, 0.92);
}
.tree-row:hover {
  background: rgba(62, 186, 255, 0.12);
}
.tree-icon {
  flex-shrink: 0;
  margin-right: 5px;
}
.tree-name {
  overflow: hidden;
  text-overflow: ellipsis;
}
.tree-count {
  color: rgba(140, 170, 200, 0.55);
  font-size: 10px;
  margin-left: 4px;
}
</style>
