# Ownership Check Forbidden Status Bugfix Design

## Overview

When an authenticated customer submits a dispute for a transaction owned by a different customer, `DisputeService.submitDispute` throws `UnauthorizedException`. `GlobalExceptionHandler` maps that exception to HTTP 401 Unauthorized. This is semantically wrong: the caller is fully authenticated (valid JWT, resolved `customerId`), so the failure is an authorization failure — the caller is not permitted to act on a resource they do not own — and must surface as HTTP 403 Forbidden.

The root problem is that `UnauthorizedException` carries two conflated meanings in the codebase: "not authenticated" (login/credential failures, correctly 401) and "authenticated but not allowed" (the ownership violation, incorrectly 401). The fix introduces a dedicated `ForbiddenException` for the authorization case and maps it to 403, leaving the 401 path for genuine authentication failures untouched.

The strategy is minimal and additive:
1. Add a new `ForbiddenException` domain exception following the exact shape of the existing exception classes.
2. Add a new `@ExceptionHandler(ForbiddenException.class)` in `GlobalExceptionHandler` that returns a structured `ErrorResponseDto` with HTTP 403.
3. Change only the ownership check in `DisputeService.submitDispute` to throw `ForbiddenException` instead of `UnauthorizedException`, reusing the same message constant.

The 403 choice (over 404) aligns with the existing Swagger `@ApiResponses` on `DisputeController`, which already document 403 for "Transaction does not belong to authenticated customer".

## Glossary

- **Bug_Condition (C)**: The condition that triggers the bug — an authenticated customer submits a dispute for a transaction that exists but is owned by a different customer, causing an ownership-violation failure to be reported as HTTP 401 instead of HTTP 403.
- **Property (P)**: The desired behavior for the bug condition — the ownership violation is reported as HTTP 403 Forbidden with the structured `ErrorResponseDto` payload and the message `AppConstants.MSG_UNAUTHORIZED_TRANSACTION_ACCESS`.
- **Preservation**: All other error mappings and success paths that must remain unchanged — 401 for login/credential failures, 404 for missing transaction/customer, 400 for duplicate disputes, and the successful dispute flow for owned transactions.
- **submitDispute**: The method in `DisputeService` (`src/main/java/com/transaction/dispute/service/DisputeService.java`) that validates ownership, scores risk, routes the decision, and persists the dispute.
- **UnauthorizedException**: The existing exception in `com.transaction.dispute.exception` mapped to HTTP 401, used for authentication/credential failures. Remains unchanged.
- **ForbiddenException**: The new exception in `com.transaction.dispute.exception` to be mapped to HTTP 403, used for authorization (ownership) failures.
- **ownership condition**: `transaction.getAccount().getUser().getCustomerId().equals(customerId)` — true when the authenticated customer owns the transaction.

## Bug Details

### Bug Condition

The bug manifests when an authenticated customer submits a dispute for a transaction that exists in the system but belongs to a different customer. The ownership guard in `submitDispute` throws `UnauthorizedException`, which `GlobalExceptionHandler` maps to HTTP 401. The response is therefore classified as an authentication failure even though the customer is correctly authenticated — the correct classification is an authorization failure (HTTP 403).

**Formal Specification:**
```
FUNCTION isBugCondition(input)
  INPUT: input of type DisputeSubmission { customerId, transactionId }
  OUTPUT: boolean

  // Customer and transaction both resolve (no 404), but ownership fails
  RETURN customerExists(input.customerId)
         AND transactionExists(input.transactionId)
         AND ownerCustomerId(input.transactionId) != input.customerId
         AND httpStatusReturned(input) != 403   // currently 401, should be 403
END FUNCTION
```

### Examples

