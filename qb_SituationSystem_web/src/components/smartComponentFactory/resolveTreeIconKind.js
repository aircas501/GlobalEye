/**
 * 解析文件树图标类型（贴近常见 IDE / VS Code 文件图标主题习惯，非官方资源）。
 * @returns {{ type: 'loading' } | { type: 'folder', variant: string, open: boolean } | { type: 'file', variant: string }}
 */
export function resolveTreeIconKind({ name, isDir, expanded, loading }) {
  if (loading) return { type: "loading" };

  const n = String(name || "");
  const lower = n.toLowerCase();

  if (isDir) {
    const v = folderVariant(lower);
    return { type: "folder", variant: v, open: !!expanded };
  }

  // 特殊文件名（优先于扩展名）
  if (lower === "package.json") return { type: "file", variant: "npm" };
  if (lower === "package-lock.json" || lower === "yarn.lock" || lower === "pnpm-lock.yaml") {
    return { type: "file", variant: "lock" };
  }
  if (lower === "tsconfig.json" || lower === "jsconfig.json") return { type: "file", variant: "tsconfig" };
  if (lower === "babel.config.js" || lower === "babel.config.cjs" || lower === "babel.config.mjs") {
    return { type: "file", variant: "babel" };
  }
  if (lower === "vue.config.js" || lower === "vue.config.ts" || lower === "vite.config.js" || lower === "vite.config.ts") {
    return { type: "file", variant: "config" };
  }
  if (lower === "webpack.config.js" || lower === "webpack.config.ts") return { type: "file", variant: "webpack" };
  if (lower === "readme.md" || lower === "readme.txt") return { type: "file", variant: "readme" };
  if (lower === ".gitignore" || lower === ".gitattributes") return { type: "file", variant: "git" };
  if (lower === "dockerfile" || lower.startsWith("dockerfile.")) return { type: "file", variant: "docker" };
  if (lower === ".env" || lower.startsWith(".env.")) return { type: "file", variant: "env" };

  const dot = lower.lastIndexOf(".");
  const ext = dot >= 0 ? lower.slice(dot + 1) : "";

  const map = {
    vue: "vue",
    js: "js",
    mjs: "js",
    cjs: "js",
    jsx: "react",
    ts: "ts",
    tsx: "react_ts",
    json: "json",
    md: "markdown",
    mdx: "markdown",
    css: "css",
    scss: "scss",
    sass: "scss",
    less: "less",
    styl: "css",
    html: "html",
    htm: "html",
    xml: "xml",
    svg: "svg",
    png: "image",
    jpg: "image",
    jpeg: "image",
    gif: "image",
    webp: "image",
    ico: "image",
    woff: "font",
    woff2: "font",
    ttf: "font",
    eot: "font",
    map: "map",
    yml: "yaml",
    yaml: "yaml",
    sh: "shell",
    bash: "shell",
    zsh: "shell",
    ps1: "shell",
    bat: "shell",
    cmd: "shell",
    py: "python",
    rs: "rust",
    go: "go",
    java: "java",
    gradle: "gradle",
    properties: "properties",
    log: "log",
    txt: "text",
    zip: "zip",
    tar: "zip",
    gz: "zip",
    pdf: "pdf"
  };

  const variant = map[ext] || "file";
  return { type: "file", variant };
}

function folderVariant(lowerName) {
  const table = [
    ["src", "src"],
    ["source", "src"],
    ["lib", "lib"],
    ["dist", "dist"],
    ["build", "dist"],
    ["out", "dist"],
    ["public", "public"],
    ["static", "public"],
    ["assets", "assets"],
    ["images", "assets"],
    ["img", "assets"],
    ["icons", "assets"],
    ["components", "components"],
    ["views", "components"],
    ["layouts", "components"],
    ["pages", "components"],
    ["router", "router"],
    ["routes", "router"],
    ["store", "store"],
    ["pinia", "store"],
    ["vuex", "store"],
    ["api", "api"],
    ["services", "api"],
    ["utils", "utils"],
    ["helpers", "utils"],
    ["hooks", "hooks"],
    ["composables", "hooks"],
    ["styles", "styles"],
    ["scss", "styles"],
    ["css", "styles"],
    ["theme", "styles"],
    ["types", "types"],
    ["typings", "types"],
    ["@types", "types"],
    ["tests", "tests"],
    ["test", "tests"],
    ["__tests__", "tests"],
    ["e2e", "tests"],
    ["mock", "mock"],
    ["mocks", "mock"],
    ["scripts", "scripts"],
    ["docs", "docs"],
    [".github", "github"],
    [".vscode", "vscode"],
    [".cursor", "vscode"],
    ["node_modules", "npm"]
  ];
  for (const [key, variant] of table) {
    if (lowerName === key) return variant;
  }
  return "default";
}
