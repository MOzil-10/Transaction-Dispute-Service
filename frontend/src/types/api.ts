// TypeScript interfaces mirroring the Spring Boot backend DTOs exactly.
// Source of truth: com.transaction.dispute.model.dto.* and model.enums.*

export type MandateType = "STANDARD_EFT" | "DEBICHECK" | "REGISTERED_MANDATE";

export type TransactionCategory =
  | "ONLINE_RETAIL"
  | "MOBILE_CONTRACT"
  | "GYM_MEMBERSHIP"
  | "LOAN"
  | "MUNICIPAL"
  | "FOOD_DELIVERY"
  | "P2P_TRANSFER"
  | "IN_STORE_RETAIL"
  | "GAMBLING";

export type TransactionStatus = "PENDING" | "COMPLETED" | "FAILED" | "REVERSED";

export type DisputeReason =
  | "UNAUTHORISED_MANDATE"
  | "INCORRECT_AMOUNT"
  | "INCORRECT_DATE"
  | "CANCELLED_CONTRACT"
  | "DUPLICATE_DEBIT";

export type DecisionRoute = "AUTO_APPROVE" | "MANUAL_INVESTIGATION";

export type DisputeStatus =
  | "SUBMITTED"
  | "AUTO_APPROVED"
  | "QUEUED_FOR_AGENT"
  | "UNDER_REVIEW"
  | "RESOLVED"
  | "REJECTED";

// ---- Auth ----
export interface RegisterRequest {
  firstName: string;
  lastName: string;
  email: string;
  password: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface AuthResponse {
  token: string;
  tokenType: string; // "Bearer"
  customerId: number;
  email: string;
}

// ---- Transactions ----
export interface TransactionResponse {
  transactionId: number;
  reference: string;
  merchant: string;
  amount: number; // BigDecimal serialised as number
  currency: string; // "ZAR"
  mandateType: MandateType;
  category: TransactionCategory;
  status: TransactionStatus;
  transactionDate: string; // ISO-8601 OffsetDateTime
  disputable: boolean;
}

// ---- Disputes ----
export interface DisputeRequest {
  transactionId: number;
  reason: DisputeReason;
  description?: string | null;
}

export interface DisputeResponse {
  disputeId: number;
  disputeReference: string; // DSP-XXXXXXXX
  transactionId: number;
  amount: number;
  reason: DisputeReason;
  riskScore: number;
  decisionRoute: DecisionRoute;
  status: DisputeStatus;
  refundedAmount: number | null;
  message: string;
  createdAt: string; // ISO-8601 OffsetDateTime
}

// ---- Spring Data Page<T> (subset of fields we use) ----
export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number; // current page index (0-based)
  size: number;
  first: boolean;
  last: boolean;
  numberOfElements: number;
}

// ---- Structured error payload (ErrorResponseDto) ----
export interface FieldErrorDto {
  field: string;
  message: string;
}

export interface ErrorResponse {
  status: number;
  error: string;
  message: string;
  path: string;
  timestamp: string;
  validationErrors?: FieldErrorDto[] | null;
}


// ---- Account overview (AccountResponseDto) ----
export interface AccountResponse {
  customerId: number;
  firstName: string;
  lastName: string;
  email: string;
  accountNumber: string;
  accountType: string; // e.g. "MAIN_ACCOUNT", "SAVINGS_PLAN"
  balance: number; // BigDecimal serialised as number
  currency: string; // "ZAR"
}
