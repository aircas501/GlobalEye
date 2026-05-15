# 全球之眼分析系统（前端）

面向大屏与桌面的全球热点事件分析前端：左侧为 **2D（OpenLayers）/ 3D（Cesium）** 统一地图视图与底坞资讯区，右侧为可编排的情报业务网格；顶栏提供 **系统设置** 与 **智能组件工厂**；右下角 **智能讨论** 助手与地图主区、关联事件等模块联动。

## 整体架构

```
顶栏（Logo / 智能组件工厂 / 设置 / 北京时间）
├─ 主区（可拖拽竖向分割条）
│  ├─ 左栏：地图主区（SituationIndex → 2D OpenLayersScene | 3D GlobeScene）
│  │       + 底坞（实时新闻 / 实时监控）
│  └─ 右栏：情报网格（LowerTabs，可增删换位与面板事件联动）
├─ 全屏覆盖：智能组件工厂（IntelGenStudioWorkspace）
├─ 模态：系统设置（情报面板配置等）
└─ 浮动：智能讨论助手（IntelligenceAssistant）
```

- **地图主区入口**：`src/components/Situation/index.vue` 在 `2d` / `3d` 间切换，分别挂载 `OpenLayersScene.vue` 与 `GlobeScene.vue`，对外统一抛出国家选中、图层与浮层交互等事件，由 `App.vue` 与右栏协调。
- **智能化后端统一**：AI 代码（aicode）、讨论室、个性化插件列表/代理请求等，共用同一浏览器可见前缀 **`/intelligentization-api`**（开发环境由 `vue.config.js` 代理到统一服务；生产由网关/Nginx 同等反代并剥前缀）。基址解析见 `src/api/intelligentizationApiBase.js`。
- **讨论与流式**：`src/api/discussion.js` 使用 `fetch` 处理 JSON 与 `text/event-stream`（POST 流式 + 可选 GET `EventSource`），与上述统一基址一致。
- **实时目标动态**：航空器/船舶等仍通过独立 WebSocket 服务订阅；封装见 `src/api/SituationWebSocket.js`。

## 主要功能

- **2D/3D 一键切换**：统一国家搜索、选中、高亮；2D 支持视角归位、经纬网显隐等。
- **实时与历史回放**：WebSocket 按 `dataType` 订阅（如`aircraft`、`ship`）并动态渲染；历史回放等能力在场景浮层中扩展。
- **情报网格**：`LowerTabs` 网格布局，支持拖拽换位、边缘缩放、刷新、删除与空位新增；面板间事件联动（如关联事件、聚焦地图目标/国家）。
- **底坞**：左侧底部固定 **实时新闻**、**实时监控** 面板，与地图主区同屏浏览。
- **智能组件工厂**：顶栏入口打开全屏工作台，支持 AI 辅助生成/编辑情报侧 Vue 单文件组件、树形资源浏览、工具调用展示等；浏览器内编译依赖 **`runtimeCompiler: true`** 与 `vue` 完整版别名（见 `vue.config.js`）。
- **个性化插件挂载**：生成或下发的插件可在 `PersonalizedPluginMountPanel` 等入口动态挂载，网络请求经 **`postPluginProxy`** 走统一智能化基址，避免直连跨域。
- **智能讨论助手**：多角色讨论、议题与流式输出、会话历史；可与外部上下文联动。

## 右侧情报网格（默认可挂载类型）

由 `src/components/LowerTabs.vue` 与 `src/components/intelComponents/js/intelPanelMenuItems.js` 约定类型键，例如：

- 地区热点事件  
- 市场信息预测  
- 关联事件发现  

（具体面板实现位于 `src/components/intelComponents/`，可按后端与产品继续扩展。）

## 技术栈

| 类别 | 说明 |
|------|------|
| 框架 | Vue 2.7、Vue CLI 5（根组件直挂，无强制 Vuex 全局 store） |
| 地图/地球 | OpenLayers 10、Cesium 1.92 |
| 网络 | Axios（业务 API）、Fetch（讨论/SSE）、WebSocket（实时目标） |
| 可视化/工具 | ECharts、`satellite.js`、`i18n-iso-countries`、CodeMirror（组件工厂编辑） |

## 运行环境

- **Node.js**：建议 `16.x` 或 `18.x LTS`
- **包管理**：使用仓库内 `package-lock.json` 与 `npm install` 保持一致

## 安装与启动

```bash
npm install
npm run serve    # 默认 http://localhost:8080
npm run build    # 输出 dist/，Cesium 静态资源复制到 dist/cesium
```

可选：从原始数据构建国家面 GeoJSON（若使用脚本管线）：

