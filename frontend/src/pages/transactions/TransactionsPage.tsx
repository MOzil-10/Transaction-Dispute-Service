import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Search, Wallet, Info, ChevronLeft, ChevronRight, ArrowLeftRight } from "lucide-react";
import { AppShell } from "@/components/layout/AppShell";
import { Card } from "@/components/ui/Card";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Spinner } from "@/components/ui/Spinner";
import { EmptyState } from "@/components/ui/EmptyState";
import { transactionService } from "@/services/transactionService";
import { useDisputes } from "@/hooks/useDisputes";
import { formatSignedZar, formatDate } from "@/lib/format";
import { TRANSACTION_STATUS_META, DISPUTE_STATUS_META, TRANSACTION_CATEGORY_LABELS } from "@/lib/constants";
import type { DisputeResponse, MandateType, Page, TransactionResponse } from "@/types/api";

const PAGE_SIZE = 10;
const MANDATE_FILTERS: { value: MandateType | ""; label: string }[] = [
  { value: "", label: "All mandate types" },
  { value: "DEBICHECK", label: "DebiCheck" },
  { value: "STANDARD_EFT", label: "Standard EFT" },
  { value: "REGISTERED_MANDATE", label: "Registered mandate" },
];

export function TransactionsPage() {
  const navigate = useNavigate();
  const { openCount, disputes } = useDisputes();
  const [page, setPage] = useState<Page<TransactionResponse> | null>(null);
  const [pageIdx, setPageIdx] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [query, setQuery] = useState("");
  const [mandate, setMandate] = useState<MandateType | "">("");
  const [fromDate, setFromDate] = useState("");
  const [toDate, setToDate] = useState("");

  // Guard an invalid range client-side (from after to) so we give immediate feedback
  // instead of firing a request the backend will reject with a 400.
  const invalidRange = !!fromDate && !!toDate && fromDate > toDate;

  useEffect(() => {
    if (invalidRange) {
      setLoading(false);
      setError("The 'from' date can't be after the 'to' date.");
      return;
    }
    let active = true;
    setLoading(true);
    setError(null);
    const hasServerFilters = mandate || fromDate || toDate;
    const call = hasServerFilters
      ? transactionService.search({ page: pageIdx, size: PAGE_SIZE, mandateType: mandate, fromDate, toDate })
      : transactionService.list(pageIdx, PAGE_SIZE);
    call
      .then((p) => { if (active) setPage(p); })
      .catch((e) => { if (active) setError(e instanceof Error ? e.message : "Could not load transactions."); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [pageIdx, mandate, fromDate, toDate, invalidRange]);

  // Client-side free-text search on the current page (merchant/reference).
  const rows = useMemo(() => {
    const content = page?.content ?? [];
    if (!query.trim()) return content;
    const q = query.toLowerCase();
    return content.filter(
      (t) => t.merchant.toLowerCase().includes(q) || t.reference.toLowerCase().includes(q),
    );
  }, [page, query]);

  // A transaction can only be disputed once. Cross-reference the customer's disputes
  // (the backend `disputable` flag does not account for an existing dispute) so a disputed
  // transaction shows its dispute status instead of an active Dispute button.
  const disputeByTxn = useMemo(() => {
    const map = new Map<number, DisputeResponse>();
    for (const d of disputes) map.set(d.transactionId, d);
    return map;
  }, [disputes]);

  const reset = () => {
    setQuery(""); setMandate(""); setFromDate(""); setToDate(""); setPageIdx(0);
  };

  // How many server-side filters are currently applied (drives the "N filters applied" chip).
  const activeFilterCount = [mandate, fromDate, toDate].filter(Boolean).length;

  const totalDebits = (page?.content ?? [])
    .filter((t) => t.amount < 0)
    .reduce((s, t) => s + Math.abs(t.amount), 0);

  return (
    <AppShell openDisputes={openCount}>
      <div className="mb-6 flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">Transaction history</h1>
          <p className="mt-1 text-sm text-slate-500">
            Review your debits and dispute any transaction you don&apos;t recognise.
          </p>
        </div>
      </div>

      <Card className="overflow-hidden">
        {/* Account summary strip */}
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-100 bg-slate-50 px-5 py-4">
          <div className="flex items-center gap-3">
            <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-blue-50 text-blue-700">
              <Wallet className="h-5 w-5" />
            </span>
            <div>
              <p className="text-sm font-semibold text-slate-900">Checking account</p>
              <p className="text-xs text-slate-500">ZAR</p>
            </div>
          </div>
          <div className="text-right">
            <p className="text-xs text-slate-500">{page?.totalElements ?? 0} transactions</p>
            <p className="text-sm font-semibold text-slate-900">Debits on page: {formatSignedZar(-totalDebits)}</p>
          </div>
        </div>

        {/* Filter bar */}
        <div className="flex flex-wrap items-center gap-3 border-b border-slate-100 px-5 py-4">
          <div className="relative min-w-[220px] flex-1">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
            <input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Search merchant or reference"
              className="h-10 w-full rounded-lg border border-slate-300 bg-white pl-9 pr-3 text-sm placeholder:text-slate-400 focus:border-blue-600 focus:outline-none focus:ring-2 focus:ring-blue-600/30"
            />
          </div>
          <select
            value={mandate}
            onChange={(e) => { setMandate(e.target.value as MandateType | ""); setPageIdx(0); }}
            className="h-10 rounded-lg border border-slate-300 bg-white px-3 text-sm text-slate-700 focus:border-blue-600 focus:outline-none focus:ring-2 focus:ring-blue-600/30"
          >
            {MANDATE_FILTERS.map((m) => <option key={m.value} value={m.value}>{m.label}</option>)}
          </select>
          <input type="date" value={fromDate} onChange={(e) => { setFromDate(e.target.value); setPageIdx(0); }} aria-label="From date"
            className="h-10 rounded-lg border border-slate-300 bg-white px-3 text-sm text-slate-700 focus:border-blue-600 focus:outline-none focus:ring-2 focus:ring-blue-600/30" />
          <input type="date" value={toDate} onChange={(e) => { setToDate(e.target.value); setPageIdx(0); }} aria-label="To date"
            className="h-10 rounded-lg border border-slate-300 bg-white px-3 text-sm text-slate-700 focus:border-blue-600 focus:outline-none focus:ring-2 focus:ring-blue-600/30" />
          {activeFilterCount > 0 && (
            <span className="inline-flex items-center rounded-full bg-blue-50 px-2.5 py-0.5 text-xs font-semibold text-blue-700 ring-1 ring-inset ring-blue-700/20">
              {activeFilterCount} filter{activeFilterCount > 1 ? "s" : ""} applied
            </span>
          )}
          <button onClick={reset} className="text-sm font-semibold text-blue-700 hover:text-blue-800">Reset</button>
        </div>

        {/* Table / states */}
        {loading ? (
          <div className="flex h-56 items-center justify-center"><Spinner /></div>
        ) : error ? (
          <div className="px-5 py-10 text-center text-sm text-red-600">{error}</div>
        ) : rows.length === 0 ? (
          <EmptyState icon={<ArrowLeftRight className="h-5 w-5" />} title="No transactions found" description="Try adjusting your search or filters." />
        ) : (
          <>
            {/* Desktop table */}
            <div className="hidden overflow-x-auto md:block">
              <table className="w-full text-left text-sm">
                <thead>
                  <tr className="border-b border-slate-100 text-xs uppercase tracking-wide text-slate-500">
                    <th className="px-5 py-3 font-semibold">Date</th>
                    <th className="px-5 py-3 font-semibold">Reference</th>
                    <th className="px-5 py-3 font-semibold">Merchant</th>
                    <th className="px-5 py-3 text-right font-semibold">Amount (ZAR)</th>
                    <th className="px-5 py-3 font-semibold">Mandate</th>
                    <th className="px-5 py-3 text-right font-semibold">Action</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {rows.map((t) => (
                    <tr key={t.transactionId} className="hover:bg-slate-50/60">
                      <td className="whitespace-nowrap px-5 py-4 text-slate-600">{formatDate(t.transactionDate)}</td>
                      <td className="whitespace-nowrap px-5 py-4 font-mono text-xs text-slate-400">{t.reference}</td>
                      <td className="px-5 py-4"><span className="block font-semibold text-slate-900">{t.merchant}</span><span className="block text-xs text-slate-500">{TRANSACTION_CATEGORY_LABELS[t.category]}</span></td>
                      <td className="whitespace-nowrap px-5 py-4 text-right font-semibold text-slate-900">{formatSignedZar(t.amount)}</td>
                      <td className="px-5 py-4"><Badge tone="slate">{t.mandateType}</Badge></td>
                      <td className="px-5 py-4 text-right">{renderAction(t, disputeByTxn.get(t.transactionId), () => navigate(`/disputes/new?transactionId=${t.transactionId}`))}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            {/* Mobile cards */}
            <ul className="divide-y divide-slate-100 md:hidden">
              {rows.map((t) => (
                <li key={t.transactionId} className="px-5 py-4">
                  <div className="flex items-start justify-between gap-3">
                    <div className="min-w-0">
                      <p className="font-semibold text-slate-900">{t.merchant}</p>
                      <p className="text-xs text-slate-500">{TRANSACTION_CATEGORY_LABELS[t.category]}</p>
                      <p className="text-xs text-slate-400">{formatDate(t.transactionDate)} &middot; <span className="font-mono">{t.reference}</span></p>
                    </div>
                    <p className="whitespace-nowrap font-semibold text-slate-900">{formatSignedZar(t.amount)}</p>
                  </div>
                  <div className="mt-3 flex items-center justify-between">
                    <Badge tone="slate">{t.mandateType}</Badge>
                    {renderAction(t, disputeByTxn.get(t.transactionId), () => navigate(`/disputes/new?transactionId=${t.transactionId}`))}
                  </div>
                </li>
              ))}
            </ul>
          </>
        )}

        {/* Pagination */}
        {page && page.totalPages > 1 && (
          <div className="flex items-center justify-between border-t border-slate-100 px-5 py-4 text-sm text-slate-500">
            <span>Page {page.number + 1} of {page.totalPages}</span>
            <div className="flex items-center gap-2">
              <button disabled={page.first} onClick={() => setPageIdx((i) => Math.max(0, i - 1))}
                className="flex h-8 w-8 items-center justify-center rounded-lg border border-slate-300 text-slate-600 disabled:opacity-40 hover:bg-slate-50">
                <ChevronLeft className="h-4 w-4" />
              </button>
              <button disabled={page.last} onClick={() => setPageIdx((i) => i + 1)}
                className="flex h-8 w-8 items-center justify-center rounded-lg border border-slate-300 text-slate-600 disabled:opacity-40 hover:bg-slate-50">
                <ChevronRight className="h-4 w-4" />
              </button>
            </div>
          </div>
        )}
      </Card>

      <p className="mt-4 flex items-start gap-2 text-xs text-slate-500">
        <Info className="mt-0.5 h-4 w-4 flex-none text-blue-700" />
        Need to query a debit? Select Dispute on an eligible transaction. Non-disputable debits (e.g. in-store
        purchases and peer-to-peer transfers) show their status instead. Existing cases are under Disputes.
      </p>
    </AppShell>
  );
}

/**
 * Action column precedence:
 *  1. A dispute already exists for this transaction -> show the dispute's status badge
 *     and a disabled "Disputed" button (it can't be disputed again).
 *  2. The transaction is disputable (and not yet disputed) -> active Dispute button.
 *  3. Otherwise (non-disputable category) -> the transaction's own status badge.
 */
function renderAction(
  t: TransactionResponse,
  existing: DisputeResponse | undefined,
  onDispute: () => void,
) {
  if (existing) {
    const meta = DISPUTE_STATUS_META[existing.status];
    return (
      <span className="inline-flex items-center gap-2">
        <Badge tone={meta.tone}>{meta.label}</Badge>
        <Button size="sm" variant="secondary" disabled title={`Dispute ${existing.disputeReference} already lodged`}>
          Disputed
        </Button>
      </span>
    );
  }
  if (t.disputable) {
    return <Button size="sm" onClick={onDispute}>Dispute</Button>;
  }
  const meta = TRANSACTION_STATUS_META[t.status];
  return <Badge tone={meta.tone}>{meta.label}</Badge>;
}