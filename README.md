# Transaction Dispute Portal

A production-ready REST API for the South African banking sector that lets customers
lodge and track debit order disputes for `STANDARD_EFT` and `DEBICHECK` payment
clearing mandates.

When a dispute is submitted, a risk score is calculated and routed through a decision
matrix — low-risk disputes are auto-approved and high-risk disputes are queued to a
back-office agent via AWS SQS.

---

## Tech Stack

| Layer | Technology |
|-------|------------|
| Language | Java 21 |
| Framework | Spring Boot 3.2 |
| Security | Spring Security 6 + JWT (JJWT 0.12) |
| Database | PostgreSQL (prod) · H2 in-memory (tests) |
| Migrations | Flyway |
| Messaging | AWS SQS (SDK v2) |
| Docs | SpringDoc OpenAPI / Swagger UI |
| Build | Maven Wrapper |

---

## API Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/api/v1/auth/register` | Public | Register a new customer |
| `POST` | `/api/v1/auth/login` | Public | Login and receive a JWT |
| `GET` | `/api/v1/transactions` | Bearer JWT | Paginated transaction history |
| `POST` | `/api/v1/disputes` | Bearer JWT | Submit a transaction dispute |
| `GET` | `/api/v1/disputes` | Bearer JWT | Paginated dispute history |

Swagger UI is available at `/swagger-ui/index.html` when the app is running.

---

## Running Locally

### Prerequisites

- Java 21
- PostgreSQL (or Docker)
- AWS SQS queue (or LocalStack for local development)

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
export AWS_SQS_DISPUTE_QUEUE_URL=https://sqs.af-south-1.amazonaws.com/123456789/dispute-events
```

### 2. Run the app

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

The API will be available at `http://localhost:8087`.

---

## Running with Docker Compose (recommended)

The quickest way to run the full stack. This starts three containers:

- **app** — the Spring Boot API (port 8087)
- **postgres** — PostgreSQL 16 database
- **localstack** — emulated AWS SQS (a `dispute-events.fifo` queue is created automatically on startup)

No AWS account, local PostgreSQL, or extra configuration is required.

```bash
# Build and start the whole stack
docker compose up --build

# Stop and remove containers (keeps the database volume)
docker compose down

# Stop and remove containers AND the database volume
docker compose down -v
```

Once started, the API is available at `http://localhost:8087` and Swagger UI at
`http://localhost:8087/swagger-ui/index.html`.

---

## Running with Docker (single container)

If you want to run only the app container against your own PostgreSQL and SQS:

```bash
docker build -t dispute-portal:latest .

docker run -p 8087:8087 \
  -e DB_URL=jdbc:postgresql://host.docker.internal:5432/dispute_db \
  -e DB_USERNAME=postgres \
  -e DB_PASSWORD=yourpassword \
  -e JWT_SECRET_KEY=404E635266556A586E3272357538782F413F4428472B4B6250655368566D5971 \
  -e AWS_REGION=af-south-1 \
  -e AWS_ACCESS_KEY_ID=your-access-key \
  -e AWS_SECRET_ACCESS_KEY=your-secret-key \
  -e AWS_SQS_DISPUTE_QUEUE_URL=https://sqs.af-south-1.amazonaws.com/123456789/dispute-events.fifo \
  dispute-portal:latest
```

---

## Running Tests

Tests run against an H2 in-memory database with Flyway migrations. No external
services are required — SQS is mocked automatically.

```bash
# Run all tests
./mvnw test

# Run tests + enforce 80% JaCoCo coverage gate
./mvnw clean verify
```

---

## Dispute Flow

```
Customer submits dispute
        │
        ▼
  Ownership check ──── not owned ──── 403 Unauthorized
        │
  Duplicate guard ─── duplicate ───── 400 Bad Request
        │
  Risk Score Engine
  (mandate type + reason → 0-100)
        │
  Decision Matrix
  score ≤ 50 → AUTO_APPROVE     → status: AUTO_APPROVED
  score > 50 → MANUAL_INVESTIGATION → status: QUEUED_FOR_AGENT
        │
  Persist dispute + publish to AWS SQS
        │
        ▼
  201 Created (disputeReference, status, message)
```

---

## Project Structure

```
src/main/java/com/transaction/dispute/
├── config/          Spring beans (Security, SQS, OpenAPI)
├── controller/      REST layer (Auth, Transaction, Dispute)
├── engine/          RiskScoreEngine, DecisionMatrix
├── exception/       Domain exceptions + GlobalExceptionHandler
├── mapper/          MapStruct interfaces
├── model/           DTOs (records), Entities, Enums
├── repository/      Spring Data JPA repositories
├── security/        JwtService, JwtAuthenticationFilter
└── service/         AuthService, TransactionService, DisputeService, SqsPublisherService
```

