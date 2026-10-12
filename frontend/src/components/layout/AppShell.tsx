import { useState, type ReactNode } from "react";
import { NavLink, useNavigate } from "react-router-dom";
import {
  LayoutGrid,
  ArrowLeftRight,
  ShieldCheck,
  Wallet,
  LogOut,
  Menu,
  X,
  Lock,
  Headphones,
} from "lucide-react";
import { Logo } from "./Logo";
import { cn } from "@/lib/cn";
import { useAuth } from "@/context/AuthContext";
import { useAccount } from "@/context/AccountContext";
import { initials } from "@/lib/format";

interface NavItem {
  to: string;
  label: string;
  icon: ReactNode;
  badge?: number;
}

export function AppShell({ children, openDisputes }: { children: ReactNode; openDisputes?: number }) {
  const [mobileOpen, setMobileOpen] = useState(false);
  const { user, logout } = useAuth();
  const { fullName } = useAccount();
  const navigate = useNavigate();

  const nav: NavItem[] = [
    { to: "/dashboard", label: "Overview", icon: <LayoutGrid className="h-5 w-5" /> },
    { to: "/transactions", label: "Transactions", icon: <ArrowLeftRight className="h-5 w-5" /> },
    { to: "/disputes", label: "Disputes", icon: <ShieldCheck className="h-5 w-5" />, badge: openDisputes },
    { to: "/account", label: "Account details", icon: <Wallet className="h-5 w-5" /> },
  ];

  const handleLogout = () => {
    logout();
    navigate("/auth/login", { replace: true });
  };

  const SidebarContent = (
    <div className="flex h-full flex-col">
      <div className="px-5 py-5">
        <Logo />
      </div>
      <p className="px-5 pb-2 pt-2 text-[11px] font-semibold uppercase tracking-wider text-slate-400">
        Customer portal
      </p>
      <nav className="flex-1 space-y-1 px-3">
        {nav.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            onClick={() => setMobileOpen(false)}
            className={({ isActive }) =>
              cn(
                "flex items-center justify-between gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-colors",
                isActive
                  ? "bg-blue-50 text-blue-700"
                  : "text-slate-600 hover:bg-slate-50 hover:text-slate-900",
              )
            }
          >
            <span className="flex items-center gap-3">
              {item.icon}
              {item.label}
            </span>
            {item.badge ? (
              <span className="flex h-5 min-w-5 items-center justify-center rounded-full bg-blue-700 px-1.5 text-xs font-semibold text-white">
                {item.badge}
              </span>
            ) : null}
          </NavLink>
        ))}
      </nav>
      <div className="m-3 rounded-xl bg-slate-50 p-4">
        <div className="flex items-center gap-2 text-slate-700">
          <Headphones className="h-4 w-4 text-blue-700" />
          <p className="text-sm font-semibold">Here to help</p>
        </div>
        <p className="mt-1 text-xs text-slate-500">Questions about a debit or an open dispute?</p>
        <a href="#" className="mt-2 inline-block text-xs font-semibold text-blue-700 hover:text-blue-800">
          Contact support &rarr;
        </a>
      </div>
      <button
        onClick={handleLogout}
        className="flex items-center gap-3 border-t border-slate-100 px-5 py-4 text-sm font-medium text-slate-600 hover:bg-slate-50 hover:text-slate-900"
      >
        <LogOut className="h-5 w-5" /> Sign out
      </button>
    </div>
  );

  return (
    <div className="min-h-screen bg-slate-50">
      {/* Desktop sidebar */}
      <aside className="fixed inset-y-0 left-0 hidden w-64 border-r border-slate-200 bg-white lg:block">
        {SidebarContent}
      </aside>

      {/* Mobile drawer */}
      {mobileOpen && (
        <div className="fixed inset-0 z-40 lg:hidden">
          <div className="absolute inset-0 bg-slate-900/40" onClick={() => setMobileOpen(false)} />
          <div className="absolute inset-y-0 left-0 w-72 bg-white shadow-xl">
            <button
              onClick={() => setMobileOpen(false)}
              className="absolute right-3 top-4 text-slate-400 hover:text-slate-700"
              aria-label="Close menu"
            >
              <X className="h-5 w-5" />
            </button>
            {SidebarContent}
          </div>
        </div>
      )}

      <div className="lg:pl-64">
        {/* Topbar */}
        <header className="sticky top-0 z-30 flex h-16 items-center gap-3 border-b border-slate-200 bg-white px-4 lg:px-8">
          <button
            onClick={() => setMobileOpen(true)}
            className="rounded-lg p-2 text-slate-600 hover:bg-slate-100 lg:hidden"
            aria-label="Open menu"
          >
            <Menu className="h-5 w-5" />
          </button>
          <p className="text-sm font-medium text-slate-500">Transaction Dispute Portal</p>
          <div className="ml-auto flex items-center gap-4">
            <span className="hidden items-center gap-1.5 text-xs font-medium text-slate-500 sm:flex">
              <Lock className="h-3.5 w-3.5" /> Secure session
            </span>
            <div className="flex items-center gap-2.5">
              <span className="flex h-8 w-8 items-center justify-center rounded-full bg-blue-700 text-xs font-semibold text-white">
                {initials(fullName || user?.email || "A B")}
              </span>
              <span className="hidden text-sm font-semibold text-slate-800 sm:block">
                {fullName || user?.email || "Customer"}
              </span>
            </div>
          </div>
        </header>

        <main className="mx-auto max-w-6xl px-4 py-6 lg:px-8 lg:py-8">{children}</main>
      </div>
    </div>
  );
}