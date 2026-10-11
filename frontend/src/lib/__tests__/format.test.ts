import { describe, it, expect, vi, afterEach } from "vitest";
import {
  formatZar,
  formatSignedZar,
  formatDate,
  daysSince,
  initials,
  accountTypeLabel,
} from "@/lib/format";

// The decimal/grouping separators produced by Intl.NumberFormat("en-ZA") depend on the host
// ICU data (some environments use "." others ","). These tests therefore assert structure and
// behaviour rather than an exact separator glyph.

describe("formatZar", () => {
  it("prefixes with the Rand symbol and includes the digits", () => {
    const out = formatZar(10000);
    expect(out.startsWith("R ")).toBe(true);
    expect(out.replace(/\D/g, "")).toBe("1000000"); // 10000.00 -> digits
  });

  it("renders exactly two fraction digits", () => {
    expect(formatZar(1234.5)).toMatch(/^R\s[\d .,]+[.,]50$/);
  });

  it("uses a plain space (not a non-breaking space) after the R symbol", () => {
    expect(formatZar(1234.5)).not.toContain("\u00a0");
    expect(formatZar(1234.5).charAt(1)).toBe(" ");
  });

  it("formats zero with two fraction digits", () => {
    expect(formatZar(0)).toMatch(/^R\s0[.,]00$/);
  });
});

describe("formatSignedZar", () => {
  it("prepends a minus sign for negative amounts and none for positive", () => {
    const neg = formatSignedZar(-250);
    const pos = formatSignedZar(250);
    expect(neg.startsWith("-R ")).toBe(true);
    expect(pos.startsWith("R ")).toBe(true);
    // Same magnitude formatting, differing only by the sign.
    expect(neg).toBe("-" + pos);
  });
});

describe("daysSince", () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it("returns the whole number of days elapsed", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-03-01T12:00:00Z"));
    expect(daysSince("2026-02-19T12:00:00Z")).toBe(10);
  });

  it("never returns a negative number for future dates", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-03-01T12:00:00Z"));
    expect(daysSince("2026-04-01T12:00:00Z")).toBe(0);
  });
});

describe("initials", () => {
  it("takes the first letters of the first two words, uppercased", () => {
    expect(initials("Trevor Sesoko")).toBe("TS");
  });

  it("handles a single word", () => {
    expect(initials("Donald")).toBe("D");
  });

  it("ignores extra whitespace", () => {
    expect(initials("  mesut   ozil  ")).toBe("MO");
  });
});

describe("accountTypeLabel", () => {
  it("maps known backend enums to friendly labels", () => {
    expect(accountTypeLabel("MAIN_ACCOUNT")).toBe("Main account");
    expect(accountTypeLabel("SAVINGS_PLAN")).toBe("Savings plan");
  });

  it("humanises an unknown enum value", () => {
    expect(accountTypeLabel("SOME_NEW_TYPE")).toBe("Some new type");
  });
});

describe("formatDate", () => {
  it("renders a readable day/month/year", () => {
    const out = formatDate("2026-04-13T12:00:00Z");
    expect(out).toMatch(/2026/);
    expect(out).toMatch(/Apr/);
  });
});