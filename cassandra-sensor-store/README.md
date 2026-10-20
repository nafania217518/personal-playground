# Cassandra Sensor Store

## Overview

A small REST service that stores sensor readings in Apache Cassandra.
It shows five Cassandra patterns in one place:

- Time-bucketed partitions.
- Clustering order for newest-first reads.
- Server-side paging with a paging state token.
- Row TTL for automatic expiry.
- A denormalized metadata table that is updated on every write.

The whole stack runs in Docker with one command.

## Tech stack

- Java 25
- Spring Boot 4.1.1
- Spring Data Cassandra 5.1
- DataStax Java driver 4 (`org.apache.cassandra:java-driver-core`)
- Apache Cassandra 5 (`cassandra:latest` image)
- Testcontainers (Cassandra module)
- JaCoCo for coverage
- Docker Compose for the local stack

## Prerequisites

- Docker with Docker Compose v2.
- At least 2 GB of free memory for Docker. Cassandra uses a 1 GB heap.
- Java 25, only for the local run and the tests.
- Maven is not needed. The project ships the Maven wrapper (`./mvnw`).

## Run everything with Docker Compose

Build the image and start the stack:

```sh
docker compose --profile app up -d --build --wait
```

The command starts three services in order:

1. `cassandra` - the database. The healthcheck runs `cqlsh -e 'describe keyspaces'`.
2. `cassandra-init` - a one-shot container. It waits for a healthy Cassandra.
   It runs `cqlsh -f /init.cql` to create the `sensors` keyspace. Then it exits.
3. `app` - the service. It starts after `cassandra-init` exits with code 0.
   Spring Boot creates the tables on startup.

The first start takes 1 to 2 minutes. Cassandra is slow to boot.
`--wait` blocks until the `app` container is healthy.
Add `--wait-timeout 300` on a slow machine.

Check the state:

```sh
docker compose --profile app ps -a
```

`cassandra-init` must show `Exited (0)`. The other two must show `Up`.

Read the logs:

```sh
docker compose --profile app logs -f app
docker compose logs cassandra-init
```

Test the service:

```sh
curl localhost:8080/actuator/health
```

Change the host port with `APP_PORT`:

```sh
APP_PORT=18085 docker compose --profile app up -d --build --wait
```

Stop the stack and delete the data:

```sh
docker compose --profile app down -v
```

Without `--profile app`, `down` does not remove the `app` container.

## Run locally

Start Cassandra and the init service only:

```sh
docker compose up -d
```

The `app` service has the `app` profile. It does not start without the flag.
Wait until `cassandra-init` exits:

```sh
docker compose ps -a
```

Start the service from the source:

```sh
./mvnw spring-boot:run
```

The service connects to `localhost:9042`. It listens on port 8080.

## Data model

### Table `readings`

| Column      | Type      | Key                  |
|-------------|-----------|----------------------|
| `sensor_id` | text      | partition key        |
| `day`       | date      | partition key        |
| `ts`        | timestamp | clustering key, DESC |
| `value`     | double    |                      |
| `unit`      | text      |                      |

The partition key is `(sensor_id, day)`.
The `day` is the UTC date of `ts`.
Day bucketing keeps each partition to one day of data for one sensor.
Without it, one sensor would grow one partition forever.
Large partitions make compaction and reads slow.

`ts` is a clustering column in descending order.
Cassandra stores rows newest first inside a partition.
A "latest readings" query reads the start of the partition and stops.

Each row has a TTL.
The TTL comes from the property `sensors.ttl-seconds`.
The default is 30 days.
Cassandra deletes expired rows on its own. The service has no cleanup job.

### Table `sensor_meta`

| Column       | Type      | Key         |
|--------------|-----------|-------------|
| `sensor_id`  | text      | primary key |
| `name`       | text      |             |
| `last_value` | double    |             |
| `last_ts`    | timestamp |             |

Cassandra has no joins and no aggregation across partitions.
The service writes the latest value twice: once in `readings`, once in `sensor_meta`.
This is denormalization. `GET /sensors/{id}` then reads one row and returns at once.

### Queries that span days

`GET /sensors/{id}/readings?from=&to=` lists the UTC days in the range.
The service queries one partition per day, newest day first.
It does not use `IN (day1, day2, ...)` on the partition key.
An `IN` query on the partition key fans out to many nodes in one request.
It also breaks the sort order across partitions and the driver paging state.
One query per partition keeps each request small and keeps the order correct.

The paging token is `<day>:<base64 driver paging state>`.
The token stores the current day and the Cassandra paging state inside that day.
A token with an empty state, like `2026-01-14:`, means "start at this day".

### Keyspace

Spring Boot does not create keyspaces.
`src/test/resources/init.cql` creates the `sensors` keyspace with `SimpleStrategy` and replication factor 1.
The `cassandra-init` service and the Testcontainers setup both run this file.

## Configuration

All values are Spring properties. Set them in `application.properties` or as environment variables.

| Property                            | Environment variable                | Default                | Meaning                           |
|-------------------------------------|-------------------------------------|------------------------|-----------------------------------|
| `spring.cassandra.contact-points`   | `SPRING_CASSANDRA_CONTACT_POINTS`   | `localhost:9042`       | Cassandra host and port.          |
| `spring.cassandra.keyspace-name`    | `SPRING_CASSANDRA_KEYSPACE_NAME`    | `sensors`              | Keyspace for both tables.         |
| `spring.cassandra.local-datacenter` | `SPRING_CASSANDRA_LOCAL_DATACENTER` | `datacenter1`          | Datacenter name of the node.      |
| `sensors.ttl-seconds`               | `SENSORS_TTL_SECONDS`               | `2592000` (30 days)    | TTL for each reading row.         |
| `server.port`                       | `SERVER_PORT`                       | `8080`                 | HTTP port inside the container.   |

