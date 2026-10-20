# rabbit-task-queue

A small task queue on RabbitMQ. You can run it with one Docker Compose command.

## Overview

The app shows these RabbitMQ patterns:

- `POST /tasks` publishes a task to the topic exchange `tasks`.
- The routing key is `task.email` or `task.report`.
- Two listeners consume the durable queues `tasks.email` and `tasks.report`.
- A listener that throws is retried. The app makes 3 attempts in total with exponential backoff.
- After the last attempt the broker moves the message to the fanout DLX `tasks.dlx`. The DLX delivers it to the queue `tasks.dlq`.
- `POST /tasks/dlq/replay` sends all DLQ messages back to the `tasks` exchange.
- The publisher uses correlated confirms. The app records each acked task id.
- Task status lives in an in-memory `ConcurrentHashMap`. A restart clears it.

## Tech stack

- Java 25 (virtual threads enabled)
- Spring Boot 4.1.1
- Spring AMQP 4.1
- RabbitMQ (management image)
- Testcontainers (tests start a real broker)
- JaCoCo (line coverage must be 90% or more)
- Docker Compose (RabbitMQ and the app)

## Prerequisites

- Docker with Compose v2. Docker Desktop includes it.
- Java 25. Only needed for the local run and for the tests.
- Maven is not needed. The project ships the Maven wrapper `./mvnw`.

## Run everything with Docker Compose

This builds the app image and starts RabbitMQ and the app.

```sh
docker compose --profile app up -d --build
```

The first build downloads Maven dependencies. It can take a few minutes.

Wait until both services are healthy:

```sh
docker compose --profile app ps
```

Follow the app logs:

```sh
docker compose --profile app logs -f app
```

The API is at <http://localhost:8080>. The management UI is at <http://localhost:15672>. Log in with `guest` / `guest`.

Change the host port with `APP_PORT`:

```sh
APP_PORT=18083 docker compose --profile app up -d --build
```

Stop everything and remove volumes:

```sh
docker compose --profile app down -v
```

## Run locally

Start only RabbitMQ:

```sh
docker compose up -d
```

Start the app on your machine:

```sh
./mvnw spring-boot:run
```

The app connects to `localhost:5672` by default.

## Configuration

Spring Boot reads each property from the environment too. Replace dots with underscores and use upper case.

| Property | Env var | Default | Meaning |
|---|---|---|---|
| `spring.rabbitmq.host` | `SPRING_RABBITMQ_HOST` | `localhost` | RabbitMQ host. Compose sets `rabbitmq`. |
| `spring.rabbitmq.port` | `SPRING_RABBITMQ_PORT` | `5672` | RabbitMQ AMQP port. |
| `spring.rabbitmq.username` | `SPRING_RABBITMQ_USERNAME` | `guest` | RabbitMQ user. |
| `spring.rabbitmq.password` | `SPRING_RABBITMQ_PASSWORD` | `guest` | RabbitMQ password. |
| `server.port` | `SERVER_PORT` | `8080` | HTTP port inside the app. |
| `spring.rabbitmq.publisher-confirm-type` | - | `correlated` | Enables publisher confirms with correlation data. |
| `spring.rabbitmq.listener.simple.default-requeue-rejected` | - | `false` | A rejected message goes to the DLX, not back to the queue. |
| `spring.rabbitmq.listener.simple.retry.enabled` | - | `true` | Enables listener retry. |
| `spring.rabbitmq.listener.simple.retry.max-retries` | - | `2` | 1 attempt + 2 retries = 3 attempts. |
| `spring.rabbitmq.listener.simple.retry.initial-interval` | - | `100ms` | First backoff. |
| `spring.rabbitmq.listener.simple.retry.multiplier` | - | `2` | Backoff multiplier. |
| `spring.rabbitmq.listener.simple.retry.max-interval` | - | `1s` | Backoff upper limit. |

The retry properties are in `src/main/resources/application.properties`. The host port in Compose is `APP_PORT` (default `8080`).

## API

The examples use port `8080`. Change it if you set `APP_PORT`.

Submit an EMAIL task:

```sh
curl -s -X POST localhost:8080/tasks -H 'Content-Type: application/json' \
  -d '{"type":"EMAIL","payload":"hello"}'
```

Response (`202 Accepted`):

```json
{"id":"9bb2be3c-3e18-476d-8cdd-ee8d3682cdfe","type":"EMAIL","status":"PENDING","attempts":0}
```

Submit a REPORT task:

```sh
curl -s -X POST localhost:8080/tasks -H 'Content-Type: application/json' \
  -d '{"type":"REPORT","payload":"monthly"}'
```

Submit a task that fails. The payload `fail` makes the listener throw. After 3 attempts the message lands in `tasks.dlq` and the status becomes `FAILED`:

```sh
curl -s -X POST localhost:8080/tasks -H 'Content-Type: application/json' \
  -d '{"type":"REPORT","payload":"fail"}'
```

Check the status of a task. Replace `<id>` with the id from the response. Status is `PENDING`, `DONE`, or `FAILED`. Unknown ids return `404`:

```sh
curl -s localhost:8080/tasks/<id>
```

Inspect queue depths:

```sh
curl -s localhost:8080/tasks/queues
```

```json
{"tasks.email":0,"tasks.report":0,"tasks.dlq":1}
```

Replay all messages in the DLQ. Each message returns to the `tasks` exchange with its original routing key:

```sh
curl -s -X POST localhost:8080/tasks/dlq/replay
```

```json
{"replayed":1}
```

Health check:

```sh
curl -s localhost:8080/actuator/health
```

## Tests

Docker must run. The tests start a RabbitMQ container with Testcontainers.

```sh
./mvnw verify
```

`verify` runs 13 tests and the JaCoCo coverage check. Coverage must be 90% or more. The report is in `target/site/jacoco/index.html`.

## Troubleshooting

- Docker is not running. `docker compose` and `./mvnw verify` fail with a connection error. Start Docker and retry.
- Port `8080`, `5672`, or `15672` is in use. Set `APP_PORT` for the app. Stop the other process for the RabbitMQ ports.
- RabbitMQ is not ready. Compose waits for the healthcheck before it starts the app. In the local run, wait a few seconds after `docker compose up -d`. The app retries the connection.
- The build fails with a Java error. Check `java -version`. The project needs Java 25.
- Old state in the broker. Run `docker compose --profile app down -v` to remove volumes, then start again.

## What to look at

- `Dockerfile` builds the jar in a JDK stage and runs it in a JRE stage as a non-root user.
- `compose.yaml` defines RabbitMQ with a healthcheck and the `app` service under the `app` profile.
- `RabbitConfig` declares the exchange, queues, bindings, DLX, JSON converter, confirm callback, and the recoverer that marks a task `FAILED`.
- `TaskController` has the four HTTP endpoints, including DLQ replay.
- `TaskListeners` consumes both queues and throws on payload `fail`.
- `TaskStatusStore` is the in-memory status map.
- `application.properties` sets retry, backoff, requeue, and publisher-confirm options.
- `TaskQueueIntegrationTest` proves each feature against a real broker.
