import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from "react";
import { authService } from "@/services/authService";
import { tokenStore, type StoredUser } from "@/services/token";
import type { LoginRequest, RegisterRequest } from "@/types/api";

interface AuthContextValue {
  user: StoredUser | null;
  isAuthenticated: boolean;
  login: (body: LoginRequest) => Promise<void>;
  register: (body: RegisterRequest) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<StoredUser | null>(() => tokenStore.getUser());

  const persist = useCallback((token: string, u: StoredUser) => {
    tokenStore.set(token);
    tokenStore.setUser(u);
    setUser(u);
  }, []);

  const login = useCallback(
    async (body: LoginRequest) => {
      const res = await authService.login(body);
      persist(res.token, { customerId: res.customerId, email: res.email });
    },
    [persist],
  );

  const register = useCallback(
    async (body: RegisterRequest) => {
      const res = await authService.register(body);
      persist(res.token, { customerId: res.customerId, email: res.email });
    },
    [persist],
  );

  const logout = useCallback(() => {
    tokenStore.clear();
    setUser(null);
  }, []);

  const value = useMemo(
    () => ({ user, isAuthenticated: !!user, login, register, logout }),
    [user, login, register, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within AuthProvider");
  return ctx;
}