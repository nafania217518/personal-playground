# Secure Notes API

A small REST API for private notes. Each user sees only their own notes. An admin sees all notes.

## Overview

This project shows:

- Stateless JWT authentication with Spring Security 7.
- Role-based access with method security.
- MongoDB documents, auditing, and full-text search.
- Integration tests with Testcontainers. Line coverage is checked at 90% or more.
- A multi-stage Docker build and a Docker Compose stack.

## Tech stack

- Java 25
- Spring Boot 4.1.1
- Spring Security 7
- JWT (HS256) via Nimbus
- Spring Data MongoDB
- Testcontainers (MongoDB)
- JaCoCo
- Docker and Docker Compose

## Prerequisites

- Docker with Docker Compose. Docker must run.
- Java 25. Only needed for local runs and tests.
- Maven is not needed. The project ships the Maven wrapper (`./mvnw`).
- `curl`. `jq` is optional. The examples below use it to read the token.

## Run everything with Docker Compose

This builds the API image and starts MongoDB and the API.

```sh
docker compose --profile app up -d --build
```

The first build downloads all dependencies. It can take a few minutes.
The API listens on `http://localhost:8080`.

Follow the logs:

```sh
docker compose --profile app logs -f app
```

Stop everything and delete the data:

```sh
docker compose --profile app down -v
```

Change the host port with `APP_PORT`:

```sh
APP_PORT=18084 docker compose --profile app up -d --build
```

Set the JWT secret and the admin password:

```sh
APP_JWT_SECRET='a-long-random-secret-with-at-least-32-bytes' \
APP_ADMIN_PASSWORD='a-strong-password' \
docker compose --profile app up -d --build
```

Without these variables Compose uses the development defaults from `application.properties`.
Inside Compose the API uses the database `notes` on the `mongo` service.

## Run locally

Start only MongoDB:

```sh
docker compose up -d
```

Start the API from source:

```sh
./mvnw spring-boot:run
```

The API connects to `mongodb://localhost:27017` and uses the default database `test`.
An admin user is created at startup. Default: `admin` / `admin-dev-password`.

Stop MongoDB with `docker compose down -v`.

## Configuration

Spring Boot reads each property from an environment variable with the same name in upper case.

| Setting | Property | Environment variable | Default |
|---|---|---|---|
| Mongo URI | `spring.mongodb.uri` | `SPRING_MONGODB_URI` | `mongodb://localhost:27017/test` |
| JWT secret (HS256, 32 bytes or more) | `app.jwt.secret` | `APP_JWT_SECRET` | dev value in `application.properties` |
| JWT expiry in seconds | `app.jwt.ttl-seconds` | `APP_JWT_TTL_SECONDS` | `3600` |
| Admin username | `app.admin.username` | `APP_ADMIN_USERNAME` | `admin` |
| Admin password | `app.admin.password` | `APP_ADMIN_PASSWORD` | `admin-dev-password` |
| CORS origin | fixed in `SecurityConfig.java` | none | `http://localhost:4200` |
| Server port | `server.port` | `SERVER_PORT` | `8080` |

Note: Spring Boot 4 renamed `spring.data.mongodb.uri` to `spring.mongodb.uri`. The old name is deprecated.

## API

All examples use `localhost:8080`. Change the host port if you set `APP_PORT`.

### Register a user

```sh
curl -i -X POST localhost:8080/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"password123"}'
```

Result: `201` with `{"id":"...","username":"alice","roles":["USER"]}`.

### Log in and store the token

```sh
TOKEN=$(curl -s -X POST localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"password123"}' | jq -r .token)
echo "$TOKEN"
```

Without `jq`, copy the `token` value from the JSON response into `TOKEN`.

### Create a note

```sh
curl -i -X POST localhost:8080/notes \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"title":"Kafka tips","content":"Use idempotent producers."}'
```

Result: `201` with the note. Save the `id` for the next steps:

```sh
ID=<id from the response>
```

### List notes

```sh
curl -s "localhost:8080/notes?page=0&size=20" -H "Authorization: Bearer $TOKEN"
```

