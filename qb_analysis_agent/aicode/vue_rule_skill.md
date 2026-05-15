---
name: intel-plugin-vue-sfc
description: >-
  Generates runnable Vue 2 single-file components for GlobalEye 个性化组件 /
  智能组件工厂：强制 `<template>` + `<script>` + `<style scoped>` 结构；网络
  请求仅写路径并通过 `props.context.proxyRequest` 代理；样式对齐
  `src/styles/theme.css` 深蓝情报主题。在编写或改写动态挂载插件、
  PersonalizedPluginMountPanel 子组件、compilePastedSfc 源、或用户提及
  pluginContext / 插件 SFC / 组件工厂时使用。
---

# 情报面板个性化插件 · Vue 单文件生成规范

面向本仓库「动态编译挂载」的插件组件：输出必须**一次可编译、可运行**，并与宿主 `PersonalizedPluginMountPanel` 的 `:context="pluginContext"` 契约一致。

## 0. 前置条件与工程结构（最高优先级）

### 0.1 必须先确认项目根目录

**规则：先尝试操作，不要直接假设根目录未设置。**

1. 第一步：直接调用 `glob("*")` 或 `glob("**/*")` 查看项目目录结构
2. 如果工具返回 `❌ 未选择项目文件夹`，则提示用户：
   > "请先在左侧文件树中选择或创建一个项目根目录，之后我将在该目录下生成代码。"
3. 如果工具正常返回了文件列表（即使是空列表 `未找到匹配`），说明根目录已设置，**直接继续执行后续任务，不要再次提示**

**禁止在未尝试工具调用的情况下，就直接提示用户选择根目录。**

### 0.2 代码生成顺序：先后端，再前端

**严格遵循以下顺序：**
1. 先生成后端工程代码（Python FastAPI / Flask 等）
2. 确认后端代码无误后，再生成前端 Vue SFC

**禁止**先生成前端再生成后端，或前后端同时生成。

### 0.3 插件命名规范

- 如果用户没有指定插件名称，由 AI 根据功能需求自动生成一个简洁的英文名称（如 `data_collect`、`event_monitor`）
- **前端 Vue 单文件**以插件名称命名：`{plugin_name}.vue`（例如插件名为 `data_collect`，则文件名为 `data_collect.vue`）
- **后端工程目录**命名为：`{plugin_name}_backend`（例如 `data_collect_backend`）
- **Vue 单文件永远直接写在项目根目录下**，不放入子文件夹

### 0.4 前后端 URI 严格匹配 —— 强制机械化流程

**这条规则是铁律，违反即为生成失败。不允许任何"我认为"、"通常"、"一般是"。**

#### 强制操作步骤（每一步不可跳过）：

**第 1 步：提取后端全部路由列表**

在写前端代码之前，必须用 `grep` 或 `read_file` 把后端工程中所有接口路由逐条列出来。例如：
```
GET    /api/news/articles       → 获取新闻列表
POST   /api/news/articles       → 创建新闻
GET    /api/news/articles/{id}  → 获取单条新闻
```

**第 2 步：对着列表写前端 URI**

前端代码中每一个接口调用，**只能从第 1 步的路由列表中逐字符复制**，不得做任何修改。包括但不限于：
- ❌ 添加版本号：后端是 `/api/news`，你写 `/api/v1/news` → **v1 哪来的？删掉！**
- ❌ 去掉前缀：后端是 `/api/news`，你写 `/news` → 后端没有这个路由！
- ❌ 改变大小写：后端是 `/api/News`，你写 `/api/news` → 不匹配！
- ❌ 改变单复数：后端是 `/api/articles`，你写 `/api/article` → 不匹配！
- ❌ 添加/删除斜杠：后端是 `/api/data`，你写 `/api/data/` → 不匹配！
- ❌ 添加后缀：后端是 `/api/data`，你写 `/api/data.json` → 不匹配！
- ✅ 唯一允许的做法：**从后端路由列表中 copy-paste，一字不改**

**第 3 步：自检——每个前端 URI 都能在第 1 步的列表中找到完全一致的对应项**

在输出前端代码之前，逐个核对：前端写的 `/api/xxx` 是否与后端路由列表中的某个路径**完全一致**。如果找不到，说明你编造了路径，必须修正。

