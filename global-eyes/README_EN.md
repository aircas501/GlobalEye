# global_eye_service

`global_eye_service` is the backend service module of the Global Eyes project suite. It is built with Spring Boot and combines news crawling, space-object data, aircraft and vessel tracking, risk forecasting, knowledge graph queries, real-time messaging, and AI-powered analysis. The service listens on port `8081` by default and exposes REST APIs, Swagger/OpenAPI documentation, native WebSocket, and STOMP channels.

Chinese documentation: [README.md](README.md).

## Capabilities

- Multi-source RSS news crawling, article parsing, statistics, and storage.
- OpenSky aircraft state sync, historical trajectory query, and GeoJSON trajectory output.
- AIS vessel tracking data import, query, filtering, and maintenance.
- ACLED risk map data import, ADM1 boundary import, trend query, and detail query.
- CAST conflict forecast import, risk trend, rankings, risk windows, and aggregate reports.
- Neo4j-backed knowledge graph queries for hot events, locations, and related articles.
- Native WebSocket and STOMP messaging with subscription, broadcast, unicast, and statistics.
- Spring AI Alibaba / DashScope support for news parsing, translation, and stock-impact analysis.

## Tech Stack

- Java 21
- Spring Boot 3.4.5
- Spring Web / WebFlux / WebSocket / STOMP
- Spring Data JPA, Spring Data Neo4j, Spring Data Elasticsearch
- MySQL, Redis, Neo4j, Elasticsearch, RocketMQ
- Spring Cloud Alibaba Nacos Discovery
- Spring AI Alibaba DashScope, AgentScope
- Orekit, WebMagic, Rome, Jsoup, EasyExcel, Playwright
- SpringDoc OpenAPI / Swagger UI

## Project Layout

```text
global_eye_service/
├── pom.xml
├── Dockerfile
├── docker-compose.yml
├── src/main/java/com/globaleyes/
│   ├── Main.java                         # Spring Boot entry point
│   ├── common/                           # Shared configuration
│   ├── crawler/                          # Crawling, storage, scheduling, domain APIs
│   ├── plugins/
│   │   ├── smartRecommendation/          # Hot events and recommendation features
│   │   └── stockAnalysis/                # News-to-stock impact analysis
│   └── websocket/                        # WebSocket, STOMP, sessions, message push
├── src/main/resources/
│   ├── application.yml                   # Port, Nacos, Swagger, config imports
│   ├── config/application-ai.yml         # AgentScope / DashScope configuration
│   ├── config/data-crawler.yml           # Data sources, external services, schedules, storage
│   ├── logback-spring.xml
│   └── orekit-data/                      # Development-time Orekit data directory
└── src/test/java/com/globaleyes/          # Unit tests
```

## Requirements

- JDK 21
- Maven 3.9+
- MySQL 8+
- Redis
- Neo4j
- Elasticsearch
- RocketMQ
- Nacos
- Optional external services: DashScope, OpenSky, ACLED, Baidu Translate, Youdao Translate

This module declares `global-eyes` as its Maven parent. Running Maven commands from the parent project directory is recommended.

## Quick Start

Start from the parent project directory:

```bash
cd global-eyes
mvn -pl global_eye_service -am spring-boot:run
```

Start from this module directory:

```bash
cd global_eye_service
mvn spring-boot:run
```

After startup:

- Swagger UI: `http://localhost:8081/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8081/v3/api-docs`
- WebSocket health check: `http://localhost:8081/api/ws/health`

If Nacos or external middleware is not available locally, disable registration, schedules, and external sync through environment variables or startup arguments:

```bash
mvn spring-boot:run \
  -Dspring-boot.run.arguments="--spring.cloud.nacos.discovery.register-enabled=false --rss.crawler.enabled=false --opensky.sync.enabled=false --external.space-track.enable=false"
```

## Build and Run

Build the executable JAR:

```bash
mvn clean package -DskipTests
```

Run the JAR:

```bash
java -jar target/global_eye_service-*.jar
```

Run with Docker:

```bash
mvn clean package -DskipTests
docker compose up -d --build
```

`docker-compose.yml` mounts host path `/data/orekit-data` to container path `/orekit-data`. `StarSpotCalcUtil` reads `/orekit-data` first, so production deployments must provide a valid Orekit data directory.

## Configuration

Main configuration files:

- `src/main/resources/application.yml`
- `src/main/resources/config/data-crawler.yml`
- `src/main/resources/config/application-ai.yml`

Sensitive values should be injected through environment variables, startup arguments, or an external config center. Do not add real secrets to README files, commit history, or container images.

Common settings:

