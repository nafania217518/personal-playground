# Projects

Seven small projects. Each project is its own git repository. Each project covers one or two technologies.

| Project | Technologies | What it shows |
|---|---|---|
| [kafka-order-events](kafka-order-events/) | Java 25, Apache Kafka | Producer, idempotent consumer, sealed event types, retries, dead-letter topic |
| [redis-rate-limiter](redis-rate-limiter/) | Java 25, Redis | Token bucket in one atomic Lua script, servlet filter, 429 with Retry-After |
| [rabbit-task-queue](rabbit-task-queue/) | Java 25, RabbitMQ | Topic routing, retry with backoff, dead-letter queue, DLQ replay, publisher confirms |
| [secure-notes-api](secure-notes-api/) | Spring Security, MongoDB | JWT login, roles and method security, owner-scoped CRUD, text search, auditing |
| [cassandra-sensor-store](cassandra-sensor-store/) | Java 25, Apache Cassandra | Time-bucketed partitions, clustering order, paging state, TTL, batches |
| [notes-angular-ui](notes-angular-ui/) | Angular 22 | Signals, functional interceptor and guard, reactive forms, Vitest |
| [quote-microservices](quote-microservices/) | Java 25, Spring Boot, Ktor, Javalin, gRPC, ELK | Three services with gRPC between them, correlation ids, JSON logs into Elasticsearch and Kibana |

## Common choices

- Spring Boot 4.1.1 on Java 25 with virtual threads.
- Tests first. Every feature has a test. Integration tests use Testcontainers, so Docker must run.
- JaCoCo enforces 90% line coverage on each Java project.
- Each project has a `Dockerfile`, a `compose.yaml`, and a README with detailed run instructions and curl examples.

## Run a project

Option 1, everything in Docker:

```bash
cd <project>
docker compose --profile app up -d --build
```

Option 2, only the broker or database in Docker, the app on the host:

```bash
cd <project>
docker compose up -d
./mvnw spring-boot:run
```

Run the tests with `./mvnw verify`. The Angular project uses `npm install`, `npx ng serve`, and `npx ng test`. The microservice project uses `./gradlew check` and `docker compose up -d --build` without a profile. See each README for details.

## Pairing

`notes-angular-ui` is the frontend for `secure-notes-api`. Start the API first. The API allows CORS from `http://localhost:4200`.
