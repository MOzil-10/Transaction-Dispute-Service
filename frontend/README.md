# Aster Bank - Transaction Dispute Portal (Frontend)

React 18 + TypeScript + Vite + Tailwind CSS + lucide-react. Integrates with the Spring Boot
dispute backend over its REST API. No mock data - every screen reads/writes live backend data.

## Scripts (npm)

- install : npm install
- develop : npm run <dev>   -> http://localhost:5173 (Vite proxies /api to http://localhost:8087)
- build   : npm run build   -> type-check + production bundle
- preview : npm run preview -> serve the built bundle

## Backend connection

The app calls the backend under /api/v1/*. In development, Vite proxies /api to
http://localhost:8087, so start the Spring Boot backend first (with the local profile and its
seeded data). For a deployed build, set VITE_API_BASE_URL to the API origin (see .env.example).

JWT handling: the token from login/registration is stored in localStorage and sent as a
Bearer header on every authenticated request. A 401 clears the session and returns the user to login.

## Screens

- /auth/login, /auth/register : JWT auth.
- /dashboard   : banking-blue account card, derived activity chart, active/resolved metrics.
- /transactions: searchable + filterable table (mandate type + date range via /search).
                 Dispute button appears only when the backend 'disputable' flag is true.
- /disputes/new: 3-step wizard with the 60-day regulatory check (banner + disabled submit when closed).
- /disputes    : tracking table (reference, reason, risk score, decision route, status).
- /account     : account profile; the API exposes no live balance endpoint, so balance is shown as unavailable.