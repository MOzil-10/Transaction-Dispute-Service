import { describe, it, expect } from "vitest";
import {
  isDisputeOpen,
  DISPUTE_STATUS_META,
  TRANSACTION_CATEGORY_LABELS,
  DISPUTE_WINDOW_DAYS,
} from "@/lib/constants";
import type { DisputeStatus, TransactionCategory } from "@/types/api";

describe("isDisputeOpen", () => {
  it("treats SUBMITTED, QUEUED_FOR_AGENT and UNDER_REVIEW as open", () => {
    expect(isDisputeOpen("SUBMITTED")).toBe(true);
    expect(isDisputeOpen("QUEUED_FOR_AGENT")).toBe(true);
    expect(isDisputeOpen("UNDER_REVIEW")).toBe(true);
  });

  it("treats terminal statuses as not open", () => {
    expect(isDisputeOpen("AUTO_APPROVED")).toBe(false);
    expect(isDisputeOpen("RESOLVED")).toBe(false);
    expect(isDisputeOpen("REJECTED")).toBe(false);
  });
});

describe("metadata maps", () => {
  it("has a label and tone for every dispute status", () => {
    const statuses: DisputeStatus[] = [
      "SUBMITTED",
      "AUTO_APPROVED",
      "QUEUED_FOR_AGENT",
      "UNDER_REVIEW",
      "RESOLVED",
      "REJECTED",
    ];
    for (const s of statuses) {
      expect(DISPUTE_STATUS_META[s]).toBeDefined();
      expect(DISPUTE_STATUS_META[s].label.length).toBeGreaterThan(0);
    }
  });

  it("has a label for every transaction category", () => {
    const categories: TransactionCategory[] = [
      "ONLINE_RETAIL",
      "MOBILE_CONTRACT",
      "GYM_MEMBERSHIP",
      "LOAN",
      "MUNICIPAL",
      "FOOD_DELIVERY",
      "P2P_TRANSFER",
      "IN_STORE_RETAIL",
      "GAMBLING",
    ];
    for (const c of categories) {
      expect(TRANSACTION_CATEGORY_LABELS[c]).toBeTruthy();
    }
  });
});

describe("DISPUTE_WINDOW_DAYS", () => {
  it("matches the 60-day regulatory window", () => {
    expect(DISPUTE_WINDOW_DAYS).toBe(60);
  });
});