<template>
  <span class="ai-code-file-icon" aria-hidden="true">
    <svg
      v-if="info.type === 'loading'"
      class="ai-code-file-icon__svg ai-code-file-icon__svg--spin"
      width="16"
      height="16"
      viewBox="0 0 16 16"
    >
      <circle cx="8" cy="8" r="6" fill="none" stroke="rgba(130,200,255,0.85)" stroke-width="2" stroke-dasharray="9 20" />
    </svg>

    <svg
      v-else-if="info.type === 'folder'"
      class="ai-code-file-icon__svg"
      width="16"
      height="16"
      viewBox="0 0 24 24"
    >
      <path
        v-if="!info.open"
        d="M10 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z"
        :fill="folderFill"
      />
      <path
        v-else
        d="M20 6h-8l-2-2H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2zm0 12H4V8h16v10z"
        :fill="folderFillOpen"
      />
    </svg>

    <svg
      v-else-if="info.variant === 'vue'"
      class="ai-code-file-icon__svg"
      width="16"
      height="16"
      viewBox="0 0 16 16"
    >
      <path d="M3 2.2L8 13.5L13 2.2h-2.2L8 9.8L5.2 2.2H3z" fill="#42b883" />
      <path d="M6 2.5L8 6.9L10 2.5H9L8 5.1L7 2.5H6z" fill="#35495e" />
    </svg>

    <svg
      v-else-if="info.variant === 'js'"
      class="ai-code-file-icon__svg"
      width="16"
      height="16"
      viewBox="0 0 16 16"
    >
      <rect x="2" y="1" width="12" height="14" rx="1.5" fill="#f0db4f" />
      <path d="M5 11.2c0 .8.5 1.3 1.4 1.3.8 0 1.4-.5 1.4-1.4V8.5h-1v3.4c0 .4-.2.6-.5.6s-.5-.2-.5-.6V8.5H5v2.7zm4.5-2.7v3.5c0 .9.6 1.4 1.5 1.4.9 0 1.5-.5 1.5-1.4v-.6h-1v.5c0 .4-.2.6-.5.6-.4 0-.5-.2-.5-.6V8.5h-1z" fill="#323330" />
    </svg>

    <svg
      v-else-if="info.variant === 'ts'"
      class="ai-code-file-icon__svg"
      width="16"
      height="16"
      viewBox="0 0 16 16"
    >
      <rect x="2" y="1" width="12" height="14" rx="1.5" fill="#3178c6" />
      <path d="M5 11.2c0 .8.5 1.3 1.4 1.3.8 0 1.4-.5 1.4-1.4V8.5h-1v3.4c0 .4-.2.6-.5.6s-.5-.2-.5-.6V8.5H5v2.7zm4.5-2.7v3.5c0 .9.6 1.4 1.5 1.4.9 0 1.5-.5 1.5-1.4v-.6h-1v.5c0 .4-.2.6-.5.6-.4 0-.5-.2-.5-.6V8.5h-1z" fill="#fff" />
    </svg>

    <svg
      v-else-if="info.variant === 'react' || info.variant === 'react_ts'"
      class="ai-code-file-icon__svg"
      width="16"
      height="16"
      viewBox="0 0 16 16"
    >
      <rect x="2" y="1" width="12" height="14" rx="1.5" :fill="info.variant === 'react_ts' ? '#3178c6' : '#1c2b3a'" />
      <ellipse cx="8" cy="8" rx="4.2" ry="1.6" fill="none" stroke="#61dafb" stroke-width="0.9" />
      <ellipse cx="8" cy="8" rx="4.2" ry="1.6" fill="none" stroke="#61dafb" stroke-width="0.9" transform="rotate(60 8 8)" />
      <ellipse cx="8" cy="8" rx="4.2" ry="1.6" fill="none" stroke="#61dafb" stroke-width="0.9" transform="rotate(-60 8 8)" />
      <circle cx="8" cy="8" r="1.2" fill="#61dafb" />
    </svg>

    <svg
      v-else-if="info.variant === 'json'"
      class="ai-code-file-icon__svg"
      width="16"
      height="16"
      viewBox="0 0 16 16"
    >
      <rect x="2" y="1" width="12" height="14" rx="1.5" fill="#fbc02d" />
      <path
        d="M5.2 5.5h1.1v1H5.2v-1zm2.5 0h1.1v1H7.7v-1zm2.5 0h1.1v1h-1.1v-1zM5 8h6v1H5V8zm0 2.2h6v1H5v-1z"
        fill="#5d4037"
        opacity=".9"
      />
    </svg>

    <svg
      v-else-if="info.variant === 'markdown'"
      class="ai-code-file-icon__svg"
      width="16"
      height="16"
      viewBox="0 0 16 16"
    >
      <rect x="2" y="1" width="12" height="14" rx="1.5" fill="#42a5f5" />
      <path d="M5 4.5h6v7H5v-7zm1 1v5h4v-5H6zm1 1h2v1H7v-1zm0 2h2v1H7v-1z" fill="#e3f2fd" />
    </svg>

    <svg
      v-else-if="info.variant === 'css' || info.variant === 'scss' || info.variant === 'less'"
      class="ai-code-file-icon__svg"
      width="16"
      height="16"
      viewBox="0 0 16 16"
    >
      <rect x="2" y="1" width="12" height="14" rx="1.5" fill="#264de4" />
      <path d="M5 5h6v1H5V5zm0 2h6v1H5V7zm0 2h4v1H5V9z" fill="#ebebeb" opacity=".95" />
    </svg>

    <svg
      v-else-if="info.variant === 'html'"
      class="ai-code-file-icon__svg"
      width="16"
      height="16"
      viewBox="0 0 16 16"
    >
      <rect x="2" y="1" width="12" height="14" rx="1.5" fill="#e44d26" />
      <path d="M5 5l1 5h1.2l-.3-1.4H8l.3 1.4H9.4L8.5 5H7.3l.2 1H6.5L6.3 5H5z" fill="#fff" opacity=".95" />
    </svg>

    <svg
      v-else-if="info.variant === 'xml'"
      class="ai-code-file-icon__svg"
      width="16"
      height="16"
      viewBox="0 0 16 16"
    >
      <rect x="2" y="1" width="12" height="14" rx="1.5" fill="#8bc34a" />
      <path d="M5.5 5.5L4 8l1.5 2.5h1.2L5.3 8l1.4-2.5H5.5zm2.8 0L6.8 8l1.5 2.5h1.1L8.1 8l1.3-2.5H8.3zm2.7 0L9.5 8l1.5 2.5H12L10.5 8 12 5.5h-1z" fill="#fff" opacity=".9" />
    </svg>

    <svg
      v-else-if="info.variant === 'svg'"
      class="ai-code-file-icon__svg"
      width="16"
      height="16"
      viewBox="0 0 16 16"
    >
      <rect x="2" y="1" width="12" height="14" rx="1.5" fill="#ffb300" />
      <path d="M5 11c1.2-2.2 2.3-6 3-6.5.4 1.2 1.4 4.3 3 6.5-1.2-.8-2.4-1.2-3-1.2-.6 0-1.8.4-3 1.2z" fill="#5d4037" opacity=".85" />
    </svg>

    <svg
      v-else-if="info.variant === 'image'"
      class="ai-code-file-icon__svg"
      width="16"
      height="16"
      viewBox="0 0 16 16"
    >
      <rect x="2" y="1" width="12" height="14" rx="1.5" fill="#7e57c2" />
      <circle cx="6" cy="5.5" r="1" fill="#fffde7" opacity=".95" />
      <path d="M4 12l2.5-3 2 2.5L11 8l2 4H4z" fill="#d1c4e9" opacity=".95" />
    </svg>

    <svg
      v-else-if="info.variant === 'npm'"
      class="ai-code-file-icon__svg"
      width="16"
      height="16"
      viewBox="0 0 16 16"
    >
      <rect x="2" y="1" width="12" height="14" rx="1.5" fill="#cb3837" />
      <path d="M4 4.5h8v7H4v-7zm1 1v5h2V5.5H5zm3 0v5h2V5.5H8zm3 0v5h2V5.5h-2z" fill="#fff" opacity=".95" />
    </svg>

    <svg
      v-else-if="info.variant === 'lock'"
      class="ai-code-file-icon__svg"
      width="16"
      height="16"
      viewBox="0 0 16 16"
    >
      <rect x="2" y="1" width="12" height="14" rx="1.5" fill="#78909c" />
      <path d="M6 7V5.5a2 2 0 114 0V7h.5a1 1 0 011 1v3.5a1 1 0 01-1 1h-5a1 1 0 01-1-1V8a1 1 0 011-1H6zm1.2 0h1.6V5.6a.8.8 0 10-1.6 0V7z" fill="#eceff1" />
    </svg>

    <svg
      v-else-if="info.variant === 'tsconfig'"
      class="ai-code-file-icon__svg"
      width="16"
      height="16"
      viewBox="0 0 16 16"
    >
      <rect x="2" y="1" width="12" height="14" rx="1.5" fill="#3178c6" />
      <path d="M5 4.5h6v7H5v-7zm1 1v5h4v-5H6zm1.2 1.2h1.6c.3 0 .5.2.5.5s-.2.4-.5.4H8.2v1.1h1.1v.9H7.1v-3.9h.1z" fill="#fff" />
    </svg>

    <svg
      v-else-if="info.variant === 'babel'"
      class="ai-code-file-icon__svg"
      width="16"
      height="16"
      viewBox="0 0 16 16"
    >
      <rect x="2" y="1" width="12" height="14" rx="1.5" fill="#fdd835" />
      <path d="M8 4.2c1.6 0 2.8 1 2.8 2.3 0 .8-.5 1.5-1.3 1.9.9.4 1.5 1.1 1.5 2 0 1.4-1.4 2.4-3 2.4s-3-1-3-2.4c0-.9.6-1.6 1.5-2-.8-.4-1.3-1.1-1.3-1.9 0-1.3 1.2-2.3 2.8-2.3z" fill="#5d4037" opacity=".88" />
    </svg>

    <svg
      v-else-if="info.variant === 'webpack'"
      class="ai-code-file-icon__svg"
      width="16"
      height="16"
      viewBox="0 0 16 16"
    >
      <rect x="2" y="1" width="12" height="14" rx="1.5" fill="#1c78c0" />
      <path d="M8 4l3.2 1.8v3.6L8 11.2 4.8 9.4V5.8L8 4z" fill="#8ed6fb" opacity=".95" />
      <path d="M8 5.2l2 1.1v2.2L8 9.6l-2-1.1V6.3l2-1.1z" fill="#1c3c54" opacity=".9" />
    </svg>

    <svg
      v-else-if="info.variant === 'config'"
      class="ai-code-file-icon__svg"
      width="16"
      height="16"
      viewBox="0 0 16 16"
    >
      <rect x="2" y="1" width="12" height="14" rx="1.5" fill="#546e7a" />
      <path
        d="M8 4.5l.4 1.1 1.2-.1-.9.8.5 1.1L8 6.8l-.8.6.5-1.1-.9-.8 1.2.1L8 4.5zm-2.8 3l.7.7-.3 1h1.2l-.3-1 .7-.7-1 .2-.5-.9-.5.9-1-.2z"
        fill="#cfd8dc"
      />
    </svg>

    <svg
      v-else-if="info.variant === 'readme'"
      class="ai-code-file-icon__svg"
      width="16"
      height="16"
      viewBox="0 0 16 16"
    >
      <rect x="2" y="1" width="12" height="14" rx="1.5" fill="#42a5f5" />
      <path d="M6 5h4v1H6V5zm0 2h4v1H6V7zm0 2h3v1H6V9z" fill="#fff" opacity=".95" />
    </svg>

    <svg
      v-else-if="info.variant === 'git'"
      class="ai-code-file-icon__svg"
      width="16"
      height="16"
      viewBox="0 0 16 16"
    >
      <rect x="2" y="1" width="12" height="14" rx="1.5" fill="#f4511e" />
      <circle cx="8" cy="6" r="1.2" fill="#fff" />
      <path d="M8 7.2v3M6.2 9.5h3.6" stroke="#fff" stroke-width="0.9" stroke-linecap="round" />
    </svg>

    <svg
      v-else-if="info.variant === 'docker'"
      class="ai-code-file-icon__svg"
      width="16"
      height="16"
      viewBox="0 0 16 16"
    >
      <rect x="2" y="1" width="12" height="14" rx="1.5" fill="#039be5" />
      <path d="M5.5 8.5h1v1h-1v-1zm1.5 0h1v1H7v-1zm1.5 0h1v1H8.5v-1zm-3-1.5h1v1h-1V7zm1.5 0h1v1H7V7zm1.5 0h1v1H8.5V7z" fill="#fff" />
    </svg>

    <svg
      v-else-if="info.variant === 'env'"
      class="ai-code-file-icon__svg"
      width="16"
      height="16"
      viewBox="0 0 16 16"
    >
      <rect x="2" y="1" width="12" height="14" rx="1.5" fill="#aed581" />
      <path d="M5 6h6v.8H5V6zm0 1.8h6v.8H5v-.8zm0 1.8h4v.8H5v-.8z" fill="#33691e" opacity=".85" />
    </svg>

    <svg
      v-else-if="info.variant === 'yaml'"
      class="ai-code-file-icon__svg"
      width="16"
      height="16"
      viewBox="0 0 16 16"
    >
      <rect x="2" y="1" width="12" height="14" rx="1.5" fill="#ff7043" />
      <path d="M5 5h6v1H5V5zm0 2h6v1H5V7zm0 2h6v1H5V9z" fill="#fff3e0" opacity=".95" />
    </svg>

    <svg
      v-else-if="info.variant === 'shell'"
      class="ai-code-file-icon__svg"
      width="16"
      height="16"
      viewBox="0 0 16 16"
    >
      <rect x="2" y="1" width="12" height="14" rx="1.5" fill="#37474f" />
      <path d="M4.5 5.5l2 1.5-2 1.5" stroke="#aed581" stroke-width="0.9" fill="none" stroke-linecap="round" />
      <path d="M8.5 10h3" stroke="#90caf9" stroke-width="0.9" stroke-linecap="round" />
    </svg>

    <svg
      v-else-if="info.variant === 'python'"
      class="ai-code-file-icon__svg"
      width="16"
      height="16"
      viewBox="0 0 16 16"
    >
      <rect x="2" y="1" width="12" height="14" rx="1.5" fill="#3776ab" />
      <path d="M7.2 5.3h1.6c.4 0 .7.3.7.7v.6H7.2v-.6c0-.4.3-.7.7-.7zm-.4 3.4h2.4v.6c0 .4-.3.7-.7.7H7.6c-.4 0-.7-.3-.7-.7v-.6z" fill="#ffd54f" />
    </svg>

    <svg
      v-else-if="isLangOrMisc"
      class="ai-code-file-icon__svg"
      width="16"
      height="16"
      viewBox="0 0 16 16"
    >
      <rect x="2" y="1" width="12" height="14" rx="1.5" :fill="miscFill" />
      <path d="M5 5h6v1H5V5zm0 2h6v1H5V7zm0 2h4v1H5V9z" fill="#fff" opacity=".92" />
    </svg>

    <svg
      v-else
      class="ai-code-file-icon__svg"
      width="16"
      height="16"
      viewBox="0 0 16 16"
    >
      <rect x="2" y="1" width="12" height="14" rx="1.5" fill="#90a4ae" />
      <path d="M5 4.5h6v7H5v-7zm1 1v5h4v-5H6z" fill="#eceff1" opacity=".85" />
    </svg>
  </span>
