<template>
  <div v-if="visible" class="history-control-panel">
    <div class="country-panel-title">历史态势</div>
    <div class="history-field">
      <label>开始时间</label>
      <input
        :value="historyStartTime"
        type="datetime-local"
        class="history-input history-datetime-input"
        step="1"
        @focus="$emit('open-datetime-picker', $event)"
        @click="$emit('open-datetime-picker', $event)"
        @input="$emit('update:historyStartTime', $event.target.value)"
      />
    </div>
    <div class="history-field">
      <label>结束时间</label>
      <input
        :value="historyEndTime"
        type="datetime-local"
        class="history-input history-datetime-input"
        step="1"
        @focus="$emit('open-datetime-picker', $event)"
        @click="$emit('open-datetime-picker', $event)"
        @input="$emit('update:historyEndTime', $event.target.value)"
      />
    </div>
    <div class="history-field">
      <label>倍速</label>
      <select
        :value="historySpeed"
        class="history-select"
        @change="$emit('update:historySpeed', Number($event.target.value))"
      >
        <option :value="1">1x</option>
        <option :value="2">2x</option>
        <option :value="4">4x</option>
        <option :value="8">8x</option>
        <option :value="16">16x</option>
      </select>
    </div>
    <div class="history-field">
      <label>进度</label>
      <input
        :value="historyCurrentMinute"
        class="history-slider"
        type="range"
        min="0"
        :max="historySliderMax"
        @mousedown="$emit('progress-drag-start')"
        @mouseup="$emit('progress-drag-end')"
        @input="$emit('update:historyCurrentMinute', Number($event.target.value))"
      />
      <div class="history-time-text">{{ historyPlaybackTimeText }}</div>
    </div>
    <div class="history-actions">
      <button type="button" class="history-btn history-btn-primary" @click="$emit('play-pause')">
        {{ historyPlaying ? "暂停" : "开始" }}
      </button>
      <button type="button" class="history-btn" @click="$emit('end')">结束</button>
    </div>
  </div>
</template>

<script>
/**
 * 历史态势时间范围、倍速与进度条（数据拉取与定时器在父组件）
 * @module components/Situation/overlays/SituationHistoryPanel
 */
export default {
  name: "SituationHistoryPanel",
  props: {
    visible: { type: Boolean, default: false },
    historyStartTime: { type: String, default: "" },
    historyEndTime: { type: String, default: "" },
    historySpeed: { type: Number, default: 1 },
    historyCurrentMinute: { type: Number, default: 0 },
    /** range 最大值，来自父组件计算的 historyMaxMinutes */
    historySliderMax: { type: Number, default: 1 },
    historyPlaybackTimeText: { type: String, default: "" },
    historyPlaying: { type: Boolean, default: false }
  }
};
</script>
