# Implementation Plan: Regulatory Dispute Eligibility

## Overview

Convert the feature design into a series of prompts for a code-generation LLM that will implement each step with incremental progress. Each task builds on the previous ones and ends with wiring the new logic into the existing `DisputeService.submitDispute` flow — no orphaned code.

The plan is ordered so the model and schema exist before the service logic that depends on them, and tests accompany each behavioral change:

1. Foundations (enum, constants, mandate value, jqwik dependency)
2. Persistence/model (entity columns, Flyway prod + H2 migrations, local seed)
3. Time abstraction (`Clock` bean + injection)
4. DTO + mapper (expose `disputable`, surface `refundedAmount`)
5. Core service logic (window guard, category guard, atomic idempotent refund)
6. Tests (jqwik Properties 1–9 + example/integration/boundary/rollback + preservation + smoke)
7. Final checkpoint (`mvn clean verify`, JaCoCo 80% gate)

Property-based tests use **jqwik** (test scope). Migration numbering follows the design: prod `V2`/`V3` in `db/migration`, H2 `V2`/`V3` in `db/migration-h2`, local seed stays `V2` in `db/seed` (Flyway tracks locations independently).

## Tasks

- [x] 1. Foundations: enums, constants, and test dependency
  - [x] 1.1 Add the `TransactionCategory` enum
    - Create `com.transaction.dispute.model.enums.TransactionCategory` with 7 constants: `ONLINE_RETAIL`, `MOBILE_CONTRACT`, `GYM_MEMBERSHIP`, `LOAN`, `MUNICIPAL` (disputable), and `P2P_TRANSFER`, `IN_STORE_RETAIL` (non-disputable)
    - Carry a `private final boolean disputable` on each constant and expose `isDisputable()` so category classification has a single source of truth (no inline literals in service logic)
    - _Requirements: 3.2, 3.3, 3.4, 3.5, 11.2_

  - [x] 1.2 Add `REGISTERED_MANDATE` to `MandateType`
    - Edit `com.transaction.dispute.model.enums.MandateType` to include `REGISTERED_MANDATE` alongside `STANDARD_EFT` and `DEBICHECK`
    - No `RiskScoreEngine` change needed — any non-`DEBICHECK` mandate already takes the existing scoring branch
    - _Requirements: 3.8_

  - [x] 1.3 Add centralized constants to `AppConstants`
    - In `com.transaction.dispute.common.AppConstants` add `DISPUTE_WINDOW_DAYS = 60`, `DISPUTE_TIMEZONE = "Africa/Johannesburg"`, and the two distinct, non-blank messages `MSG_DISPUTE_WINDOW_CLOSED` and `MSG_CATEGORY_NOT_DISPUTABLE`
    - _Requirements: 1.6, 9.3, 11.2_

  - [x] 1.4 Add jqwik as a test-scope dependency
    - Add the jqwik dependency with `<scope>test</scope>` to `pom.xml`
    - Do not alter the existing JaCoCo 80% line-coverage gate configuration
    - _Requirements: 11.1_

- [x] 2. Persistence model and migrations
  - [x] 2.1 Add the `category` column to `TransactionEntity`
    - Add `@Enumerated(EnumType.STRING) @Column(name = "category", nullable = false, length = 50) private TransactionCategory category;` to `TransactionEntity` with getter/setter
    - _Requirements: 3.1_

  - [x] 2.2 Add refund audit fields to `DisputeEntity`
    - Add nullable `@Column(name = "refunded_amount", precision = 19, scale = 2) BigDecimal refundedAmount` and `@Column(name = "refunded_at") OffsetDateTime refundedAt` with getters/setters; both remain unset while a dispute is not refunded
    - _Requirements: 8.3, 8.5_

  - [x] 2.3 Add prod Flyway migration for the transaction category column
    - Create `V2__add_transaction_category.sql` in `src/main/resources/db/migration`: `ALTER TABLE transactions ADD COLUMN category VARCHAR(50) NOT NULL DEFAULT 'ONLINE_RETAIL';` (DEFAULT backfills existing rows and keeps `ddl-auto: validate` happy)
    - _Requirements: 3.1, 3.6_

  - [x] 2.4 Add prod Flyway migration for the dispute refund audit columns
    - Create `V3__add_dispute_refund_audit.sql` in `src/main/resources/db/migration`: add `refunded_amount DECIMAL(19,2)` and `refunded_at TIMESTAMPTZ` to `disputes`
    - _Requirements: 8.3, 8.4_

  - [x] 2.5 Add H2 Flyway migration for the transaction category column
    - Create `V2__add_transaction_category.sql` in `src/test/resources/db/migration-h2`: `ALTER TABLE transactions ADD COLUMN category VARCHAR(50) NOT NULL DEFAULT 'ONLINE_RETAIL';`
    - _Requirements: 3.7_

  - [x] 2.6 Add H2 Flyway migration for the dispute refund audit columns
    - Create `V3__add_dispute_refund_audit.sql` in `src/test/resources/db/migration-h2`: add `refunded_amount DECIMAL(19,2)` and `refunded_at TIMESTAMP WITH TIME ZONE` to `disputes`
    - _Requirements: 8.4_

  - [x] 2.7 Update the local seed to carry explicit categories
    - Edit `src/main/resources/db/seed/V2__seed_local_data.sql` so every `INSERT INTO transactions (...)` includes `category`; map by merchant intent (gym → `GYM_MEMBERSHIP`, loan → `LOAN`, Pick n Pay → `IN_STORE_RETAIL`, online retail → `ONLINE_RETAIL`, etc.) so local startup validates cleanly against the NOT NULL column
    - _Requirements: 3.1, 11.2_

