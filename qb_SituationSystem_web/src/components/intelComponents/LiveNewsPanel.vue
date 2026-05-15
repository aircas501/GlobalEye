<template>
  <section class="card app-panel live-news-panel">
    <div class="module-title">
      <div class="module-title-left">
        <span class="module-title-emoji" aria-hidden="true">{{ titleEmoji }}</span>新闻直播
      </div>
      <button
        v-if="!isBilibiliChannel"
        type="button"
        class="mute-toggle-btn"
        :aria-pressed="!isMuted"
        :title="isMuted ? '开启声音' : '静音'"
        @click="toggleMute"
      >
        <img
          class="mute-toggle-btn__icon"
          :class="isMuted ? 'is-muted' : 'is-sound'"
          :src="isMuted ? muteIconSrc : soundIconSrc"
          width="18"
          height="18"
          alt=""
          aria-hidden="true"
          draggable="false"
        />
      </button>
    </div>

    <div class="channel-row" role="tablist" aria-label="新闻频道">
      <button
        v-for="ch in channels"
        :key="ch.id"
        type="button"
        class="channel-tab"
        :class="{ 'is-active': ch.id === activeId }"
        role="tab"
        :aria-selected="ch.id === activeId"
        @click="selectChannel(ch.id)"
      >
        {{ ch.label }}
      </button>
    </div>

    <div class="player-wrap">
      <iframe
        v-if="embedSrc"
        :key="`${activeId}-${isMuted ? 'muted' : 'sound'}`"
        class="player-iframe"
        title="新闻直播"
        :src="embedSrc"
        :allow="iframeAllow"
        allowfullscreen
        :referrerpolicy="iframeReferrerPolicy"
      />
    </div>
  </section>
</template>

<script>
/**
 * 实时新闻面板：
 * 提供多频道直播切换与静音控制，兼容 B 站 iframe 与 YouTube embed。
 */
import { PANEL_TITLE_EMOJI } from "./js/panelTitleEmoji.js";
import {
  buildYoutubeChannelEmbedUrl,
  buildYoutubeVideoEmbedUrl
} from "./js/liveEmbed.js";

/**
 * 使用 YouTube embed：频道当前直播
 * https://www.youtube.com/embed/live_stream?channel=CHANNEL_ID
 * CHANNEL_ID 为频道唯一 ID（非 @handle）
 */
const CHANNELS = [
{
    id: "cctv13",
    label: "CCTV13",
    iframeUrl:
      "https://www.bilibili.com/blackboard/live/live-activity-h5-player.html?cid=8178490&type=room&autoplay=1"
  },
  { id: "f24", label: "France 24", videoId: "Ap-UM1O9RBU" },
  { id: "aj", label: "Al Jazeera", videoId: "gCNeDWCI0vo" },
  { id: "dw", label: "DW", videoId: "LuKwFajn37U" },

];

export default {
  name: "LiveNewsPanel",
  data() {
    return {
      titleEmoji: PANEL_TITLE_EMOJI["live-news"],
      channels: CHANNELS,
      activeId: CHANNELS[0].id,
      isMuted: true,
      soundIconSrc: `${process.env.BASE_URL}assets/svg/声音播放.svg`,
      muteIconSrc: `${process.env.BASE_URL}assets/svg/声音静音.svg`
    };
  },
  computed: {
    activeChannel() {
      return this.channels.find((c) => c.id === this.activeId) || this.channels[0];
    },
    isBilibiliChannel() {
      const ch = this.activeChannel;
      return Boolean(ch && ch.iframeUrl && ch.iframeUrl.includes("bilibili.com"));
    },
    embedSrc() {
      const ch = this.activeChannel;
      if (!ch) return "";
      if (ch.iframeUrl) return ch.iframeUrl;
      if (ch.videoId) return buildYoutubeVideoEmbedUrl({ videoId: ch.videoId }, this.isMuted);
      return buildYoutubeChannelEmbedUrl(ch.channelId, this.isMuted);
    },
    iframeAllow() {
      if (this.isBilibiliChannel) {
        return "autoplay; fullscreen; picture-in-picture; encrypted-media";
      }
      return "accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share";
    },
    iframeReferrerPolicy() {
      // B 站直播首切偶发请求失败，放宽 referrer policy 提升首次加载成功率
      return this.isBilibiliChannel
        ? "no-referrer-when-downgrade"
        : "strict-origin-when-cross-origin";
    }
  },
  methods: {
    /** 切换新闻频道。 */
    selectChannel(id) {
      if (id === this.activeId) return;
      this.activeId = id;
    },
    /** 切换播放器静音状态。 */
    toggleMute() {
      this.isMuted = !this.isMuted;
    }
  }
};
</script>

<style scoped>
.live-news-panel {
  height: 100%;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.module-title {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 6px;
}

.module-title-left {
  display: inline-flex;
  align-items: center;
}

.mute-toggle-btn {
  /* 略大于图标，给静音态 scale 留出余量，避免视觉上比播放小一圈 */
  width: 22px;
  height: 22px;
  padding: 0;
  border: none;
  border-radius: 4px;
  background: transparent;
  cursor: pointer;
  line-height: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  overflow: visible;
}

.mute-toggle-btn:hover {
  opacity: 0.92;
}

.mute-toggle-btn__icon {
  width: 17px;
  height: 17px;
  display: block;
  object-fit: contain;
  object-position: center;
  flex-shrink: 0;
  pointer-events: none;
  transform-origin: center center;
}

/* 有声：绿色 */
.mute-toggle-btn__icon.is-sound {
  filter: brightness(0) saturate(100%) invert(52%) sepia(51%) saturate(520%)
    hue-rotate(95deg) brightness(96%) contrast(92%);
}

/* 静音：灰色；略放大以抵消 SVG 内边距差异，与播放图标视觉体量对齐 */
.mute-toggle-btn__icon.is-muted {
  filter: brightness(0) saturate(100%) invert(58%) sepia(4%) saturate(0%)
    hue-rotate(169deg) brightness(96%) contrast(88%);
  opacity: 0.9;
  transform: scale(1.14);
}

.channel-row {
  display: flex;
  flex-wrap: nowrap;
  gap: 4px;
  margin-top: 8px;
  flex-shrink: 0;
  overflow-x: auto;
  overflow-y: hidden;
  scrollbar-width: thin;
}

.channel-tab {
  padding: 4px 8px;
  border-radius: 6px;
  border: 1px solid rgba(120, 140, 165, 0.45);
  background: rgba(8, 18, 36, 0.55);
  color: rgba(220, 232, 248, 0.92);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0;
  cursor: pointer;
  line-height: 1.2;
  flex: 0 0 auto;
  white-space: nowrap;
}

.channel-tab:hover {
  border-color: rgba(95, 185, 255, 0.55);
  color: rgba(235, 246, 255, 0.98);
}

.channel-tab.is-active {
  border-color: rgba(34, 197, 94, 0.85);
  background: rgba(22, 101, 52, 0.35);
  color: #fff;
}

.player-wrap {
  flex: 1;
  min-height: 0;
  margin-top: 10px;
  position: relative;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid rgba(95, 185, 255, 0.22);
  background: #0a0f18;
}

.player-iframe {
  display: block;
  width: 100%;
  height: 100%;
  min-height: 160px;
  border: 0;
}
</style>
