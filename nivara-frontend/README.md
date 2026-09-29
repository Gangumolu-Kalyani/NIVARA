# NIVARA — Caregiver Frontend

The caregiver-facing dashboard and Daily Assistance app for NIVARA, built against
[`nivara-backend`](../nivara-backend). Covers **Person 5**'s scope: the caregiver dashboard,
cognitive progress analytics, reminders (Daily Assistance) and the alert center.

This app is standalone today — it owns its own routing and auth — and is meant to be folded into
the shared shell Person 6 builds for the rest of the team's modules.

---

## Tech stack

| | |
|---|---|
| Framework | React 19 + TypeScript, built with Vite |
| Routing | React Router |
| Styling | Plain CSS with design tokens (`src/styles/tokens.css`), no UI framework |
| API access | A single typed `fetch` wrapper (`src/api/client.ts`) — no other file calls `fetch` directly |

## Getting started

```bash
cd nivara-frontend
cp .env.example .env.local   # points VITE_API_BASE_URL at the backend
npm install
npm run dev
```

The backend must be running separately (`cd ../nivara-backend && ./mvnw spring-boot:run`) — see
its README for setup. By default the app talks to `http://localhost:8080`.

```bash
npm run build     # type-checks (tsc -b) then builds to dist/
npm run preview   # serves the production build locally
npm run lint       # oxlint
```

## Structure

```
src/
├── api/         # fetch wrapper + typed endpoint calls (client.ts, endpoints.ts)
├── auth/        # AuthContext (login/session) and PatientContext (selected patient)
├── components/  # shared UI: nav, status pills, charts, forms, sync badge
├── pages/       # one file per route
├── styles/      # design tokens + shared layout/component classes
└── types/       # TypeScript mirrors of the backend's request/response DTOs
```

## Routes

| Path | Module |
|---|---|
| `/overview` | Dashboard summary cards + today's care timeline |
| `/progress` | Cognitive performance, trends, recent activities |
| `/daily-care` | Daily Assistance: reminders + today's summary + care rhythm |
| `/alerts`, `/alerts/:uuid` | Alert center and alert detail |
| `/patient` | Patient status and care team |

## Design

Warm, calm, mobile-first: bottom tab bar on phone/tablet, a compact top bar past 860px. Status is
always icon + text, never color alone. See `src/styles/tokens.css` for the palette (ivory/cream
ground, indigo/teal/lavender accents) and light/dark variants.

## Notes

- No offline queueing yet — `src/components/SyncBadge.tsx` reflects real `navigator.onLine` /
  request state, it never claims a fake "synced".
- "Call Patient" in the alert detail view is a placeholder: no phone number is modeled on the
  patient yet.