- [x] 3. Deterministic time abstraction
  - [x] 3.1 Add `TimeConfig` providing a SAST-anchored `Clock` bean
    - Create `com.transaction.dispute.config.TimeConfig` with `@Bean Clock clock()` returning `Clock.system(ZoneId.of(AppConstants.DISPUTE_TIMEZONE))`
    - _Requirements: 2.1_

  - [x] 3.2 Inject `Clock` into `DisputeService`
    - Add `private final Clock clock;` to `DisputeService` (constructor injection); use it everywhere the current instant/date is needed so no direct system-clock call remains
    - _Requirements: 2.1, 2.3_

- [x] 4. DTO and mapper changes
  - [x] 4.1 Expose the `disputable` flag on `TransactionResponseDto`
    - Add `boolean disputable` to `TransactionResponseDto`; in `TransactionMapper` add `@Mapping(target = "disputable", expression = "java(entity.getCategory().isDisputable())")`; update any manual `TransactionResponseDto` construction sites to pass the derived flag
    - _Requirements: 5.1, 5.2, 5.3_

  - [x] 4.2 Surface `refundedAmount` on `DisputeResponseDto`
    - Add nullable `BigDecimal refundedAmount` to `DisputeResponseDto`; thread `saved.getRefundedAmount()` / `entity.getRefundedAmount()` through the manual record construction in `DisputeService.submitDispute` and `getDisputeHistory`; keep `DisputeMapper` mapping consistent (field names match)
    - _Requirements: 8.1_

- [x] 5. Core service logic in `DisputeService.submitDispute`
  - [x] 5.1 Implement `assertWithinDisputeWindow`
    - Add `AccountRepository` dependency to `DisputeService`; implement the private guard computing `txDate = transactionDate.atZoneSameInstant(ZoneId.of(AppConstants.DISPUTE_TIMEZONE)).toLocalDate()`, `today = LocalDate.now(clock)`, `elapsed = ChronoUnit.DAYS.between(txDate, today)`; throw `BadRequestException(AppConstants.MSG_DISPUTE_WINDOW_CLOSED)` when `elapsed > AppConstants.DISPUTE_WINDOW_DAYS`; day 60 passes, day 61 fails; check never inspects mandate type
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.7, 2.2_

  - [x] 5.2 Implement `assertCategoryDisputable`
    - Add the private guard throwing `BadRequestException(AppConstants.MSG_CATEGORY_NOT_DISPUTABLE)` when `!transaction.getCategory().isDisputable()`; lives in the service so it runs for every submission regardless of origin
    - _Requirements: 4.1, 4.2, 4.3_

  - [x] 5.3 Wire the two guards into the ordered guard sequence
    - Insert `assertWithinDisputeWindow` (step 4) then `assertCategoryDisputable` (step 5) after the ownership check and before the duplicate guard, so a window/category rejection returns 400 via the existing `GlobalExceptionHandler.handleBadRequest` and no `DisputeEntity` is created
    - _Requirements: 9.1, 9.2, 9.4, 9.5, 9.6, 10.1, 10.2, 10.3_

  - [x] 5.4 Implement `applyRefundIfEligible` and invoke it after save
    - Implement the private method: when `route == DecisionRoute.AUTO_APPROVE` and `dispute.getRefundedAmount() == null`, credit `account.setBalance(account.getBalance().add(transaction.getAmount()))`, `accountRepository.save(account)`, set `refundedAmount = transaction.getAmount()` and `refundedAt = OffsetDateTime.now(clock)`; do nothing for `MANUAL_INVESTIGATION`; call it inside the existing single `@Transactional submitDispute` after the dispute is saved so the dispute save and balance credit commit or roll back together; keep SQS publish non-fatal and the 201 response intact
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 7.1, 7.2, 7.3, 8.1, 8.2, 8.5, 10.4, 10.5_

