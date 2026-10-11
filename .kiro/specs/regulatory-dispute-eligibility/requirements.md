# Requirements Document

## Introduction

This feature extends the existing dispute submission flow of the South African Transaction Dispute Portal with three interlocking regulatory and business rules. All three rules converge on the single entry point `DisputeService.submitDispute` and share a new persisted transaction-category model.

1. **60-Calendar-Day Dispute Window** — a PASA-driven rule (effective 13 April 2026) restricting disputes to within 60 calendar days of the transaction date, evaluated in the Africa/Johannesburg timezone.
2. **Transaction-Category Eligibility** — a Capitec-style rule where each transaction carries a category, and only disputable categories may be disputed; the disputable flag is exposed to clients so a frontend can hide the dispute action.
3. **Immediate Atomic Balance Refund** — for auto-approved, rule-cleared disputes, the system immediately and atomically credits the customer account balance with the full transaction amount, with idempotency guarantees and an audit trail.

The feature preserves all existing dispute behaviors (ownership, duplicate, not-found, successful routing, SQS publishing) and enforces every rule server-side. This is a backend-only change: no user interface is built. All messages, category lists, timezone, and the 60-day constant are centralized (in `AppConstants` or enum definitions) with zero hardcoding, and the 80% JaCoCo line-coverage gate must continue to pass on `mvn clean verify`.

## Glossary

- **Dispute_Service**: The server-side component (`DisputeService.submitDispute`) that validates, routes, and persists dispute submissions.
- **Transaction**: A persisted `TransactionEntity`, including `transactionDate` (an `OffsetDateTime`), `amount` (a `BigDecimal`), and `account`.
- **Account**: A persisted `AccountEntity`, holding the customer's `balance` (a `BigDecimal`).
- **Dispute**: A persisted `DisputeEntity` representing a submitted dispute.
- **Transaction_Category**: A new persisted enumerated attribute on `TransactionEntity` indicating the kind of transaction (for example `ONLINE_RETAIL`, `P2P_TRANSFER`).
- **Disputable_Flag**: A boolean property derived from the transaction category indicating whether a transaction may be disputed.
- **Dispute_Window**: The period of 60 calendar days from the transaction date, inclusive of day 60, during which a dispute is permitted.
- **SAST**: South African Standard Time, represented by the `Africa/Johannesburg` timezone.
- **Reference_Clock**: An injected or abstracted time source (a `java.time.Clock`) used to evaluate the current instant deterministically for testing.
- **Decision_Route**: The routing outcome produced by the decision engine, one of `AUTO_APPROVE` or `MANUAL_INVESTIGATION`.
- **Auto_Approved_Dispute**: A dispute whose `Decision_Route` is `AUTO_APPROVE`.
- **Error_Handler**: The `GlobalExceptionHandler` that converts exceptions into an `ErrorResponseDto` with the appropriate HTTP status.
- **App_Constants**: The `AppConstants` class holding centralized messages, defaults, and configuration values.
- **Refund**: An atomic credit to the `Account` balance equal to the full `Transaction` amount, recorded on the `Dispute`.

## Requirements

### Requirement 1: 60-Calendar-Day Dispute Window

**User Story:** As a dispute operations stakeholder, I want disputes restricted to within 60 calendar days of the transaction date, so that the portal complies with the PASA-driven regulatory dispute window.

#### Acceptance Criteria

1. WHEN a dispute is submitted and the elapsed time from the Transaction `transactionDate` to the current date in SAST is 60 calendar days or fewer, THE Dispute_Service SHALL treat the dispute as within the Dispute_Window and continue processing.
2. IF a dispute is submitted and the elapsed time from the Transaction `transactionDate` to the current date in SAST is 61 calendar days or more, THEN THE Dispute_Service SHALL reject the dispute with the dispute-window-closed error.
3. WHEN the current date in SAST is exactly 60 calendar days after the Transaction `transactionDate`, THE Dispute_Service SHALL treat the dispute as within the Dispute_Window.
4. WHEN the current date in SAST is exactly 61 calendar days after the Transaction `transactionDate`, THE Dispute_Service SHALL reject the dispute with the dispute-window-closed error.
5. THE Dispute_Service SHALL evaluate the elapsed calendar days using the Africa/Johannesburg timezone.
6. THE Dispute_Service SHALL read the Dispute_Window length of 60 days from a centralized constant in App_Constants.
7. THE Dispute_Service SHALL apply the Dispute_Window rule to transactions of mandate type STANDARD_EFT, DEBICHECK, and REGISTERED_MANDATE.