**为什么这条规则如此严格？**
因为 LLM 的训练数据中大量存在 `/api/v1/`、`/api/v2/` 等版本化路径模式，你会有强烈惯性去添加这些前缀。**这是幻觉，必须压制。** 后端代码中定义的路径就是唯一的真相来源，任何偏离都会导致 404 错误。

### 0.5 前端数据解析规范

- 前端解析后端接口返回的数据时，**必须严格按照后端代码中实际定义的返回格式**进行解析
- **禁止自由发挥或猜测**后端返回的数据结构，必须以 `read_file` 查看后端代码中实际定义的 response model / return 语句为准
- 若发现后端返回格式与前端解析逻辑不一致，会产生运行时错误，必须修正前端解析逻辑

## 1. 文件形态：单一 `.vue`（传统 SFC）

- **只交付一个** Vue 2 单文件，包含且仅按顺序包含三段：
  1. `<template>` … `</template>`
  2. `<script>` … `</script>`（默认 `export default { ... }`，不用 `<script setup>`）
  3. `<style scoped>` … `</style>`
- **禁止**：第二个 `.vue`、额外 `.js` / `.ts` 入口、在 SFC 外再拆模块（除非用户明确要求且宿主支持）。
- **命名**：`export default { name: 'XxxPlugin', ... }` 使用 PascalCase + `Plugin` 后缀，与 `name` 字段一致，便于调试。
- **依赖**：仅用浏览器与 Vue 2 内置能力；勿引入未在宿主锁定的 npm 包名（若需图表等，用原生 DOM/CSS 或用户明确提供的全局变量）。

## 2. 网络请求：路径写在组件内，主机与端口由宿主 `context` 代理

宿主挂载方式（参考 `src/components/intelComponents/personalizedPlugins/PersonalizedPluginMountPanel.vue`）：

```vue
<component :is="previewCtor" :context="pluginContext" />
```

**硬性规则：**

- **禁止**在插件代码中写死 `http://`、`https://`、IP、端口、或完整 origin（例如 `http://127.0.0.1:8080/...`）。
- 接口在代码里**只出现相对业务路径**（如 `'/data-table'`、`'/api/foo'`），由宿主侧代理转发到真实服务。
- **禁止**对业务路径直接使用全局 `fetch('/data-table')`：在开发环境下会打到 Vue devServer，易返回 HTML 导致 JSON 解析失败。应使用上下文提供的代理方法。

`pluginContext` 中与请求相关的约定（与当前仓库实现一致）：

- `this.context.proxyRequest(path, options)`：类 fetch，返回带 `json()` / `data` 的对象；**首选**。
- `this.context.request(path, init)`：返回服务端 JSON 数据（axios 解包后的 `data`）。
- 另设有全局兜底 `window.__SIG_PLUGIN_CONTEXT__`（未声明 `props` 时可用），新代码仍应**声明 `props.context`** 以保持清晰。

**必须**在组件中声明的 `props` 与请求写法模板（结构与注释保持如下语义；可按业务增删 `data` / `computed` / `methods`，但 **`context` 与 `proxyRequest` 用法不可删改义**）：

```javascript
export default {
  name: 'DataTablePlugin',
  props: {
    /** 上下文对象，由宿主应用传入，包含 apiBase 等信息 */
    context: {
      type: Object,
      default: () => ({})
    },
  },
  data() {
    return {
      tableData: {},
      loading: true,
      error: null,
      searchText: '',
    }
  },
  computed: {
    columns() {
      return this.tableData.columns || []
    },
    filteredData() {
      const rows = this.tableData.data || []
      if (!this.searchText) return rows
      const q = this.searchText.toLowerCase()
      return rows.filter(row =>
        Object.values(row).some(v => String(v).toLowerCase().includes(q))
      )
    },
  },
  mounted() {
    this.fetchData()
  },
  methods: {
    async fetchData() {
      this.loading = true
      this.error = null
      try {
        // ★ 通过代理发请求，只传路径，不关心 IP/端口
        const req = this.context.proxyRequest || fetch
        const res = await req('/data-table')
        this.tableData = res.data || (await res.json())
      } catch (e) {
        this.error = `数据加载失败: ${e.message}`
      } finally {
        this.loading = false
      }
    },
  },
}
```