- **Cross-customer dispute (primary case)**: Customer A owns transaction `TXN-123`. Customer B (authenticated) submits a dispute for `TXN-123`. Expected: HTTP 403 Forbidden with message "You are not authorized to dispute this transaction". Actual (defect): HTTP 401 Unauthorized.
- **Structured payload**: On the ownership violation, the response body should be an `ErrorResponseDto` with `status=403`, `error="Forbidden"`, `message=AppConstants.MSG_UNAUTHORIZED_TRANSACTION_ACCESS`, and the request `path`. Actual (defect): `status=401`, `error="Unauthorized"`.
- **Edge — self-owned transaction**: Customer A disputes their own transaction `TXN-123`. This is NOT the bug condition (ownership holds); the dispute proceeds normally. Must remain unchanged.
- **Edge — non-existent transaction**: Customer B disputes `TXN-999` which does not exist. This is NOT the bug condition (resolves to 404 before the ownership check). Must remain unchanged.

## Expected Behavior

### Preservation Requirements

**Unchanged Behaviors:**
- Login and credential verification failures (`UnauthorizedException` / `BadCredentialsException`) must continue to return HTTP 401 Unauthorized via the existing handler.
- A dispute referencing a non-existent transaction must continue to return HTTP 404 Not Found.
- A dispute referencing a non-existent customer must continue to return HTTP 404 Not Found.
- A duplicate dispute (transaction already disputed) must continue to return HTTP 400 Bad Request.
- A valid dispute for a transaction the authenticated customer owns must continue to persist the dispute and return the routing outcome successfully.
- Every handled error must continue to return the structured `ErrorResponseDto` shape (status, error, message, path, timestamp, and validationErrors where applicable).

**Scope:**
All inputs that do NOT satisfy the bug condition (authenticated customer, transaction exists, owner != customerId) must be completely unaffected by this fix. This includes:
- Authentication/credential failures (still 401)
- Missing transaction or customer (still 404)
- Duplicate dispute attempts (still 400)
- Successful disputes for owned transactions (still 201 with routing outcome)

**Note:** The actual expected correct behavior for the bug condition is defined in the Correctness Properties section (Property 1).

## Hypothesized Root Cause

Based on the bug description and code inspection, the cause is confirmed rather than hypothesized:

1. **Semantic conflation in exception type**: `DisputeService.submitDispute` throws `UnauthorizedException` for the ownership violation. `UnauthorizedException` is simultaneously the correct type for authentication/credential failures, so a single exception type carries two distinct HTTP semantics.
   - Ownership check: `if (!transaction.getAccount().getUser().getCustomerId().equals(customerId)) throw new UnauthorizedException(...)`

2. **Handler maps to 401**: `GlobalExceptionHandler.handleUnauthorized` handles `{UnauthorizedException.class, BadCredentialsException.class}` and returns `HttpStatus.UNAUTHORIZED` (401), so the authorization failure inherits 401.

3. **No dedicated authorization exception**: The exception package has `ResourceNotFoundException` (404), `BadRequestException` (400), and `UnauthorizedException` (401), but no `ForbiddenException` (403), so there is no existing route to a 403 response.

The fix is to introduce the missing `ForbiddenException` + handler and redirect only the ownership check to it.

## Correctness Properties

Property 1: Bug Condition - Ownership Violation Returns 403 Forbidden

