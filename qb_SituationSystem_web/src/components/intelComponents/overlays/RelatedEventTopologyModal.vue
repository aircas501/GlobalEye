<template>
  <div class="dialog-mask" :style="maskStyle" @click.self="$emit('close')">
    <section class="dialog-panel">
      <header class="dialog-head">
        <h3>关联事件拓扑详情</h3>
        <button type="button" class="close-btn" @click="$emit('close')">×</button>
      </header>
      <div ref="dialogBody" class="dialog-body">
        <svg class="topology-lines" viewBox="0 0 100 100" preserveAspectRatio="none">
          <line
            v-for="item in detailEdges"
            :key="`dt-ln-${item.id}`"
            :x1="item.sourceX"
            :y1="item.sourceY"
            :x2="item.targetX"
            :y2="item.targetY"
          />
        </svg>
        <div
          v-for="item in detailNodes"
          :key="`dt-nd-${item.id}`"
          class="branch-node"
          :style="{
            left: `${item.x}%`,
            top: `${item.y}%`,
            width: `${item.size}px`,
            minHeight: `${item.size}px`,
            background: item.color,
            borderColor: item.borderColor,
            fontSize: `${Math.max(8, Math.floor(item.size * 0.24))}px`
          }"
          @mouseenter="showTooltip(item, $event)"
          @mouseleave="hideTooltip"
          @click.stop="$emit('select-node', item)"
        >
          {{ item.shortTitle || item.title }}
        </div>

        <aside v-if="hoverNode" class="tooltip-card" :style="{ left: `${tooltipPos.x}px`, top: `${tooltipPos.y}px` }">
          <div class="tip-title">{{ hoverNode.title }}</div>
          <div class="tip-line"><span>类型：</span>{{ hoverNode.entityType || "未知" }}</div>
          <div class="tip-line"><span>描述：</span>{{ hoverNode.description || "暂无描述" }}</div>
        </aside>
      </div>
    </section>
  </div>
</template>

<script>
export default {
  name: "RelatedEventTopologyModal",
  props: {
    queryText: { type: String, default: "" },
    detailNodes: { type: Array, default: () => [] },
    detailEdges: { type: Array, default: () => [] }
  },
  data() {
    return {
      hoverNode: null,
      tooltipPos: { x: 24, y: 24 },
      _mountedToBody: false,
      maskRect: {
        left: 0,
        top: 0,
        width: 0,
        height: 0
      }
    };
  },
  computed: {
    maskStyle() {
      return {
        left: `${this.maskRect.left}px`,
        top: `${this.maskRect.top}px`,
        width: `${this.maskRect.width}px`,
        height: `${this.maskRect.height}px`
      };
    }
  },
  mounted() {
    if (this.$el && this.$el.parentNode !== document.body) {
      document.body.appendChild(this.$el);
      this._mountedToBody = true;
    }
    this.syncMaskRect();
    window.addEventListener("resize", this.syncMaskRect);
    window.addEventListener("scroll", this.syncMaskRect, true);
  },
  beforeDestroy() {
    window.removeEventListener("resize", this.syncMaskRect);
    window.removeEventListener("scroll", this.syncMaskRect, true);
    if (this._mountedToBody && this.$el && this.$el.parentNode === document.body) {
      document.body.removeChild(this.$el);
      this._mountedToBody = false;
    }
  },
  methods: {
    syncMaskRect() {
      const panelRoot = document.querySelector(".lower-tabs-root");
      const targetRect = panelRoot ? panelRoot.getBoundingClientRect() : null;
      if (!targetRect) {
        this.maskRect = {
          left: 0,
          top: 0,
          width: window.innerWidth,
          height: window.innerHeight
        };
        return;
      }
      this.maskRect = {
        left: Math.max(0, targetRect.left),
        top: Math.max(0, targetRect.top),
        width: Math.max(0, targetRect.width),
        height: Math.max(0, targetRect.height)
      };
    },
    showTooltip(node, evt) {
      this.hoverNode = node;
      const bodyEl = this.$refs.dialogBody;
      if (!bodyEl) {
        this.tooltipPos = { x: 24, y: 24 };
        return;
      }

      const bodyRect = bodyEl.getBoundingClientRect();
      const nodeX = evt && evt.clientX ? evt.clientX - bodyRect.left : bodyRect.width / 2;
      const nodeY = evt && evt.clientY ? evt.clientY - bodyRect.top : bodyRect.height / 2;

      const tooltipW = Math.min(320, bodyRect.width * 0.34);
      const tooltipH = 210;
      const gap = 14;

      // 在节点的对角方向放置浮层，避免覆盖节点本身。
      let left = nodeX < bodyRect.width / 2 ? nodeX + gap : nodeX - tooltipW - gap;
      let top = nodeY < bodyRect.height / 2 ? nodeY + gap : nodeY - tooltipH - gap;

      const minX = 8;
      const minY = 8;
      const maxX = Math.max(minX, bodyRect.width - tooltipW - 8);
      const maxY = Math.max(minY, bodyRect.height - tooltipH - 8);

      left = Math.min(maxX, Math.max(minX, left));
      top = Math.min(maxY, Math.max(minY, top));

      this.tooltipPos = { x: left, y: top };
    },
    hideTooltip() {
      this.hoverNode = null;
    }
  }
};
</script>

