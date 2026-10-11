# Local Testing Guide

How to run the Transaction Dispute Portal locally, mimic AWS SQS with LocalStack,
and exercise every endpoint in Postman â€” including the regulatory-dispute-eligibility
rules (60-day window, transaction-category eligibility, auto-approve balance refund).

---

## 1. How SQS is mimicked locally

The app publishes a dispute event to an AWS SQS FIFO queue after each dispute decision.
Locally we do NOT use real AWS â€” we use **LocalStack**, which emulates SQS inside a Docker
container.

- `docker compose up` starts: the app, PostgreSQL, and LocalStack.
- `localstack/init-sqs.sh` auto-creates the `dispute-events.fifo` queue on startup.
- `docker-compose.yml` points the app at LocalStack via `AWS_SQS_ENDPOINT_OVERRIDE=http://localstack:4566`,
  so `SqsPublisherService` sends messages to LocalStack instead of AWS.

Important: **SQS is not on the response critical path.** A dispute is persisted and the API
returns 201 even if publishing fails (the error is logged and `sqsMessageId` is left null).
So you confirm the SQS integration by INSPECTING THE QUEUE after a dispute â€” not by the HTTP
response. See section 5.

---

## 2. Start the stack

```bash
docker compose up --build
```

Starts Postgres, LocalStack (auto-creates `dispute-events.fifo`), and the app on
http://localhost:8087. Swagger UI: http://localhost:8087/swagger-ui/index.html

Check health:

```bash
docker compose ps
```

---

## 3. Seeded data (local profile only)

Three customers share password **`Password123!`**. Account types use Capitec product names
(MAIN_ACCOUNT, SAVINGS_PLAN).

| User | Email | Account Type |
|------|-------|--------------|
| Trevor Sesoko | trevor.sesoko@example.co.za | MAIN_ACCOUNT |
| Mesut Ozil | mesut.ozil@example.co.za | MAIN_ACCOUNT |
| Donald Duck | donald.duck@example.co.za | SAVINGS_PLAN |

Trevor's transactions are curated to exercise each regulatory path:

| Txn ID | Merchant | Mandate | Category | Age | Disputing it does... |
|--------|----------|---------|----------|-----|----------------------|
| 2000001 | VIRGIN ACTIVE GYM | DEBICHECK | GYM_MEMBERSHIP | 10d | **AUTO_APPROVE + balance refund** with INCORRECT_DATE (DEBICHECK 30 + 5 = 35) |
| 2000002 | ABSA HOME LOAN | STANDARD_EFT | LOAN | 7d | MANUAL_INVESTIGATION (no refund) |
| 2000003 | PICK N PAY | STANDARD_EFT | IN_STORE_RETAIL | 2d | **400 category not disputable** |
| 2000010 | TAKEALOT | STANDARD_EFT | ONLINE_RETAIL | 4d | MANUAL_INVESTIGATION (STANDARD_EFT 50 + reason > 50) |
| 2000011 | OLD FINANCE LOAN | STANDARD_EFT | LOAN | 90d | **400 window closed** (past 60 days) |

Note on mandate types: gyms use DEBICHECK (authenticated recurring mandate - correct for a
gym membership). Pick n Pay and Takealot are point-of-sale / online CARD purchases, not DebiCheck
mandates, so they use STANDARD_EFT (the least-wrong fit; the model has no CARD type). Loans use
STANDARD_EFT here.

Mesut (2000004-2000006) and Donald (2000007-2000009) have the same gym/loan/retail mix.

### The three regulatory rules

- **60-day window**: a dispute is only accepted if the transaction is <= 60 calendar days old
  (evaluated in Africa/Johannesburg). Older -> HTTP 400 "the 60-day dispute window has closed".
- **Category eligibility**: ONLINE_RETAIL, MOBILE_CONTRACT, GYM_MEMBERSHIP, LOAN, MUNICIPAL are
  disputable; P2P_TRANSFER and IN_STORE_RETAIL are not -> HTTP 400 "not eligible for dispute".
  Each transaction's `disputable` flag is exposed in GET /transactions so a UI can hide the action.
