# MealPilot

MealPilot is a **decision-first food ordering assistant**.

Most food apps optimize for browsing; MealPilot optimizes for **getting a confident decision in seconds**.

## 1) Product Idea (What we’re building)

### Problem

Users often:

- Open a food app, scroll for 10–20 minutes, and still feel unsure.
- Re-order the same comfort meals but waste time finding them.
- Struggle to match food with constraints (time, budget, mood, health).

### Solution

MealPilot takes minimal input (tap or voice) and returns **1–3 ranked “ready to order” decisions**:

- Each suggestion includes a **confidence score** and **why** it was chosen.
- The user gives quick feedback (accept / reject + reason).
- Over time, MealPilot learns habits: comfort meals, budget band, cuisine preferences, time-of-day patterns.

### What makes it “product-level”

- Decision quality improves via feedback loop and history.
- Strong focus on speed and habit inference.
- Clear path to integrations (deep links today; partner APIs later).

## 2) Key UX Principles

- **Decision > Browsing**: user shouldn’t need to scroll lists.
- **3 results max**: keep the output simple and actionable.
- **Explainability**: show “why” to build trust.
- **Feedback loop**: every decision should improve the next.

## 3) Full Workflow (End-to-End)

### A) Onboarding (first run)

Goal: capture just enough to make day-1 decisions decent.

- Location (manual / device), optional
- Food type preference (veg/non-veg/eggetarian)
- Typical budget range (or “infer from orders”)
- Spice preference (low/medium/high)
- “Comfort items” seed list (user picks 3–5 common orders)

### B) Daily decision (primary loop)

1. User opens app.
2. Presses **Decide** (or uses voice: “decide dinner under 250”).
3. Backend returns 1–3 suggestions:
   - `title` (what to order)
   - `confidence` (0–1)
   - `why[]` (short bullet reasons)
   - `cta` (deep link / copy order / share)
4. User action:
   - Accept → (optionally) “ordered” confirmation
   - Reject → choose reason (too pricey / too heavy / not feeling it / too far / etc.)

### C) Learning (after each decision)

- Update preference weights.
- Record acceptance/rejection reasons.
- Adjust inferred budget band and time patterns.

### D) Edge cases

- No history yet → rely on onboarding comfort items + simple heuristics.
- User rejects all 3 → ask one follow-up question (simple): “budget?” or “veg/non-veg?” then retry.

## 4) Tech Stack (MVP)

### Backend (current workspace: `mealpilot-api/`)

- Java: **21**
- Spring Boot: **3.4.1**
- Spring WebFlux (reactive)
- Spring Data Reactive MongoDB
- Spring Security (JWT Bearer auth)
- Spring Boot Actuator
- Tests: `spring-boot-starter-test`, `reactor-test`, `spring-security-test`
- Build safety: Maven Enforcer requires Java **>= 21**

### Database

- MongoDB (local): `mongodb://localhost:27017/mealpilot`

### Frontend (planned: `mealpilot-web/`)

- React + TypeScript (Vite)
- PWA-friendly structure
- Voice input via Web Speech API (where supported)

### Later (not required for MVP)

- Redis (caching, rate limits, session-ish hints)
- Kafka/RabbitMQ (event stream for learning/analytics)
- Observability: OpenTelemetry + tracing dashboards

## 5) Current Backend Features (already implemented)

### Health

- `GET /api/health`
  - returns `{ status, service, time }`

### Items (“Go-To Items” foundation)

Purpose: store user’s common meals so the decision engine has a safe baseline.

- `GET /api/items`
- `POST /api/items`
- `PATCH /api/items/{id}`
- `DELETE /api/items/{id}` (soft delete)

Notes:

- Auth is now real JWT Bearer auth.
  - Create an account: `POST /api/auth/register`
  - Log in to get a token: `POST /api/auth/login`
  - Call protected APIs with: `Authorization: Bearer <token>`
- Mongo model uses an `active` flag for soft deletes.

## 6) MVP APIs

### Decide

- `POST /api/decide`
  - Input: context (budget, meal time, mood), optional voice-transcript text
  - Output: ranked suggestions (1–3), confidence, why

### Feedback

- `POST /api/decisions/{id}/feedback`
  - ACCEPT / REJECT
  - reason codes + free text

### History

- `GET /api/decisions` (recent decisions, optional `?limit=50`)
- `GET /api/decisions/{id}` (fetch a single decision)

## 7) Suggested Data Model (minimal)

This section describes the **current persisted shape** in MongoDB and how it evolves over time.

### Schema versioning

- MongoDB is schema-less; we evolve by **adding optional fields**.
- Documents may include `schemaVersion` (integer). Missing means “pre-versioning”.
- New writes should set `schemaVersion=2`.

### User

- `id`
- preference hints (veg/spice/cuisines)
- inferred budget band

### Item

- `id`, `userId`, `name`
- tags (cuisine, veg, spicy)
- `active`

### Decision

- `id`, `userId`, `createdAt`
- `input` snapshot (budget, tags, query, limit)
- `candidates[]`:
  - item snapshot (`id`, `name`, `restaurantName`, `tags`, `priceEstimate`)
  - `score`, `confidence`
  - `why[]` (human-readable explanation)
  - `breakdown` (structured explainability: budget/tag/query/preference contributions)
- `feedback` (optional, decision-level)
- `meta` (optional reproducibility metadata)

#### Decision explainability fields

- `candidates[].why[]` is the user-facing explanation.
- `candidates[].breakdown` is a structured breakdown that makes the decision explainable/debuggable.

#### Decision reproducibility fields (`meta`)

Goal: be able to answer “why did we recommend this?” later, even if the user’s items/preferences change.

- `meta.algorithm` / `meta.algorithmVersion`: identifies the scoring algorithm.
- `meta.inputHash`: hash of normalized request inputs.
- `meta.itemsHash`: hash of the item set used at decision time.
- `meta.preferenceHash`: hash of the learned preference signals used.
- `meta.preferenceSnapshot`: a snapshot of learned weights/penalties at decision time.

### Feedback

Feedback is stored **inside the Decision** as `decision.feedback`:

- `status`: `ACCEPT` / `REJECT` / `SKIP`
- `reasonCode`: stable short code (kept for filtering and backwards compatibility)
- `reason`: structured taxonomy (`category`, `code`, `tags[]`)
- `rating`: optional 1–5
- `comment`: optional free text

### UserPreference (learning + explicit profile)

`user_preferences` stores two types of signals:

- Learned signals (implicit): `tagWeights`, `restaurantWeights`, `pricePenalty`
- Explicit profile (user-controlled): `profile` (budget bounds, prefer/avoid tags, dietary/allergen constraints, notes)

### Migration strategy

- No breaking migrations are required: old documents continue to deserialize because new fields are optional.
- For analytics/ops you can backfill `schemaVersion`:
  - set `mealpilot.migrations.enabled=true` and start the API once to backfill `user_preferences.schemaVersion`.

## 8) How to Run (Windows)

Prereqs:

- JDK 21 installed and active in the terminal
- MongoDB running at `localhost:27017`

From `MealPilot/mealpilot-api`:

- `powershell -NoProfile -ExecutionPolicy Bypass -File .\run-dev.ps1`

Verify:

- Backend defaults to `http://localhost:9000`
- `GET http://localhost:9000/api/health` or `GET http://localhost:9000/actuator/health`

## 9) Non-Goals (for MVP)

- Full Swiggy/Zomato cart automation (not reliable without partner APIs).
- Large browsing UI or complex filtering.
- Heavy personalization ML pipeline (we’ll start with scoring + rules + feedback).
