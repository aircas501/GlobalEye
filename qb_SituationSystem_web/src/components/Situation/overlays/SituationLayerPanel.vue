<template>
  <div class="layer-control-panel">
    <div class="layer-control-title">目标图层</div>
    <label class="layer-check">
      <input
        :checked="layerVisibility.satellite"
        type="checkbox"
        @change="toggle('satellite', $event.target.checked)"
      />
      <span class="legend-emoji" aria-hidden="true">🛰️</span>
      <span>卫星</span>
    </label>
    <label class="layer-check">
      <input
        :checked="layerVisibility.flight"
        type="checkbox"
        @change="toggle('flight', $event.target.checked)"
      />
      <span class="legend-emoji" aria-hidden="true">✈️</span>
      <span>飞机</span>
    </label>
    <label class="layer-check">
      <input
        :checked="layerVisibility.vessel"
        type="checkbox"
        @change="toggle('vessel', $event.target.checked)"
      />
      <span class="legend-emoji" aria-hidden="true">🚢</span>
      <span>船舶</span>
    </label>
    <label class="layer-check">
      <input
        :checked="layerVisibility.intel"
        type="checkbox"
        @change="toggle('intel', $event.target.checked)"
      />
      <span class="legend-emoji" aria-hidden="true">📍</span>
      <span>目标点</span>
    </label>
  </div>
</template>

<script>
/**
 * OpenLayers 矢量图层显隐开关（父组件负责 applyLayerVisibility）
 * @module components/Situation/overlays/SituationLayerPanel
 */
export default {
  name: "SituationLayerPanel",
  props: {
    layerVisibility: {
      type: Object,
      required: true
    }
  },
  methods: {
    toggle(key, checked) {
      this.$emit("update:layerVisibility", { ...this.layerVisibility, [key]: checked });
    }
  }
};
</script>
