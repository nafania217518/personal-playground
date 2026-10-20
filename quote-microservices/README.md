# Quote microservices

## Overview

This project has three small services. They work together to quote and order products.

- `gateway` exposes a REST API. It is the only public entry point.
- `inventory-service` holds stock per SKU. It serves gRPC and a small admin HTTP API.
- `pricing-service` holds unit prices. It computes totals and discounts. It serves gRPC and a small admin HTTP API.

The gateway calls the two services with gRPC. All three services write JSON logs. Logstash ships the logs to Elasticsearch. Kibana shows them.

Every request has a correlation id. The gateway reads the `X-Correlation-Id` header or creates one. It sends the id to the services as gRPC metadata `x-correlation-id`. Every log line in all three services carries the same `correlationId`. You can follow one request across all services in Kibana.

## Architecture

```
                 REST (HTTP 8080)
  client  ───────────────────────►  gateway (Spring Boot)
                                       │              │
                               gRPC 9091              gRPC 9092
                                       ▼              ▼
                        inventory-service (Javalin)   pricing-service (Ktor)
                             admin HTTP 8081          admin HTTP 8082

  gateway, inventory-service, pricing-service
        │  JSON over TCP 5000 (logstash-logback-encoder)
        ▼
    Logstash ──► Elasticsearch (9200) ──► Kibana (5601)
```

## Tech stack

- Java 25
- Spring Boot 4.1.1 with the Spring gRPC client starter (`gateway`)
- Javalin 7 (`inventory-service`)
- Ktor 3.6 and Kotlin 2.4 (`pricing-service`)
- grpc-java 1.84, protobuf 4.36
- Gradle 9.8 with a version catalog (`gradle/libs.versions.toml`)
- logstash-logback-encoder 9
- Elasticsearch, Logstash, Kibana 9.5.5

## Prerequisites

- Java 25 (only for local runs; Docker builds bring their own JDK)
- Docker with Docker Compose
- About 4 GB of memory for Docker. The ELK stack needs most of it.

## Run everything with Docker Compose

```bash
docker compose up -d --build --wait
```

This command builds three images and starts seven containers:
`elasticsearch`, `logstash`, `kibana`, `kibana-init`, `inventory-service`, `pricing-service`, `gateway`.

The first build downloads Gradle and all dependencies inside Docker. Expect 5 to 10 minutes. Later builds use the Docker cache and are faster.

`kibana-init` runs once. It creates the Kibana data view `quote-logs` and then exits.

Check health:

```bash
docker compose ps
curl -s localhost:8080/actuator/health
curl -s localhost:8081/health
curl -s localhost:8082/health
curl -s -o /dev/null -w '%{http_code}\n' localhost:5601/api/status
```

Change host ports when they are in use. The gateway uses `APP_PORT`. Logstash uses `LOGSTASH_PORT` (macOS AirPlay often takes 5000). The inventory admin API uses `INVENTORY_HTTP_PORT`:

```bash
APP_PORT=18090 LOGSTASH_PORT=15000 INVENTORY_HTTP_PORT=18081 docker compose up -d --build --wait
```

`--wait` exits with code 1 after `kibana-init` exits. This is expected for a one-shot container. Check `docker compose ps` instead.

Stop and remove everything, including the Elasticsearch data volume:

```bash
docker compose down -v
```

## Run locally

Open three terminals and start one service in each:

```bash
./gradlew :inventory-service:run
./gradlew :pricing-service:run
./gradlew :gateway:bootRun
```

ELK is optional for local runs. Start only the ELK containers:

```bash
docker compose up -d elasticsearch logstash kibana kibana-init
```

Without ELK, the services log to the console and log a warning about Logstash. See Troubleshooting.

## Ports

| Service           | Port | Protocol | Purpose                      |
|-------------------|------|----------|------------------------------|
| gateway           | 8080 | HTTP     | Public REST API (`APP_PORT` in Compose) |
| inventory-service | 8081 | HTTP     | Admin API and health (`INVENTORY_HTTP_PORT` in Compose) |
| inventory-service | 9091 | gRPC     | `Inventory` service          |
| pricing-service   | 8082 | HTTP     | Admin API and health         |
| pricing-service   | 9092 | gRPC     | `Pricing` service            |
| logstash          | 5000 | TCP      | JSON log input (`LOGSTASH_PORT` in Compose) |
| elasticsearch     | 9200 | HTTP     | Search API                   |
| kibana            | 5601 | HTTP     | Web UI                       |

## Configuration

All settings are environment variables.

