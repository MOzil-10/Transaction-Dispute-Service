import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { transactionService } from "@/services/transactionService";

function okPage() {
  return new Response(JSON.stringify({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 10, first: true, last: true, numberOfElements: 0 }), {
    status: 200,
    headers: { "content-type": "application/json" },
  });
}

describe("transactionService.search URL building", () => {
  let fetchMock: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    localStorage.clear();
    fetchMock = vi.fn().mockResolvedValue(okPage());
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => vi.unstubAllGlobals());

  function calledUrl(): string {
    return String(fetchMock.mock.calls[0][0]);
  }

  it("includes the mandate type filter when set", async () => {
    await transactionService.search({ page: 0, size: 10, mandateType: "DEBICHECK" });
    expect(calledUrl()).toContain("/api/v1/transactions/search");
    expect(calledUrl()).toContain("mandateType=DEBICHECK");
  });

  it("includes from and to date filters when set", async () => {
    await transactionService.search({ page: 0, size: 10, fromDate: "2026-01-01", toDate: "2026-02-01" });
    expect(calledUrl()).toContain("fromDate=2026-01-01");
    expect(calledUrl()).toContain("toDate=2026-02-01");
  });

  it("omits empty filters", async () => {
    await transactionService.search({ page: 0, size: 10, mandateType: "", fromDate: "", toDate: "" });
    const url = calledUrl();
    expect(url).not.toContain("mandateType=");
    expect(url).not.toContain("fromDate=");
    expect(url).not.toContain("toDate=");
  });
});