# MealPilot Product/UX Spec (v0)

Date: 2026-01-18

## Goals

- Mobile-first, "small but complete" product flow: Auth → Items → Decide → History → Feedback → Preferences.
- Make errors and loading states consistent and non-blocking.
- Keep UI components minimal and reusable (no big design system build-out).

## Route Map (current + required)

**Public**

- `/` Landing
- `/auth` Login/Register

**Authed**

- `/items` Items list + add
- `/decide` Decide form + results
- `/history` Decision history list
- `/history/:id` (NEW) Decision detail + feedback
- `/settings` Preferences + session/advanced settings

**Developer**

- `/dev` Dev console (optional)

## Primary Flows

### 1) New user onboarding

1. `/` → CTA "Get started" → `/auth`
2. Register (or Login)
3. Redirect to `/items` or `/decide`
4. Add 3–10 items
5. Run Decide to get 1–3 picks

### 2) Daily use

1. `/decide` → see ranked candidates + "why"
2. Optionally rate/accept/reject/skip
3. `/history` for previous picks

### 3) Preference tuning

1. `/settings` → Preferences
2. Set budget bounds, prefer/avoid tags/restaurants, dietary/allergen constraints, notes
3. Next Decide respects constraints and uses profile in reproducibility meta

## Screen Specs

### Landing (`/`)

- Hero message + 1 primary CTA.
- If logged in: CTA goes to `/decide`, else `/auth`.
- Secondary CTA to `/items`.

**Empty / edge**

- N/A

### Auth (`/auth`)

- Login/Register form.
- On success: store JWT and redirect to `from` route or `/decide`.
- If redirected due to expiry/401: show "Session expired" toast/banner.

**Empty / edge**

- Invalid creds: show field-level errors (server `fieldErrors`) and a top-level toast.

### Items (`/items`)

- Left: create item form.
- Right: list of active items.
- Each item: name, restaurant, tags, priceEstimate.

**Empty / edge**

- No items: show empty state with action hint "Add a few meals".
- API error: show error box + retry.
- Loading: skeleton list.

### Decide (`/decide`)

- Form: budget, must-have tags, avoid tags, query, limit.
- Result: ranked list with score/confidence/why.
- CTA: "View decision" (if decisionId exists) → `/history/:id`.

**Empty / edge**

- No items: show a friendly prompt to go to `/items`.
- 400 validation: map `fieldErrors`.
- 401: force logout + toast.

### History list (`/history`)

- List recent decisions with createdAt + feedback status.
- Each row: tap → `/history/:id`.
- Filtering: All / Has feedback / No feedback.

**Empty / edge**

- Empty list: "No decisions yet" with CTA to `/decide`.
- Loading: skeleton rows.

### History detail (`/history/:id`) (NEW)

- Summary: createdAt, selected top pick, meta (algorithm/schemaVersion), input snapshot.
- Candidates list with breakdown expandable.
- Feedback panel:
  - Status: ACCEPT / REJECT / SKIP
  - Optional: category, reasonCode, tags, rating (1–5), comment
  - Submit updates the decision and returns to list or stays with confirmation.

**Empty / edge**

- Not found: show not-found state and link back to history.
- Forbidden: show a clear error and logout (treat as session issue) or return.

### Settings (`/settings`)

- Preferences (primary): edit explicit PreferenceProfile.
- Advanced: API base URL (dev), refresh-token toggle (optional).

**Empty / edge**

- Logged out: redirect to `/auth`.
- Loading: skeleton form.

## Standard Empty / Edge States

- **Global loading**: skeleton components (never blank pages).
- **Global errors**:
  - `ApiError` → show message + requestId (if present) and retry.
  - Uncaught render error → ErrorBoundary fallback with "Reload".
- **401 handling**: force logout + toast "Session expired" then redirect to `/auth`.
- **Rate limiting (429)**: show toast "Too many requests" and keep UI usable.

## Minimal UI Kit

- `Button`, `Field` (existing)
- Add: `ToastHost`, `ErrorBoundary`, `Skeleton`, `Card`, `Badge`, `EmptyState`
- Visual principles:
  - One primary action per screen.
  - Small text for secondary details.
  - Bottom nav for core sections.

## Non-goals (for this 2–5 day pass)

- Full design system or theming overhaul.
- Complex preference editors (chips/multiselect) — use CSV inputs first.
- Refresh-token backend work (optional later).