- [x] 6. Checkpoint - core implementation complete
  - Ensure all tests pass, ask the user if questions arise.

- [x] 7. Property-based tests (jqwik, min 100 tries each)
  - [x]* 7.1 Property 1 — window boundary and determinism
    - **Property 1: Dispute-window boundary and determinism** — generate `transactionDate` and `elapsedDays` (0–200), build `Clock.fixed(..., SAST)`, assert the window check accepts iff `elapsedDays <= 60` and that two evaluations of the same inputs agree
    - **Validates: Requirements 1.1, 1.2, 2.3**

  - [x]* 7.2 Property 2 — window decision independent of mandate type
    - **Property 2: Window decision is independent of mandate type** — for a generated date/clock, iterate `STANDARD_EFT`, `DEBICHECK`, `REGISTERED_MANDATE` and assert identical window decisions
    - **Validates: Requirements 1.7**

  - [x]* 7.3 Property 3 — category classification
    - **Property 3: Category disputable classification** — iterate all `TransactionCategory` values and assert `isDisputable()` matches the specification table
    - **Validates: Requirements 3.3, 3.4**

  - [x]* 7.4 Property 4 — category eligibility gate
    - **Property 4: Category eligibility gate** — with other guards satisfied, assert submission proceeds iff `category.isDisputable()`, else `BadRequestException(MSG_CATEGORY_NOT_DISPUTABLE)`
    - **Validates: Requirements 4.1, 4.2**

  - [x]* 7.5 Property 5 — blocked submissions create no dispute record
    - **Property 5: Blocked submissions create no dispute record** — for out-of-window dates or non-disputable categories, assert `disputeRepository.save` is never called / count unchanged
    - **Validates: Requirements 9.6**

  - [x]* 7.6 Property 6 — response disputable flag mirrors category
    - **Property 6: Response disputable flag mirrors category** — for a `TransactionEntity` with a random category assert `TransactionMapper.toDto(e).disputable() == e.getCategory().isDisputable()`
    - **Validates: Requirements 5.1, 5.2, 5.3**

  - [x]* 7.7 Property 7 — auto-approved refund credits balance and records audit
    - **Property 7: Auto-approved refund credits balance and records audit** — generate `startingBalance` and `amount`, process an eligible AUTO_APPROVE dispute, assert `newBalance == startingBalance + amount`, `refundedAmount == amount`, `refundedAt != null`
    - **Validates: Requirements 6.1, 8.1, 8.2**

  - [x]* 7.8 Property 8 — manual-investigation disputes are not refunded
    - **Property 8: Manual-investigation disputes are not refunded** — force MANUAL_INVESTIGATION and assert balance unchanged and `refundedAmount`/`refundedAt` null
    - **Validates: Requirements 6.2, 8.5**

  - [x]* 7.9 Property 9 — refund idempotence
    - **Property 9: Refund idempotence** — apply the refund step twice to the same dispute and assert the balance equals a single credit and `refundedAmount` is unchanged
    - **Validates: Requirements 7.1, 7.2, 7.3**

