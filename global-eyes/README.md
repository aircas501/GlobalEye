# global_eye_service

`global_eye_service` 是 Global Eyes 项目套件中的后端服务模块，基于 Spring Boot 构建，整合新闻采集、空间目标数据、航空与船舶轨迹、风险预测、知识图谱、实时消息推送和 AI 分析能力。服务默认监听 `8081` 端口，并提供 REST API、Swagger/OpenAPI 文档、原生 WebSocket 和 STOMP 消息通道。

英文版文档见 [README_EN.md](README_EN.md)。

## 核心能力

- 多源 RSS 新闻采集、文章解析、统计和存储。
- OpenSky 航空器状态同步、历史轨迹查询和 GeoJSON 轨迹输出。
- AIS 船舶追踪数据导入、查询、筛选和维护。
- ACLED 风险地图数据导入、行政区边界导入、趋势和详情查询。
- CAST 冲突预测数据导入、风险趋势、排名和综合报告。
- Neo4j 知识图谱支撑热点事件、地区和文章关系查询。
- 原生 WebSocket 与 STOMP 消息推送，支持订阅、广播、单播和统计。
- Spring AI Alibaba / DashScope 支撑新闻解析、翻译和股票影响分析。

## 技术栈

- Java 21
- Spring Boot 3.4.5
- Spring Web / WebFlux / WebSocket / STOMP
- Spring Data JPA、Spring Data Neo4j、Spring Data Elasticsearch
- MySQL、Redis、Neo4j、Elasticsearch、RocketMQ
- Spring Cloud Alibaba Nacos Discovery
- Spring AI Alibaba DashScope、AgentScope
- Orekit、WebMagic、Rome、Jsoup、EasyExcel、Playwright
- SpringDoc OpenAPI / Swagger UI

## 目录结构

```text
global_eye_service/
├── pom.xml
├── Dockerfile
├── docker-compose.yml
├── src/main/java/com/globaleyes/
│   ├── Main.java                         # Spring Boot 启动入口
│   ├── common/                           # 通用配置
│   ├── crawler/                          # 数据采集、存储、调度、领域接口
│   ├── plugins/
│   │   ├── smartRecommendation/          # 热点事件与推荐能力
│   │   └── stockAnalysis/                # 新闻股票影响分析
│   └── websocket/                        # WebSocket、STOMP、会话和推送
├── src/main/resources/
│   ├── application.yml                   # 服务端口、Nacos、Swagger、配置导入
│   ├── config/application-ai.yml         # AgentScope / DashScope 配置
│   ├── config/data-crawler.yml           # 数据源、外部服务、调度和存储配置
│   ├── logback-spring.xml
│   └── orekit-data/                      # 开发期 Orekit 数据目录
└── src/test/java/com/globaleyes/          # 单元测试
```

## 环境要求

- JDK 21
- Maven 3.9+
- MySQL 8+
- Redis
- Neo4j
- Elasticsearch
- RocketMQ
- Nacos
- 可选外部服务：DashScope、OpenSky、ACLED、百度翻译、有道翻译

本模块的 `pom.xml` 声明父工程为 `global-eyes`，推荐从父工程目录执行 Maven 命令。

## 快速启动

从父工程目录启动：

```bash
cd global-eyes
mvn -pl global_eye_service -am spring-boot:run
```

从当前模块目录启动：

```bash
cd global_eye_service
mvn spring-boot:run
```

启动后访问：

- Swagger UI: `http://localhost:8081/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8081/v3/api-docs`
- WebSocket 健康检查: `http://localhost:8081/api/ws/health`

如本地没有 Nacos 或外部中间件，建议通过环境变量或启动参数关闭注册、调度和外部同步：

```bash
mvn spring-boot:run \
  -Dspring-boot.run.arguments="--spring.cloud.nacos.discovery.register-enabled=false --rss.crawler.enabled=false --opensky.sync.enabled=false --external.space-track.enable=false"
```

## 构建与运行

构建可执行 JAR：

```bash
mvn clean package -DskipTests
```

运行 JAR：

```bash
java -jar target/global_eye_service-*.jar
```

Docker 运行：

```bash
mvn clean package -DskipTests
docker compose up -d --build
```

`docker-compose.yml` 会将主机 `/data/orekit-data` 挂载到容器 `/orekit-data`。`StarSpotCalcUtil` 会优先读取 `/orekit-data`，因此生产环境需要准备 Orekit 数据目录。

## 配置说明

主配置入口：

- `src/main/resources/application.yml`
- `src/main/resources/config/data-crawler.yml`
- `src/main/resources/config/application-ai.yml`

敏感配置应通过环境变量、启动参数或外部配置中心覆盖，不要把真实密钥写入 README、提交记录或镜像。

常用配置项：