</template>

<script>
import { resolveTreeIconKind } from "./resolveTreeIconKind.js";

const FOLDER_FILL = {
  default: "#c9a052",
  src: "#42a5f5",
  public: "#ffa726",
  components: "#66bb6a",
  api: "#ff8a65",
  utils: "#90a4ae",
  styles: "#ab47bc",
  router: "#26c6da",
  store: "#ffca28",
  assets: "#ba68c8",
  dist: "#ef9a9a",
  lib: "#5c6bc0",
  hooks: "#9ccc65",
  types: "#7986cb",
  tests: "#f48fb1",
  mock: "#a1887f",
  scripts: "#fff176",
  docs: "#4db6ac",
  github: "#b0bec5",
  vscode: "#64b5f6",
  npm: "#ffcc80"
};

const MISC = new Set([
  "rust",
  "go",
  "java",
  "gradle",
  "properties",
  "log",
  "text",
  "zip",
  "pdf",
  "map",
  "font"
]);

const MISC_FILL = {
  rust: "#ce422b",
  go: "#00add8",
  java: "#f89820",
  gradle: "#02303a",
  properties: "#78909c",
  log: "#8d6e63",
  text: "#78909c",
  zip: "#ffb300",
  pdf: "#e53935",
  map: "#78909c",
  font: "#8e24aa"
};

