import { Navigate, Outlet, useLocation } from 'react-router-dom';
import type { Role } from '../lib/types';
import { useAuth } from './AuthContext';

/**
 * UX guard only. The backend authorises every request independently; hiding a route never grants
 * or denies real access.
 */
export function ProtectedRoute({ role }: { role: Role }) {
  const { me, isLoading } = useAuth();
  const location = useLocation();

  if (isLoading) return <p role="status" className="p-8 text-muted">Checking your session…</p>;
  if (!me) return <Navigate to={role === 'ADMIN' ? '/admin/login' : '/user/login'} replace state={{ from: location.pathname }} />;
  if (me.role !== role) return <Navigate to={me.role === 'ADMIN' ? '/admin' : '/app'} replace />;
  return <Outlet />;
}