### Search notes

```sh
curl -s "localhost:8080/notes?q=kafka" -H "Authorization: Bearer $TOKEN"
```

### Get one note

```sh
curl -s localhost:8080/notes/$ID -H "Authorization: Bearer $TOKEN"
```

### Update a note

```sh
curl -s -X PUT localhost:8080/notes/$ID \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"title":"New title","content":"New text"}'
```

### Delete a note

```sh
curl -i -X DELETE localhost:8080/notes/$ID -H "Authorization: Bearer $TOKEN"
```

Result: `204`.

### List all notes as admin

```sh
ADMIN=$(curl -s -X POST localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin-dev-password"}' | jq -r .token)
curl -s localhost:8080/admin/notes -H "Authorization: Bearer $ADMIN"
```

A `USER` token gets `403` here.

### Endpoints

| Method | Path | Auth | Result |
|---|---|---|---|
| POST | `/auth/register` | none | 201 user, 409 duplicate, 400 invalid |
| POST | `/auth/login` | none | 200 `{token, expiresIn}`, 401 bad credentials |
| GET | `/notes?q=&page=&size=` | USER | 200 page of own notes |
| POST | `/notes` | USER | 201 note |
| GET | `/notes/{id}` | USER | 200 note, 404 if not own |
| PUT | `/notes/{id}` | USER | 200 note, 404 if not own |
| DELETE | `/notes/{id}` | USER | 204, 404 if not own |
| GET | `/admin/notes` | ADMIN | 200 all notes, 403 for USER |

## Security notes

- Sessions are stateless. Each request carries a JWT in the `Authorization` header.
- Tokens are signed with HS256 and one shared secret.
- Passwords are hashed with BCrypt. The hash never leaves the server.
- `/admin/**` is protected with `@PreAuthorize("hasRole('ADMIN')")`.
- CSRF is disabled because the API uses bearer tokens, not cookies.
- CORS allows `http://localhost:4200` for the Angular frontend.
- The JWT secret and admin password in `application.properties` are for development only. Set `APP_JWT_SECRET` and `APP_ADMIN_PASSWORD` in any real deployment.
- The Docker image runs as a non-root user.

## Frontend pairing

The `notes-angular-ui` project is the frontend for this API.
Start it and open `http://localhost:4200`. It calls this API on `http://localhost:8080`.

## Tests

Docker must run. Tests start MongoDB in a Testcontainers container.

```sh
./mvnw verify
```

This runs all tests and the JaCoCo coverage check (90% line coverage per package).
The report is in `target/site/jacoco/index.html`.

## Troubleshooting

| Problem | Cause | Fix |
|---|---|---|
| `Cannot connect to the Docker daemon` | Docker is not running | Start Docker Desktop and retry |
| `port is already allocated` on 8080 | Another process uses the port | Run with `APP_PORT=18084` or stop the other process |
| `port is already allocated` on 27017 | Another MongoDB uses the port | Stop the other MongoDB |
| `401` on `/notes` with a token | The token expired (1 hour) or the secret changed | Log in again and store the new token |
| `403` on `/admin/notes` | The token belongs to a `USER` | Log in as `admin` |
| API logs show `Connection refused` to `localhost:27017` in Docker | Wrong Mongo URI variable | Use `SPRING_MONGODB_URI`, not `SPRING_DATA_MONGODB_URI` |
| Tests fail with a Testcontainers error | Docker is not running | Start Docker and run `./mvnw verify` again |

## What to look at

- `Dockerfile`: multi-stage build, JRE image, non-root user.
- `compose.yaml`: MongoDB with a healthcheck, API under the `app` profile.
- `SecurityConfig.java`: filter chain, JWT decoding, role mapping, CORS, admin seeding.
- `JwtService.java`: token issue and decode with one HS256 key.
- `AuthController.java`: register and login.
- `NoteController.java`: CRUD, ownership check, text search, admin list.
- `Note.java`: document with text index and audit fields.
- `SecureNotesApiTests.java`: end-to-end tests against a real MongoDB container.