export default {
  name: "AiCodeFileIcon",
  props: {
    name: { type: String, required: true },
    isDir: { type: Boolean, default: false },
    expanded: { type: Boolean, default: false },
    loading: { type: Boolean, default: false }
  },
  computed: {
    info() {
      return resolveTreeIconKind({
        name: this.name,
        isDir: this.isDir,
        expanded: this.expanded,
        loading: this.loading
      });
    },
    folderFill() {
      if (this.info.type !== "folder") return FOLDER_FILL.default;
      return FOLDER_FILL[this.info.variant] || FOLDER_FILL.default;
    },
    folderFillOpen() {
      return lightenHex(this.folderFill, 0.12);
    },
    isLangOrMisc() {
      return this.info.type === "file" && MISC.has(this.info.variant);
    },
    miscFill() {
      return MISC_FILL[this.info.variant] || "#78909c";
    }
  }
};

/** 将 #rrggbb 略提亮，用于「文件夹打开」态 */
function lightenHex(hex, amount) {
  const m = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(hex);
  if (!m) return hex;
  const r = Math.min(255, Math.round(parseInt(m[1], 16) + 255 * amount));
  const g = Math.min(255, Math.round(parseInt(m[2], 16) + 255 * amount));
  const b = Math.min(255, Math.round(parseInt(m[3], 16) + 255 * amount));
  const h = (n) => n.toString(16).padStart(2, "0");
  return `#${h(r)}${h(g)}${h(b)}`;
}
</script>

<style scoped>
.ai-code-file-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  flex-shrink: 0;
  vertical-align: middle;
}
.ai-code-file-icon__svg {
  display: block;
  overflow: visible;
}
.ai-code-file-icon__svg--spin {
  animation: ai-code-file-icon-spin 0.75s linear infinite;
}
@keyframes ai-code-file-icon-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
