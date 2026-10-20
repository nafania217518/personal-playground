# Redis Token-Bucket Rate Limiter

## Overview

A small Spring Boot service. It limits HTTP requests with a token bucket stored in Redis.
One Lua script does the whole check-and-update step. Redis runs the script atomically.
Concurrent callers can never take more tokens than the bucket holds.

The project shows:

- Token bucket algorithm with refill over time.
- Atomic Redis operations with a Lua script.
- A servlet filter that returns `429 Too Many Requests` with a `Retry-After` header.
- Type-safe configuration with a `@ConfigurationProperties` record.
- Integration tests against a real Redis in Testcontainers.
- A concurrency test with 50 virtual threads.
- A multi-stage Dockerfile and a Docker Compose profile that runs the whole stack.

## Tech stack

- Java 25
- Spring Boot 4.1.1
- Spring Data Redis (Lettuce)
- Redis Lua scripting
- Testcontainers
- JaCoCo (line coverage must be at least 90%)
- Docker and Docker Compose

## Prerequisites

- Docker. It must run before you start the app or the tests.
- Java 25. You need it only for local runs and tests.
- Maven is not needed. The project ships the Maven wrapper (`./mvnw`).

## Run everything with Docker Compose

This mode needs only Docker. It builds the jar inside a container.

Start Redis and the app:

```sh
docker compose --profile app up -d --build
```

The first build downloads dependencies. It can take a few minutes.
Compose starts the app only after Redis answers `redis-cli ping`.

Check that the app is up:

```sh
curl -i http://localhost:8080/api/ping -H 'X-Api-Key: demo'
```

Follow the logs:

```sh
docker compose --profile app logs -f app
```

Stop and remove the containers and the network:

```sh
docker compose --profile app down -v
```

Change the host port with `APP_PORT`:

```sh
APP_PORT=18082 docker compose --profile app up -d --build
curl -i http://localhost:18082/api/ping -H 'X-Api-Key: demo'
```

## Run locally

This mode needs Java 25 and Docker.

Start only Redis:

```sh
docker compose up -d
```

Start the app with the Maven wrapper:

```sh
./mvnw spring-boot:run
```

The service listens on `http://localhost:8080`.

Stop Redis:

```sh
docker compose down
```

## Configuration

Set these values in `src/main/resources/application.properties` or as environment variables.
Spring Boot maps `a.b-c` to the environment variable `A_B_C`.

| Property                      | Environment variable          | Default     | Meaning                                 |
|-------------------------------|-------------------------------|-------------|-----------------------------------------|
| `ratelimit.capacity`          | `RATELIMIT_CAPACITY`          | `10`        | Maximum tokens in one bucket.           |
| `ratelimit.refill-per-second` | `RATELIMIT_REFILL_PER_SECOND` | `1`         | Tokens added to the bucket per second.  |
| `spring.data.redis.host`      | `SPRING_DATA_REDIS_HOST`      | `localhost` | Redis host. Compose sets it to `redis`. |
| `spring.data.redis.port`      | `SPRING_DATA_REDIS_PORT`      | `6379`      | Redis port.                             |
| `server.port`                 | `SERVER_PORT`                 | `8080`      | HTTP port inside the process.           |

Each `X-Api-Key` header value gets its own bucket. Requests without the header use the client IP address.

To change a value in Compose, add the environment variable to the `environment` block of the `app` service.

## API

Replace `8080` with your `APP_PORT` value if you changed it.

### `GET /api/ping`

Rate limited. Returns `{"pong":true}`.

```sh
curl -i http://localhost:8080/api/ping -H 'X-Api-Key: demo'
```

The response contains `X-RateLimit-Remaining`.

Trigger a `429`:

```sh
for i in $(seq 1 12); do
  curl -si http://localhost:8080/api/ping -H 'X-Api-Key: demo' | grep -Ei '^HTTP|Retry-After'
done
```

The first 10 calls return `200`. The rest return `429` with a `Retry-After` header.
Wait one second and one token comes back.

### `GET /api/limits/{key}`

Not rate limited. Reads the stored bucket state.

```sh
curl http://localhost:8080/api/limits/demo
```

Response: `{"key":"demo","tokens":0,"capacity":10}`.
The endpoint returns the stored token count. It does not apply refill since the last request.
An unknown key returns `tokens` equal to `capacity`.

### `GET /actuator/health`

Spring Boot Actuator health. Not rate limited.

```sh
curl http://localhost:8080/actuator/health
```

## Tests

Docker must run. Tests start Redis in a container with Testcontainers.

```sh
./mvnw verify
```

This runs all tests and the JaCoCo coverage check.
The coverage report is in `target/site/jacoco/index.html`.

## Troubleshooting

| Symptom                                               | Cause                        | Fix                                                        |
|-------------------------------------------------------|------------------------------|------------------------------------------------------------|
| `Cannot connect to the Docker daemon`                 | Docker is not running.       | Start Docker Desktop. Run the command again.               |
| `Bind for 0.0.0.0:8080 failed: port is already allocated` | Another process uses 8080. | Set `APP_PORT` to a free port.                            |
| `Bind for 0.0.0.0:6379 failed`                        | Another Redis uses 6379.     | Stop it, or change the `redis` port mapping in `compose.yaml`. |
| Tests fail with `Could not find a valid Docker environment` | Docker is not running. | Start Docker. Run `./mvnw verify` again.                   |
| App exits with `Unable to connect to Redis`           | Redis is not up.             | Run `docker compose up -d` first.                          |

## What to look at

- `src/main/java/dev/portfolio/ratelimiter/RateLimiter.java` - the Lua script and the `Decision` record.
- `src/main/java/dev/portfolio/ratelimiter/RateLimitFilter.java` - the `OncePerRequestFilter` that sets the headers.
- `src/main/java/dev/portfolio/ratelimiter/RateLimitProperties.java` - the configuration record.
- `src/test/java/dev/portfolio/ratelimiter/RateLimiterTest.java` - see `concurrentCallersNeverExceedCapacity`.
- `src/test/java/dev/portfolio/ratelimiter/RateLimitFilterTest.java` - MockMvc tests for the HTTP layer.
- `Dockerfile` - two-stage build, non-root runtime user.
- `compose.yaml` - Redis with healthcheck, `app` profile.