说明：`context` 上除 `proxyRequest` 外，宿主还可扩展其它字段；**任何与后端地址相关的配置均由宿主注入**，插件内只消费 `this.context`，不写死环境。

## 3. 样式：贴近系统主体（`src/styles/theme.css`）

插件运行在全局主题之下，`<style scoped>` 应优先使用**同一套语义色**，与右侧情报卡片一致，避免白底浅色控件「出戏」。

### 3.1 CSS 变量（`:root`，优先使用）

| 变量 | 值 | 用途 |
|------|-----|------|
| `--bg-main` | `#031026` | 页面级深色底参考 |
| `--bg-card` | `rgba(7, 34, 79, 0.92)` | 卡片/面板背景 |
| `--line-main` | `#1c6fb8` | 主边框、分隔线 |
| `--font-main` | `#d8eeff` | 主文字 |
| `--font-muted` | `#8dc7f3` | 次要说明文字 |
| `--accent` | `#17a6ff` | 高亮、链接、强调 |

在 scoped 样式中示例：`color: var(--font-main);` `border: 1px solid var(--line-main);` `background: var(--bg-card);`

### 3.2 全局类名（可选复用，无则自行仿色）

宿主已定义的可选类（插件根节点可包一层以统一观感）：`.card`（卡片盒）、`.module-title` / `.panel-title`（约 `14px` 粗体标题色 `#9fe5ff`）、`.field` + `label`、`.btn` / `.btn-primary`、`.inline-form`、`.panel-scrollbar`（细滚动条）。

### 3.3 表单与列表常用实色（与 `theme.css` 一致）

- 输入框：`background: #062a55;` `border: 1px solid #2d6da5;` `color: var(--font-main);` `border-radius: 4px;` `height: 30px` 量级。
- 主按钮：`.btn-primary` 渐变 `linear-gradient(135deg, #1083d3, #0e4d9d)`；描边按钮 `border-color: #2f88ca`，`background: #0e3c77`，文字 `#d4f0ff`。
- 小块/列表项：`border: 1px solid #225f99;` `border-radius: 6px;` `background: rgba(5, 30, 60, 0.7);` 标题字色可与 `.event-title` 一致 `#9ee3ff`。
- 圆角习惯：**外层卡片 10px**，内层块 **6px**。
- 字体栈与全局一致：`"Segoe UI", "Microsoft YaHei", sans-serif`（或 `system-ui`）。

### 3.4 动效与可访问性

- 过渡：`0.15s ease` 量级即可，避免夸张动画。
- loading / 空状态 / 错误：使用 `--font-muted` 或 `#9ecff0` 一类冷色字，错误信息可读即可，勿用大红色满屏。

## 4. 自检清单（生成结束前在内心过一遍）

- [ ] 单文件三段齐全，`scoped` 已加。
- [ ] 无硬编码 URL/IP/端口；数据请求走 `this.context.proxyRequest`（或 `this.context.request`），路径为字符串常量或基于 `props` 的相对 path。
- [ ] `props.context` 已声明，`default: () => ({})`。
- [ ] 样式以 CSS 变量与深蓝冷色为主，无默认白底大卡片破坏整体。
- [ ] 不在模板中依赖未注入的全局（除文档允许的 `__SIG_PLUGIN_CONTEXT__` 兜底外，优先 `props`）。
- [ ] 已先尝试 glob 确认根目录可用，未盲目提示用户选择。
- [ ] 后端代码先生成，前端代码后生成。
- [ ] 前端 URI 与后端接口路径严格一致，不写 `http://` / IP / 端口。
- [ ] 前端数据解析逻辑与后端实际返回格式一致（已通过 read_file 确认）。

## 5. 与宿主行为对齐（便于排查）

- 动态组件通过 `compilePastedSfc` 编译，语法需符合 **Vue 2** 模板与选项式 API。
- 若接口返回非 JSON 或代理报错，在组件内捕获并设置 `error` 字符串，避免未处理 Promise 拒绝对控制台噪音过大。
