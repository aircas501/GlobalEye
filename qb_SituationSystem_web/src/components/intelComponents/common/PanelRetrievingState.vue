<template>
  <div class="panel-retrieving" role="status" aria-live="polite">
    <div class="panel-retrieving__glow" aria-hidden="true">
      <span class="panel-retrieving__ring" />
    </div>
    <p class="panel-retrieving__text">loading...</p>
  </div>
</template>

<script>
export default {
  name: "PanelRetrievingState"
};
</script>

<style scoped>
.panel-retrieving {
  flex: 1 1 auto;
  min-height: 160px;
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  pointer-events: none;
  user-select: none;
}

.panel-retrieving__glow {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 52px;
  height: 52px;
  border-radius: 50%;
  background: radial-gradient(
    circle,
    rgba(94, 230, 255, 0.14) 0%,
    rgba(94, 230, 255, 0) 70%
  );
}

.panel-retrieving__ring {
  display: block;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  border: 2px solid rgba(95, 185, 255, 0.22);
  border-top-color: rgba(122, 242, 255, 0.95);
  border-right-color: rgba(94, 230, 255, 0.5);
  box-shadow:
    0 0 12px rgba(80, 200, 255, 0.25),
    inset 0 0 8px rgba(80, 200, 255, 0.08);
  animation: panel-retrieving-spin 0.72s linear infinite;
}

.panel-retrieving__text {
  margin: 0;
  font-size: 13px;
  font-weight: 500;
  letter-spacing: 0.08em;
  color: rgba(158, 220, 255, 0.92);
}

@keyframes panel-retrieving-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
