import type {
  TransactionCategory,
  DisputeReason,
  DisputeStatus,
  DecisionRoute,
  TransactionStatus,
} from "@/types/api";

// Regulatory dispute window (PASA, effective 13 April 2026).
export const DISPUTE_WINDOW_DAYS = 60;

export const DISPUTE_REASON_OPTIONS: {
  value: DisputeReason;
  label: string;
  hint: string;
}[] = [
  {
    value: "UNAUTHORISED_MANDATE",
    label: "I don't recognise or did not authorise this debit",
    hint: "The debit was taken without a valid mandate.",
  },
  {
    value: "INCORRECT_AMOUNT",
    label: "The amount debited is incorrect",
    hint: "A different amount was taken than agreed.",
  },
  {
    value: "CANCELLED_CONTRACT",
    label: "I cancelled the contract or debit mandate",
    hint: "The debit continued after cancellation.",
  },
  {
    value: "DUPLICATE_DEBIT",
    label: "I was debited more than once",
    hint: "The same transaction was taken twice.",
  },
  {
    value: "INCORRECT_DATE",
    label: "The debit was taken on the wrong date",
    hint: "Processed outside the agreed collection date.",
  },
];

export const DISPUTE_REASON_LABELS: Record<DisputeReason, string> = {
  UNAUTHORISED_MANDATE: "Unauthorised mandate",
  INCORRECT_AMOUNT: "Incorrect amount",
  INCORRECT_DATE: "Incorrect date",
  CANCELLED_CONTRACT: "Cancelled contract",
  DUPLICATE_DEBIT: "Duplicate debit",
};

export type BadgeTone = "green" | "amber" | "red" | "blue" | "slate";

export const DISPUTE_STATUS_META: Record<
  DisputeStatus,
  { label: string; tone: BadgeTone }
> = {
  SUBMITTED: { label: "Submitted", tone: "blue" },
  AUTO_APPROVED: { label: "Auto-approved", tone: "green" },
  QUEUED_FOR_AGENT: { label: "Queued for agent", tone: "amber" },
  UNDER_REVIEW: { label: "Under review", tone: "amber" },
  RESOLVED: { label: "Resolved", tone: "green" },
  REJECTED: { label: "Rejected", tone: "red" },
};

export const DECISION_ROUTE_META: Record<
  DecisionRoute,
  { label: string; tone: BadgeTone }
> = {
  AUTO_APPROVE: { label: "Auto-approve", tone: "green" },
  MANUAL_INVESTIGATION: { label: "Manual investigation", tone: "amber" },
};

export const TRANSACTION_STATUS_META: Record<
  TransactionStatus,
  { label: string; tone: BadgeTone }
> = {
  COMPLETED: { label: "Complete", tone: "green" },
  PENDING: { label: "Pending", tone: "amber" },
  FAILED: { label: "Failed", tone: "red" },
  REVERSED: { label: "Reversed", tone: "slate" },
};

const OPEN_DISPUTE_STATUSES: DisputeStatus[] = [
  "SUBMITTED",
  "QUEUED_FOR_AGENT",
  "UNDER_REVIEW",
];

export function isDisputeOpen(status: DisputeStatus): boolean {
  return OPEN_DISPUTE_STATUSES.includes(status);
}

/** Human-readable subtitle shown under each merchant, derived from the transaction category. */
export const TRANSACTION_CATEGORY_LABELS: Record<TransactionCategory, string> = {
  ONLINE_RETAIL: "Online purchase",
  MOBILE_CONTRACT: "Mobile contract",
  GYM_MEMBERSHIP: "Gym membership",
  LOAN: "Loan / credit agreement",
  MUNICIPAL: "Municipal services",
  FOOD_DELIVERY: "Food delivery",
  P2P_TRANSFER: "Money transfer",
  IN_STORE_RETAIL: "In-store purchase",
  GAMBLING: "Betting / gaming",
};