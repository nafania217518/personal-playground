# notes-angular-ui

A small Angular front end for `secure-notes-api`. It runs in Docker or on your machine.

## Overview

The app shows:

- Login and register with a JWT token.
- A notes list with debounced search.
- Create and delete notes.
- Protected routes. A guard sends logged-out users to `/login`.
- An HTTP interceptor. It adds the token and handles `401`.

## Tech stack

- Angular 22, zoneless.
- Standalone components only.
- Signals for state (`signal`, `computed`, `toObservable`).
- Functional HTTP interceptor and functional route guard.
- Reactive forms.
- Vitest for unit tests.
- Docker: multi-stage build, nginx serves the static files.

## Prerequisites

- Node 22 or 23 and npm. Needed for local run, tests, and build.
- Docker with Compose. Needed only for the Docker run.
- `secure-notes-api` in the sibling folder `../secure-notes-api`.

Note on `.npmrc`: it sets `legacy-peer-deps=true`.
Some dev dependencies declare peer ranges that do not include Angular 22 yet.
Without this flag, `npm install` stops with a peer-dependency conflict.
The flag tells npm to ignore peer ranges. The packages work fine together.

## Run with Docker Compose

1. Start the API first. The UI calls it at `http://localhost:8080`.

```sh
cd ../secure-notes-api
docker compose --profile app up -d --build
cd ../notes-angular-ui
```

2. Build and start the UI:

```sh
docker compose up -d --build
```

3. Open `http://localhost:4200`.

4. Stop the UI:

```sh
docker compose down
```

Change the host port with `WEB_PORT`:

```sh
WEB_PORT=14200 docker compose up -d --build
```

No proxy is needed. The browser calls the API directly at `http://localhost:8080`.
The two compose stacks are separate. Start and stop them separately.

Files:

- `Dockerfile`: stage 1 builds with `node:22-alpine`. Stage 2 serves with `nginx:alpine`.
- `nginx.conf`: serves `/usr/share/nginx/html` on port 80. Unknown paths fall back to `index.html`.
- `compose.yaml`: one service, `web`.
- `.dockerignore`: keeps `node_modules`, `dist`, `.git`, `.angular`, `coverage` out of the build context.

## Run locally

Start the API first (see above). Then:

```sh
npm install
npx ng serve
```

Open `http://localhost:4200`.

## Configuration

The API base URL is `apiUrl` in:

- `src/environments/environment.ts` (production build, Docker image).
- `src/environments/environment.development.ts` (`ng serve`, development build).

Both default to `http://localhost:8080`. Change the value and rebuild.
For Docker, run `docker compose up -d --build` again after the change.

The API allows CORS from `http://localhost:4200` only.
If you serve the UI on another port, update the API CORS config too.

## Using the app

1. Open `http://localhost:4200`. You see the login page.
2. Click `No account? Register`. Enter a username and password. Click `Register`.
3. Log in with the same values. The app goes to `/notes`.
4. Type a title and content. Click `Add note`. The note appears in the list.
5. Type in the search box. The list filters after 300 ms.
6. Click `Delete` on a note to remove it.
7. Click `Logout`. The app returns to `/login`.

The API creates an admin user at startup: `admin` / `admin-dev-password`.
The admin can log in here too. The UI shows only the admin's own notes.
The `/admin/notes` endpoint is not used by this UI.

## Tests

```sh
npx ng test --watch=false
```

Coverage:

```sh
npx ng test --watch=false --coverage
```

The report is in `coverage/notes-angular-ui/index.html`.
The target is 90% or more statements for `src/app`.

## Build

```sh
npx ng build
```

Output is in `dist/notes-angular-ui/browser`.

## Troubleshooting

- Login fails or the page shows a network error: the API is not running. Start it (see above). Check `curl localhost:8080/actuator/health` or the API README.
- Browser console shows a CORS error: the UI is not on `http://localhost:4200`. Use port 4200, or add your origin to the API CORS config.
- `port is already allocated` or `address already in use`: another process uses port 4200. Set `WEB_PORT` for Docker, or `npx ng serve --port 4300` locally. Then update the API CORS config.
- `npm install` stops with `ERESOLVE`: check that `.npmrc` with `legacy-peer-deps=true` is present.
- Old UI after a change: Docker uses a cached build. Run `docker compose up -d --build`.

## What to look at

- `src/app/auth.service.ts`: login, register, logout. The token lives in `localStorage` and a signal.
- `src/app/auth.interceptor.ts`: adds `Authorization: Bearer <token>`. On `401` it logs out and goes to `/login`.
- `src/app/auth.guard.ts`: one-line functional guard.
- `src/app/notes.service.ts`: typed calls to `/notes`.
- `src/app/login.component.ts`: reactive form with a register toggle.
- `src/app/notes.component.ts`: list, search with `debounceTime(300)`, create, delete, logout.
- `src/app/app.routes.ts`: routes with the guard.
- `src/app/*.spec.ts`: one spec per requirement. The `it` text is the requirement.