| 配置 | 说明 |
| --- | --- |
| `SERVER_PORT` | 服务端口，默认 `8081` |
| `SPRING_CLOUD_NACOS_DISCOVERY_REGISTER_ENABLED` | 是否注册到 Nacos，本地调试可设为 `false` |
| `SPRING_DATASOURCE_URL` / `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` | MySQL 连接配置 |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` / `REDIS_DATABASE` | Redis 连接配置 |
| `ES_URIS` / `ES_USERNAME` / `ES_PASSWORD` | Elasticsearch 连接配置 |
| `NEO4J_URI` / `NEO4J_USERNAME` / `NEO4J_PASSWORD` | Neo4j 连接配置 |
| `ROCKETMQ_NAME_SERVER` / `ROCKETMQ_PRODUCER_GROUP` | RocketMQ 生产者配置 |
| `AI_DASHSCOPE_API_KEY` / `DASHSCOPE_API_KEY` | DashScope / AgentScope 模型配置 |
| `OPENSKY_CLIENT_ID` / `OPENSKY_CLIENT_SECRET` | OpenSky OAuth 客户端配置 |
| `ACLED_USERNAME` / `ACLED_PASSWORD` / `ACLED_ACCESS_TOKEN` | ACLED 数据访问配置 |
| `BAIDU_TRANSLATE_APPID` / `BAIDU_TRANSLATE_SECRET` | 百度翻译配置 |
| `YOUDAO_TRANSLATE_APPID` / `YOUDAO_TRANSLATE_SECRET` | 有道翻译配置 |

新闻存储后端由 `storage.type` 控制，可选值包括 `local`、`mysql`、`elasticsearch`、`rocketmq`。当前配置默认使用 RocketMQ，主题为 `news-articles`。

## 主要接口分组

| 分组 | 基础路径 | 说明 |
| --- | --- | --- |
| RSS 新闻采集 | `/api/rss` | 触发采集、管理 RSS 源、查询文章和采集统计 |
| 文章解析 | `/api/article` | 单篇或批量解析文章正文 |
| AIS 船舶轨迹 | `/api/ais/track` | 船舶轨迹分页、最新数据、按 MMSI 查询、Excel 导入 |
| OpenSky 航空器 | `/api/opensky` | 手动同步、历史轨迹、GeoJSON 轨迹、过期清理 |
| ACLED 风险地图 | `/api/acled/risk` | 聚合数据导入、边界导入、地图、趋势、详情、筛选项 |
| CAST 冲突预测 | `/api/cast/forecast` | 预测数据导入、趋势、排名、风险窗口、综合报告 |
| 地理位置 | `/api/locations` | 国家和地区基础查询 |
| 热点事件 | `/api/hot-events` | 按地区、领域查询热点事件及关联文章 |
| 新闻股票分析 | `/api/news-stock` | 分析新闻对股票和市场的潜在影响 |
| WebSocket 管理 | `/api/ws` | REST 方式推送消息、广播、单播、统计和健康检查 |

完整参数和返回结构以 Swagger UI 为准。

## WebSocket 使用

原生 WebSocket：

- 连接地址：`ws://localhost:8081/ws`
- SockJS 地址：`http://localhost:8081/ws-sockjs`

客户端连接后可发送订阅消息：

```json
{
  "action": "subscribe",
  "dataType": "satellite"
}
```

取消订阅：

```json
{
  "action": "unsubscribe",
  "dataType": "satellite"
}
```

心跳：

```json
{
  "action": "ping"
}
```

STOMP：

- 连接端点：`/stomp`
- 订阅主题：`/topic/{channel}`
- 发送消息：`/app/send/{channel}`
- 按等级发送：`/app/sendWithLevel/{channel}/{level}`

消息等级支持 `info`、`warn`、`error`。

## 定时任务

- RSS 采集：由 `rss.crawler.enabled` 控制，Cron 默认为 `rss.crawler.cron`。
- OpenSky 同步：由 `opensky.sync.enabled` 控制，间隔由 `opensky.sync.interval` 控制。
- OpenSky 清理：由 `opensky.sync.cleanup-cron` 控制，默认每天凌晨执行。

本地调试时如不依赖外部服务，建议关闭相关定时任务。

## 测试

运行全部测试：

```bash
mvn test
```

运行单个测试类：

```bash
mvn -Dtest=StorageConfigTest test
mvn -Dtest=DashScopeCompatibilityTest test
mvn -Dtest=RocketmqStorageServiceTest test
```

## 开发注意事项

- `src/main/resources/orekit-data` 在 Maven 构建资源中被排除，容器运行时需要挂载 `/orekit-data`。
- `application.yml` 会导入 `config/application-ai.yml` 和 `config/data-crawler.yml`，修改配置时需要同时检查这三个文件。
- WebSocket 支持动态 `dataType`，无需预先在服务端注册固定通道。
- 多数数据接口依赖数据库、中间件或第三方 API，本地启动失败时优先检查连接配置和是否需要关闭相关调度。
- 生产环境应限制 WebSocket/CORS 来源，并通过配置中心或密钥管理系统注入敏感配置。
