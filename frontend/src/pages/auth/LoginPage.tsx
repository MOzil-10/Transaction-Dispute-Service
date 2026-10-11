import { useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { Eye, EyeOff, ArrowRight, ShieldCheck, Wallet, ArrowLeftRight, AlertCircle } from "lucide-react";
import { AuthLayout } from "@/components/layout/AuthLayout";
import { Input } from "@/components/ui/Input";
import { Button } from "@/components/ui/Button";
import { useAuth } from "@/context/AuthContext";

export function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [show, setShow] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      await login({ email, password });
      navigate("/dashboard", { replace: true });
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unable to sign in.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <AuthLayout
      eyebrow="Transaction Dispute Portal"
      headline={<>Your account.<br />Your peace of mind.</>}
      blurb="Stay in control of your checking account, review transactions and resolve debit disputes with confidence."
      highlights={[
        { title: "A clear account overview", description: "Your balance and activity, in one place.", icon: <Wallet className="h-4 w-4" /> },
        { title: "Every transaction, traceable", description: "Review EFT and DebiCheck mandates.", icon: <ArrowLeftRight className="h-4 w-4" /> },
        { title: "Disputes, without the uncertainty", description: "Follow each case from start to resolution.", icon: <ShieldCheck className="h-4 w-4" /> },
      ]}
      topRight={
        <span>
          New to Aster Bank?{" "}
          <Link to="/auth/register" className="font-semibold text-blue-700 hover:text-blue-800">
            Create an account
          </Link>
        </span>
      }
    >
      <p className="mb-1.5 text-xs font-semibold uppercase tracking-wide text-blue-700">Welcome back</p>
      <h2 className="text-2xl font-bold text-slate-900">Sign in to your account</h2>
      <p className="mt-1.5 text-sm text-slate-500">Access your balance, transactions and disputes securely.</p>

      {error && (
        <div className="mt-5 flex items-start gap-2 rounded-lg bg-red-50 p-3 text-sm text-red-700 ring-1 ring-inset ring-red-600/20">
          <AlertCircle className="mt-0.5 h-4 w-4 flex-none" />
          <span>{error}</span>
        </div>
      )}

      <form onSubmit={onSubmit} className="mt-6 space-y-4">
        <Input
          label="Email address"
          name="email"
          type="email"
          autoComplete="email"
          required
          placeholder="you@example.co.za"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
        />
        <Input
          label="Password"
          name="password"
          type={show ? "text" : "password"}
          autoComplete="current-password"
          required
          placeholder="Enter your password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          trailing={
            <button type="button" onClick={() => setShow((s) => !s)} aria-label="Toggle password visibility">
              {show ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
            </button>
          }
        />
        <div className="flex items-center justify-between">
          <label className="flex items-center gap-2 text-sm text-slate-600">
            <input type="checkbox" className="h-4 w-4 rounded border-slate-300 text-blue-700 focus:ring-blue-600" />
            Remember my email
          </label>
          <a href="#" className="text-sm font-semibold text-blue-700 hover:text-blue-800">Forgot password?</a>
        </div>
        <Button type="submit" size="lg" loading={loading} className="w-full">
          <ArrowRight className="h-4 w-4" /> Sign in
        </Button>
      </form>

      <p className="mt-5 text-center text-sm text-slate-500">
        Don&apos;t have an account?{" "}
        <Link to="/auth/register" className="font-semibold text-blue-700 hover:text-blue-800">
          Create an account
        </Link>
      </p>
      <div className="mt-5 flex items-start gap-2 rounded-lg bg-slate-50 p-3 text-xs text-slate-500 ring-1 ring-inset ring-slate-200">
        <ShieldCheck className="mt-0.5 h-4 w-4 flex-none text-blue-700" />
        We&apos;ll never ask you to share your password or one-time PIN. Only sign in on a device you trust.
      </div>
    </AuthLayout>
  );
}