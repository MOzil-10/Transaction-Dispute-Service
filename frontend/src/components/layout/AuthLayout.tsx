import type { ReactNode } from "react";
import { Logo } from "./Logo";
import { ShieldCheck, Lock } from "lucide-react";

interface AuthLayoutProps {
  children: ReactNode;
  topRight: ReactNode;
  eyebrow: string;
  headline: ReactNode;
  blurb: string;
  highlights: { title: string; description: string; icon: ReactNode }[];
}

export function AuthLayout({ children, topRight, eyebrow, headline, blurb, highlights }: AuthLayoutProps) {
  return (
    <div className="flex min-h-screen flex-col lg:flex-row">
      {/* Brand panel */}
      <aside className="flex flex-col justify-between bg-slate-50 px-6 py-8 lg:w-[42%] lg:px-12 lg:py-10">
        <Logo />
        <div className="hidden lg:block">
          <p className="mb-3 flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-blue-700">
            <ShieldCheck className="h-4 w-4" />
            {eyebrow}
          </p>
          <h1 className="text-4xl font-extrabold leading-tight tracking-tight text-slate-900">
            {headline}
          </h1>
          <p className="mt-4 max-w-md text-sm leading-relaxed text-slate-500">{blurb}</p>
          <ul className="mt-10 space-y-5">
            {highlights.map((h) => (
              <li key={h.title} className="flex items-start gap-3">
                <span className="mt-0.5 flex h-8 w-8 flex-none items-center justify-center rounded-lg bg-white text-blue-700 ring-1 ring-slate-200">
                  {h.icon}
                </span>
                <div>
                  <p className="text-sm font-semibold text-slate-800">{h.title}</p>
                  <p className="text-sm text-slate-500">{h.description}</p>
                </div>
              </li>
            ))}
          </ul>
        </div>
        <p className="hidden items-center gap-2 text-xs text-slate-400 lg:flex">
          <Lock className="h-3.5 w-3.5" /> Secure access. Your information stays private.
        </p>
      </aside>

      {/* Form panel */}
      <main className="flex flex-1 flex-col px-6 py-8 lg:px-16 lg:py-10">
        <div className="flex justify-end text-sm text-slate-500">{topRight}</div>
        <div className="flex flex-1 items-center justify-center">
          <div className="w-full max-w-md py-10">{children}</div>
        </div>
        <footer className="flex flex-wrap items-center justify-center gap-x-5 gap-y-1 text-xs text-slate-400">
          <span>&copy; 2026 DisputeX</span>
          <a href="#" className="hover:text-slate-600">Privacy</a>
          <a href="#" className="hover:text-slate-600">Terms</a>
          <a href="#" className="font-medium text-blue-700 hover:text-blue-800">Contact support</a>
        </footer>
      </main>
    </div>
  );
}