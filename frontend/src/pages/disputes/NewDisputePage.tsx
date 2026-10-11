import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import {
  ArrowLeft, ArrowRight, Info, AlertTriangle, CheckCircle2, ShieldCheck,
} from "lucide-react";
import { AppShell } from "@/components/layout/AppShell";
import { Card } from "@/components/ui/Card";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Spinner } from "@/components/ui/Spinner";
import { transactionService } from "@/services/transactionService";
import { disputeService } from "@/services/disputeService";
import { useAccount } from "@/context/AccountContext";
import { ApiError } from "@/services/apiError";
import { formatSignedZar, formatZar, formatDate, daysSince } from "@/lib/format";
import { DISPUTE_REASON_OPTIONS, DISPUTE_WINDOW_DAYS, DECISION_ROUTE_META, DISPUTE_STATUS_META } from "@/lib/constants";
import type { DisputeReason, DisputeResponse, TransactionResponse } from "@/types/api";

type Step = 1 | 2 | 3;

export function NewDisputePage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const transactionId = Number(params.get("transactionId"));
  const { reload: reloadAccount } = useAccount();

  const [txn, setTxn] = useState<TransactionResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [step, setStep] = useState<Step>(1);
  const [reason, setReason] = useState<DisputeReason | null>(null);
  const [description, setDescription] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [result, setResult] = useState<DisputeResponse | null>(null);
  const [existingDispute, setExistingDispute] = useState<DisputeResponse | null>(null);

  // There is no GET-by-id endpoint, so find the transaction in the list. We also load the
  // customer's disputes to detect whether this transaction has already been disputed (a
  // transaction can only be disputed once), so we can warn and block before submission.
  useEffect(() => {
    if (!transactionId) { setLoadError("No transaction selected."); setLoading(false); return; }
    Promise.all([transactionService.list(0, 100), disputeService.list(0, 100)])
      .then(([txnPage, disputePage]) => {
        const found = txnPage.content.find((t) => t.transactionId === transactionId) ?? null;
        if (!found) setLoadError("We couldn't find that transaction.");
        setTxn(found);
        setExistingDispute(disputePage.content.find((d) => d.transactionId === transactionId) ?? null);
      })
      .catch((e) => setLoadError(e instanceof Error ? e.message : "Could not load the transaction."))
      .finally(() => setLoading(false));
  }, [transactionId]);

  const windowInfo = useMemo(() => {
    if (!txn) return null;
    const elapsed = daysSince(txn.transactionDate);
    const remaining = DISPUTE_WINDOW_DAYS - elapsed;
    return { elapsed, remaining, closed: elapsed > DISPUTE_WINDOW_DAYS };
  }, [txn]);

  const canSubmit = !!reason && !!windowInfo && !windowInfo.closed && !existingDispute;

  const submit = async () => {
    if (!txn || !reason) return;
    setSubmitting(true);
    setSubmitError(null);
    try {
      const res = await disputeService.submit({ transactionId: txn.transactionId, reason, description: description || null });
      setResult(res);
      setStep(3);
      // An auto-approved dispute credits the refund to the account immediately on the backend,
      // so refresh the shared account overview to show the new balance without a re-login.
      void reloadAccount();
    } catch (e) {
      // Backend is the source of truth for the 60-day rule and eligibility.
      setSubmitError(e instanceof ApiError ? e.message : "Could not submit your dispute. Please try again.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <AppShell>
      <Link to="/transactions" className="mb-4 inline-flex items-center gap-1.5 text-sm font-semibold text-blue-700 hover:text-blue-800">
        <ArrowLeft className="h-4 w-4" /> Back to transaction history
      </Link>
      <h1 className="text-2xl font-bold text-slate-900">Dispute transaction</h1>
      <p className="mt-1 text-sm text-slate-500">
        Tell us why you&apos;re disputing this debit. You can review your details before submitting a case.
      </p>

      <Stepper step={step} />

      {loading ? (
        <div className="flex h-56 items-center justify-center"><Spinner /></div>
      ) : loadError ? (
        <Card className="mt-6 p-6 text-center text-sm text-red-600">{loadError}</Card>
      ) : !txn || !windowInfo ? null : (
        <div className="mt-6 grid gap-6 lg:grid-cols-3">
          <div className="lg:col-span-2">
            {step === 1 && (
              <StepReason
                txn={txn}
                windowInfo={windowInfo}
                existingDispute={existingDispute}
                reason={reason}
                setReason={setReason}
                description={description}
                setDescription={setDescription}
                onContinue={() => setStep(2)}
                canContinue={canSubmit}
              />
            )}
            {step === 2 && (
              <StepReview
                txn={txn}
                reason={reason!}
                description={description}
                submitting={submitting}
                error={submitError}
                onBack={() => setStep(1)}
                onSubmit={submit}
              />
            )}
            {step === 3 && result && <StepConfirmation result={result} onDone={() => navigate("/disputes")} />}
          </div>

          {step !== 3 && <TransactionAside txn={txn} windowInfo={windowInfo} />}
        </div>
      )}
    </AppShell>
  );
}

function Stepper({ step }: { step: Step }) {
  const steps = ["Dispute details", "Review and submit", "Confirmation"];
  return (
    <Card className="mt-6 flex items-center gap-2 px-4 py-3 sm:gap-4 sm:px-6">
      {steps.map((label, i) => {
        const n = (i + 1) as Step;
        const active = step === n;
        const done = step > n;
        return (
          <div key={label} className="flex flex-1 items-center gap-2">
            <span className={[
              "flex h-7 w-7 flex-none items-center justify-center rounded-full text-xs font-semibold",
              done ? "bg-green-600 text-white" : active ? "bg-blue-700 text-white" : "bg-slate-100 text-slate-500",
            ].join(" ")}>
              {done ? <CheckCircle2 className="h-4 w-4" /> : n}
            </span>
            <span className={["hidden text-sm font-medium sm:block", active ? "text-blue-700" : "text-slate-500"].join(" ")}>{label}</span>
            {i < steps.length - 1 && <span className="mx-1 hidden h-px flex-1 bg-slate-200 sm:block" />}
          </div>
        );
      })}
    </Card>
  );
}

function AlreadyDisputedBanner({ dispute }: { dispute: DisputeResponse }) {
  const status = DISPUTE_STATUS_META[dispute.status];
  return (
    <div className="mb-5 flex items-start gap-3 rounded-xl bg-amber-50 p-4 ring-1 ring-inset ring-amber-600/20">
      <AlertTriangle className="mt-0.5 h-5 w-5 flex-none text-amber-600" />
      <div className="flex-1">
        <p className="text-sm font-semibold text-amber-800">This transaction has already been disputed</p>
        <p className="mt-1 text-sm text-amber-700">
          A dispute ({dispute.disputeReference}) is already on file for this debit, so it can&apos;t be disputed
          again. Its current status is <span className="font-semibold">{status.label}</span>.
        </p>
        <Link to="/disputes" className="mt-2 inline-flex items-center gap-1 text-sm font-semibold text-blue-700 hover:text-blue-800">
          Track this dispute &rarr;
        </Link>
      </div>
    </div>
  );
}

function WindowBanner({ info }: { info: { elapsed: number; remaining: number; closed: boolean } }) {
  if (info.closed) {
    return (
      <div className="mb-5 flex items-start gap-3 rounded-xl bg-red-50 p-4 ring-1 ring-inset ring-red-600/20">
        <AlertTriangle className="mt-0.5 h-5 w-5 flex-none text-red-600" />
        <div>
          <p className="text-sm font-semibold text-red-800">The 60-day dispute window has closed</p>
          <p className="mt-1 text-sm text-red-700">
            Under South African debit-order rules (effective April 2026), disputes must be raised within{" "}
            {DISPUTE_WINDOW_DAYS} calendar days of the transaction. This debit was {info.elapsed} days ago, so it can
            no longer be disputed online. Please contact support if you believe this is an error.
          </p>
        </div>
      </div>
    );
  }
  if (info.remaining <= 10) {
    return (
      <div className="mb-5 flex items-start gap-3 rounded-xl bg-amber-50 p-4 ring-1 ring-inset ring-amber-600/20">
        <AlertTriangle className="mt-0.5 h-5 w-5 flex-none text-amber-600" />
        <p className="text-sm text-amber-800">
          <span className="font-semibold">{info.remaining} day{info.remaining === 1 ? "" : "s"} left</span> to dispute
          this debit. The regulatory window closes {DISPUTE_WINDOW_DAYS} days after the transaction date.
        </p>
      </div>
    );
  }
  return (
    <div className="mb-5 flex items-start gap-3 rounded-xl bg-blue-50 p-4 ring-1 ring-inset ring-blue-700/20">
      <ShieldCheck className="mt-0.5 h-5 w-5 flex-none text-blue-700" />
      <p className="text-sm text-blue-800">
        Within the {DISPUTE_WINDOW_DAYS}-day dispute window ({info.remaining} days remaining). You can proceed.
      </p>
    </div>
  );
}

function StepReason({ txn, windowInfo, existingDispute, reason, setReason, description, setDescription, onContinue, canContinue }: {
  txn: TransactionResponse;
  windowInfo: { elapsed: number; remaining: number; closed: boolean };
  existingDispute: DisputeResponse | null;
  reason: DisputeReason | null;
  setReason: (r: DisputeReason) => void;
  description: string;
  setDescription: (s: string) => void;
  onContinue: () => void;
  canContinue: boolean;
}) {
  const locked = windowInfo.closed || !!existingDispute;
  return (
    <Card className="p-5 sm:p-6">
      {existingDispute ? <AlreadyDisputedBanner dispute={existingDispute} /> : <WindowBanner info={windowInfo} />}
      <h2 className="text-lg font-semibold text-slate-900">Why are you disputing this debit?</h2>
      <p className="mt-1 text-sm text-slate-500">Select the reason that best describes your query. Required.</p>

      <fieldset disabled={locked} className="mt-4 space-y-2.5 disabled:opacity-60">
        {DISPUTE_REASON_OPTIONS.map((opt) => {
          const selected = reason === opt.value;
          return (
            <label key={opt.value} className={[
              "flex cursor-pointer items-start gap-3 rounded-xl border p-3.5 transition-colors",
              selected ? "border-blue-600 bg-blue-50/60 ring-1 ring-inset ring-blue-600" : "border-slate-200 hover:bg-slate-50",
            ].join(" ")}>
              <input type="radio" name="reason" className="mt-0.5 h-4 w-4 text-blue-700 focus:ring-blue-600"
                checked={selected} onChange={() => setReason(opt.value)} />
              <span>
                <span className="block text-sm font-medium text-slate-900">{opt.label}</span>
                <span className="block text-xs text-slate-500">{opt.hint}</span>
              </span>
            </label>
          );
        })}
      </fieldset>

      <div className="mt-6">
        <div className="flex items-center justify-between">
          <label htmlFor="desc" className="text-sm font-semibold text-slate-700">Supporting details</label>
          <span className="text-xs text-slate-400">Optional</span>
        </div>
        <p className="mb-2 text-xs text-slate-500">Share what happened, including any contact you&apos;ve had with {txn.merchant}.</p>
        <textarea id="desc" rows={4} maxLength={1000} value={description} disabled={locked}
          onChange={(e) => setDescription(e.target.value)}
          placeholder="Add any details that could help us understand your dispute..."
          className="w-full rounded-lg border border-slate-300 p-3 text-sm placeholder:text-slate-400 focus:border-blue-600 focus:outline-none focus:ring-2 focus:ring-blue-600/30 disabled:bg-slate-50" />
        <div className="mt-1 flex items-center justify-between text-xs text-slate-400">
          <span>Don&apos;t include your PIN, password or full card number.</span>
          <span>{description.length} / 1,000</span>
        </div>
      </div>

      <div className="mt-6 flex items-center justify-between">
        <Link to="/transactions"><Button variant="secondary">Back</Button></Link>
        <Button onClick={onContinue} disabled={!canContinue}>
          Continue to review <ArrowRight className="h-4 w-4" />
        </Button>
      </div>
    </Card>
  );
}

function StepReview({ txn, reason, description, submitting, error, onBack, onSubmit }: {
  txn: TransactionResponse; reason: DisputeReason; description: string;
  submitting: boolean; error: string | null; onBack: () => void; onSubmit: () => void;
}) {
  const reasonLabel = DISPUTE_REASON_OPTIONS.find((o) => o.value === reason)?.label ?? reason;
  return (
    <Card className="p-5 sm:p-6">
      <h2 className="text-lg font-semibold text-slate-900">Review your dispute</h2>
      <p className="mt-1 text-sm text-slate-500">Please confirm the details below. Your dispute won&apos;t be submitted until you confirm.</p>

      {error && (
        <div className="mt-4 flex items-start gap-2 rounded-lg bg-red-50 p-3 text-sm text-red-700 ring-1 ring-inset ring-red-600/20">
          <AlertTriangle className="mt-0.5 h-4 w-4 flex-none" /> <span>{error}</span>
        </div>
      )}

      <dl className="mt-5 divide-y divide-slate-100 text-sm">
        <Row label="Merchant" value={txn.merchant} />
        <Row label="Amount" value={formatSignedZar(txn.amount)} />
        <Row label="Transaction date" value={formatDate(txn.transactionDate)} />
        <Row label="Reference" value={<span className="font-mono text-xs">{txn.reference}</span>} />
        <Row label="Reason" value={reasonLabel} />
        <Row label="Supporting details" value={description || <span className="text-slate-400">None provided</span>} />
      </dl>

      <div className="mt-6 flex items-center justify-between">
        <Button variant="secondary" onClick={onBack} disabled={submitting}>Back</Button>
        <Button onClick={onSubmit} loading={submitting}>
          {submitting ? "Submitting..." : "Confirm and submit"}
        </Button>
      </div>
    </Card>
  );
}

function StepConfirmation({ result, onDone }: { result: DisputeResponse; onDone: () => void }) {
  const route = DECISION_ROUTE_META[result.decisionRoute];
  const status = DISPUTE_STATUS_META[result.status];
  const refunded = result.refundedAmount != null && result.refundedAmount > 0;
  return (
    <Card className="p-6 text-center sm:p-10">
      <div className="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-full bg-green-50 text-green-600">
        <CheckCircle2 className="h-7 w-7" />
      </div>
      <h2 className="text-xl font-bold text-slate-900">Dispute submitted</h2>
      <p className="mt-1 text-sm text-slate-500">
        Your case reference is <span className="font-semibold text-slate-800">{result.disputeReference}</span>.
      </p>

      <div className="mx-auto mt-6 max-w-sm space-y-3 text-left">
        <div className="flex items-center justify-between rounded-lg bg-slate-50 px-4 py-3 text-sm">
          <span className="text-slate-500">Decision route</span>
          <Badge tone={route.tone}>{route.label}</Badge>
        </div>
        <div className="flex items-center justify-between rounded-lg bg-slate-50 px-4 py-3 text-sm">
          <span className="text-slate-500">Status</span>
          <Badge tone={status.tone}>{status.label}</Badge>
        </div>
        <div className="flex items-center justify-between rounded-lg bg-slate-50 px-4 py-3 text-sm">
          <span className="text-slate-500">Risk score</span>
          <span className="font-semibold text-slate-900">{result.riskScore} / 100</span>
        </div>
      </div>

      {refunded ? (
        <div className="mx-auto mt-5 flex max-w-sm items-start gap-2 rounded-xl bg-green-50 p-4 text-left ring-1 ring-inset ring-green-600/20">
          <CheckCircle2 className="mt-0.5 h-5 w-5 flex-none text-green-600" />
          <p className="text-sm text-green-800">
            This dispute was auto-approved and <span className="font-semibold">{formatZar(result.refundedAmount!)}</span> has been
            credited back to your account immediately.
          </p>
        </div>
      ) : (
        <div className="mx-auto mt-5 flex max-w-sm items-start gap-2 rounded-xl bg-blue-50 p-4 text-left ring-1 ring-inset ring-blue-700/20">
          <Info className="mt-0.5 h-5 w-5 flex-none text-blue-700" />
          <p className="text-sm text-blue-800">
            Your dispute has been queued for a back-office agent. You can track its progress under Disputes.
          </p>
        </div>
      )}

      <div className="mt-7 flex items-center justify-center gap-3">
        <Button onClick={onDone}>Track my disputes <ArrowRight className="h-4 w-4" /></Button>
      </div>
    </Card>
  );
}

function TransactionAside({ txn, windowInfo }: { txn: TransactionResponse; windowInfo: { elapsed: number; closed: boolean } }) {
  return (
    <div className="space-y-4">
      <Card className="p-5">
        <h3 className="text-sm font-semibold text-slate-900">Transaction you&apos;re disputing</h3>
        <p className="mt-3 text-base font-bold text-slate-900">{txn.merchant}</p>
        <p className="mt-2 text-2xl font-extrabold text-slate-900">{formatSignedZar(txn.amount)}</p>
        <p className="text-xs text-slate-500">Debit amount (ZAR)</p>
        <dl className="mt-4 space-y-3 border-t border-slate-100 pt-4 text-sm">
          <AsideRow label="Date" value={formatDate(txn.transactionDate)} />
          <AsideRow label="Reference" value={<span className="font-mono text-xs">{txn.reference}</span>} />
          <AsideRow label="Mandate type" value={<Badge tone="slate">{txn.mandateType}</Badge>} />
          <AsideRow label="Days since debit" value={`${windowInfo.elapsed} of ${DISPUTE_WINDOW_DAYS}`} />
        </dl>
      </Card>
      <Card className="bg-blue-50/60 p-5 ring-1 ring-inset ring-blue-700/10">
        <p className="flex items-center gap-2 text-sm font-semibold text-slate-800">
          <Info className="h-4 w-4 text-blue-700" /> What happens next?
        </p>
        <p className="mt-2 text-sm text-slate-600">
          Low-risk eligible disputes are auto-approved and refunded immediately. Higher-risk cases are routed to an
          agent. You can track every case under Disputes.
        </p>
      </Card>
    </div>
  );
}

function Row({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="flex items-start justify-between gap-4 py-3">
      <dt className="text-slate-500">{label}</dt>
      <dd className="text-right font-medium text-slate-900">{value}</dd>
    </div>
  );
}
function AsideRow({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="flex items-center justify-between gap-3">
      <dt className="text-slate-500">{label}</dt>
      <dd className="font-medium text-slate-900">{value}</dd>
    </div>
  );
}