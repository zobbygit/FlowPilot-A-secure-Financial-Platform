import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Button } from '../../components/Button';
import { api } from '../../lib/api';
import type { NotificationPage } from '../../lib/types';

export function NotificationsPage() {
  const qc = useQueryClient();
  const [page, setPage] = useState(0);
  const [unreadOnly, setUnreadOnly] = useState(false);
  const q = useQuery({ queryKey: ['notifications', page, unreadOnly], queryFn: async () => (await api.get<NotificationPage>('/notifications', { params: { page, size: 20, unreadOnly } })).data, placeholderData: (p) => p });
  const refresh = () => { qc.invalidateQueries({ queryKey: ['notifications'] }); qc.invalidateQueries({ queryKey: ['unread'] }); };
  const read = useMutation({ mutationFn: (id: string) => api.post(`/notifications/${id}/read`), onSuccess: refresh });
  const readAll = useMutation({ mutationFn: () => api.post('/notifications/read-all'), onSuccess: refresh });
  const pages = q.data ? Math.max(1, Math.ceil(q.data.total / q.data.size)) : 1;
  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div><h1 className="text-2xl font-bold">Notifications</h1><p className="text-sm text-muted">{q.data ? `${q.data.unread} unread` : ''} · choose what you receive in Settings → Notifications</p></div>
        <div className="flex gap-2"><Button variant="secondary" aria-pressed={unreadOnly} onClick={() => { setPage(0); setUnreadOnly((v) => !v); }}>{unreadOnly ? 'Showing unread' : 'Show unread only'}</Button><Button variant="secondary" loading={readAll.isPending} onClick={() => readAll.mutate()}>Mark all read</Button></div>
      </div>
      {q.data?.items.length === 0 && <p className="rounded-2xl border border-dashed border-line p-6 text-sm text-muted">You're all caught up.</p>}
      <ul className="space-y-2">
        {q.data?.items.map((n) => (
          <li key={n.id} className={`rounded-2xl border p-4 ${n.read ? 'border-line bg-surface' : 'border-accent/50 bg-accent/10'}`}>
            <div className="flex items-start justify-between gap-3">
              <div><p className="font-medium">{!n.read && <span className="mr-2 inline-block h-2 w-2 rounded-full bg-accent" aria-label="unread" />}{n.title}</p>
                {n.body && <p className="text-sm text-muted">{n.body}</p>}
                <p className="mt-1 text-xs text-muted">{n.type.toLowerCase().replace('_', ' ')} · {new Date(n.createdAt).toLocaleString()}</p></div>
              <div className="flex shrink-0 gap-2">{n.link && <Link to={n.link} onClick={() => !n.read && read.mutate(n.id)} className="text-sm font-medium text-accent underline">Open</Link>}{!n.read && <Button variant="ghost" onClick={() => read.mutate(n.id)}>Mark read</Button>}</div>
            </div>
          </li>))}
      </ul>
      <div className="flex items-center justify-between text-sm text-muted"><Button variant="secondary" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>Previous</Button><span>Page {page + 1} of {pages}</span><Button variant="secondary" disabled={page + 1 >= pages} onClick={() => setPage((p) => p + 1)}>Next</Button></div>
    </div>
  );
}
