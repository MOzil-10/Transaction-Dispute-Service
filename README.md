# DisputeX - Transaction Dispute Portal

A full-stack Transaction Dispute Portal for the South African banking sector. Customers can
view their transactions, lodge disputes against debit orders (`STANDARD_EFT` and `DEBICHECK`
mandates), and track the full history of their disputes from submission to resolution.

When a dispute is submitted, the service validates ownership and eligibility (a 60-day
regulatory window and transaction-category rules), calculates a risk score, and routes the
dispute through a decision matrix. Low-risk disputes are auto-approved and refunded atomically;
higher-risk disputes are queued to a back-office agent via AWS SQS.

- **Backend** - Java 21 / Spring Boot 3.2 REST API (`/dispute` module root)
- **Frontend** - React 18 + TypeScript + Vite + Tailwind single-page app (`/frontend`)

---

## Tech Stack

| Layer | Technology |
|-------|------------|
| Backend language | Java 21 |
| Backend framework | Spring Boot 3.2 |
| Security | Spring Security 6 + JWT (JJWT 0.12) |
| Database | PostgreSQL (prod) - H2 in-memory (tests) |
| Migrations | Flyway |
| Messaging | AWS SQS (SDK v2), emulated locally with LocalStack |
| API docs | SpringDoc OpenAPI / Swagger UI |
| Backend tests | JUnit 5, Mockito, jqwik (property-based), JaCoCo (80% gate) |
| Frontend | React 18, TypeScript, Vite, Tailwind CSS, lucide-react |
| Frontend tests | Vitest + React Testing Library |
| Container | Multi-stage Dockerfiles + Docker Compose |

---

## API Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/api/v1/auth/register` | Public | Register a new customer (provisions a main account) |
| `POST` | `/api/v1/auth/login` | Public | Login and receive a JWT |
| `GET` | `/api/v1/account` | Bearer JWT | Read-only account overview (profile + live balance) |
| `GET` | `/api/v1/transactions` | Bearer JWT | Paginated transaction history |
| `GET` | `/api/v1/transactions/search` | Bearer JWT | Filter transactions by mandate type and date range |
| `POST` | `/api/v1/disputes` | Bearer JWT | Submit a transaction dispute |
| `GET` | `/api/v1/disputes` | Bearer JWT | Paginated dispute history |

Swagger UI is available at `/swagger-ui/index.html` when the backend is running.

---

## Quick Start - Full Stack with Docker Compose (recommended)

The fastest way to run everything. This starts four containers:

