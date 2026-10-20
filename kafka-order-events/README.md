# Kafka Order Events

A small Spring Boot service. It shows event-driven order handling with Kafka.

## Overview

- A REST endpoint publishes `OrderPlaced` and `OrderCancelled` events to the `orders` topic.
- A Kafka listener consumes the events. It keeps per-customer stats in memory.
- The listener is idempotent. The same `eventId` updates the stats only once.
- A failing message is retried 2 times. Then it goes to the `orders.DLT` topic.
- Topics are created on startup with `NewTopic` beans.
- Integration tests run against a real Kafka in Docker with Testcontainers.

## Tech stack

- Java 25 (records, sealed interfaces, pattern-matching switch, virtual threads)
- Spring Boot 4.1.1
- Spring Kafka 4 with Jackson 3 JSON serializers
- Apache Kafka in KRaft mode (`apache/kafka-native`)
- Testcontainers
- JaCoCo with a 90% line coverage gate
- Docker Compose with a multi-stage `Dockerfile`

## Prerequisites

- Docker with Docker Compose v2. Docker Desktop includes it.
- Java 25. You need it only to run or test the app outside Docker.
- You do not need Maven. The project ships the Maven wrapper (`./mvnw`).

Check your tools:

```sh
docker compose version
java -version
```

## Run everything with Docker Compose

This builds the app image and starts Kafka and the app.

```sh
docker compose --profile app up -d --build
```

The first build downloads dependencies. It takes a few minutes. Later builds use the Docker cache.

The app listens on `http://localhost:8080`. Check that it is up:

```sh
curl -s localhost:8080/actuator/health
```

See the logs:

```sh
docker compose --profile app logs -f app
docker compose --profile app logs -f kafka
```

Stop everything and remove the containers, network and volumes:

```sh
docker compose --profile app down -v
```

Change the host port with `APP_PORT`:

```sh
APP_PORT=18081 docker compose --profile app up -d --build
curl -s localhost:18081/actuator/health
```

Inside Docker the app connects to Kafka at `kafka:29092`. Compose sets this with `SPRING_KAFKA_BOOTSTRAP_SERVERS`.

## Run locally

Start only Kafka. The `app` service has a profile, so this command does not start it.

```sh
docker compose up -d
```

Start the application on the host:

```sh
./mvnw spring-boot:run
```

The application listens on port 8080. Kafka listens on `localhost:9092` for the host.

Stop Kafka:

```sh
docker compose down -v
```

## Configuration

| Property | Environment variable | Default | Meaning |
|---|---|---|---|
| `spring.kafka.bootstrap-servers` | `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka address. Compose sets `kafka:29092` for the app container. |
| `server.port` | `SERVER_PORT` | `8080` | Port inside the app process or container. |
| - | `APP_PORT` | `8080` | Host port that Compose maps to the container port 8080. |
| `spring.threads.virtual.enabled` | `SPRING_THREADS_VIRTUAL_ENABLED` | `true` | Use virtual threads for web requests. |
| `spring.kafka.consumer.auto-offset-reset` | `SPRING_KAFKA_CONSUMER_AUTO_OFFSET_RESET` | `earliest` | Read the topic from the start on first run. |

Spring Boot maps each property to an environment variable. Replace `.` and `-` with `_` and use upper case.

The Kafka broker has two listeners:

| Listener | Address | Used by |
|---|---|---|
| `HOST` | `localhost:9092` | Processes on your machine |
| `INTERNAL` | `kafka:29092` | Containers in the Compose network |

## API

The examples use port 8080. Change the port if you set `APP_PORT`.

Place an order:

```sh
curl -s -X POST localhost:8080/orders \
  -H 'Content-Type: application/json' \
  -d '{"customerId":"c1","amount":10.50}'
```

Response: `201 Created` with `{"orderId":"...","customerId":"c1","amount":10.50}`.
`customerId` must not be blank. `amount` must be positive. Invalid input returns `400 Bad Request`.

Read customer stats:

```sh
curl -s localhost:8080/stats/c1
```

Response: `{"customerId":"c1","orderCount":1,"totalRevenue":10.50}`.
The listener updates the stats a moment after the order. Repeat the call if the count is still old.

Cancel an order:

```sh
curl -s -X DELETE localhost:8080/orders/<orderId>
```

Response: `204 No Content`. The stats drop by one order and its amount. An unknown `orderId` returns `404 Not Found`.

Send a poison message:

```sh
curl -s -X POST localhost:8080/orders \
  -H 'Content-Type: application/json' \
  -d '{"customerId":"poison","amount":1}'
```

The listener fails 3 times. The message then lands in `orders.DLT`.

Health check:

```sh
curl -s localhost:8080/actuator/health
```

Response: `{"status":"UP"}`.

## Tests

Docker must run. The tests start Kafka in a container.

```sh
./mvnw verify
```

What happens:

- `StatsAggregatorTest` runs unit tests without Spring.
- `OrderEventsIntegrationTest` starts `apache/kafka-native` with Testcontainers. It runs the full flow against a real broker.
- JaCoCo checks that line coverage is 90% or more. The build fails below that.

The coverage report is in `target/site/jacoco/index.html`.

## Troubleshooting

Docker is not running:

- Symptom: `Cannot connect to the Docker daemon`.
- Fix: start Docker Desktop. Then run the command again.

Port is in use:

- Symptom: `Bind for 0.0.0.0:8080 failed: port is already allocated`, or the app exits with `Port 8080 was already in use`.
- Fix: stop the other process. Or start with another port: `APP_PORT=18081 docker compose --profile app up -d --build`.
- Port 9092 in use: stop the other Kafka. Or change the first `9092` in the `ports` entry of `compose.yaml`.

Kafka is not ready yet:

- Symptom: the app logs `Connection to node -1 (kafka/...:29092) could not be established`.
- Cause: Kafka takes a few seconds to start. Compose waits for the Kafka health check before it starts the app. The Kafka client also retries.
- Fix: wait a few seconds. Run `docker compose --profile app logs kafka` if the warnings do not stop.

Local app cannot reach Kafka:

- Symptom: `./mvnw spring-boot:run` logs connection warnings for `localhost:9092`.
- Fix: run `docker compose up -d` first. Check with `docker compose ps` that `kafka` is `healthy`.

Stale image after a code change:

- Fix: run `docker compose --profile app up -d --build`. The `--build` flag rebuilds the app image.

## What to look at

- `OrderEvent` - sealed interface with the `OrderPlaced` and `OrderCancelled` records.
- `OrderController` - REST endpoints. It publishes events with `KafkaTemplate`.
- `OrderListener` - the `@KafkaListener` on the `orders` topic.
- `StatsAggregator` - idempotent stats update with a pattern-matching switch. Pure Java.
- `KafkaConfig` - topic beans and the retry plus dead-letter error handler.
- `OrderEventsIntegrationTest` - end-to-end tests with Testcontainers.
- `StatsAggregatorTest` - unit tests for the aggregator without Spring.
- `Dockerfile` - multi-stage build. Stage 1 builds the jar with the Maven wrapper. Stage 2 runs it as a non-root user on a JRE image.
- `compose.yaml` - Kafka with two listeners and a health check. The `app` service runs only with `--profile app`.

## Notes

- Spring Kafka 4 uses `-dlt` as the default dead-letter suffix. This project sets `orders.DLT` explicitly.
- The processed-id set and the order store are in memory. A database or Redis replaces them in production.
- The Kafka health check is a TCP connect to port 9092 with bash. The `apache/kafka-native` image has no Kafka CLI tools.
