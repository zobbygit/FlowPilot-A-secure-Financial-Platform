import { useQuery, useQueryClient } from '@tanstack/react-query';
import { createContext, useCallback, useContext, useEffect, useMemo, type ReactNode } from 'react';
import { api, resetCsrf, toApiError } from '../lib/api';
import type { Me } from '../lib/types';

interface AuthContextValue {
  me: Me | null;
  isLoading: boolean;
  setMe: (me: Me | null) => void;
  signOut: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);
export const ME_KEY = ['me'] as const;

export function AuthProvider({ children }: { children: ReactNode }) {
  const qc = useQueryClient();

  const { data, isLoading } = useQuery<Me | null>({
    queryKey: ME_KEY,
    retry: false,
    staleTime: 60_000,
    queryFn: async () => {
      try {
        return (await api.get<Me>('/auth/me')).data;
      } catch (e) {
        if (toApiError(e).status === 401) return null;   // not signed in is a normal state
        throw e;
      }
    },
  });

  const setMe = useCallback((me: Me | null) => qc.setQueryData(ME_KEY, me), [qc]);

  useEffect(() => {
    const expired = () => { qc.clear(); qc.setQueryData(ME_KEY, null); };
    window.addEventListener('fp:session-expired', expired);
    return () => window.removeEventListener('fp:session-expired', expired);
  }, [qc]);

  const signOut = useCallback(async () => {
    try { await api.post('/auth/logout'); } catch { /* already signed out server-side */ }
    resetCsrf();
    qc.clear();
    qc.setQueryData(ME_KEY, null);
  }, [qc]);

  const value = useMemo(() => ({ me: data ?? null, isLoading, setMe, signOut }), [data, isLoading, setMe, signOut]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>');
  return ctx;
}