```bash
npm run build:countries
```

## 环境变量

通过 `.env.development` / `.env.production` 配置接口前缀、瓦片模式与部署路径。**不要将含密钥的私有配置提交到版本库。**

```bash
# 实时 WS（建议走 devServer 代理路径，生产可为 wss 完整地址或同源代理路径）
VUE_APP_SATELLITE_WS_URL=/satellite-ws/ws

# 2D 底图：true=本地 public/tiles，false=外网瓦片
VUE_APP_2D_USE_LOCAL_TILE=true

# 部署子路径时与 publicPath 对齐，例如 /situation/
VUE_APP_PUBLIC_PATH=/

# 各业务 HTTP 基址（开发默认走本地代理前缀）
VUE_APP_VESSEL_API_BASE=/vessel-api
VUE_APP_SAT_API_BASE=/sat-api
VUE_APP_OVERPASS_API_BASE=/overpass-api
VUE_APP_RECOMMENDATION_API_BASE=/recommendation-api

# 智能化统一后端（aicode / 讨论 / 插件）——三者应指向同一前缀
# 也可用 VUE_APP_INTELLIGENTIZATION_API_BASE 单独覆盖（见 intelligentizationApiBase.js）
VUE_APP_AICODE_API_BASE=/intelligentization-api
VUE_APP_DISCUSSION_API_BASE=/intelligentization-api
VUE_APP_PLUGINS_API_BASE=/intelligentization-api

# 关联事件图谱（可选，默认 /graph-api）
VUE_APP_RELATED_EVENT_GRAPH_API_BASE=/graph-api
```

## 开发代理（devServer）

`vue.config.js` 中已配置代理前缀（目标主机以仓库内实际配置为准，部署时请在网关侧改为你的后端地址）：

- `/vessel-api`、`/sat-api`、`/hot-api`、`/overpass-api`、`/recommendation-api`
- **`/intelligentization-api`**：智能化统一服务；为 **SSE** 设置较长超时，并在响应为 `text/event-stream` 时去掉 `content-encoding`，避免流被错误压缩或中断
- `/graph-api`
- `/satellite-ws`（WebSocket）

**说明**：开发服务器默认 **`compress: false`**，避免 devServer 对 **SSE** 做 gzip 缓冲导致 `EventSource` / 流式接口异常。

## 关键目录说明

| 路径 | 说明 |
|------|------|
| `src/components/Situation/` | 地图主区入口、`OpenLayersScene` / `GlobeScene`、国家/图层/历史等浮层 |
| `src/api/SituationWebSocket.js` | 实时目标 WS 地址解析、订阅与重连 |
| `src/api/intelligentizationApiBase.js` | 智能化统一基址解析与 axios 路径拼接 |
| `src/api/aicodeHttp.js`、`aicodeSse.js` | 智能组件工厂 HTTP / SSE |
| `src/api/discussion.js` | 讨论室 fetch + 流式解析 |
| `src/api/pluginHttp.js` | 插件列表、详情、代理请求 |
| `src/api/countryNamesZh.js` | 中文国名表加载（`public/data/countryNamesZh.json`） |
| `src/components/intelComponents/` | 面板、弹层、底坞新闻/监控等 |
| `src/components/smartComponentFactory/` | 智能组件工厂工作台与子组件 |
| `src/components/intelComponents/personalizedPlugins/` | 个性化插件编译与挂载 |
| `src/utils/compilePastedSfc.js` | 粘贴 SFC 源码在浏览器侧编译辅助 |
| `src/components/intelligenceAssistant/` | 智能讨论助手 |
| `src/components/LowerTabs.vue` | 右侧组件网格容器 |
| `src/components/system/` | 系统设置对话框等 |
| `public/data/countries.geojson` | 国家面数据 |
| `public/data/countryNamesZh.json` | 国家代码 → 中文国名（可选） |
| `public/w2560/` | 本地国旗资源（2D 国家面板等） |
| `public/tiles/` | 可选本地离线瓦片 |

## 部署说明

- 生产环境若启用本地瓦片与国旗资源，请保证 `public/tiles`、`public/w2560`（及所用 `public/data/*`）随构建发布。
- **Cesium** 静态资源由 `CopyWebpackPlugin` 复制到 `dist/cesium`，并依赖 `main.js` 中 `window.CESIUM_BASE_URL = "/cesium"`。
- 子路径部署时同步设置 `VUE_APP_PUBLIC_PATH` 与网关路由；**`/intelligentization-api`** 须在网关与开发代理行为一致（反代到智能化服务并剥前缀），否则讨论、工厂与插件请求会失败。
