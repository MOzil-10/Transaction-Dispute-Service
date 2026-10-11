import { useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { Eye, EyeOff, ArrowRight, CheckCircle2, User, CreditCard, ShieldCheck, AlertCircle } from "lucide-react";
import { AuthLayout } from "@/components/layout/AuthLayout";
import { Input } from "@/components/ui/Input";
import { Button } from "@/components/ui/Button";
import { useAuth } from "@/context/AuthContext";

export function RegisterPage() {
  const { register } = useAuth();
  const navigate = useNavigate();
  const [fullName, setFullName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [agree, setAgree] = useState(false);
  const [show, setShow] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);

    const parts = fullName.trim().split(/\s+/);
    if (parts.length < 2) {
      setError("Please enter your first and last name.");
      return;
    }
    if (password.length < 8) {
      setError("Password must be at least 8 characters long.");
      return;
    }
    const firstName = parts[0];
    const lastName = parts.slice(1).join(" ");

    setLoading(true);
    try {
      await register({ firstName, lastName, email, password });
      navigate("/dashboard", { replace: true });
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unable to create your account.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <AuthLayout
      eyebrow="Transaction Dispute Portal"
      headline={<>A clear start.<br />A secure account.</>}
      blurb="Create your profile and checking account in one secure step. We'll guide you through the rest."
      highlights={[
        { title: "Create your profile", description: "One registration. One checking account.", icon: <span className="text-xs font-bold">01</span> },
        { title: "Verify your identity", description: "Complete FICA checks before transacting.", icon: <span className="text-xs font-bold">02</span> },
        { title: "Bank with confidence", description: "Manage your money and debit disputes.", icon: <span className="text-xs font-bold">03</span> },
      ]}
      topRight={
        <span>
          Already a customer?{" "}
          <Link to="/auth/login" className="font-semibold text-blue-700 hover:text-blue-800">Sign in</Link>
        </span>
      }
    >
      <p className="mb-1.5 text-xs font-semibold uppercase tracking-wide text-blue-700">Step 1 of 3 &middot; Your details</p>
      <h2 className="text-2xl font-bold text-slate-900">Create your account</h2>
      <p className="mt-1.5 text-sm text-slate-500">
        A few details to get you started. Use your legal name as it appears on your identity document.
      </p>

      {error && (
        <div className="mt-5 flex items-start gap-2 rounded-lg bg-red-50 p-3 text-sm text-red-700 ring-1 ring-inset ring-red-600/20">
          <AlertCircle className="mt-0.5 h-4 w-4 flex-none" />
          <span>{error}</span>
        </div>
      )}

      <form onSubmit={onSubmit} className="mt-6 space-y-4">
        <Input label="Full name" name="fullName" required placeholder="Thabo Mokoena" value={fullName} onChange={(e) => setFullName(e.target.value)} />
        <Input label="Email address" name="email" type="email" autoComplete="email" required placeholder="you@example.co.za" value={email} onChange={(e) => setEmail(e.target.value)} />
        <Input
          label="Password"
          name="password"
          type={show ? "text" : "password"}
          autoComplete="new-password"
          required
          placeholder="Create a password"
          hint="Use 8 or more characters with a mix of letters and numbers."
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          trailing={
            <button type="button" onClick={() => setShow((s) => !s)} aria-label="Toggle password visibility">
              {show ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
            </button>
          }
        />

        <div className="rounded-xl bg-slate-50 p-4 ring-1 ring-inset ring-slate-200">
          <p className="flex items-center gap-2 text-sm font-semibold text-slate-800">
            <CheckCircle2 className="h-4 w-4 text-blue-700" /> One registration. Both created together.
          </p>
          <div className="mt-3 flex flex-wrap gap-x-6 gap-y-2 text-sm text-slate-600">
            <span className="flex items-center gap-2"><User className="h-4 w-4 text-slate-400" /> Customer profile</span>
            <span className="flex items-center gap-2"><CreditCard className="h-4 w-4 text-slate-400" /> ZAR checking account</span>
          </div>
          <p className="mt-3 text-xs text-slate-500">
            On successful registration, your customer profile and checking account are automatically
            provisioned in a single transaction. If either fails, neither is created.
          </p>
        </div>

        <label className="flex items-start gap-2.5 text-sm text-slate-600">
          <input
            type="checkbox"
            checked={agree}
            onChange={(e) => setAgree(e.target.checked)}
            className="mt-0.5 h-4 w-4 rounded border-slate-300 text-blue-700 focus:ring-blue-600"
          />
          <span>
            I agree to the Terms of Service and Privacy Notice, including the processing of my
            information for identity verification.
          </span>
        </label>

        <Button type="submit" size="lg" loading={loading} disabled={!agree} className="w-full">
          <ArrowRight className="h-4 w-4" /> Create account
        </Button>
        <p className="flex items-start gap-2 text-xs text-slate-500">
          <ShieldCheck className="mt-0.5 h-4 w-4 flex-none text-blue-700" />
          Next: verify your identity. After registration, complete FICA checks before making transactions with your checking account.
        </p>
      </form>
    </AuthLayout>
  );
}