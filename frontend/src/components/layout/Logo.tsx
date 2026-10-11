import { ShieldCheck } from "lucide-react";
import { cn } from "@/lib/cn";

export function Logo({ className }: { className?: string }) {
  return (
    <div className={cn("flex items-center gap-2.5", className)}>
      <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-blue-700 text-white">
        <ShieldCheck className="h-5 w-5" />
      </span>
      <span className="text-lg font-bold tracking-tight text-slate-900">DisputeX</span>
    </div>
  );
}