| Setting | Purpose |
| --- | --- |
| `SERVER_PORT` | Service port, default `8081` |
| `SPRING_CLOUD_NACOS_DISCOVERY_REGISTER_ENABLED` | Nacos registration toggle. Set to `false` for isolated local runs |
| `SPRING_DATASOURCE_URL` / `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` | MySQL connection |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` / `REDIS_DATABASE` | Redis connection |
| `ES_URIS` / `ES_USERNAME` / `ES_PASSWORD` | Elasticsearch connection |
| `NEO4J_URI` / `NEO4J_USERNAME` / `NEO4J_PASSWORD` | Neo4j connection |
| `ROCKETMQ_NAME_SERVER` / `ROCKETMQ_PRODUCER_GROUP` | RocketMQ producer configuration |
| `AI_DASHSCOPE_API_KEY` / `DASHSCOPE_API_KEY` | DashScope / AgentScope model configuration |
| `OPENSKY_CLIENT_ID` / `OPENSKY_CLIENT_SECRET` | OpenSky OAuth client credentials |
| `ACLED_USERNAME` / `ACLED_PASSWORD` / `ACLED_ACCESS_TOKEN` | ACLED access configuration |
| `BAIDU_TRANSLATE_APPID` / `BAIDU_TRANSLATE_SECRET` | Baidu Translate configuration |
| `YOUDAO_TRANSLATE_APPID` / `YOUDAO_TRANSLATE_SECRET` | Youdao Translate configuration |

News storage is selected by `storage.type`. Supported values are `local`, `mysql`, `elasticsearch`, and `rocketmq`. The current configuration defaults to RocketMQ with topic `news-articles`.

## API Groups

| Group | Base Path | Description |
| --- | --- | --- |
| RSS news crawling | `/api/rss` | Trigger crawling, manage RSS sources, query articles and crawl statistics |
| Article parsing | `/api/article` | Parse single or multiple article bodies |
| AIS vessel tracking | `/api/ais/track` | Vessel paging, latest data, MMSI lookup, Excel import |
| OpenSky aircraft | `/api/opensky` | Manual sync, historical trajectories, GeoJSON output, old-data cleanup |
| ACLED risk map | `/api/acled/risk` | Aggregated data import, boundary import, map, trend, detail, filters |
| CAST conflict forecast | `/api/cast/forecast` | Forecast import, trends, rankings, risk windows, aggregate reports |
| Locations | `/api/locations` | Country and location lookup |
| Hot events | `/api/hot-events` | Query hot events and related articles by location and topic |
| News stock analysis | `/api/news-stock` | Analyze potential news impact on stocks and markets |
| WebSocket management | `/api/ws` | REST message push, broadcast, unicast, statistics, health check |

Use Swagger UI for complete request parameters and response schemas.

## WebSocket Usage

Native WebSocket:

- Endpoint: `ws://localhost:8081/ws`
- SockJS endpoint: `http://localhost:8081/ws-sockjs`

Subscribe after connecting:

```json
{
  "action": "subscribe",
  "dataType": "satellite"
}
```

Unsubscribe:

```json
{
  "action": "unsubscribe",
  "dataType": "satellite"
}
```

Heartbeat:

```json
{
  "action": "ping"
}
```

STOMP:

- Endpoint: `/stomp`
- Subscribe to: `/topic/{channel}`
- Send to: `/app/send/{channel}`
- Send with level: `/app/sendWithLevel/{channel}/{level}`

Message levels are `info`, `warn`, and `error`.

## Scheduled Jobs

- RSS crawling: controlled by `rss.crawler.enabled`, cron expression from `rss.crawler.cron`.
- OpenSky sync: controlled by `opensky.sync.enabled`, interval from `opensky.sync.interval`.
- OpenSky cleanup: controlled by `opensky.sync.cleanup-cron`, runs daily by default.

Disable external schedules during local debugging if the related third-party services are unavailable.

## Tests

Run all tests:

```bash
mvn test
```

Run individual test classes:

```bash
mvn -Dtest=StorageConfigTest test
mvn -Dtest=DashScopeCompatibilityTest test
mvn -Dtest=RocketmqStorageServiceTest test
```

## Development Notes

- `src/main/resources/orekit-data` is excluded from Maven packaged resources. Mount `/orekit-data` for container runtime.
- `application.yml` imports `config/application-ai.yml` and `config/data-crawler.yml`; check all three files when changing configuration.
- WebSocket supports dynamic `dataType` values and does not require server-side pre-registration.
- Many APIs depend on databases, middleware, or third-party APIs. If local startup fails, first check connection settings and whether related schedules should be disabled.
- Production deployments should restrict WebSocket/CORS origins and inject secrets through a config center or secret-management system.
