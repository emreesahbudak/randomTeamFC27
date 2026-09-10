# FC27 — Random Team & League Manager

Monorepo for the FC27 app: fair random team draws for friend-group FC27 sessions, plus automated league tracking.

## Layout

```
backend/   Java 21 + Spring Boot 3.5.5 (Maven) — REST API
web/       React + Vite + TypeScript + TailwindCSS — web app
mobile/    React Native + Expo + TypeScript — iOS & Android app
shared/    OpenAPI-generated TS client + shared business logic (wheel algorithm, league calc, Zustand store)
docs/      API.md — endpoint-by-endpoint reference (params, roles, auth)
```

## Requirements

- JDK 21 (backend) — set via `.vscode/settings.json` `java.configuration.runtimes`
- Node.js 24+ / npm 11+ (web, mobile, shared)
- PostgreSQL 17, listening on **port 5433** (not the default 5432) — either:
  - **Docker** (if it works on your machine): `docker compose up -d` — maps container Postgres to host port 5433, or
  - **Native install** (what this project actually runs on day to day — Docker Desktop was unreliable on the primary dev machine): install PostgreSQL 17 (`winget install PostgreSQL.PostgreSQL.17` on Windows), then create the role/database once:
    ```sql
    CREATE ROLE fc27 WITH LOGIN PASSWORD 'fc27_dev_pw';
    CREATE DATABASE fc27 OWNER fc27;
    ```
    Set the native install to listen on port 5433 (`postgresql.conf`'s `port = 5433`), or override the app's port via the `DB_PORT` env var if you leave it on 5432.

## Local dev

```bash
# Backend (dev profile seeds 18 teams / 3 league types / an admin@fc27.app test account)
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Web
cd shared && npm run build   # first time, and after any shared/src change — see below
npm run dev:web

# Mobile
npm run dev:mobile
```

No Postgres available at all (native or Docker)? Run the backend with `-Dspring-boot.run.profiles=dev,h2local` instead — a persistent local H2 database in Postgres-compat mode, zero setup, good enough for solo local dev (not for anything beyond that).

Swagger UI: http://localhost:8080/swagger-ui.html

## Shared TypeScript API client

`shared/` holds a TS client generated from the backend's OpenAPI spec, plus (from Step 6 onward) shared business logic used by both `web/` and `mobile/`.

The client is generated from `shared/openapi-spec.json` — a **static, checked-in snapshot**, not a live call — so `npm test` and `npm run generate:api` work without the backend running. After changing any backend controller/DTO, refresh that snapshot and rebuild:

```bash
cd shared
npm run export:api-spec   # backend must be running — re-fetches /v3/api-docs
npm run build             # regenerates src/generated/ from that snapshot, compiles to dist/
npm test                  # (re)generates + validates the client's shape
```

`web/` and `mobile/` import `@fc27/shared`'s **compiled** `dist/` output (`main`/`types` point there), not raw `src/` — a workspace package consumed as source lets its own strict compiler options leak into whatever imports it (bit us once: the generated client failed web's `noUnusedLocals`/`erasableSyntaxOnly` checks). So after changing anything in `shared/src/`, run `npm run build --workspace shared` (or `npm run build:shared` from root) before `web`'s dev server or typecheck will see the change.

Both `shared/src/generated/` and `shared/dist/` are gitignored — build artifacts, always reproducible from `openapi-spec.json` + `src/`.

## Shared business logic

Also in `shared/src/`, used by both `web/` and `mobile/` so the two never diverge:

- `wheel.ts` — the team-draw algorithm: `filterTeams` (star/league filters), `spinWheel` (fair random pick, Mirror Match support), `filterAndSpin`.
- `authStore.ts` — `createAuthStore(storage)`, a Zustand store factory. Each platform passes its own storage adapter (web: `localStorage`, mobile: `AsyncStorage`) at app startup.
- `apiClient.ts` — `createApiConfiguration(...)` wires the generated API client to the current access token automatically.
- `logger.ts` — structured `logDebug/logInfo/logWarn/logError`, the frontend counterpart to the backend's Slf4j logging.

## Web app

`web/` is a React + Vite + TailwindCSS SPA, routed with `react-router-dom`:

- `/` — Wheel Arena (star/league filters, Mirror Match, spin) — public, works for guests
- `/login` — Guest or Email OTP (Google/SMS are built on the backend but not exposed here — see project memory for why)
- `/profile` — requires auth
- `/admin/teams` — requires the `ADMIN` role; full Team + League Type CRUD

Tests: Vitest + React Testing Library (`npm run test:web` from root, or `npm test` inside `web/`). League/match/standings screens aren't built yet — no backend for them until Step 9.

## Mobile app

`mobile/` is an Expo (React Native) app, styled with NativeWind (Tailwind for RN) using the same color tokens as `web/src/index.css`. Bottom tab bar: **Çark** (wheel arena, vertical layout) and **Profil** (shows the Email OTP / Guest login form when signed out, account info + logout when signed in). Same feature scope as `web/` — no admin CRUD screen and no League/Match/standings UI yet (Step 9).

Persistent auto-login works like web's "Remember Me" but without cookies: the refresh token is stored in `expo-secure-store` (hardware-encrypted Keychain/Keystore) on real iOS/Android builds. `expo-secure-store` has **no web implementation at all** (an empty stub — every call throws), so `mobile/src/lib/refreshToken.ts` falls back to AsyncStorage when `Platform.OS === "web"`, purely so `expo start --web` stays usable as a browser-testable stand-in on machines without a simulator/device — real builds never take that branch.

```bash
cd mobile && npx expo start --web --port 8081   # browser-testable dev mode (port 8081 is already in the backend's default CORS allowlist)
npx expo start                                   # normal Expo dev server — scan the QR with Expo Go for a real device
```

## Status

Build is being developed step by step; see project memory / conversation history for the agreed 9-step plan. Do not skip ahead to a step that hasn't been started.