| Service           | Variable              | Default     | Meaning                              |
|-------------------|-----------------------|-------------|--------------------------------------|
| gateway           | `HTTP_PORT`           | `8080`      | REST port                            |
| gateway           | `INVENTORY_GRPC_HOST` | `localhost` | Host of inventory-service gRPC       |
| gateway           | `INVENTORY_GRPC_PORT` | `9091`      | Port of inventory-service gRPC       |
| gateway           | `PRICING_GRPC_HOST`   | `localhost` | Host of pricing-service gRPC         |
| gateway           | `PRICING_GRPC_PORT`   | `9092`      | Port of pricing-service gRPC         |
| inventory-service | `HTTP_PORT`           | `8081`      | Admin HTTP port                      |
| inventory-service | `GRPC_PORT`           | `9091`      | gRPC port                            |
| pricing-service   | `HTTP_PORT`           | `8082`      | Admin HTTP port                      |
| pricing-service   | `GRPC_PORT`           | `9092`      | gRPC port                            |
| all three         | `LOGSTASH_HOST`       | `localhost` | Logstash TCP host                    |
| all three         | `LOGSTASH_PORT`       | `5000`      | Logstash TCP port                    |

Seed data lives in code. Stock: `SKU-1` = 100, `SKU-2` = 5, `SKU-3` = 0. Prices in cents: `SKU-1` = 1999, `SKU-2` = 500, `SKU-3` = 12000. Currency is `EUR`. Discount: 5 % from 10 units, 10 % from 100 units.

## API

Request body for both gateway endpoints: `{"sku": "SKU-1", "quantity": 12}`. `sku` must not be blank. `quantity` must be at least 1.

Quote. Returns stock and price, does not change stock:

```bash
curl -s -X POST localhost:8080/quotes \
  -H 'content-type: application/json' -H 'X-Correlation-Id: demo-1' \
  -d '{"sku":"SKU-1","quantity":12}'
# {"sku":"SKU-1","quantity":12,"available":100,"enough":true,
#  "unitPriceCents":1999,"totalCents":22788,"currency":"EUR","discountPercent":5}
```

Order, success. Reserves stock and returns 201:

```bash
curl -s -i -X POST localhost:8080/orders \
  -H 'content-type: application/json' -d '{"sku":"SKU-2","quantity":2}'
# HTTP/1.1 201
# {"orderId":"<uuid>","sku":"SKU-2","quantity":2,"totalCents":1000,"currency":"EUR","remaining":3}
```

Order, not enough stock. Returns 409 as an RFC 9457 problem detail:

```bash
curl -s -X POST localhost:8080/orders \
  -H 'content-type: application/json' -d '{"sku":"SKU-2","quantity":10}'
# {"type":"about:blank","title":"Conflict","status":409,
#  "detail":"not enough stock for SKU-2: requested 10, available 5","instance":"/orders"}
```

Unknown SKU. Returns 404:

```bash
curl -s -X POST localhost:8080/quotes \
  -H 'content-type: application/json' -d '{"sku":"NOPE","quantity":1}'
# {"type":"about:blank","title":"Not Found","status":404,"detail":"unknown sku NOPE","instance":"/quotes"}
```

Admin endpoints on the services:

```bash
# inventory-service
curl -s localhost:8081/health                 # {"status":"UP"}
curl -s localhost:8081/stock                  # {"SKU-1":100,"SKU-2":5,"SKU-3":0}
curl -s localhost:8081/stock/SKU-2            # {"sku":"SKU-2","available":5}
curl -s -X PUT localhost:8081/stock/SKU-3 -H 'content-type: application/json' -d '{"quantity":40}'

# pricing-service
curl -s localhost:8082/health                 # {"status":"UP"}
curl -s localhost:8082/prices                 # {"SKU-1":1999,"SKU-2":500,"SKU-3":12000}
curl -s localhost:8082/prices/SKU-1           # {"sku":"SKU-1","unitPriceCents":1999,"currency":"EUR"}
curl -s -X PUT localhost:8082/prices/SKU-1 -H 'content-type: application/json' -d '{"unitPriceCents":1799}'

# gateway
curl -s localhost:8080/actuator/health        # {"status":"UP"}
```

Every HTTP response from all three services echoes the `X-Correlation-Id` header.

## Observability

All services log one JSON object per line. Key fields:

| Field           | Example                         |
|-----------------|---------------------------------|
| `@timestamp`    | `2026-10-10T12:00:00.123Z`      |
| `service`       | `gateway`, `inventory-service`, `pricing-service` |
| `correlationId` | `demo-1`                        |
| `level`         | `INFO`                          |
| `logger_name`   | `dev.portfolio.inventory.InventoryGrpcService` |
| `message`       | `CheckStock sku=SKU-1 quantity=12` |