_For any_ input where the bug condition holds (authenticated customer exists, the transaction exists, and the transaction owner's `customerId` does not equal the authenticated `customerId`), the fixed `submitDispute` SHALL throw `ForbiddenException`, which `GlobalExceptionHandler` SHALL map to an HTTP 403 Forbidden response carrying a structured `ErrorResponseDto` with `status=403`, `error="Forbidden"`, and `message=AppConstants.MSG_UNAUTHORIZED_TRANSACTION_ACCESS`.

**Validates: Requirements 2.1, 2.2**

Property 2: Preservation - All Other Error Mappings and Success Paths Unchanged

_For any_ input where the bug condition does NOT hold (authentication/credential failures, missing transaction, missing customer, duplicate dispute, or a valid dispute on an owned transaction), the fixed code SHALL produce exactly the same result as the original code — preserving the 401 mapping for `UnauthorizedException`/`BadCredentialsException`, the 404 mapping for `ResourceNotFoundException`, the 400 mapping for `BadRequestException`, the successful 201 dispute flow, and the structured `ErrorResponseDto` shape for all handled errors.

**Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.6**

## Fix Implementation

### Changes Required

Assuming our root cause analysis is correct, three changes are required, each following existing project conventions exactly.

**File 1 (new)**: `src/main/java/com/transaction/dispute/exception/ForbiddenException.java`

**Change**: Add a new domain exception that mirrors the shape of `UnauthorizedException`, `BadRequestException`, and `ResourceNotFoundException`.
```java
package com.transaction.dispute.exception;

public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
```

**File 2**: `src/main/java/com/transaction/dispute/exception/GlobalExceptionHandler.java`

**Change**: Add a new `@ExceptionHandler(ForbiddenException.class)` method that logs at error level (consistent with the other handlers) and returns a structured `ErrorResponseDto` with HTTP 403, using the four-argument `ErrorResponseDto` constructor and `HttpStatus.FORBIDDEN.getReasonPhrase()` for the `error` field.
```java
@ExceptionHandler(ForbiddenException.class)
public ResponseEntity<ErrorResponseDto> handleForbidden(ForbiddenException ex, HttpServletRequest request) {
    log.error("Forbidden access [{}]: {}", request.getRequestURI(), ex.getMessage());
    var error = new ErrorResponseDto(
            HttpStatus.FORBIDDEN.value(),
            HttpStatus.FORBIDDEN.getReasonPhrase(),
            ex.getMessage(),
            request.getRequestURI()
    );
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
}
```
The existing `handleUnauthorized({UnauthorizedException.class, BadCredentialsException.class})` method is left untouched.

**File 3**: `src/main/java/com/transaction/dispute/service/DisputeService.java`

**Change**: In `submitDispute`, change the ownership-check branch to throw `ForbiddenException` instead of `UnauthorizedException`, reusing `AppConstants.MSG_UNAUTHORIZED_TRANSACTION_ACCESS`. Update the import from `UnauthorizedException` to `ForbiddenException` (the service does not use `UnauthorizedException` anywhere else).
```java
if (!transaction.getAccount().getUser().getCustomerId().equals(customerId)) {
    log.error("Unauthorized dispute attempt — customerId={} does not own transactionId={}", customerId, request.transactionId());
    throw new ForbiddenException(AppConstants.MSG_UNAUTHORIZED_TRANSACTION_ACCESS);
}
```

**Not changed**:
- `UnauthorizedException` class and its 401 handler (still used for login/credential failures).
- `AppConstants.MSG_UNAUTHORIZED_TRANSACTION_ACCESS` (reused as-is).
- `DisputeController` Swagger annotations (already document 403).
- `ErrorResponseDto` (its existing four-argument constructor is reused).

## Testing Strategy

### Validation Approach

The testing strategy follows a two-phase approach: first, surface a counterexample that demonstrates the bug on unfixed code (the ownership violation returning 401), then verify the fix returns 403 for the bug condition and preserves every other mapping and the success path. The existing `DisputeServiceTest` suite already exercises the service layer and must continue to pass, with the ownership test updated to the new expectation.

### Exploratory Bug Condition Checking

**Goal**: Surface the counterexample that demonstrates the bug BEFORE implementing the fix, and confirm the root cause (ownership violation mapped to 401 instead of 403). If refuted, re-hypothesize.

**Test Plan**: Register two customers, create a transaction owned by the first, and have the second submit a dispute for it. Assert the authorization outcome. On the UNFIXED code the ownership check throws `UnauthorizedException` (→ 401); the existing `DisputeServiceTest.submitDispute_ShouldThrowUnauthorizedException_WhenTransactionNotOwned` captures exactly this. At the service layer the counterexample is the thrown exception type (`UnauthorizedException`); at the web layer it is the HTTP status (401).

**Test Cases**:
1. **Cross-customer dispute (service layer)**: Second customer disputes the first customer's transaction (will throw `UnauthorizedException` on unfixed code; should throw `ForbiddenException` after fix).
2. **Cross-customer dispute (web layer, optional MockMvc)**: Same scenario asserting HTTP 403 and `error="Forbidden"` (returns 401 on unfixed code).
3. **Edge — out-of-scope transaction**: Dispute a non-existent transaction (returns 404, not part of the bug condition).

**Expected Counterexamples**:
- Ownership violation surfaces as `UnauthorizedException` / HTTP 401.
- Confirmed cause: the ownership check throws `UnauthorizedException`, which the handler maps to 401; no `ForbiddenException`/403 path exists.

### Fix Checking

**Goal**: Verify that for all inputs where the bug condition holds, the fixed function produces the expected behavior (403 Forbidden with the structured payload and the authorization message).

**Pseudocode:**
```
FOR ALL input WHERE isBugCondition(input) DO
  // service layer
  ASSERT throws(submitDispute_fixed(input), ForbiddenException)
         AND message == MSG_UNAUTHORIZED_TRANSACTION_ACCESS
  // web layer
  result := httpResponse(input)
  ASSERT result.status == 403
         AND result.error == "Forbidden"
         AND result.message == MSG_UNAUTHORIZED_TRANSACTION_ACCESS
END FOR
```

### Preservation Checking

**Goal**: Verify that for all inputs where the bug condition does NOT hold, the fixed function produces the same result as the original function.

**Pseudocode:**
```
FOR ALL input WHERE NOT isBugCondition(input) DO
  ASSERT submitDispute_original(input) == submitDispute_fixed(input)
  ASSERT httpStatus_original(input) == httpStatus_fixed(input)
END FOR
```

**Testing Approach**: Property-based testing is recommended for preservation checking because:
- It generates many test cases automatically across the input domain.
- It catches edge cases that manual unit tests might miss.
- It provides strong guarantees that behavior is unchanged for all non-buggy inputs.

**Test Plan**: Observe behavior on UNFIXED code for the non-bug inputs (401 auth failures, 404 missing transaction/customer, 400 duplicate dispute, successful owned-transaction dispute), then assert the same outcomes hold after the fix. The existing `DisputeServiceTest` cases already encode these observations and must stay green.

**Test Cases**:
1. **Auth/credential preservation**: Login/credential failure still maps to 401 (handler for `UnauthorizedException`/`BadCredentialsException` untouched).
2. **Missing transaction preservation**: `submitDispute_ShouldThrow...` for a non-existent transaction still yields 404 (`ResourceNotFoundException`).
3. **Missing customer preservation**: Non-existent customer still yields 404.
4. **Duplicate dispute preservation**: `submitDispute_ShouldThrowBadRequestException_WhenDuplicateDisputeDetected` still yields 400.
5. **Success path preservation**: `submitDispute_ShouldPersistDisputeWithDecision_WhenTransactionIsEligible` and the routing/SQS tests still pass unchanged.
6. **Error shape preservation**: Any handled error still returns the `ErrorResponseDto` structure.

### Unit Tests

- Update `DisputeServiceTest.submitDispute_ShouldThrowUnauthorizedException_WhenTransactionNotOwned` to expect `ForbiddenException` (rename to reflect the new semantics, e.g. `submitDispute_ShouldThrowForbiddenException_WhenTransactionNotOwned`), still asserting `message == AppConstants.MSG_UNAUTHORIZED_TRANSACTION_ACCESS`.
- Keep the duplicate-dispute (400), not-owned vs not-found distinction, and success-path tests unchanged.
- Add a `GlobalExceptionHandler` unit test (or MockMvc slice) asserting `ForbiddenException` maps to HTTP 403 with `error="Forbidden"` and the structured payload.

### Property-Based Tests

- Generate random (customerId, transactionId) pairs where the transaction exists and is owned by a different customer, and verify the fixed service consistently throws `ForbiddenException` with the authorization message (Property 1).
- Generate random non-bug inputs (owned transactions, missing resources, duplicates) and verify outcomes are identical before and after the fix (Property 2).

### Integration Tests

- Full MockMvc flow: authenticated customer B posts a dispute for customer A's transaction → assert HTTP 403 and `ErrorResponseDto` body with `status=403`, `error="Forbidden"`, correct `message` and `path`.
- Context preservation: authenticated customer disputes their own transaction → HTTP 201 with routing outcome; non-existent transaction → 404; duplicate → 400; these remain unchanged.
- Coverage gate: `mvn verify` must pass the 80% JaCoCo coverage threshold; the new `ForbiddenException` and handler branch are covered by the updated ownership test and the handler/integration test.
