/**
 * 实时媒体嵌入工具：
 * 统一构建 YouTube embed URL，减少面板重复参数定义。
 */
const YOUTUBE_EMBED_DEFAULTS = {
  autoplay: "1",
  rel: "0",
  modestbranding: "1",
  controls: "0",
  fs: "0",
  disablekb: "1",
  iv_load_policy: "3",
  cc_load_policy: "0",
  playsinline: "1"
};

/**
 * 构建 YouTube 频道直播地址（live_stream 模式）。
 * @param {string} channelId YouTube 频道 ID
 * @param {boolean} isMuted 是否静音
 * @returns {string}
 */
export function buildYoutubeChannelEmbedUrl(channelId, isMuted) {
  const params = new URLSearchParams({
    ...YOUTUBE_EMBED_DEFAULTS,
    channel: String(channelId || "").trim(),
    mute: isMuted ? "1" : "0"
  });
  return `https://www.youtube.com/embed/live_stream?${params.toString()}`;
}

/**
 * 构建 YouTube 单视频/直播回放地址。
 * @param {object} options
 * @param {string} options.videoId 视频 ID
 * @param {number} [options.startSeconds] 起播秒数
 * @param {boolean} isMuted 是否静音
 * @returns {string}
 */
export function buildYoutubeVideoEmbedUrl(options, isMuted) {
  const videoId = String(options?.videoId || "").trim();
  const params = new URLSearchParams({
    ...YOUTUBE_EMBED_DEFAULTS,
    mute: isMuted ? "1" : "0"
  });
  if (Number.isFinite(Number(options?.startSeconds))) {
    params.set("start", String(Number(options.startSeconds)));
  }
  return `https://www.youtube.com/embed/${videoId}?${params.toString()}`;
}
