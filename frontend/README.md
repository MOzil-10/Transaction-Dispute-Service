# DisputeX - Transaction Dispute Portal (Frontend)

React 18 + TypeScript + Vite + Tailwind CSS + lucide-react. Integrates with the Spring Boot
dispute backend over its REST API. No mock data - every screen reads and writes live backend data.

## Scripts (npm)

| Command | Description |
|---------|-------------|
| `npm install` | Install dependencies |
| `npm run dev` | Dev server at http://localhost:5173 (Vite proxies `/api` to http://localhost:8087) |
| `npm run build` | Type-check + production bundle (output in `dist/`) |
| `npm run preview` | Serve the built bundle locally |
| `npm run test` | Run the Vitest unit suite (watch mode) |
| `npm run test:run` | Run the unit suite once (CI mode) |
| `npm run typecheck` | Type-check without emitting |

## Backend connection

The app calls the backend under `/api/v1/*`.

- **Development:** Vite proxies `/api` to http://localhost:8087, so start the Spring Boot backend
  first (default `local` profile, with its seeded demo data).
- **Docker / production:** the bundled nginx config proxies `/api` to the `app` service. To point
  a standalone build at a different API origin, set `VITE_API_BASE_URL`.

**JWT handling:** the token from login/registration is stored in `localStorage` and sent as a
`Bearer` header on every authenticated request. A `401` clears the session and returns the user
to the login screen.

## Screens

- `/auth/login`, `/auth/register` - JWT authentication.
- `/dashboard` - account card with the live balance, a derived activity chart, and active/resolved metrics.
- `/transactions` - searchable, filterable table (mandate type + date range via `/search`). The
  dispute action appears only when the backend `disputable` flag is true, and shows the existing
  status when a transaction has already been disputed.
- `/disputes/new` - 3-step wizard with the 60-day regulatory check (banner + disabled submit when the window is closed).
- `/disputes` - dispute tracking table (reference, reason, amount, decision route, status, opened date).
- `/account` - account profile: holder name, account number, account type, live balance, and currency.

## Docker

A multi-stage `Dockerfile` builds the Vite bundle and serves it with nginx (`nginx.conf` provides
the SPA fallback and the `/api` proxy). It is wired into the root `docker-compose.yml` as the
`frontend` service and published on http://localhost:3000.

## Testing

Unit tests use Vitest + React Testing Library and live in `src/**/__tests__`. They cover
formatting utilities, the API error/client layer, and key UI behaviour. Run with `npm run test:run`.