- [x] 8. Example, boundary, integration, and preservation tests
  - [x] 8.1 Update shared test fixtures for the new model
    - Update `TestPayloadFactory` and existing `DisputeServiceTest` fixtures so seeded/mock transactions carry a `category` and an in-window `transactionDate` under the fixed clock; add a `@TestConfiguration FixedClockConfig` providing `Clock.fixed(Instant.parse("2026-06-01T10:00:00Z"), ZoneId.of("Africa/Johannesburg"))`
    - _Requirements: 2.2_

  - [x]* 8.2 Day-60 / day-61 and zone boundary tests
    - Seed a transaction exactly 60 SAST-days before the fixed clock (accepted → routing/201) and exactly 61 days before (`BadRequestException(MSG_DISPUTE_WINDOW_CLOSED)`, no dispute persisted); add a UTC-vs-SAST day-crossing instant proving the SAST day drives the count; add a fixed-future-clock case proving the service reads the injected clock
    - _Requirements: 1.3, 1.4, 1.5, 2.1, 2.2_

  - [x]* 8.3 Category eligibility example and controller tests
    - Example tests: each disputable category proceeds; each non-disputable category returns 400 with `MSG_CATEGORY_NOT_DISPUTABLE` and no record; add a `MockMvc` test confirming a direct API call to dispute a non-disputable transaction returns 400
    - _Requirements: 4.2, 4.3, 9.2, 9.5_

  - [x]* 8.4 Refund credit, idempotency, and atomic rollback tests
    - H2 integration test reloading account/dispute to assert persisted balance and audit fields on AUTO_APPROVE; idempotency example re-invoking the refund path on an already-refunded dispute; rollback example with a spy/stub `AccountRepository` whose `save` throws, asserting no `DisputeEntity` row and unchanged balance
    - _Requirements: 6.1, 6.3, 6.4, 7.1, 7.2, 8.1, 8.2_

  - [x]* 8.5 Mapper disputable-flag example tests
    - Two example tests (one disputable, one non-disputable category) asserting `disputable` is true/false in the DTO
    - _Requirements: 5.1, 5.2, 5.3_

  - [x]* 8.6 Preserve existing dispute behavior
    - Keep/extend `DisputeServiceTest`: not-found 404, ownership 403, duplicate 400, eligible 201 with routing outcome, SQS publish failure non-fatal — passing unchanged aside from new required inputs (category, in-window date)
    - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.5_

  - [x]* 8.7 Constants, enum, and schema smoke tests
    - Assert `AppConstants.DISPUTE_WINDOW_DAYS == 60`, `DISPUTE_TIMEZONE == "Africa/Johannesburg"`, the two messages are distinct and non-blank; `TransactionCategory.values()` has the expected 7; `MandateType` contains `REGISTERED_MANDATE`; a `@SpringBootTest` context-loads test boots under `ddl-auto: validate` on H2, proving the migrations match the entity model
    - _Requirements: 3.1, 3.2, 3.6, 3.7, 3.8, 8.3, 8.4, 9.3_

- [x] 9. Final checkpoint - full build and coverage gate
  - Run `mvn clean verify`; confirm all unit, property, and integration tests pass and the JaCoCo 80% line-coverage gate holds. Ensure all tests pass, ask the user if questions arise.
  - _Requirements: 11.1, 11.2_

## Notes

- Tasks marked with `*` are optional (test sub-tasks) and can be skipped for a faster MVP, but they carry the property/example coverage that sustains the 80% JaCoCo gate.
- Each task references specific requirement sub-clauses for traceability; every one of the 11 requirements is covered by at least one task.
- The two new guards reuse `BadRequestException` → the existing `GlobalExceptionHandler.handleBadRequest`, so no new error-handler branch is introduced.
- The refund shares the existing single `@Transactional submitDispute` unit of work, giving atomicity and rollback for free.
- jqwik is the property-based testing library (test scope). Migration numbering follows the design (prod `V2`/`V3` in `db/migration`, H2 `V2`/`V3` in `db/migration-h2`, local seed stays `V2` in `db/seed`).

## Task Dependency Graph

Execution waves below group independent leaf sub-tasks that can run in parallel. Tasks writing to the same file are placed in separate waves (e.g. the four `DisputeService` tasks 3.2 → 5.1 → 5.2/5.3 → 5.4; the two `AppConstants`/enum/pom foundations in wave 0). Later waves depend on all earlier waves completing.

ASCII overview:

```
Wave 0: 1.1  1.2  1.3  1.4            (foundations: enums, constants, pom)
Wave 1: 2.1  2.2  2.3  2.4  2.5  2.6  2.7  3.1   (model + migrations + seed + Clock bean)
Wave 2: 3.2  4.1  4.2                 (inject Clock into service; DTO/mapper)
Wave 3: 5.1                           (window guard - DisputeService)
Wave 4: 5.2  5.3                      (category guard + wiring - DisputeService)
Wave 5: 5.4                           (atomic idempotent refund - DisputeService)
Wave 6: 7.3  7.6  8.1                 (pure-logic properties + test fixtures)
Wave 7: 7.1  7.2  7.4  7.5  7.7  7.8  7.9   (service/mapper properties)
Wave 8: 8.2  8.3  8.4  8.5  8.6  8.7  (examples, integration, preservation, smoke)
```

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2", "1.3", "1.4"] },
    { "id": 1, "tasks": ["2.1", "2.2", "2.3", "2.4", "2.5", "2.6", "2.7", "3.1"] },
    { "id": 2, "tasks": ["3.2", "4.1", "4.2"] },
    { "id": 3, "tasks": ["5.1"] },
    { "id": 4, "tasks": ["5.2", "5.3"] },
    { "id": 5, "tasks": ["5.4"] },
    { "id": 6, "tasks": ["7.3", "7.6", "8.1"] },
    { "id": 7, "tasks": ["7.1", "7.2", "7.4", "7.5", "7.7", "7.8", "7.9"] },
    { "id": 8, "tasks": ["8.2", "8.3", "8.4", "8.5", "8.6", "8.7"] }
  ]
}
```
