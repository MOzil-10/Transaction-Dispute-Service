const ZAR = new Intl.NumberFormat("en-ZA", {
  style: "currency",
  currency: "ZAR",
  minimumFractionDigits: 2,
});

/** Formats a number as ZAR, e.g. 10000 -> "R 10,000.00". */
export function formatZar(amount: number): string {
  // en-ZA renders "R" with a non-breaking space; normalise to a plain space.
  return ZAR.format(amount).replace(/\u00a0/g, " ");
}

/** Formats a signed amount for a debit/credit, keeping the sign explicit. */
export function formatSignedZar(amount: number): string {
  const sign = amount < 0 ? "-" : "";
  return `${sign}${formatZar(Math.abs(amount))}`;
}

export function formatDate(iso: string): string {
  const d = new Date(iso);
  return d.toLocaleDateString("en-ZA", {
    day: "2-digit",
    month: "short",
    year: "numeric",
  });
}

export function formatDateTime(iso: string): string {
  const d = new Date(iso);
  return d.toLocaleString("en-ZA", {
    day: "2-digit",
    month: "short",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

/** Whole calendar days elapsed since the given ISO date (floored, never negative). */
export function daysSince(iso: string): number {
  const then = new Date(iso).getTime();
  const now = Date.now();
  const diff = Math.floor((now - then) / (1000 * 60 * 60 * 24));
  return Math.max(0, diff);
}

export function initials(name: string): string {
  return name
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((p) => p[0]?.toUpperCase() ?? "")
    .join("");
}


/** Humanises a backend account-type enum, e.g. "MAIN_ACCOUNT" -> "Main account". */
export function accountTypeLabel(type: string): string {
  const map: Record<string, string> = {
    MAIN_ACCOUNT: "Main account",
    SAVINGS_PLAN: "Savings plan",
    CHECKING: "Checking account",
  };
  if (map[type]) return map[type];
  const lower = type.replace(/_/g, " ").toLowerCase();
  return lower.charAt(0).toUpperCase() + lower.slice(1);
}