The `app` service in `compose.yaml` sets `SPRING_CASSANDRA_CONTACT_POINTS=cassandra:9042`.
`APP_PORT` is a Compose variable. It maps a host port to container port 8080.

## API

Replace `8080` with your `APP_PORT` value when needed.

### Write one reading

```sh
curl -X POST localhost:8080/sensors/kitchen/readings \
  -H 'Content-Type: application/json' \
  -d '{"ts":"2026-01-15T10:00:00Z","value":21.5,"unit":"C"}'
```

Response `201 Created`:

```json
{"sensorId":"kitchen","ts":"2026-01-15T10:00:00Z","value":21.5,"unit":"C"}
```

`ts` is optional. The default is now.
`value` and `unit` are required. A missing field returns `400`.

### Write a batch

```sh
curl -X POST localhost:8080/sensors/kitchen/readings/batch \
  -H 'Content-Type: application/json' \
  -d '[{"value":21.6,"unit":"C"},{"value":21.7,"unit":"C"}]'
```

Response `201 Created`:

```json
{"inserted":2}
```

The maximum is 50 items.
All rows go into one logged batch. The batch also updates `sensor_meta` with the newest item.

### Read readings with paging

Read newest first, four per page:

```sh
curl 'localhost:8080/sensors/kitchen/readings?from=2026-01-01T00:00:00Z&to=2026-01-31T23:59:59Z&size=4'
```

Response `200 OK`:

```json
{
  "content": [
    {"sensorId":"kitchen","ts":"2026-01-15T10:00:00Z","value":21.5,"unit":"C"}
  ],
  "pagingState": "2026-01-15:AAAA..."
}
```

`from` and `to` are required. `size` is optional. The default is 100.
`pagingState` is `null` on the last page.
Pass the token back to get the next page:

```sh
curl 'localhost:8080/sensors/kitchen/readings?from=2026-01-01T00:00:00Z&to=2026-01-31T23:59:59Z&size=4&pagingState=2026-01-15:AAAA...'
```

URL-encode the token if it contains `+`, `/` or `=`.

### Read sensor metadata

```sh
curl localhost:8080/sensors/kitchen
```

Response `200 OK`:

```json
{"sensorId":"kitchen","name":null,"lastValue":21.7,"lastTs":"2026-01-15T10:00:01Z"}
```

An unknown sensor returns `404`.

### Set the sensor name

```sh
curl -X PUT localhost:8080/sensors/kitchen \
  -H 'Content-Type: application/json' \
  -d '{"name":"Kitchen"}'
```

Response `200 OK` with the full metadata row.
The next reading keeps the name.

### Health

```sh
curl localhost:8080/actuator/health
```

## Tests

```sh
./mvnw verify
```

Tests start Cassandra in a Docker container with Testcontainers.
Docker must run. The first start takes about 30 to 60 seconds.

Spring starts two application contexts.
`SensorApiTest` uses the default TTL.
`TtlTest` sets `sensors.ttl-seconds=2`. A different property set means a new context.
This is expected. It costs a few seconds.

`verify` also runs the JaCoCo check.
Line coverage must be at least 90 percent per package.
The report is in `target/site/jacoco/index.html`.

Test classes:

- `DayBucketTest` - pure unit tests for day bucketing.
- `SensorApiTest` - end-to-end tests of the REST API against Cassandra.
- `TtlTest` - writes with a 2 second TTL and waits for the row to expire.

## Troubleshooting

### Cassandra is slow to start

Cassandra needs 1 to 2 minutes on the first boot.
The healthcheck has a 60 second start period and 12 retries of 10 seconds.
If `--wait` times out, run `docker compose --profile app up -d --wait --wait-timeout 300`.
Check the log with `docker compose logs cassandra`.

### Out of memory

Cassandra exits at once or restarts in a loop.
Give Docker at least 2 GB of memory.
The compose file sets `MAX_HEAP_SIZE=1G` and `HEAP_NEWSIZE=200M`.
Lower these values only if you have less memory. Cassandra becomes slow below 1 GB.

### Port in use

`Bind for 0.0.0.0:8080 failed`: another process uses port 8080.
Start with `APP_PORT=18085 docker compose --profile app up -d --wait`.
`Bind for 0.0.0.0:9042 failed`: another Cassandra runs on the host.
Stop it, or change the `9042:9042` mapping in `compose.yaml`.

### `cassandra-init` exits with an error

The keyspace was not created. The app then fails with `Keyspace 'sensors' does not exist`.
Run `docker compose logs cassandra-init`.
Rerun with `docker compose up -d cassandra-init`.

### Tests fail with a Docker error

Testcontainers needs the Docker socket.
Check `docker ps` works from your shell.

## What to look at

- `ReadingKey` - the composite primary key with partition and clustering columns.
- `Reading`, `SensorMeta` - the two tables as Java records.
- `DayBucket` - instant to UTC day, and days in a range.
- `SensorController` - all endpoints: TTL insert, logged batch, per-day paging, metadata write.
- `src/test/resources/init.cql` - the keyspace. Spring Boot does not create keyspaces.
- `compose.yaml` - Cassandra, the one-shot init service, and the `app` profile.
- `Dockerfile` - two-stage build. JDK builds the jar. JRE runs it as a non-root user.
