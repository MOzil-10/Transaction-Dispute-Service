import { Link } from "react-router-dom";
import { ShieldCheck } from "lucide-react";
import { AppShell } from "@/components/layout/AppShell";
import { Card } from "@/components/ui/Card";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Spinner } from "@/components/ui/Spinner";
import { EmptyState } from "@/components/ui/EmptyState";
import { useDisputes } from "@/hooks/useDisputes";
import { formatZar, formatDate } from "@/lib/format";
import { DISPUTE_STATUS_META, DECISION_ROUTE_META, DISPUTE_REASON_LABELS } from "@/lib/constants";
import type { DisputeResponse } from "@/types/api";


export function DisputesPage() {
  const { disputes, loading, error, openCount, resolvedCount } = useDisputes();

  return (
    <AppShell openDisputes={openCount}>
      <div className="mb-6 flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">Dispute tracking</h1>
          <p className="mt-1 text-sm text-slate-500">Follow each case from submission to resolution.</p>
        </div>
        <Link to="/transactions"><Button variant="secondary">Raise a new dispute</Button></Link>
      </div>

      <div className="mb-6 grid grid-cols-2 gap-4 sm:grid-cols-3">
        <Card className="bg-slate-50 p-4"><p className="text-2xl font-bold text-slate-900">{loading ? "\u2014" : disputes.length}</p><p className="text-xs text-slate-500">Total disputes</p></Card>
        <Card className="bg-slate-50 p-4"><p className="text-2xl font-bold text-slate-900">{loading ? "\u2014" : openCount}</p><p className="text-xs text-slate-500">Active</p></Card>
        <Card className="col-span-2 bg-slate-50 p-4 sm:col-span-1"><p className="text-2xl font-bold text-slate-900">{loading ? "\u2014" : resolvedCount}</p><p className="text-xs text-slate-500">Resolved</p></Card>
      </div>

      <Card className="overflow-hidden">
        {loading ? (
          <div className="flex h-56 items-center justify-center"><Spinner /></div>
        ) : error ? (
          <div className="px-5 py-10 text-center text-sm text-red-600">{error}</div>
        ) : disputes.length === 0 ? (
          <EmptyState
            icon={<ShieldCheck className="h-5 w-5" />}
            title="No disputes yet"
            description="When you dispute a transaction, it will appear here with its risk score, routing and status."
            action={<Link to="/transactions"><Button>Review transactions</Button></Link>}
          />
        ) : (
          <>
            <div className="hidden overflow-x-auto lg:block">
              <table className="w-full text-left text-sm">
                <thead>
                  <tr className="border-b border-slate-100 text-xs uppercase tracking-wide text-slate-500">
                    <th className="px-5 py-3 font-semibold">Reference</th>
                    <th className="px-5 py-3 font-semibold">Reason</th>
                    <th className="px-5 py-3 text-right font-semibold">Amount</th>
                    <th className="px-5 py-3 font-semibold">Decision route</th>
                    <th className="px-5 py-3 font-semibold">Status</th>
                    <th className="px-5 py-3 font-semibold">Opened</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {disputes.map((d) => <DisputeRow key={d.disputeId} d={d} />)}
                </tbody>
              </table>
            </div>
            <ul className="divide-y divide-slate-100 lg:hidden">
              {disputes.map((d) => <DisputeCard key={d.disputeId} d={d} />)}
            </ul>
          </>
        )}
      </Card>
    </AppShell>
  );
}

function DisputeRow({ d }: { d: DisputeResponse }) {
  const status = DISPUTE_STATUS_META[d.status];
  const route = DECISION_ROUTE_META[d.decisionRoute];
  return (
    <tr className="hover:bg-slate-50/60">
      <td className="whitespace-nowrap px-5 py-4 font-semibold text-slate-900">{d.disputeReference}</td>
      <td className="px-5 py-4 text-slate-600">{DISPUTE_REASON_LABELS[d.reason]}</td>
      <td className="whitespace-nowrap px-5 py-4 text-right font-semibold text-slate-900">{formatZar(d.amount)}</td>
      <td className="px-5 py-4"><Badge tone={route.tone}>{route.label}</Badge></td>
      <td className="px-5 py-4"><Badge tone={status.tone}>{status.label}</Badge></td>
      <td className="whitespace-nowrap px-5 py-4 text-slate-500">{formatDate(d.createdAt)}</td>
    </tr>
  );
}

function DisputeCard({ d }: { d: DisputeResponse }) {
  const status = DISPUTE_STATUS_META[d.status];
  const route = DECISION_ROUTE_META[d.decisionRoute];
  return (
    <li className="px-5 py-4">
      <div className="flex items-center justify-between">
        <p className="font-semibold text-slate-900">{d.disputeReference}</p>
        <span className="font-semibold text-slate-900">{formatZar(d.amount)}</span>
      </div>
      <p className="mt-0.5 text-sm text-slate-500">{DISPUTE_REASON_LABELS[d.reason]} &middot; {formatDate(d.createdAt)}</p>
      <div className="mt-3 flex flex-wrap items-center gap-2">
        <Badge tone={status.tone}>{status.label}</Badge>
        <Badge tone={route.tone}>{route.label}</Badge>
      </div>
    </li>
  );
}