Logstash writes the lines to the index `quote-logs-YYYY.MM.dd`.

Kibana:

1. Open http://localhost:5601.
2. Go to Discover.
3. Select the data view `quote-logs`.
4. Filter with `correlationId:"demo-1"`.
5. You see one request in three services: the gateway controller, `CheckStock` in inventory-service, and `GetPrice` in pricing-service.

Elasticsearch with curl:

```bash
curl -s 'localhost:9200/quote-logs-*/_search?q=correlationId:demo-1&_source=service,message&size=10' | jq '.hits.hits[]._source'
```

## gRPC contract

The proto files live in `proto/src/main/proto/`:

- `inventory.proto`: service `Inventory`
  - `CheckStock(StockRequest) returns (StockReply)`
  - `Reserve(ReserveRequest) returns (ReserveReply)`. Reserve is idempotent per `order_id`.
- `pricing.proto`: service `Pricing`
  - `GetPrice(PriceRequest) returns (PriceReply)`

The services return these gRPC status codes. The gateway maps them to HTTP:

| gRPC code                          | Cause                                | HTTP |
|------------------------------------|--------------------------------------|------|
| `NOT_FOUND`                        | Unknown SKU                          | 404  |
| `FAILED_PRECONDITION`              | Not enough stock to reserve          | 409  |
| `INVALID_ARGUMENT`                 | `quantity` below 1                   | 400  |
| `UNAVAILABLE`, `DEADLINE_EXCEEDED` | Service down or slow                 | 503  |
| any other code                     |                                      | 502  |

The mapping lives in `gateway/.../GrpcExceptionHandler.java`. The response body is a Spring `ProblemDetail`.

## Tests

```bash
./gradlew check
```

`check` runs the tests and the JaCoCo coverage gate. The gate fails the build below 90 % line coverage per module.

| Module            | Tests | Tools                                                     |
|-------------------|-------|-----------------------------------------------------------|
| gateway           | 10    | `@SpringBootTest` + MockMvc, in-process gRPC fake servers |
| inventory-service | 26    | JUnit 5, `JavalinTest`, in-process gRPC server            |
| pricing-service   | 18    | kotlin-test, Ktor `testApplication`, in-process gRPC server |

No test needs Docker or a network port. The gRPC tests use `grpc-inprocess`. The gateway test wires the Spring gRPC client to in-process channels.

## Troubleshooting

- Elasticsearch exits or restarts. It needs memory. Give Docker at least 4 GB. On Linux, set `sudo sysctl -w vm.max_map_count=262144`.
- Kibana takes 1 to 2 minutes to start. `docker compose up --wait` waits for its health check. `kibana-init` runs after that.
- The first `docker compose build` is slow. Each image downloads Gradle and the dependencies. Later builds use the cache.
- Running locally without ELK prints `Connection refused` warnings from `LogstashTcpSocketAppender`. The appender retries every 30 seconds. The services still work. Start ELK or ignore the warnings.
- A host port is in use (`bind: address already in use`). Set `APP_PORT`, `LOGSTASH_PORT` or `INVENTORY_HTTP_PORT`.
- `kibana-init` fails with 400. The data view already exists. The container treats 400 as success.

## What to look at

- `proto/src/main/proto/inventory.proto`, `pricing.proto`: the contract.
- `gateway/src/main/java/dev/portfolio/quote/gateway/QuoteController.java`: REST to gRPC.
- `gateway/.../GrpcExceptionHandler.java`: gRPC status to HTTP problem detail.
- `gateway/.../Correlation.java`: HTTP header to MDC to gRPC metadata.
- `gateway/src/main/resources/application.properties`: gRPC channel targets.
- `inventory-service/src/main/java/dev/portfolio/inventory/StockService.java`: stock rules and errors.
- `inventory-service/.../InventoryGrpcService.java`, `HttpApi.java`, `CorrelationInterceptor.java`.
- `pricing-service/src/main/kotlin/dev/portfolio/pricing/PriceBook.kt`: price and discount rules.
- `pricing-service/.../Grpc.kt`, `Http.kt`, `Main.kt`.
- `*/src/main/resources/logback.xml`: JSON console and Logstash TCP appenders.
- `compose.yaml`, `elk/logstash.conf`, `*/Dockerfile`: the runtime.
- `gradle/libs.versions.toml`: all versions in one place.
