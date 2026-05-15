<template>
  <div class="panel-no-data" role="status">
    <img
      class="panel-no-data-img"
      src="/assets/svg/暂无新闻.svg"
      alt=""
      draggable="false"
    />
    <p class="panel-no-data-hint">No data available</p>
  </div>
</template>

<script>
export default {
  name: "PanelNoData"
};
</script>

<style scoped>
.panel-no-data {
  flex: 1 1 auto;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 100%;
  min-height: 160px;
  pointer-events: none;
  user-select: none;
  gap: 10px;
}

.panel-no-data-img {
  width: min(84px, 32%);
  max-width: 96px;
  height: auto;
  display: block;
  opacity: 0.58;
}

.panel-no-data-hint {
  margin: 0;
  font-size: 12px;
  font-style: italic;
  color: rgba(200, 220, 240, 0.45);
  letter-spacing: 0.02em;
}
</style>
