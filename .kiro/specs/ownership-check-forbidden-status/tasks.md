# Implementation Plan

- [x] 1. Write bug condition exploration test
  - **Property 1: Bug Condition** - Ownership Violation Returns 403 Forbidden
  - **CRITICAL**: This test MUST FAIL on unfixed code - failure confirms the bug exists
  - **DO NOT attempt to fix the test or the code when it fails**
  - **NOTE**: This test encodes the expected behavior - it will validate the fix when it passes after implementation
  - **GOAL**: Surface counterexamples that demonstrate the bug exists (ownership violation reported as 401 instead of 403)
  - **Scoped PBT Approach**: This is a deterministic bug. Scope the property to the concrete failing case: an authenticated customer disputing a transaction owned by a different, existing customer. Use multiple generated (customerId, transactionId) pairs where the transaction exists and is owned by someone else to strengthen the "for all" guarantee.
  - Test implementation details from Bug Condition in design (`isBugCondition`): `customerExists(customerId) AND transactionExists(transactionId) AND ownerCustomerId(transactionId) != customerId`
  - **Service-layer counterexample**: Register a second customer, then call `disputeService.submitDispute(otherCustomerId, requestForFirstCustomersTransaction)`. The test assertion should match the Expected Behavior Property from design: it should expect `ForbiddenException` with message `AppConstants.MSG_UNAUTHORIZED_TRANSACTION_ACCESS`.
  - **Web-layer counterexample (optional MockMvc)**: Same cross-customer scenario asserting HTTP 403 and `error="Forbidden"`.
  - Run test on UNFIXED code
  - **EXPECTED OUTCOME**: Test FAILS (this is correct - it proves the bug exists). On unfixed code the ownership check throws `UnauthorizedException` (→ HTTP 401), so expecting `ForbiddenException`/403 fails.
  - Document counterexamples found: "Cross-customer dispute surfaces as `UnauthorizedException` / HTTP 401 instead of `ForbiddenException` / HTTP 403" — confirms root cause (ownership check throws `UnauthorizedException`, handler maps it to 401, no 403 path exists).
  - Mark task complete when test is written, run, and failure is documented
  - _Requirements: 2.1, 2.2_

- [x] 2. Write preservation property tests (BEFORE implementing fix)
  - **Property 2: Preservation** - All Other Error Mappings and Success Paths Unchanged
  - **IMPORTANT**: Follow observation-first methodology — run UNFIXED code for non-bug-condition inputs, record outputs, then assert those outputs hold
  - Observe behavior on UNFIXED code for inputs where `isBugCondition` is false:
    - Duplicate dispute (transaction already disputed) → `BadRequestException` / HTTP 400 (`submitDispute_ShouldThrowBadRequestException_WhenDuplicateDisputeDetected`)
    - Non-existent transaction → `ResourceNotFoundException` / HTTP 404
    - Non-existent customer → `ResourceNotFoundException` / HTTP 404
    - Login/credential failure → `UnauthorizedException` / `BadCredentialsException` → HTTP 401 (handler untouched)
    - Valid dispute on an owned transaction → successful persistence + routing outcome, HTTP 201 (`submitDispute_ShouldPersistDisputeWithDecision_WhenTransactionIsEligible`, routing + SQS tests)
    - Every handled error returns the structured `ErrorResponseDto` shape (status, error, message, path, timestamp, validationErrors where applicable)
  - Write/confirm property-based and example tests capturing observed behavior patterns from Preservation Requirements. The existing `DisputeServiceTest` cases already encode these observations.
  - Property-based testing generates many non-bug inputs (owned transactions, missing resources, duplicates) for stronger guarantees
  - Run tests on UNFIXED code
  - **EXPECTED OUTCOME**: Tests PASS (confirms baseline behavior to preserve)
  - Mark task complete when tests are written/confirmed, run, and passing on unfixed code
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6_