- **Auto-approve refund**: when a dispute auto-approves (risk score <= 50), the full transaction
  amount is credited back to the account balance immediately and atomically, and the response
  carries `refundedAmount`. Manual-investigation disputes are NOT refunded.

### Risk scoring (to predict the route)

Base: DEBICHECK 30, STANDARD_EFT / REGISTERED_MANDATE 50. Reason adds: UNAUTHORISED_MANDATE +20,
CANCELLED_CONTRACT / DUPLICATE_DEBIT +15, INCORRECT_AMOUNT +10, INCORRECT_DATE +5.
Score <= 50 -> AUTO_APPROVE (refund); > 50 -> MANUAL_INVESTIGATION (no refund).

---

## 4. Run the Postman collection

Import `postman/Dispute-Portal-Local.postman_collection.json`. Logins auto-save each JWT into
`trevorToken` / `mesutToken` / `donaldToken`.

Suggested order:

1. Auth > Login - Trevor / Mesut / Donald
2. Transactions > Get Transactions - Trevor â€” asserts the `disputable` flag (Pick n Pay = false, Takealot = true)
3. Disputes > AUTO_APPROVE + REFUND (Takealot 2000010) â€” 201, AUTO_APPROVED, `refundedAmount` populated
4. Disputes > MANUAL_INVESTIGATION no refund (Loan 2000002) â€” 201, QUEUED_FOR_AGENT, `refundedAmount` null
5. Disputes > CATEGORY NOT DISPUTABLE 400 (Pick n Pay 2000003)
6. Disputes > WINDOW CLOSED 400 (90-day-old 2000011)
7. Disputes > DUPLICATE 400 (re-run Takealot 2000010)
8. Disputes > FORBIDDEN 403 (Mesut disputes Trevor's 2000001)
9. Disputes > NOT FOUND 404 (missing txn)
10. Disputes > VALIDATION 400 (missing fields)
11. Disputes > Get Dispute History - Trevor

Each request carries assertions, visible in Postman's Test Results tab.

### Verifying the refund balance change

After the auto-approve dispute on the gym transaction (2000001, amount R450.00), Trevor's
MAIN_ACCOUNT balance (seeded at R25 000) should increase by R450.00. Confirm via the DB:

```bash
docker exec dispute-postgres psql -U dispute -d dispute_db \
  -c "SELECT a.account_number, a.balance FROM accounts a JOIN customers c ON c.customer_id = a.customer_id WHERE c.email = 'trevor.sesoko@example.co.za';"
```

---

## 5. Verifying the AWS SQS integration (LocalStack)

After submitting an eligible dispute (step 3 or 4), inspect the LocalStack queue:

```bash
# List queues (confirms dispute-events.fifo exists)
docker exec dispute-localstack awslocal sqs list-queues

# Receive messages from the dispute queue
docker exec dispute-localstack awslocal sqs receive-message \
  --queue-url http://localhost:4566/000000000000/dispute-events.fifo \
  --max-number-of-messages 10
```

Expected message body (non-PII identifiers only):

```json
{
  "disputeReference": "DSP-XXXXXXXX",
  "transactionId": 2000001,
  "reason": "INCORRECT_DATE",
  "riskScore": 35,
  "decisionRoute": "AUTO_APPROVE",
  "status": "AUTO_APPROVED"
}
```

Confirm the SQS message id was persisted on the dispute:

```bash
docker exec dispute-postgres psql -U dispute -d dispute_db \
  -c "SELECT dispute_reference, decision_route, status, refunded_amount, sqs_message_id FROM disputes ORDER BY created_at DESC LIMIT 5;"
```

A non-null `sqs_message_id` means publishing succeeded. Note that blocked disputes
(category/window/404) produce NO queue message and NO disputes row â€” the guards run before
anything is persisted or published.

### Running without Docker/LocalStack

`./mvnw spring-boot:run -Dspring-boot.run.profiles=local` against your own Postgres but with
no reachable SQS still works for API testing: the publish fails silently (logged, `sqs_message_id`
stays null) and every endpoint still returns the correct status. You just have no queue to inspect.

---

## 6. Resetting the data

```bash
docker compose down -v   # wipe the DB volume so the seed re-runs fresh
docker compose up --build
```