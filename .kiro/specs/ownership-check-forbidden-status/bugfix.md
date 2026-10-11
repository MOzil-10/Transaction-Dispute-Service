# Bugfix Requirements Document

## Introduction

When an authenticated customer submits a dispute for a transaction that belongs to a different customer, the API returns HTTP 401 Unauthorized. This is semantically incorrect: the customer is fully authenticated (valid JWT, populated security context, resolved customerId). The failure is an authorization failure — the customer is not permitted to dispute a transaction they do not own — which should surface as HTTP 403 Forbidden.

The root cause is in `DisputeService.submitDispute`, where the ownership check throws `UnauthorizedException`. `GlobalExceptionHandler` maps that exception to HTTP 401. Because `UnauthorizedException` is also used (correctly) for login and credential failures, the two distinct meanings — "not authenticated" and "authenticated but not allowed" — are conflated.

The fix must return 403 Forbidden for the ownership violation while leaving every other error mapping unchanged, including the 401 response for login/credential failures. The decision to use 403 (rather than 404) aligns with the existing Swagger `@ApiResponses` on `DisputeController`, which already document 403 for "Transaction does not belong to authenticated customer".

## Bug Analysis

### Current Behavior (Defect)

When an authenticated customer disputes a transaction owned by another customer, the ownership check throws `UnauthorizedException`, which is mapped to a 401 response.

1.1 WHEN an authenticated customer submits a dispute for a transaction owned by a different customer THEN the system throws `UnauthorizedException` and returns HTTP 401 Unauthorized
1.2 WHEN the ownership check fails THEN the system reports an authentication failure (401) even though the customer is correctly authenticated, conflating authorization failure with authentication failure

### Expected Behavior (Correct)

The ownership violation must be reported as an authorization failure (403), not an authentication failure (401).

2.1 WHEN an authenticated customer submits a dispute for a transaction owned by a different customer THEN the system SHALL return HTTP 403 Forbidden
2.2 WHEN the ownership check fails THEN the system SHALL respond with the structured `ErrorResponseDto` payload carrying status 403, the Forbidden reason phrase, and the message `AppConstants.MSG_UNAUTHORIZED_TRANSACTION_ACCESS` ("You are not authorized to dispute this transaction")

### Unchanged Behavior (Regression Prevention)

All other error mappings and behaviors must remain exactly as they are today.

3.1 WHEN a login or credential verification fails THEN the system SHALL CONTINUE TO return HTTP 401 Unauthorized (via `UnauthorizedException` / `BadCredentialsException`)
3.2 WHEN a dispute references a transaction that does not exist THEN the system SHALL CONTINUE TO return HTTP 404 Not Found
3.3 WHEN a dispute references a customer that does not exist THEN the system SHALL CONTINUE TO return HTTP 404 Not Found
3.4 WHEN a dispute is submitted for a transaction that already has a dispute THEN the system SHALL CONTINUE TO return HTTP 400 Bad Request
3.5 WHEN an authenticated customer submits a valid dispute for a transaction they own THEN the system SHALL CONTINUE TO persist the dispute and return the routing outcome successfully
3.6 WHEN any handled error occurs THEN the system SHALL CONTINUE TO return the structured `ErrorResponseDto` shape (status, error, message, path, timestamp, and validationErrors where applicable)
