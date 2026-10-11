import { Wallet, CircleCheck } from "lucide-react";
import { AppShell } from "@/components/layout/AppShell";
import { Card } from "@/components/ui/Card";
import { Spinner } from "@/components/ui/Spinner";
import { useAccount } from "@/context/AccountContext";
import { useDisputes } from "@/hooks/useDisputes";
import { formatZar, accountTypeLabel } from "@/lib/format";

export function AccountPage() {
  const { account, loading, error, fullName } = useAccount();
  const { openCount } = useDisputes();

  return (
    <AppShell openDisputes={openCount}>
      <h1 className="text-2xl font-bold text-slate-900">Account details</h1>
      <p className="mt-1 text-sm text-slate-500">Your account profile and balance.</p>

      <div className="mt-6 grid gap-6 lg:grid-cols-2">
        <Card className="p-5">
          <div className="flex items-center gap-3">
            <span className="flex h-10 w-10 items-center justify-center rounded-lg bg-blue-50 text-blue-700"><Wallet className="h-5 w-5" /></span>
            <div>
              <p className="font-semibold text-slate-900">
                {account ? accountTypeLabel(account.accountType) : "Primary account"}
              </p>
              <p className="text-xs text-slate-500">{account?.currency ?? "ZAR"} &middot; South Africa</p>
            </div>
          </div>

          {loading && !account ? (
            <div className="flex h-40 items-center justify-center"><Spinner /></div>
          ) : error ? (
            <p className="mt-5 text-sm text-red-600">{error}</p>
          ) : (
            <dl className="mt-5 divide-y divide-slate-100 text-sm">
              <div className="flex justify-between py-3"><dt className="text-slate-500">Account holder</dt><dd className="font-medium text-slate-900">{fullName || "\u2014"}</dd></div>
              <div className="flex justify-between py-3"><dt className="text-slate-500">Account number</dt><dd className="font-medium text-slate-900">{account?.accountNumber ?? "\u2014"}</dd></div>
              <div className="flex justify-between py-3"><dt className="text-slate-500">Account type</dt><dd className="font-medium text-slate-900">{account ? accountTypeLabel(account.accountType) : "\u2014"}</dd></div>
              <div className="flex justify-between py-3"><dt className="text-slate-500">Available balance</dt><dd className="font-semibold text-slate-900">{account ? formatZar(account.balance) : "\u2014"}</dd></div>
              <div className="flex justify-between py-3"><dt className="text-slate-500">Currency</dt><dd className="font-medium text-slate-900">{account?.currency ?? "ZAR"}</dd></div>
              <div className="flex justify-between py-3"><dt className="text-slate-500">FICA status</dt><dd className="flex items-center gap-1.5 font-medium text-green-700"><CircleCheck className="h-4 w-4" /> Verified</dd></div>
            </dl>
          )}
        </Card>

        <Card className="p-5">
          <p className="text-sm font-semibold text-slate-800">Contact</p>
          <dl className="mt-4 divide-y divide-slate-100 text-sm">
            <div className="flex justify-between py-3"><dt className="text-slate-500">Email</dt><dd className="font-medium text-slate-900">{account?.email ?? "\u2014"}</dd></div>
          </dl>
        </Card>
      </div>
    </AppShell>
  );
}