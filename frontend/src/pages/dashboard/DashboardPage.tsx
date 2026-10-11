import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import {
  CreditCard,
  ArrowLeftRight,
  ShieldCheck,
  Download,
  ChevronRight,
  CircleCheck,
  TrendingUp,
} from "lucide-react";
import { AppShell } from "@/components/layout/AppShell";
import { Card } from "@/components/ui/Card";
import { Badge } from "@/components/ui/Badge";
import { Spinner } from "@/components/ui/Spinner";
import { useAuth } from "@/context/AuthContext";
import { useAccount } from "@/context/AccountContext";
import { useDisputes } from "@/hooks/useDisputes";
import { transactionService } from "@/services/transactionService";
import { formatZar, formatDate, accountTypeLabel } from "@/lib/format";
import { DISPUTE_STATUS_META, DISPUTE_REASON_LABELS } from "@/lib/constants";
import type { TransactionResponse } from "@/types/api";

function greeting(): string {
  const h = new Date().getHours();
  if (h < 12) return "Good morning";
  if (h < 18) return "Good afternoon";
  return "Good evening";
}

export function DashboardPage() {
  const { user } = useAuth();
  const { account, loading: accountLoading } = useAccount();
  const { disputes, openCount, resolvedCount, loading: disputesLoading } = useDisputes();
  const [txns, setTxns] = useState<TransactionResponse[]>([]);
  const [txLoading, setTxLoading] = useState(true);

  useEffect(() => {
    transactionService
      .list(0, 50)
      .then((p) => setTxns(p.content))
      .catch(() => setTxns([]))
      .finally(() => setTxLoading(false));
  }, []);

  // Derived: total debits this month + a simple daily bar series (no chart endpoint on the backend).
  const { totalDebits, series, maxBar } = useMemo(() => {
    const now = new Date();
    const month = txns.filter((t) => {
      const d = new Date(t.transactionDate);
      return d.getMonth() === now.getMonth() && d.getFullYear() === now.getFullYear() && t.amount < 0;
    });
    const total = month.reduce((sum, t) => sum + Math.abs(t.amount), 0);
    const byDay = new Map<number, number>();
    for (const t of month) byDay.set(new Date(t.transactionDate).getDate(), (byDay.get(new Date(t.transactionDate).getDate()) ?? 0) + Math.abs(t.amount));
    const days = Array.from(byDay.keys()).sort((a, b) => a - b).slice(0, 8);
    const s = days.map((day) => ({ day, value: byDay.get(day) ?? 0 }));
    return { totalDebits: total, series: s, maxBar: Math.max(1, ...s.map((x) => x.value)) };
  }, [txns]);

  const firstName = account?.firstName ?? user?.email?.split("@")[0]?.split(".")[0] ?? "there";
  const activeDisputes = disputes.filter((d) => DISPUTE_STATUS_META[d.status].tone !== "green").slice(0, 3);

  return (
    <AppShell openDisputes={openCount}>
      <div className="mb-6 flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold capitalize text-slate-900">
            {greeting()}, {firstName}
          </h1>
          <p className="mt-1 text-sm text-slate-500">
            Here&apos;s your account overview for{" "}
            {new Date().toLocaleDateString("en-ZA", { weekday: "long", day: "2-digit", month: "long", year: "numeric" })}.
          </p>
        </div>
        <span className="flex items-center gap-1.5 text-sm font-medium text-green-700">
          <CircleCheck className="h-4 w-4" /> FICA verified
        </span>
      </div>

      {/* Balance card - Banking Blue */}
      <div className="relative overflow-hidden rounded-2xl bg-blue-700 p-6 text-white shadow-sm">
        <div className="flex items-start justify-between">
          <div>
            <p className="text-sm font-medium text-blue-100">
              {account ? accountTypeLabel(account.accountType) : "Account"}
            </p>
            <p className="mt-4 text-xs uppercase tracking-wide text-blue-200">Available balance</p>
            <p className="mt-1 text-3xl font-extrabold tracking-tight sm:text-4xl">
              {accountLoading && !account ? "\u2014" : account ? formatZar(account.balance) : "\u2014"}
            </p>
            <p className="mt-3 text-sm text-blue-100">
              Account ending {account?.accountNumber?.slice(-4) ?? "0000"} &middot; {account?.currency ?? "ZAR"}
            </p>
          </div>
          <CreditCard className="h-6 w-6 text-blue-200" />
        </div>
        <Link
          to="/account"
          className="mt-4 inline-flex items-center gap-1 text-sm font-semibold text-white/90 hover:text-white"
        >
          View account details <ChevronRight className="h-4 w-4" />
        </Link>
      </div>

      <div className="mt-6 grid gap-6 lg:grid-cols-3">
        {/* Recent activity (derived chart) */}
        <Card className="p-5 lg:col-span-2">
          <div className="mb-4 flex items-center justify-between">
            <h2 className="text-base font-semibold text-slate-900">Recent activity</h2>
            <span className="flex items-center gap-1 text-xs text-slate-400">
              <TrendingUp className="h-3.5 w-3.5" /> This month
            </span>
          </div>
          <p className="text-2xl font-bold text-slate-900">{formatZar(totalDebits)}</p>
          <p className="text-xs text-slate-500">Total debits this month</p>
          {txLoading ? (
            <div className="flex h-40 items-center justify-center"><Spinner /></div>
          ) : series.length === 0 ? (
            <div className="flex h-40 items-center justify-center text-sm text-slate-400">
              No debit activity this month yet.
            </div>
          ) : (
            <div className="mt-4 flex h-40 items-end gap-2">
              {series.map((b) => (
                <div key={b.day} className="flex flex-1 flex-col items-center gap-2">
                  <div className="flex w-full flex-1 items-end">
                    <div
                      className="w-full rounded-t bg-blue-600"
                      style={{ height: `${Math.max(4, (b.value / maxBar) * 100)}%` }}
                      title={formatZar(b.value)}
                    />
                  </div>
                  <span className="text-[10px] text-slate-400">{String(b.day).padStart(2, "0")}</span>
                </div>
              ))}
            </div>
          )}
        </Card>

        {/* Quick actions + metrics */}
        <div className="space-y-6">
          <div className="grid grid-cols-2 gap-4">
            <Card className="bg-slate-50 p-4">
              <p className="text-2xl font-bold text-slate-900">{disputesLoading ? "\u2014" : openCount}</p>
              <p className="text-xs text-slate-500">Active disputes</p>
            </Card>
            <Card className="bg-slate-50 p-4">
              <p className="text-2xl font-bold text-slate-900">{disputesLoading ? "\u2014" : resolvedCount}</p>
              <p className="text-xs text-slate-500">Resolved disputes</p>
            </Card>
          </div>
          <Card className="p-5">
            <h2 className="mb-3 text-base font-semibold text-slate-900">Quick actions</h2>
            <div className="space-y-1">
              <QuickAction to="/transactions" icon={<ArrowLeftRight className="h-5 w-5" />} title="Review transactions" subtitle="Find a debit and raise a dispute" />
              <QuickAction to="/disputes" icon={<ShieldCheck className="h-5 w-5" />} title="Track disputes" subtitle="Check updates on your open cases" />
              <QuickAction to="/account" icon={<Download className="h-5 w-5" />} title="Account details" subtitle="View your checking account" />
            </div>
          </Card>
        </div>
      </div>

      {/* Active disputes preview */}
      <Card className="mt-6 overflow-hidden">
        <div className="flex items-center justify-between px-5 py-4">
          <h2 className="text-base font-semibold text-slate-900">Your active disputes</h2>
          <Link to="/disputes" className="text-sm font-semibold text-blue-700 hover:text-blue-800">
            View all disputes &rarr;
          </Link>
        </div>
        {disputesLoading ? (
          <div className="flex h-24 items-center justify-center"><Spinner /></div>
        ) : activeDisputes.length === 0 ? (
          <p className="px-5 pb-6 text-sm text-slate-500">You have no active disputes.</p>
        ) : (
          <ul className="divide-y divide-slate-100 border-t border-slate-100">
            {activeDisputes.map((d) => {
              const meta = DISPUTE_STATUS_META[d.status];
              return (
                <li key={d.disputeId} className="flex flex-col gap-3 px-5 py-4 sm:flex-row sm:items-center sm:justify-between">
                  <div className="min-w-0">
                    <p className="break-words font-semibold text-slate-900">{DISPUTE_REASON_LABELS[d.reason]}</p>
                    <p className="break-words text-xs text-slate-500">
                      {d.disputeReference} &middot; Opened {formatDate(d.createdAt)}
                    </p>
                  </div>
                  <div className="flex flex-wrap items-center gap-x-3 gap-y-2 sm:flex-none sm:justify-end">
                    <span className="font-semibold text-slate-900">{formatZar(d.amount)}</span>
                    <Badge tone={meta.tone}>{meta.label}</Badge>
                    <Link to="/disputes" className="text-sm font-semibold text-blue-700 hover:text-blue-800">
                      View case &rarr;
                    </Link>
                  </div>
                </li>
              );
            })}
          </ul>
        )}
      </Card>
    </AppShell>
  );
}

function QuickAction({ to, icon, title, subtitle }: { to: string; icon: React.ReactNode; title: string; subtitle: string }) {
  return (
    <Link to={to} className="flex items-center gap-3 rounded-lg px-2 py-2.5 hover:bg-slate-50">
      <span className="flex h-9 w-9 flex-none items-center justify-center rounded-lg bg-blue-50 text-blue-700">{icon}</span>
      <span className="min-w-0 flex-1">
        <span className="block text-sm font-semibold text-slate-800">{title}</span>
        <span className="block text-xs text-slate-500">{subtitle}</span>
      </span>
      <ChevronRight className="h-4 w-4 flex-none text-slate-400" />
    </Link>
  );
}