- [x] 3. Fix for ownership violation returning 401 instead of 403

  - [x] 3.1 Add the ForbiddenException domain exception
    - Create `src/main/java/com/transaction/dispute/exception/ForbiddenException.java`
    - `public class ForbiddenException extends RuntimeException` with a single-arg `ForbiddenException(String message)` constructor calling `super(message)`
    - Mirror the exact shape of `UnauthorizedException`, `BadRequestException`, and `ResourceNotFoundException`
    - _Bug_Condition: isBugCondition(input) where ownerCustomerId(transactionId) != customerId (authenticated, transaction exists)_
    - _Expected_Behavior: expectedBehavior(result) → ForbiddenException mapped to HTTP 403 from design_
    - _Preservation: Preservation Requirements from design (no existing exception/handler altered)_
    - _Requirements: 2.1, 2.2_

  - [x] 3.2 Add the ForbiddenException handler to GlobalExceptionHandler
    - Add `@ExceptionHandler(ForbiddenException.class) handleForbidden(...)` to `GlobalExceptionHandler`
    - Log at error level consistent with other handlers: `log.error("Forbidden access [{}]: {}", request.getRequestURI(), ex.getMessage())`
    - Return `ResponseEntity.status(HttpStatus.FORBIDDEN)` with the four-arg `ErrorResponseDto` (`HttpStatus.FORBIDDEN.value()`=403, `HttpStatus.FORBIDDEN.getReasonPhrase()`="Forbidden", `ex.getMessage()`, `request.getRequestURI()`)
    - **Leave the existing `handleUnauthorized({UnauthorizedException.class, BadCredentialsException.class})` 401 handler untouched**
    - _Bug_Condition: isBugCondition(input) — ownership violation needs a 403 route_
    - _Expected_Behavior: expectedBehavior(result) → structured ErrorResponseDto with status=403, error="Forbidden", message=MSG_UNAUTHORIZED_TRANSACTION_ACCESS_
    - _Preservation: 401/404/400 handlers and ErrorResponseDto shape unchanged_
    - _Requirements: 2.1, 2.2, 3.6_

  - [x] 3.3 Redirect the ownership check in DisputeService to ForbiddenException
    - In `DisputeService.submitDispute`, change the ownership-check branch to `throw new ForbiddenException(AppConstants.MSG_UNAUTHORIZED_TRANSACTION_ACCESS)` instead of `UnauthorizedException`
    - Update the import from `com.transaction.dispute.exception.UnauthorizedException` to `com.transaction.dispute.exception.ForbiddenException` (the service uses `UnauthorizedException` nowhere else)
    - Reuse `AppConstants.MSG_UNAUTHORIZED_TRANSACTION_ACCESS` as-is; leave all other branches (404 not-found, 400 duplicate, success path) unchanged
    - _Bug_Condition: isBugCondition(input) where !transaction.getAccount().getUser().getCustomerId().equals(customerId)_
    - _Expected_Behavior: expectedBehavior(result) → throws ForbiddenException → HTTP 403 from design_
    - _Preservation: ResourceNotFoundException (404), BadRequestException (400), and success flow untouched_
    - _Requirements: 2.1, 2.2, 3.2, 3.3, 3.4, 3.5_

  - [x] 3.4 Verify bug condition exploration test now passes
    - **Property 1: Expected Behavior** - Ownership Violation Returns 403 Forbidden
    - **IMPORTANT**: Re-run the SAME test from task 1 - do NOT write a new test
    - The test from task 1 encodes the expected behavior; when it passes it confirms the expected behavior is satisfied
    - Update `DisputeServiceTest.submitDispute_ShouldThrowUnauthorizedException_WhenTransactionNotOwned` → rename to `submitDispute_ShouldThrowForbiddenException_WhenTransactionNotOwned`, expecting `ForbiddenException` and still asserting `message == AppConstants.MSG_UNAUTHORIZED_TRANSACTION_ACCESS`
    - Add handler verification that `ForbiddenException` maps to HTTP 403 with `error="Forbidden"` and the structured payload (GlobalExceptionHandler unit test or MockMvc slice via `DisputeControllerTest`)
    - Run the bug condition exploration test(s) from step 1
    - **EXPECTED OUTCOME**: Test PASSES (confirms bug is fixed — ownership violation now 403)
    - _Requirements: 2.1, 2.2_

  - [x] 3.5 Verify preservation tests still pass
    - **Property 2: Preservation** - All Other Error Mappings and Success Paths Unchanged
    - **IMPORTANT**: Re-run the SAME tests from task 2 - do NOT write new tests
    - Run preservation tests from step 2 (401 auth failures, 404 missing transaction/customer, 400 duplicate dispute, 201 success path, ErrorResponseDto shape)
    - **EXPECTED OUTCOME**: Tests PASS (confirms no regressions)
    - Confirm all tests still pass after fix (no regressions)
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6_

- [x] 4. Checkpoint - Ensure all tests pass
  - Run `mvn clean verify` to confirm compilation succeeds, all tests pass, and the 80% JaCoCo coverage gate holds (the new `ForbiddenException` and handler branch are covered by the updated ownership test and the handler/integration test)
  - Ensure all tests pass; ask the user if questions arise
  - _Requirements: 2.1, 2.2, 3.1, 3.2, 3.3, 3.4, 3.5, 3.6_

## Task Dependency Graph

```json
{
  "waves": [
    {
      "wave": 1,
      "tasks": ["1", "2"],
      "dependsOn": []
    },
    {
      "wave": 2,
      "tasks": ["3.1"],
      "dependsOn": ["1", "2"]
    },
    {
      "wave": 3,
      "tasks": ["3.2", "3.3"],
      "dependsOn": ["3.1"]
    },
    {
      "wave": 4,
      "tasks": ["3.4", "3.5"],
      "dependsOn": ["3.1", "3.2", "3.3"]
    },
    {
      "wave": 5,
      "tasks": ["4"],
      "dependsOn": ["3.4", "3.5"]
    }
  ]
}
```

```
Task 1 (Bug Condition exploration test — FAILS on unfixed code)
   │
   │   Task 2 (Preservation tests — PASS on unfixed code)
   │      │
   │      │   (Tasks 1 and 2 are independent and both precede the fix;
   │      │    both must be written and run against UNFIXED code first)
   ▼      ▼
Task 3 (Fix)
   ├── 3.1 Add ForbiddenException ──────────────┐
   │                                            │
   ├── 3.2 Add ForbiddenException handler ──────┤ (3.2 and 3.3 depend on 3.1)
   │                                            │
   ├── 3.3 Redirect ownership check ────────────┘
   │
   ├── 3.4 Verify Property 1 (re-run task 1 → now PASSES)   [depends on 3.1, 3.2, 3.3]
   │
   └── 3.5 Verify Property 2 (re-run task 2 → still PASSES) [depends on 3.1, 3.2, 3.3]
   │
   ▼
Task 4 (Checkpoint — mvn clean verify, all tests + 80% JaCoCo gate)
        [depends on 3.4 and 3.5]
```

**Ordering summary:**
- **1 → 3** and **2 → 3**: exploration and preservation tests are written and run on unfixed code before any fix.
- **3.1 → 3.2, 3.1 → 3.3**: the handler and the service redirect both reference the new `ForbiddenException`.
- **{3.1, 3.2, 3.3} → 3.4** and **{3.1, 3.2, 3.3} → 3.5**: verification re-runs the Property 1 and Property 2 tests after the fix.
- **{3.4, 3.5} → 4**: the checkpoint gates on both fix-checking and preservation-checking passing.
