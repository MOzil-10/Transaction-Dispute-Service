import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { Page, TransactionResponse } from "@/types/api";

// Mock the shell so we do not need auth/account/router context in this unit test.
vi.mock("@/components/layout/AppShell", () => ({
  AppShell: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}));

// Account context: the page only reads `account` (for the balance strip). Stub it so the
// component does not need a real AccountProvider wrapper in this unit test.
vi.mock("@/context/AccountContext", () => ({
  useAccount: () => ({ account: null, loading: false, error: null, fullName: "", reload: vi.fn() }),
}));

// No disputes in this scenario.
vi.mock("@/hooks/useDisputes", () => ({
  useDisputes: () => ({ disputes: [], openCount: 0, resolvedCount: 0, loading: false, error: null, reload: vi.fn() }),
}));

// Navigation is irrelevant here.
vi.mock("react-router-dom", () => ({ useNavigate: () => vi.fn() }));

const listMock = vi.fn();
const searchMock = vi.fn();
vi.mock("@/services/transactionService", () => ({
  transactionService: {
    list: (...args: unknown[]) => listMock(...args),
    search: (...args: unknown[]) => searchMock(...args),
  },
}));

import { TransactionsPage } from "@/pages/transactions/TransactionsPage";

function emptyPage(): Page<TransactionResponse> {
  return { content: [], totalElements: 0, totalPages: 0, number: 0, size: 10, first: true, last: true, numberOfElements: 0 };
}

describe("TransactionsPage filtering", () => {
  beforeEach(() => {
    listMock.mockReset().mockResolvedValue(emptyPage());
    searchMock.mockReset().mockResolvedValue(emptyPage());
  });

  it("loads the plain list when no server filters are set", async () => {
    render(<TransactionsPage />);
    await waitFor(() => expect(listMock).toHaveBeenCalled());
    expect(searchMock).not.toHaveBeenCalled();
  });

  it("calls search with the mandate type when the mandate filter changes", async () => {
    const user = userEvent.setup();
    render(<TransactionsPage />);
    await waitFor(() => expect(listMock).toHaveBeenCalled());

    const select = screen.getByRole("combobox");
    await user.selectOptions(select, "DEBICHECK");

    await waitFor(() => expect(searchMock).toHaveBeenCalled());
    const arg = searchMock.mock.calls.at(-1)![0];
    expect(arg).toMatchObject({ mandateType: "DEBICHECK" });
  });

  it("calls search with the from date when a date filter changes", async () => {
    const user = userEvent.setup();
    render(<TransactionsPage />);
    await waitFor(() => expect(listMock).toHaveBeenCalled());

    const fromDate = screen.getByLabelText("From date");
    await user.type(fromDate, "2026-01-15");

    await waitFor(() => expect(searchMock).toHaveBeenCalled());
    const arg = searchMock.mock.calls.at(-1)![0];
    expect(arg.fromDate).toBe("2026-01-15");
  });
});