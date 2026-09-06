# Handoff: Plan 3 (Android app) — context for continuing this project

This project builds an Android app that scans a product photo, identifies it with a vision
AI, and compares its price across Peruvian stores. **Plans 1 and 2 (the backend) are done
and merged to `master`.** This document is everything a fresh AI/developer needs to build
**Plan 3: the Android app** without re-deriving decisions already made.

## What exists right now

- `docs/superpowers/specs/2026-09-06-comparador-precios-design.md` — the original design
  spec. Read this first for the product vision. **Note:** it originally scoped 4 stores
  (Plaza Vea, Wong, Falabella, Ripley); the actual implementation covers only **Plaza Vea +
  Wong** (see "Deliberate scope reduction" below).
- `docs/superpowers/plans/2026-09-06-backend-identificacion-visual.md` — Plan 1's
  implementation plan (done).
- `docs/superpowers/plans/2026-09-06-scrapers-precios-plazavea-wong.md` — Plan 2's
  implementation plan (done).
- Backend source: `api/` (Vercel serverless functions), `lib/` (business logic), `test/`
  (Vitest), `scripts/verify-stores.ts` (live re-verification script for the scrapers).
- Repo is a local git repo (no remote configured), single branch `master`, 20 commits.
- Node/TypeScript project at the **repo root** — `package.json`, `tsconfig.json`,
  `node_modules/` all live at the top level, not in a subfolder.

## The API contract Plan 3 must build against

**`POST /api/scan`** — the only endpoint. Deployed on Vercel (Node.js serverless function).

### Request

```json
{ "image": "data:image/jpeg;base64,<...>" }
```

- `image` is **required**, must be a string, and must match
  `^data:image\/(jpeg|png|gif|webp);base64,([A-Za-z0-9+/]+={0,2})$` — i.e. a full data URI
  with one of those 4 media types, standard base64 alphabet, **no line wraps**.
- **Android must encode with `Base64.NO_WRAP`**, not `Base64.DEFAULT` — `DEFAULT` inserts a
  `\n` every 76 characters, which fails the regex above and returns a 400.
- Max length of the base64 payload string: **4,000,000 characters** (~3MB decoded). Compress
  the photo before sending; there is no server-side resizing.

### Response — 200 OK

```json
{
  "identification": {
    "marca": "Gloria",
    "nombre": "Leche evaporada",
    "presentacion": "400g",
    "categoria": "abarrotes",
    "confianza": 0.92
  },
  "tiendas": [
    { "tienda": "Plaza Vea", "estado": "encontrado", "producto": "Leche Evaporada Gloria 400g", "precio": 4.5, "url": "https://www.plazavea.com.pe/..." },
    { "tienda": "Wong", "estado": "no_encontrado" }
  ]
}
```

- `identification.confianza` is a number 0–1. **If it's below 0.5, `tiendas` will be an
  empty array `[]`** — the backend skips store search entirely on low-confidence
  identifications. The Android UI must handle this case by prompting the user to retake the
  photo or enter the product name manually (this was always the intended UX per the design
  spec — Plan 3 is where it actually gets built).
