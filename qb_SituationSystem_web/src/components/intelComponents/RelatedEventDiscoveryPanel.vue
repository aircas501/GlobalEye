<template>
  <section class="card app-panel">
    <div class="module-title">
      <span class="module-title-emoji" aria-hidden="true">{{ titleEmoji }}</span>关联事件发现
    </div>
    <div class="search-row">
      <div class="query-input-wrap">
        <input
          v-model.trim="queryText"
          class="query-input"
          type="text"
          placeholder="请输入事件关键词"
          @keydown.enter.prevent="onSearch"
        />
        <button
          type="button"
          class="mic-btn"
          :class="{ 'mic-btn--active': isListening }"
          :disabled="!speechSupported"
          :title="micTitle"
          :aria-label="micAriaLabel"
          :aria-pressed="isListening"
          @click="toggleSpeechInput"
        >
          <svg class="mic-icon" viewBox="0 0 24 24" aria-hidden="true">
            <path
              fill="currentColor"
              d="M12 14c1.66 0 3-1.34 3-3V5c0-1.66-1.34-3-3-3S9 3.34 9 5v6c0 1.66 1.34 3 3 3zm5.91-3c-.49 0-.9.36-.98.85C16.52 14.2 14.47 16 12 16s-4.52-1.8-4.93-4.15c-.08-.49-.49-.85-.98-.85-.61 0-1.09.54-1 1.14.49 3 2.89 5.35 5.91 5.78V20c0 .55.45 1 1 1s1-.45 1-1v-2.08c3.02-.43 5.42-2.78 5.91-5.78.1-.6-.39-1.14-1-1.14z"
            />
          </svg>
        </button>
      </div>
      <button type="button" class="search-btn" @click="onSearch">检索</button>
    </div>
    <div v-if="sttError" class="stt-error">{{ sttError }}</div>
    <div v-if="showSettingPanel" class="setting-panel">
      <div class="setting-title">图谱参数设置</div>
      <label class="setting-item">
        <span>top_k</span>
        <input v-model.trim="draftGraphParams.topK" type="number" min="1" step="1" />
      </label>
      <label class="setting-item">
        <span>max_depth</span>
        <input v-model.trim="draftGraphParams.maxDepth" type="number" min="1" step="1" />
      </label>
      <label class="setting-item">
        <span>max_nodes</span>
        <input v-model.trim="draftGraphParams.maxNodes" type="number" min="1" step="1" />
      </label>
      <div v-if="settingError" class="setting-error">{{ settingError }}</div>
      <div class="setting-actions">
        <button type="button" class="setting-btn-ghost" @click="cancelSetting">取消</button>
        <button type="button" class="setting-btn-primary" @click="applySetting">确定</button>
      </div>
    </div>
    <div class="panel-content">
      <div v-if="!hasSearched && !isSearching" class="empty-state">点击“检索”后生成关联事件拓扑图</div>
      <PanelRetrievingState v-else-if="isSearching" />
      <div v-else class="preview-wrap">
        <button type="button" class="preview-canvas" @click="openModal">
          <div class="preview-graph">
            <svg class="topology-lines" viewBox="0 0 100 100" preserveAspectRatio="none">
              <line
                v-for="item in previewEdges"
                :key="`pv-ln-${item.id}`"
                :x1="item.sourceX"
                :y1="item.sourceY"
                :x2="item.targetX"
                :y2="item.targetY"
              />
            </svg>
            <div
              v-for="item in previewNodes"
              :key="`pv-nd-${item.id}`"
              class="branch-node branch-node--preview"
              :style="{
                left: `${item.x}%`,
                top: `${item.y}%`,
                width: `${item.previewSize}px`,
                minHeight: `${item.previewSize}px`,
                background: item.color,
                borderColor: item.borderColor
              }"
            />
          </div>
          <div class="preview-tip">已发现 {{ graphNodes.length }} 个节点，点击查看详情</div>
        </button>
      </div>
      <div v-if="requestError" class="request-error">{{ requestError }}</div>
    </div>
    <RelatedEventTopologyModal
      v-if="showDialog"
      :query-text="queryText"
      :detail-nodes="detailNodes"
      :detail-edges="detailEdges"
      @select-node="onSelectDetailNode"
      @close="closeModal"
    />
  </section>
