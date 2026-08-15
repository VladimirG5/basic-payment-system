import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react';
import { setAuthToken } from '../api/authToken';
import { decodeJwt } from '../api/jwt';

interface AuthContextValue {
  email: string | null;
  isAuthenticated: boolean;
  login: (token: string) => void;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [email, setEmail] = useState<string | null>(null);

  const login = useCallback((token: string) => {
    setAuthToken(token);
    setEmail(decodeJwt(token).email);
  }, []);

  const logout = useCallback(() => {
    setAuthToken(null);
    setEmail(null);
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({ email, isAuthenticated: email !== null, login, logout }),
    [email, login, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