- **frontend** - the React SPA served by nginx (http://localhost:3000)
- **app** - the Spring Boot API (http://localhost:8087)
- **postgres** - PostgreSQL 16 database
- **localstack** - emulated AWS SQS (a `dispute-events.fifo` queue is created on startup)

No AWS account, local Java/Node, or extra configuration required.

```bash
# Build and start the whole stack
docker compose up --build

# Stop and remove containers (keeps the database volume)
docker compose down

# Stop and remove containers AND the database volume
docker compose down -v
```

Once started:

- Portal UI: http://localhost:3000
- API: http://localhost:8087
- Swagger UI: http://localhost:8087/swagger-ui/index.html

> **Note on secrets:** the `JWT_SECRET_KEY` and AWS credentials in `docker-compose.yml` are
> throwaway values for local/demo use only. In a real deployment these come from a secret
> manager and are never committed.

The database is seeded with demo customers (local profile only). All demo users share the
password `Password123!`:

| Email | Account |
|-------|---------|
| `trevor.sesoko@example.co.za` | Main account |
| `mesut.ozil@example.co.za` | Main account |
| `donald.duck@example.co.za` | Savings plan |

---

## Running the Backend Locally (without Docker)

### Prerequisites
- Java 21
- PostgreSQL (or the Docker `postgres` service)
- An AWS SQS queue, or LocalStack for local development

### 1. Set environment variables

```bash
export DB_URL=jdbc:postgresql://localhost:5432/dispute_db
export DB_USERNAME=postgres
export DB_PASSWORD=yourpassword
export JWT_SECRET_KEY=404E635266556A586E3272357538782F413F4428472B4B6250655368566D5971
export JWT_EXPIRATION=86400000
export AWS_REGION=af-south-1
export AWS_ACCESS_KEY_ID=your-access-key
export AWS_SECRET_ACCESS_KEY=your-secret-key
export AWS_SQS_DISPUTE_QUEUE_URL=https://sqs.af-south-1.amazonaws.com/123456789/dispute-events.fifo
```

### 2. Run the app

```bash
./mvnw spring-boot:run
```

The `local` profile is the default, so no `--spring.profiles.active` flag is required. The API
is available at http://localhost:8087.

---

## Running the Frontend Locally (without Docker)

### Prerequisites
- Node.js 20+

```bash
cd frontend
npm install
npm run dev      # http://localhost:5173 (Vite proxies /api to http://localhost:8087)
```

Start the backend first. For a production build, run `npm run build` (output in `frontend/dist`).
To point a deployed build at a non-default API origin, set `VITE_API_BASE_URL`.

---

## Running Tests

### Backend

Tests run against an H2 in-memory database with Flyway migrations. No external services are
required - SQS is mocked automatically.

```bash
./mvnw test            # run all tests
./mvnw clean verify    # run tests + enforce the 80% JaCoCo coverage gate
```

### Frontend

```bash
cd frontend
npm run test           # run the Vitest unit suite
npm run test:run       # single run (CI mode)
```

---

## Dispute Flow

```
Customer submits dispute
        |
        v
  Ownership check ------- not owned --------- 403 Forbidden
        |
  60-day window check --- window closed ----- 400 Bad Request
        |
  Category eligibility -- not disputable ---- 400 Bad Request
        |
  Duplicate guard ------- duplicate --------- 400 Bad Request
        |
  Risk Score Engine
  (mandate type + reason -> 0-100)
        |
  Decision Matrix
  score <= 50 -> AUTO_APPROVE          -> status: AUTO_APPROVED (atomic refund applied)
  score >  50 -> MANUAL_INVESTIGATION  -> status: QUEUED_FOR_AGENT (published to SQS)
        |
        v
  201 Created (disputeReference, status, refundedAmount, message)
```

---

## Project Structure

```
.
├── Dockerfile                 Backend image (multi-stage, non-root)
├── docker-compose.yml         Full stack: frontend, app, postgres, localstack
├── frontend/                  React + TypeScript SPA
│   ├── Dockerfile             Frontend image (Vite build -> nginx)
│   ├── nginx.conf             SPA fallback + /api proxy to the backend
│   └── src/
│       ├── components/        Layout + reusable UI
│       ├── context/           Auth + Account providers
│       ├── pages/             Auth, dashboard, transactions, disputes, account
│       └── services/          Typed API client
└── src/main/java/com/transaction/dispute/
    ├── config/                Spring beans (Security, SQS, OpenAPI)
    ├── controller/            REST layer (Auth, Account, Transaction, Dispute)
    ├── engine/                RiskScoreEngine, DecisionMatrix
    ├── exception/             Domain exceptions + GlobalExceptionHandler
    ├── mapper/                MapStruct interfaces
    ├── model/                 DTOs (records), Entities, Enums
    ├── repository/            Spring Data JPA repositories
    ├── security/              JwtService, JwtAuthenticationFilter
    └── service/               Auth, Account, Transaction, Dispute, SqsPublisher
```


---

## Security Considerations

- **JWT signing key is required, never defaulted.** The app reads `application.security.jwt.secret-key`
  from configuration/environment (`JWT_SECRET_KEY`) with **no in-code fallback**, and validates at
  startup that it is at least 32 bytes (256 bits) for HMAC-SHA256. A missing or weak key fails fast.
  The value in `docker-compose.yml` is a throwaway demo key for local use only; a real deployment
  supplies it from a secret manager.
- **Passwords** are hashed with BCrypt; raw passwords and secrets are never logged.
- **Customer identity** is always derived from the JWT (`customerId` claim), never trusted from the
  request body or a client-supplied id. Every transaction and dispute lookup enforces ownership;
  a cross-customer dispute returns `403 Forbidden`.
- **Duplicate disputes** are prevented at the database level by a `UNIQUE` constraint on
  `disputes.transaction_id`, so even two concurrent submissions cannot both succeed - not merely an
  application-code check.
- **Account balance** carries a JPA `@Version` optimistic lock, so concurrent refund credits to the
  same account cannot silently overwrite each other.
- **CORS** is restricted to configured browser origins (`CORS_ALLOWED_ORIGINS`, default: the Vite dev
  server and the dockerised frontend). It is not open to all origins.
- **Actuator** exposes only `/actuator/health` over HTTP (with `show-details: never`); sensitive
  endpoints are not exposed.
- **Error responses** use a consistent `ErrorResponseDto`; stack traces and SQL errors are never
  returned to the client.
- **CSRF** is disabled, which is appropriate for a stateless, token-in-header (not cookie) API.

### Known limitations

- **AWS SQS** publishing is best-effort and non-fatal: if the queue is unreachable the dispute is
  still persisted and the customer still gets a response. There is no transactional outbox, so under
  a crash between DB commit and publish an event can be missed - acceptable for this assessment, and
  the consumer side would need to be idempotent (SQS gives at-least-once delivery, not exactly-once).
- **Risk weights and the decision threshold are illustrative defaults**, not a validated fraud model.
  With the current weights, STANDARD_EFT disputes always route to manual review.
- **No rate limiting** on the authentication endpoints yet.
- **Test schema** uses H2 (PostgreSQL compatibility mode) with a parallel migration set; it approximates
  but does not fully reproduce PostgreSQL behaviour. Dialect-specific issues are best caught with a
  Testcontainers PostgreSQL integration test (not yet added).