</template>

<script>
import { getRelatedEventGraph } from "@/api/relatedEventGraph";
import PanelRetrievingState from "./common/PanelRetrievingState.vue";
import RelatedEventTopologyModal from "./overlays/RelatedEventTopologyModal.vue";
import { PANEL_TITLE_EMOJI } from "./js/panelTitleEmoji.js";

export default {
  name: "RelatedEventDiscoveryPanel",
  components: { RelatedEventTopologyModal, PanelRetrievingState },
  props: {
    panelId: { type: String, default: "" }
  },
  data() {
    return {
      titleEmoji: PANEL_TITLE_EMOJI["rel-event"],
      queryText: "",
      hasSearched: false,
      isSearching: false,
      showDialog: false,
      graphNodes: [],
      graphEdges: [],
      requestError: "",
      showSettingPanel: false,
      settingError: "",
      graphParams: {
        topK: 5,
        maxDepth: 2,
        maxNodes: 10
      },
      draftGraphParams: {
        topK: "5",
        maxDepth: "2",
        maxNodes: "10"
      },
      speechSupported: false,
      isListening: false,
      sttError: "",
      speechBaseText: "",
      speechFinalBuffer: "",
      recognition: null
    };
  },
  computed: {
    previewNodes() {
      return this.graphNodes;
    },
    detailNodes() {
      return this.graphNodes;
    },
    previewEdges() {
      return this.graphEdges;
    },
    detailEdges() {
      return this.graphEdges;
    },
    micTitle() {
      if (!this.speechSupported) return "当前浏览器不支持语音输入";
      return this.isListening ? "点击结束语音识别" : "点击开始语音输入（将请求麦克风权限）";
    },
    micAriaLabel() {
      if (!this.speechSupported) return "语音输入不可用";
      return this.isListening ? "结束语音识别" : "开始语音识别";
    }
  },
  methods: {
    onExternalOpenSetting(evt) {
      const targetPanelId = String(evt?.detail?.panelId || "");
      if (targetPanelId && this.panelId && targetPanelId !== this.panelId) return;
      this.openSettingPanel();
    },
    async onSearch() {
      if (!this.queryText) {
        this.isSearching = false;
        this.hasSearched = false;
        this.graphNodes = [];
        this.graphEdges = [];
        this.requestError = "";
        return;
      }
      this.requestError = "";
      this.isSearching = true;
      this.hasSearched = false;
      this.showDialog = false;
      try {
        const resp = await getRelatedEventGraph({
          query: this.queryText,
          top_k: this.graphParams.topK,
          max_depth: this.graphParams.maxDepth,
          max_nodes: this.graphParams.maxNodes
        });
        const { nodes, edges } = this.normalizeGraphResponse(resp);
        const { layoutNodes, layoutEdges } = this.buildGraphLayout(nodes, edges);
        this.graphNodes = layoutNodes;
        this.graphEdges = layoutEdges;
      } catch (err) {
        this.graphNodes = [];
        this.graphEdges = [];
        this.requestError = err?.message || "关联事件查询失败，请稍后重试";
      } finally {
        this.isSearching = false;
        this.hasSearched = true;
      }
    },
    normalizeGraphResponse(resp) {
      const nodes = Array.isArray(resp?.nodes) ? resp.nodes : [];
      const edges = Array.isArray(resp?.edges) ? resp.edges : [];
      const normalizedNodes = nodes.map((item, idx) => {
        const props = item?.properties || {};
        const displayName = String(props.entity_id || item?.labels?.[0] || item?.id || `节点${idx + 1}`);
        const entityType = String(props.entity_type || "").trim() || "unknown";
        return {
          id: String(item?.id ?? idx),
          title: displayName,
          shortTitle: displayName.slice(0, 4),
          description: String(props.description || "").trim(),
          entityType,
          level: 0,
          size: 18,
          previewSize: 10,
          color: this.getNodeColor(entityType),
          borderColor: this.getNodeBorderColor(entityType),
          raw: item
        };
      });
      const idSet = new Set(normalizedNodes.map((item) => item.id));
      const normalizedEdges = edges
        .map((item, idx) => {
          const sourceId = String(item?.source ?? "");
          const targetId = String(item?.target ?? "");
          if (!idSet.has(sourceId) || !idSet.has(targetId)) return null;
          return {
            id: String(item?.id ?? idx),
            source: sourceId,
            target: targetId,
            description: String(item?.properties?.description || "").trim(),
            raw: item
          };
        })
        .filter(Boolean);
      return {
        nodes: this.attachNodeLevels(normalizedNodes, normalizedEdges),
        edges: normalizedEdges
      };
    },
    attachNodeLevels(nodes, edges) {
      if (!nodes.length) return nodes;
      const degreeMap = new Map(nodes.map((n) => [n.id, 0]));
      const graphMap = new Map(nodes.map((n) => [n.id, []]));
      edges.forEach((e) => {
        if (!graphMap.has(e.source) || !graphMap.has(e.target)) return;
        graphMap.get(e.source).push(e.target);
        graphMap.get(e.target).push(e.source);
        degreeMap.set(e.source, (degreeMap.get(e.source) || 0) + 1);
        degreeMap.set(e.target, (degreeMap.get(e.target) || 0) + 1);
      });
      const rootId = nodes.reduce((bestId, node) => {
        if (!bestId) return node.id;
        return (degreeMap.get(node.id) || 0) > (degreeMap.get(bestId) || 0) ? node.id : bestId;
      }, "");
      const levelMap = new Map();
      const queue = [rootId];
      levelMap.set(rootId, 0);
      for (let i = 0; i < queue.length; i += 1) {
        const cur = queue[i];
        const curLevel = levelMap.get(cur) || 0;
        (graphMap.get(cur) || []).forEach((nextId) => {
          if (levelMap.has(nextId)) return;
          levelMap.set(nextId, curLevel + 1);
          queue.push(nextId);
        });
      }
      return nodes.map((node, idx) => {
        const level = levelMap.has(node.id) ? levelMap.get(node.id) : idx % 3;
        const size = Math.max(18, 38 - level * 4);
        const previewSize = Math.max(9, 18 - level * 2);
        return {
          ...node,
          level,
          size,
          previewSize
        };
      });
    },
    buildGraphLayout(nodes, edges) {
      const count = nodes.length;
      if (!count) return { layoutNodes: [], layoutEdges: [] };
      const { rootId, treeEdges, childrenMap, levelByNode } = this.buildMindMapTree(nodes, edges);
      const subtreeWeight = new Map();
      const calcWeight = (nodeId) => {
        const children = childrenMap.get(nodeId) || [];
        if (!children.length) {
          subtreeWeight.set(nodeId, 1);
          return 1;
        }
        let total = 0;
        children.forEach((childId) => {
          total += calcWeight(childId);
        });
        const val = Math.max(1, total);
        subtreeWeight.set(nodeId, val);
        return val;
      };
      calcWeight(rootId);
      const posMap = new Map();
      const centerX = 50;
      const centerY = 50;
      const baseRadius = 15;
      const levelStep = 13;
      const margin = 7;
      const placeNode = (nodeId, startAngle, endAngle) => {
        const level = levelByNode.get(nodeId) || 0;
        const angle = (startAngle + endAngle) / 2;
        const radius = level === 0 ? 0 : baseRadius + (level - 1) * levelStep;
        const x = centerX + Math.cos(angle) * radius;
        const y = centerY + Math.sin(angle) * radius;
        posMap.set(nodeId, { x, y });
        const children = childrenMap.get(nodeId) || [];
        if (!children.length) return;
        const span = Math.max(0.22, endAngle - startAngle);
        const totalWeight = children.reduce((sum, id) => sum + (subtreeWeight.get(id) || 1), 0) || 1;
        let cursor = startAngle;
        children.forEach((childId) => {
          const weight = subtreeWeight.get(childId) || 1;
          const childSpan = (span * weight) / totalWeight;
          placeNode(childId, cursor, cursor + childSpan);
          cursor += childSpan;
        });
      };
      // 根节点分配整圆，一级节点向四周发散。
      placeNode(rootId, -Math.PI, Math.PI);
      const unplaced = nodes.filter((n) => !posMap.has(n.id));
      unplaced.forEach((node, idx) => {
        const angle = (-Math.PI / 2) + (Math.PI * 2 * idx) / Math.max(1, unplaced.length);
        const radius = baseRadius + levelStep * 2;
        posMap.set(node.id, {
          x: centerX + Math.cos(angle) * radius,
          y: centerY + Math.sin(angle) * radius
        });
      });
      // 局部防碰撞，避免同层文字挤压。
      const points = Array.from(posMap.entries()).map(([id, p]) => ({ id, x: p.x, y: p.y }));
      for (let step = 0; step < 180; step += 1) {
        for (let i = 0; i < points.length; i += 1) {
          for (let j = i + 1; j < points.length; j += 1) {
            const a = points[i];
            const b = points[j];
            const dx = a.x - b.x;
            const dy = a.y - b.y;
            const dist = Math.sqrt(dx * dx + dy * dy) + 0.0001;
            const minDist = 7.8;
            if (dist >= minDist) continue;
            const push = (minDist - dist) * 0.42;
            const ux = dx / dist;
            const uy = dy / dist;
            a.x += ux * push;
            a.y += uy * push;
            b.x -= ux * push;
            b.y -= uy * push;
          }
        }
        points.forEach((p) => {
          p.x = Math.min(100 - margin, Math.max(margin, p.x));
          p.y = Math.min(100 - margin, Math.max(margin, p.y));
        });
      }
      points.forEach((p) => {
        posMap.set(p.id, { x: p.x, y: p.y });
      });
      const layoutNodes = nodes.map((node) => {
        const p = posMap.get(node.id) || { x: 50, y: 50 };
        return {
          ...node,
          x: Number(p.x.toFixed(2)),
          y: Number(p.y.toFixed(2))
        };
      });
      const finalPosMap = new Map(layoutNodes.map((item) => [item.id, item]));
      // 思维导图模式下只渲染主干树边，减少交叉造成的视觉混乱。
      const layoutEdges = treeEdges
        .map((edge) => {
          const source = finalPosMap.get(edge.source);
          const target = finalPosMap.get(edge.target);
          if (!source || !target) return null;
          return {
            ...edge,
            sourceX: source.x,
            sourceY: source.y,
            targetX: target.x,
            targetY: target.y
          };
        })
        .filter(Boolean);
      return { layoutNodes, layoutEdges };
    },
    buildMindMapTree(nodes, edges) {
      const degree = new Map(nodes.map((n) => [n.id, 0]));
      const adj = new Map(nodes.map((n) => [n.id, []]));
      edges.forEach((e) => {
        if (!adj.has(e.source) || !adj.has(e.target)) return;
        adj.get(e.source).push(e.target);
        adj.get(e.target).push(e.source);
        degree.set(e.source, (degree.get(e.source) || 0) + 1);
        degree.set(e.target, (degree.get(e.target) || 0) + 1);
      });
      const rootId = nodes.reduce((best, n) => {
        if (!best) return n.id;
        return (degree.get(n.id) || 0) > (degree.get(best) || 0) ? n.id : best;
      }, "");
      const parent = new Map();
      const levelByNode = new Map([[rootId, 0]]);
      const childrenMap = new Map(nodes.map((n) => [n.id, []]));
      const queue = [rootId];
      for (let i = 0; i < queue.length; i += 1) {
        const cur = queue[i];
        const nextLevel = (levelByNode.get(cur) || 0) + 1;
        (adj.get(cur) || []).forEach((next) => {
          if (levelByNode.has(next)) return;
          parent.set(next, cur);
          levelByNode.set(next, nextLevel);
          childrenMap.get(cur).push(next);
          queue.push(next);
        });
      }
      const edgeMap = new Map(edges.map((e, idx) => [`${e.source}__${e.target}`, { ...e, _idx: idx }]));
      const treeEdges = [];
      parent.forEach((p, child) => {
        const key1 = `${p}__${child}`;
        const key2 = `${child}__${p}`;
        treeEdges.push(
          edgeMap.get(key1) || edgeMap.get(key2) || { id: `${p}_${child}`, source: p, target: child }
        );
      });
      return { rootId, treeEdges, childrenMap, levelByNode };
    },
    getNodeColor(entityType) {
      const colorMap = {
        event: "rgba(0, 189, 126, 0.75)",
        organization: "rgba(92, 139, 255, 0.78)",
        person: "rgba(255, 95, 109, 0.8)",
        artifact: "rgba(246, 160, 23, 0.8)",
        location: "rgba(154, 108, 255, 0.8)",
        method: "rgba(255, 120, 40, 0.78)",
        concept: "rgba(16, 195, 214, 0.78)",
        unknown: "rgba(125, 196, 255, 0.72)"
      };
      return colorMap[entityType] || colorMap.unknown;
    },
    getNodeBorderColor(entityType) {
      const borderMap = {
        event: "rgba(140, 255, 211, 0.95)",
        organization: "rgba(164, 191, 255, 0.95)",
        person: "rgba(255, 181, 187, 0.95)",
        artifact: "rgba(255, 210, 137, 0.95)",
        location: "rgba(208, 190, 255, 0.95)",
        method: "rgba(255, 187, 146, 0.95)",
        concept: "rgba(151, 246, 255, 0.95)",
        unknown: "rgba(175, 223, 255, 0.95)"
      };
      return borderMap[entityType] || borderMap.unknown;
    },
    openSettingPanel() {
      this.settingError = "";
      this.draftGraphParams = {
        topK: String(this.graphParams.topK),
        maxDepth: String(this.graphParams.maxDepth),
        maxNodes: String(this.graphParams.maxNodes)
      };
      this.showSettingPanel = true;
    },
    validateSettingValue(value, label) {
      const text = String(value || "").trim();
      if (!text) return { ok: false, message: `${label} 为必填项` };
      const num = Number(text);
      if (!Number.isInteger(num) || num <= 0) return { ok: false, message: `${label} 需为正整数` };
      return { ok: true, value: num };
    },
    applySetting() {
      const topK = this.validateSettingValue(this.draftGraphParams.topK, "top_k");
      if (!topK.ok) {
        this.settingError = topK.message;
        return;
      }
      const maxDepth = this.validateSettingValue(this.draftGraphParams.maxDepth, "max_depth");
      if (!maxDepth.ok) {
        this.settingError = maxDepth.message;
        return;
      }
      const maxNodes = this.validateSettingValue(this.draftGraphParams.maxNodes, "max_nodes");
      if (!maxNodes.ok) {
        this.settingError = maxNodes.message;
        return;
      }
      this.graphParams = {
        topK: topK.value,
        maxDepth: maxDepth.value,
        maxNodes: maxNodes.value
      };
      this.settingError = "";
      this.showSettingPanel = false;
    },
    cancelSetting() {
      this.settingError = "";
      this.showSettingPanel = false;
    },
    openModal() {
      if (!this.graphNodes.length) return;
      this.showDialog = true;
    },
    closeModal() {
      this.showDialog = false;
    },
    onSelectDetailNode(node) {
      const description = node && node.description != null ? String(node.description).trim() : "";
      const fallbackTitle = node && node.title != null ? String(node.title).trim() : "";
      const contextText = description || fallbackTitle;
      if (!contextText) return;
      window.dispatchEvent(
        new CustomEvent("related-event-selected", {
          detail: {
            title: contextText,
            contextText,
            description,
            nodeTitle: fallbackTitle
          }
        })
      );
    },
    getSpeechRecognitionCtor() {
      return typeof window !== "undefined"
        ? window.SpeechRecognition || window.webkitSpeechRecognition
        : null;
    },
    ensureRecognition() {
      if (this.recognition) return this.recognition;
      const Ctor = this.getSpeechRecognitionCtor();
      if (!Ctor) return null;
      const r = new Ctor();
      r.lang = "zh-CN";
      r.continuous = true;
      r.interimResults = true;
      r.onresult = (event) => {
        let interim = "";
        for (let i = event.resultIndex; i < event.results.length; i += 1) {
          const piece = event.results[i][0]?.transcript || "";
          if (event.results[i].isFinal) {
            this.speechFinalBuffer += piece;
          } else {
            interim += piece;
          }
        }
        const merged = `${this.speechBaseText}${this.speechFinalBuffer}${interim}`.trim();
        this.queryText = merged;
      };
      r.onerror = (event) => {
        const code = event?.error || "";
        if (code === "aborted" || code === "no-speech") return;
        const map = {
          "not-allowed": "麦克风权限被拒绝，请在浏览器设置中允许使用麦克风",
          "service-not-allowed": "语音服务不可用，请检查网络或浏览器设置",
          "network": "语音识别需要网络，请检查连接后重试"
        };
        this.sttError = map[code] || "语音识别出错，请重试";
        this.isListening = false;
      };
      r.onend = () => {
        this.isListening = false;
      };
      this.recognition = r;
      return r;
    },
    toggleSpeechInput() {
      if (!this.speechSupported) return;
      if (this.isListening) {
        this.stopSpeechInput();
        return;
      }
      this.sttError = "";
      const r = this.ensureRecognition();
      if (!r) {
        this.sttError = "无法初始化语音识别";
        return;
      }
      this.speechBaseText = this.queryText ? `${this.queryText} ` : "";
      this.speechFinalBuffer = "";
      try {
        r.start();
        this.isListening = true;
      } catch (e) {
        this.sttError = "无法启动语音识别，请稍后重试";
        this.isListening = false;
      }
    },
    stopSpeechInput() {
      if (!this.recognition) return;
      try {
        this.recognition.stop();
      } catch (e) {
        /* ignore */
      }
      this.isListening = false;
    }
  },
  mounted() {
    this.speechSupported = !!this.getSpeechRecognitionCtor();
    window.addEventListener("related-event-open-setting", this.onExternalOpenSetting);
  },
  beforeDestroy() {
    this.stopSpeechInput();
    if (this.recognition) {
      this.recognition.onresult = null;
      this.recognition.onerror = null;
      this.recognition.onend = null;
      this.recognition = null;
    }
    window.removeEventListener("related-event-open-setting", this.onExternalOpenSetting);
  }
};
</script>