- Each entry in `tiendas` has `estado` = `"encontrado"` | `"no_encontrado"` | `"error"`.
  - `"encontrado"`: `producto`, `precio` (number, soles), `url` are present.
  - `"no_encontrado"` / `"error"`: those three fields are absent. `"error"` also has a
    `mensaje` string (a human-readable reason, safe to show or log, but not guaranteed
    stable wording — don't pattern-match on it).
  - **Important known limitation:** the fuzzy matcher can occasionally still match a
    multi-pack product instead of the single unit the user photographed (this was a real
    bug found and partially fixed during Plan 2 — see below). **Display `producto` (the
    matched product's full name) clearly next to the price**, not just the price, so the
    user can visually catch a pack mismatch. This is a deliberate mitigation, not
    optional polish.

### Response — error statuses

- `400` — validation error (bad/missing `image`, unsupported type, oversized). Body:
  `{ "error": "<message>" }`.
- `405` — wrong HTTP method (only `POST` is allowed).
- `502` — the vision AI identification failed (e.g. AI Gateway issue). Body:
  `{ "error": "<message>" }`.
- `500` — anything else unexpected.

## Deliberate scope reduction: only 2 of the original 4 stores

Before writing Plan 2, live research showed:
- **Plaza Vea and Wong** both run on VTEX and expose a public, unauthenticated JSON search
  API (`GET {baseUrl}/api/catalog_system/pub/products/search/{term}?_from=0&_to=9`) — easy
  and reliable. Both are implemented and **verified against the real live sites** (Task 5 of
  Plan 2 actually called them and got real prices back).
- **Falabella** has no verifiable public API (a guessed internal endpoint returned 400).
- **Ripley** returns `403 Forbidden` on a simple request — active anti-bot protection.

This was a decision made **with the user** (Marco), not a silent gap. Falabella/Ripley are
deferred to a possible future "Plan 2b." **Plan 3 should build the Android UI to display
however many stores are in the `tiendas` array — don't hardcode "2 stores" anywhere in the
UI**, since a future backend change could add more without an app update if done right (a
`RecyclerView`/`LazyColumn` over the array, not two fixed rows).

## The matching-quality bug (fixed, but worth knowing about)

Plan 2's fuzzy-matching algorithm (`lib/matching.ts`) originally had no concept of brand or
pack size. Live verification for "Gloria Leche evaporada 400g" (a single can) matched a
**6-pack at ~5x the real price** at both stores, and a competing brand could in theory
outscore the correct one. This was caught by a human-reviewed final code review (not by
automated tests alone) and fixed by requiring a brand-token match as a hard gate plus a
penalty for pack/multipack indicator words. The fix is verified against the real numbers
from the bug (both now score well below the match threshold), but the reviewer flagged a
residual: a *short, terse* pack-title could theoretically still slip through if its text
happens to overlap enough with the identification. This is why the UI-level mitigation
above (show `producto` next to `precio`) matters — it's not just a nice-to-have.

## Backend implementation details (only relevant if Plan 3 needs to touch the backend)

- Vision AI: Vercel AI SDK **v7** — `generateText` + `Output.object({ schema })`, **not**
  `generateObject` (that function doesn't exist in v7; an earlier attempt to look this up
  from memory would have been wrong — it was verified against live docs before writing
  Plan 1). Model string: `'anthropic/claude-sonnet-5'` via the AI Gateway.
- Needs `AI_GATEWAY_API_KEY` (pulled via `vercel env pull .env.local` once the project is
  linked to a Vercel account — **this step has not been done yet**, see below).
- Tests: Vitest. Pattern used throughout: `lib/<name>.ts` (pure logic, easily testable) +
  `api/<name>.ts` (thin Vercel HTTP adapter). Mocks use `vi.hoisted()` for module mocks
  (a real Vitest 2.1.9 quirk: `vi.mock()` factories only auto-hoist variable names that
  *start* with `mock`, not ones that merely end with `Mock` — this bit multiple tasks
  during implementation).
- `scripts/verify-stores.ts` (run via `npx tsx scripts/verify-stores.ts`) makes real network
  calls to Plaza Vea/Wong and is the fastest way to sanity-check the scrapers still work if
  either site changes its API.

## What has NOT been done yet (not blocking Plan 3, but worth knowing)

- **Plan 1's Task 7** (manual live end-to-end verification of `POST /api/scan` against a
  real deployed Vercel instance with real AI Gateway credentials) was explicitly deferred by
  the user and never completed. The vision-AI code path is verified correct against the
  installed SDK's types and mocked tests, but has never actually been called live. Whoever
  deploys this to Vercel for Plan 3 to hit should do this check first — instructions are in
  `docs/superpowers/plans/2026-09-06-backend-identificacion-visual.md`, Task 7.
- No CORS configuration exists — likely a non-issue since Android's HTTP client isn't a
  browser, but hasn't been explicitly tested.
- No rate-limiting/abuse protection on `/api/scan` — flagged twice in code review as an open
  decision (each request costs money via the AI call and now also makes 2 outbound calls to
  third-party sites from a shared IP). Not addressed; a decision for whoever handles
  production launch.
- The backend has never actually been deployed to Vercel. Someone needs to `vercel link` +
  deploy before Plan 3's app has a real URL to call.

## Suggested next step for whoever picks up Plan 3

This project was being built using Claude Code's "superpowers" skill set (brainstorming →
writing a design spec → writing an implementation plan → subagent-driven implementation
with TDD and code review → merge). Plans 1 and 2 followed: spec approval → a detailed
implementation plan with bite-sized TDD tasks and exact code → task-by-task implementation
with review after each task → a final whole-branch review → fix any findings → merge to
master. If continuing with a different AI/tool, it doesn't need to follow this exact
process, but the **design spec** (`docs/superpowers/specs/2026-09-06-comparador-precios-design.md`)
and the API contract above are the two things it must respect. Decisions still open for
Plan 3 specifically (not yet made by the user):

- Where the Android Gradle project lives in this repo (currently only backend code exists at
  the repo root — a new top-level folder like `android/` is the obvious choice, but the
  human should confirm).
- The exact base URL the app points at (depends on where/whether the backend ends up
  deployed).
