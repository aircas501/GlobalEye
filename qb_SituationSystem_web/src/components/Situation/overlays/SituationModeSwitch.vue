<template>
  <div class="situation-mode-switch" role="group" aria-label="态势时间模式">
    <label class="mode-option">
      <input
        :checked="value === 'realtime'"
        type="radio"
        :name="name"
        value="realtime"
        @change="$emit('input', 'realtime')"
      />
      <span>实时</span>
    </label>
    <label class="mode-option">
      <input
        :checked="value === 'history'"
        type="radio"
        :name="name"
        value="history"
        @change="$emit('input', 'history')"
      />
      <span>历史</span>
    </label>
  </div>
</template>

<script>
/**
 * 实时 / 历史态势切换（2D OpenLayers 与 3D Cesium 共用）
 * @module components/Situation/overlays/SituationModeSwitch
 */
export default {
  name: "SituationModeSwitch",
  model: {
    prop: "value",
    event: "input"
  },
  props: {
    /** 当前模式：realtime | history */
    value: { type: String, default: "realtime" },
    /** 同页多实例时区分 radio name，避免冲突 */
    name: { type: String, default: "situationMode" }
  }
};
</script>