### Requirement 2: Deterministic Time Evaluation

**User Story:** As a developer, I want the current instant to be injected through an abstracted time source, so that the day-60 versus day-61 boundary can be asserted deterministically in automated tests.

#### Acceptance Criteria

1. THE Dispute_Service SHALL obtain the current instant from the Reference_Clock rather than from a direct system-clock call.
2. WHERE a test supplies a fixed Reference_Clock, THE Dispute_Service SHALL evaluate the Dispute_Window against that fixed instant.
3. THE Dispute_Service SHALL produce the same Dispute_Window decision for the same Transaction `transactionDate` and the same Reference_Clock instant on every evaluation.

### Requirement 3: Transaction-Category Model

**User Story:** As a data owner, I want each transaction to carry a persisted category, so that eligibility rules and client display can be driven by the category.

#### Acceptance Criteria

1. THE Transaction SHALL carry a Transaction_Category attribute persisted as a new column on the transactions table.
2. THE Transaction_Category SHALL support the values ONLINE_RETAIL, MOBILE_CONTRACT, GYM_MEMBERSHIP, LOAN, MUNICIPAL, P2P_TRANSFER, and IN_STORE_RETAIL.
3. THE Transaction_Category SHALL classify ONLINE_RETAIL, MOBILE_CONTRACT, GYM_MEMBERSHIP, LOAN, and MUNICIPAL as disputable.
4. THE Transaction_Category SHALL classify P2P_TRANSFER and IN_STORE_RETAIL as non-disputable.
5. THE Transaction_Category SHALL derive the Disputable_Flag from a centralized definition in App_Constants or the enum definition, with no inline category literals in service logic.
6. THE feature SHALL provide a Flyway migration adding the Transaction_Category column for the PostgreSQL production database.
7. THE feature SHALL provide a Flyway migration adding the Transaction_Category column for the H2 test database.
8. THE MandateType enumeration SHALL include REGISTERED_MANDATE in addition to the existing STANDARD_EFT and DEBICHECK values.

### Requirement 4: Transaction-Category Dispute Eligibility Enforcement

**User Story:** As a dispute operations stakeholder, I want disputes against non-disputable categories rejected server-side, so that ineligible transactions cannot be disputed even through a direct API call.

#### Acceptance Criteria

1. WHEN a dispute is submitted for a Transaction whose Transaction_Category is disputable, THE Dispute_Service SHALL continue processing the dispute.
2. IF a dispute is submitted for a Transaction whose Transaction_Category is non-disputable, THEN THE Dispute_Service SHALL reject the dispute with the category-not-disputable error.
3. THE Dispute_Service SHALL enforce the Transaction_Category eligibility rule for every dispute submission regardless of the request origin.

### Requirement 5: Expose Disputable Flag to Clients

**User Story:** As a frontend developer, I want each transaction response to include its disputable flag, so that the client can hide or disable the dispute action for ineligible transactions.

#### Acceptance Criteria

1. THE TransactionResponseDto SHALL include the Disputable_Flag for each transaction.
2. WHEN a transaction with a disputable Transaction_Category is returned, THE Dispute_Service SHALL set the Disputable_Flag to true in the TransactionResponseDto.
3. WHEN a transaction with a non-disputable Transaction_Category is returned, THE Dispute_Service SHALL set the Disputable_Flag to false in the TransactionResponseDto.

### Requirement 6: Immediate Atomic Balance Refund

**User Story:** As a customer, I want auto-approved disputes to credit my account balance immediately with the full transaction amount, so that I am reimbursed without waiting for back-office intervention.

#### Acceptance Criteria

1. WHEN a dispute is within the Dispute_Window AND the Transaction_Category is disputable AND the Decision_Route is AUTO_APPROVE, THE Dispute_Service SHALL credit the Account balance by the full Transaction amount.
2. WHILE a dispute routes to MANUAL_INVESTIGATION, THE Dispute_Service SHALL persist the dispute without crediting the Account balance.
3. THE Dispute_Service SHALL persist the Dispute record and credit the Account balance within a single atomic database transaction.
4. IF the Account balance credit fails, THEN THE Dispute_Service SHALL roll back the Dispute persistence so that no partial Refund is recorded.