<style scoped>
.dialog-mask {
  position: fixed;
  z-index: 6000;
  background: rgba(4, 12, 24, 0.62);
  display: grid;
  place-items: center;
  overflow: hidden;
}

.dialog-panel {
  width: min(760px, calc(100% - 20px));
  height: min(500px, calc(100% - 20px));
  border-radius: 10px;
  border: 1px solid rgba(95, 185, 255, 0.45);
  background: linear-gradient(180deg, rgba(9, 28, 58, 0.98), rgba(5, 19, 42, 0.98));
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.dialog-head {
  height: 40px;
  padding: 0 12px;
  border-bottom: 1px solid rgba(95, 185, 255, 0.25);
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.dialog-head h3 {
  margin: 0;
  font-size: 14px;
  color: rgba(220, 238, 255, 0.95);
}

.close-btn {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  border: 1px solid rgba(95, 185, 255, 0.4);
  color: #dff2ff;
  background: rgba(7, 46, 93, 0.7);
  font-size: 18px;
  line-height: 1;
  cursor: pointer;
}

.dialog-body {
  position: relative;
  flex: 1;
  overflow: hidden;
}

.topology-lines {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}

.topology-lines line {
  stroke: rgba(132, 217, 255, 0.52);
  stroke-width: 0.45;
}

.branch-node {
  position: absolute;
  transform: translate(-50%, -50%);
  border-radius: 50%;
  border: 1px solid rgba(175, 223, 255, 0.95);
  background: rgba(125, 196, 255, 0.72);
  color: #dff2ff;
  display: grid;
  place-items: center;
  text-align: center;
}

.branch-node {
  width: 26px;
  min-height: 26px;
  padding: 0;
  font-size: 9px;
  line-height: 1;
  cursor: pointer;
  overflow: hidden;
}

.tooltip-card {
  position: absolute;
  z-index: 4;
  width: min(320px, 34%);
  border-radius: 8px;
  border: 1px solid rgba(106, 203, 255, 0.45);
  background: rgba(5, 27, 58, 0.95);
  padding: 10px;
  color: rgba(225, 241, 255, 0.95);
  box-shadow: 0 8px 22px rgba(4, 18, 38, 0.42);
  font-size: 12px;
}

.tip-title {
  font-weight: 800;
  margin-bottom: 8px;
}

.tip-line + .tip-line {
  margin-top: 6px;
}

.tip-line span {
  color: rgba(136, 214, 255, 0.95);
}
</style>
