import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { accountService } from "@/services/accountService";
import { useAuth } from "@/context/AuthContext";
import type { AccountResponse } from "@/types/api";

interface AccountContextValue {
  account: AccountResponse | null;
  loading: boolean;
  error: string | null;
  /** Full customer name, or a sensible fallback while loading. */
  fullName: string;
  reload: () => Promise<void>;
}

const AccountContext = createContext<AccountContextValue | undefined>(undefined);

export function AccountProvider({ children }: { children: ReactNode }) {
  const { isAuthenticated } = useAuth();
  const [account, setAccount] = useState<AccountResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await accountService.overview();
      setAccount(data);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not load account details.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (isAuthenticated) {
      void load();
    } else {
      setAccount(null);
      setError(null);
    }
  }, [isAuthenticated, load]);

  const fullName = useMemo(() => {
    if (account) return `${account.firstName} ${account.lastName}`.trim();
    return "";
  }, [account]);

  const value = useMemo(
    () => ({ account, loading, error, fullName, reload: load }),
    [account, loading, error, fullName, load],
  );

  return <AccountContext.Provider value={value}>{children}</AccountContext.Provider>;
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAccount(): AccountContextValue {
  const ctx = useContext(AccountContext);
  if (!ctx) throw new Error("useAccount must be used within AccountProvider");
  return ctx;
}