<style scoped>
.app-panel {
  height: 100%;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  position: relative;
}
.module-title {
  position: relative;
}
.search-row {
  margin-top: 8px;
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 8px;
}
.query-input-wrap {
  position: relative;
  min-width: 0;
}
.query-input {
  width: 100%;
  box-sizing: border-box;
  height: 30px;
  border-radius: 6px;
  border: 1px solid rgba(95, 185, 255, 0.45);
  background: rgba(5, 26, 57, 0.76);
  color: rgba(220, 238, 255, 0.96);
  padding: 0 34px 0 10px;
  outline: none;
}
.mic-btn {
  position: absolute;
  right: 4px;
  top: 50%;
  transform: translateY(-50%);
  width: 26px;
  height: 26px;
  padding: 0;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: rgba(140, 210, 255, 0.82);
  cursor: pointer;
  display: grid;
  place-items: center;
}
.mic-btn:hover:not(:disabled) {
  color: rgba(190, 235, 255, 0.98);
  background: rgba(30, 90, 150, 0.35);
}
.mic-btn:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}
.mic-btn--active {
  color: rgba(120, 255, 200, 0.95);
  background: rgba(0, 120, 90, 0.25);
}
.mic-icon {
  width: 16px;
  height: 16px;
  display: block;
}
.stt-error {
  margin-top: 6px;
  color: #ff9ea5;
  font-size: 12px;
}
.query-input:focus {
  border-color: rgba(120, 215, 255, 0.85);
  box-shadow: 0 0 0 1px rgba(120, 215, 255, 0.2) inset;
}
.search-btn {
  height: 30px;
  min-width: 64px;
  border-radius: 6px;
  border: 1px solid rgba(95, 185, 255, 0.65);
  color: #def2ff;
  font-weight: 700;
  cursor: pointer;
}
.search-btn {
  background: rgba(19, 107, 182, 0.4);
}
.setting-panel {
  margin-top: 8px;
  border: 1px solid rgba(95, 185, 255, 0.35);
  border-radius: 8px;
  background: rgba(4, 24, 55, 0.7);
  padding: 10px;
}
.setting-title {
  font-size: 12px;
  font-weight: 700;
  color: rgba(220, 238, 255, 0.94);
  margin-bottom: 10px;
}
.setting-item {
  display: grid;
  grid-template-columns: 90px 1fr;
  gap: 8px;
  align-items: center;
}
.setting-item + .setting-item {
  margin-top: 8px;
}
.setting-item span {
  color: rgba(194, 225, 250, 0.88);
  font-size: 12px;
}
.setting-item input {
  height: 28px;
  border-radius: 6px;
  border: 1px solid rgba(95, 185, 255, 0.45);
  background: rgba(5, 26, 57, 0.76);
  color: rgba(220, 238, 255, 0.96);
  padding: 0 8px;
  outline: none;
}
.setting-actions {
  margin-top: 10px;
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
.setting-btn-ghost,
.setting-btn-primary {
  height: 28px;
  min-width: 56px;
  border-radius: 6px;
  cursor: pointer;
}
.setting-btn-ghost {
  border: 1px solid rgba(95, 185, 255, 0.45);
  color: #cfe9ff;
  background: rgba(7, 46, 93, 0.45);
}
.setting-btn-primary {
  border: 1px solid rgba(95, 185, 255, 0.7);
  color: #e2f5ff;
  background: rgba(19, 107, 182, 0.4);
}
.panel-content {
  flex: 1;
  min-height: 0;
  margin-top: 10px;
}
.empty-state {
  height: 100%;
  display: grid;
  place-items: center;
  color: rgba(205, 234, 255, 0.7);
  border: 1px dashed rgba(94, 179, 255, 0.35);
  border-radius: 8px;
  background: rgba(4, 24, 55, 0.25);
}
.preview-wrap {
  height: 100%;
}
.preview-canvas {
  width: 100%;
  height: 100%;
  border-radius: 8px;
  border: 1px solid rgba(95, 185, 255, 0.35);
  background: radial-gradient(circle at center, rgba(38, 100, 170, 0.2), rgba(5, 20, 45, 0.7));
  position: relative;
  overflow: hidden;
  cursor: pointer;
  display: grid;
  grid-template-rows: 1fr 34px;
}
.preview-graph {
  position: relative;
  overflow: hidden;
}
.preview-tip {
  position: relative;
  z-index: 2;
  display: grid;
  place-items: center;
  font-size: 10px;
  color: rgba(186, 196, 208, 0.72);
  border-top: 1px solid rgba(95, 185, 255, 0.22);
  background: rgba(8, 33, 66, 0.5);
  padding: 0 8px;
}
.request-error,
.setting-error {
  margin-top: 8px;
  color: #ff9ea5;
  font-size: 12px;
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
  border-radius: 999px;
  border: 1px solid rgba(175, 223, 255, 0.95);
  background: rgba(125, 196, 255, 0.72);
  width: 13px;
  min-height: 13px;
  padding: 0;
}
</style>
