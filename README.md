# MealPilot

MealPilot is a **decision-first food ordering assistant**.

Instead of browsing endlessly, you provide minimal input (budget, tags, query) and get **1–3 ranked “ready-to-order” suggestions** with **confidence + reasons**. Your feedback (accept/reject + reason) teaches the system over time.

## Project layout

- `mealpilot-api/` — Spring Boot 3 / Java 21 reactive API (WebFlux + Reactive MongoDB)
- `mealpilot-web/` — React + TypeScript dev UI (Vite)

## Core workflow (end-to-end)

1. Register

- `POST /api/auth/register`

2. Login (get JWT)

- `POST /api/auth/login` → `accessToken`

3. Create items (your go-to meals)

- `POST /api/items`
- `GET /api/items`

4. Decide

- `POST /api/decide` → returns 1–3 ranked candidates and a `decisionId`

5. History

- `GET /api/decisions` supports cursor pagination (see `X-Next-Cursor` response header)

6. Feedback (learning loop)

- `POST /api/decisions/{id}/feedback`

## Security model

- Public endpoints:
  - `GET /` (HTML in browser; JSON if `Accept: application/json`)
  - `GET /api/health`
  - `GET /actuator/health`, `GET /actuator/info`
  - `POST /api/auth/register`, `POST /api/auth/login`
  - OpenAPI/Swagger:
    - `GET /v3/api-docs`
    - `GET /swagger-ui.html` (redirects to Swagger UI)
    - `GET /swagger-ui/**`
- Everything else requires: `Authorization: Bearer <jwt>`

## Run locally (Windows)

Prereqs:

- JDK 21
- MongoDB listening at `mongodb://localhost:27017/mealpilot`

### 1) Start backend (fixed port)

From repo root (recommended):

- `powershell -NoProfile -ExecutionPolicy Bypass -File run-dev.ps1`

Or backend only:

- `cd mealpilot-api`
- `powershell -NoProfile -ExecutionPolicy Bypass -File .\run-dev.ps1`

Backend URL (default): `http://localhost:9000`

### 2) Start frontend

- `cd mealpilot-web`
- `npm install`
- `npm run dev`

Open:

- http://127.0.0.1:5173/

If you changed backend port, paste the backend base URL into Settings (it’s remembered in localStorage).

### 3) Generate typed API client (frontend)

With the backend running, generate TypeScript types from the live OpenAPI schema:

- `cd mealpilot-web`
- `npm run api:gen`

This writes: `mealpilot-web/src/gen/api-types.ts`.

## Notes

- Port `8080` is commonly occupied on Windows (e.g., Jenkins service). MealPilot defaults to `9000` to avoid conflicts.
- The React UI is a dev UI intended to exercise the full flow quickly.

## Full product idea

See [MEALPILOT.md](MEALPILOT.md) for the detailed product idea, UX principles, and longer-term roadmap.
