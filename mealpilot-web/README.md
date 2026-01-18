# mealpilot-web

React + TypeScript dev UI for the MealPilot backend.

## What it does

This UI drives the full flow:

- register → login (stores JWT in localStorage)
- create/list items
- decide (get ranked candidates)
- list decisions (reads `X-Next-Cursor` for paging)
- send feedback for the last decision

## Quickstart flow (recommended order)

In the UI, follow this order:

1. Auth: Generate demo creds → Register → Login
2. Items: Create sample items → Load items
3. Decide: Click Decide (creates a `decisionId`)
4. Decisions: Load decisions (history) → optional “Load more” using `X-Next-Cursor`
5. Feedback: Send feedback for the last decision

Tip: Use the “Flow / State” panel + “Run demo flow” button to run the whole sequence and see how each feature unlocks the next.

## Run (Windows)

From repo root:

1. Start backend (auto port):

- `powershell -NoProfile -ExecutionPolicy Bypass -File mealpilot-api/run-dev.ps1 -PreferredPort 8000 -AutoPort`

Copy the `MEALPILOT_URL=http://localhost:XXXX` it prints.

2. Start frontend:

- `cd mealpilot-web`
- `npm install`
- `npm run dev`

Open:

- http://127.0.0.1:5173/

## Configure API base URL

The UI defaults to `VITE_API_BASE` (see `.env.example`) or `http://localhost:9000`.

You can change the API base URL at the top of the UI at runtime; it is saved in localStorage.
