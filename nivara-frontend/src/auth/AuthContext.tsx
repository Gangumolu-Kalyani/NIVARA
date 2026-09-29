import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { setAuthToken } from "../api/client";
import { AuthApi } from "../api/endpoints";
import type { AccountResponse } from "../types/api";

interface AuthState {
  token: string | null;
  account: AccountResponse | null;
  loading: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (fullName: string, email: string, password: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthState | undefined>(undefined);
const STORAGE_KEY = "nivara.token";

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState<string | null>(() => localStorage.getItem(STORAGE_KEY));
  const [account, setAccount] = useState<AccountResponse | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setAuthToken(token);
    if (!token) {
      setAccount(null);
      setLoading(false);
      return;
    }
    AuthApi.me()
      .then(setAccount)
      .catch(() => {
        setToken(null);
        localStorage.removeItem(STORAGE_KEY);
      })
      .finally(() => setLoading(false));
  }, [token]);

  const value = useMemo<AuthState>(
    () => ({
      token,
      account,
      loading,
      async login(email, password) {
        const response = await AuthApi.login(email, password);
        localStorage.setItem(STORAGE_KEY, response.accessToken);
        setToken(response.accessToken);
        setAccount(response.account);
      },
      async register(fullName, email, password) {
        await AuthApi.register(fullName, email, password);
      },
      logout() {
        localStorage.removeItem(STORAGE_KEY);
        setToken(null);
        setAccount(null);
      },
    }),
    [token, account, loading],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within AuthProvider");
  }
  return context;
}
