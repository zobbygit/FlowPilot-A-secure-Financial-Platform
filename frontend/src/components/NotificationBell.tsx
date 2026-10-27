import { useQuery } from '@tanstack/react-query';
import { Bell } from 'lucide-react';
import { Link } from 'react-router-dom';
import { api } from '../lib/api';

export function NotificationBell({ to = '/app/notifications' }: { to?: string }) {
  const q = useQuery({ queryKey: ['unread'], queryFn: async () => (await api.get<{ unread: number }>('/notifications/unread-count')).data.unread, refetchInterval: 120_000 });
  const n = q.data ?? 0;
  return (
    <Link to={to} aria-label={n > 0 ? `Notifications, ${n} unread` : 'Notifications'}
      className="relative flex h-9 w-9 items-center justify-center rounded-xl border border-line bg-surface text-muted hover:text-ink">
      <Bell className="h-4 w-4" aria-hidden="true" />
      {n > 0 && <span className="num absolute -right-1 -top-1 flex h-4 min-w-4 items-center justify-center rounded-full bg-danger px-1 text-[10px] font-bold text-white">{n > 99 ? '99+' : n}</span>}
    </Link>
  );
}