### Requirement 7: Refund Idempotency

**User Story:** As a finance stakeholder, I want refunds to be idempotent, so that a retried or duplicate refund never double-credits a customer's balance.

#### Acceptance Criteria

1. IF a Refund has already been recorded for a Dispute, THEN THE Dispute_Service SHALL NOT credit the Account balance a second time for that Dispute.
2. WHEN the same Auto_Approved_Dispute Refund is processed more than once, THE Dispute_Service SHALL leave the Account balance equal to the balance after a single credit.
3. THE Dispute_Service SHALL determine whether a Refund has already occurred for a Dispute from the recorded Refund audit fields.

### Requirement 8: Refund Audit Trail

**User Story:** As an auditor, I want the refunded amount and refund timestamp recorded on each refunded dispute, so that I can verify what was refunded and when.

#### Acceptance Criteria

1. WHEN a Refund is executed for a Dispute, THE Dispute_Service SHALL record the refunded amount on the Dispute.
2. WHEN a Refund is executed for a Dispute, THE Dispute_Service SHALL record the refund timestamp on the Dispute.
3. THE Dispute SHALL carry the refunded-amount and refunded-at fields persisted as new columns on the disputes table.
4. THE feature SHALL provide Flyway migrations adding the refunded-amount and refunded-at columns for the PostgreSQL production database and the H2 test database.
5. WHILE a dispute is not refunded, THE Dispute_Service SHALL leave the refunded-amount and refunded-at fields unset.

### Requirement 9: Rejection Semantics for Blocked Disputes

**User Story:** As an API consumer, I want blocked disputes to return a clear 400 response with a distinct reason, so that I can understand why a dispute was not accepted.

#### Acceptance Criteria

1. WHEN the Dispute_Service rejects a dispute because the Dispute_Window has closed, THE Error_Handler SHALL return HTTP 400 Bad Request with an ErrorResponseDto.
2. WHEN the Dispute_Service rejects a dispute because the Transaction_Category is non-disputable, THE Error_Handler SHALL return HTTP 400 Bad Request with an ErrorResponseDto.
3. THE App_Constants SHALL define a distinct dispute-window-closed message and a distinct category-not-disputable message.
4. WHEN a dispute is rejected because the Dispute_Window has closed, THE Error_Handler SHALL return the dispute-window-closed message from App_Constants.
5. WHEN a dispute is rejected because the Transaction_Category is non-disputable, THE Error_Handler SHALL return the category-not-disputable message from App_Constants.
6. IF a dispute is rejected because the Dispute_Window has closed or because the Transaction_Category is non-disputable, THEN THE Dispute_Service SHALL NOT create a Dispute record for that submission.

### Requirement 10: Preservation of Existing Dispute Behavior

**User Story:** As a product owner, I want all existing dispute behaviors preserved, so that the new rules add enforcement without regressing current functionality.

#### Acceptance Criteria

1. IF the authenticated customer does not own the disputed Transaction, THEN THE Error_Handler SHALL return HTTP 403 Forbidden via the ownership check.
2. IF a Dispute already exists for the Transaction, THEN THE Error_Handler SHALL return HTTP 400 Bad Request for the duplicate dispute.
3. IF the Transaction or the customer is not found, THEN THE Error_Handler SHALL return HTTP 404 Not Found.
4. WHEN a dispute is eligible and successfully submitted, THE Dispute_Service SHALL return HTTP 201 Created with the routing outcome.
5. IF the SQS publish of a dispute event fails, THEN THE Dispute_Service SHALL complete the dispute submission without failing the request.

### Requirement 11: Non-Functional Quality Gates

**User Story:** As an engineering lead, I want coverage and configuration standards maintained, so that the feature meets the project's quality bar.

#### Acceptance Criteria

1. WHEN `mvn clean verify` is run, THE build SHALL pass the JaCoCo line-coverage gate of 80 percent.
2. THE feature SHALL centralize the Dispute_Window length, the SAST timezone, the category disputable definitions, and all user-facing messages in App_Constants or enum definitions, with no hardcoded literals in service logic.
