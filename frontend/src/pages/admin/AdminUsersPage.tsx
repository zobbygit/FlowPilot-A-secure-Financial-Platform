import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Alert } from '../../components/Alert';
import { Button } from '../../components/Button';
import { SelectField } from '../../components/SelectField';
import { TextField } from '../../components/TextField';
import { api, toApiError } from '../../lib/api';
import type { AdminUser, Paged } from '../../lib/types';

export function AdminUsersPage() {
  const qc = useQueryClient();
  const [q, setQ] = useState(''); const [status, setStatus] = useState(''); const [page, setPage] = useState(0); const [error, setError] = useState<string | null>(null);
  const list = useQuery({ queryKey: ['admin-users', q, status, page], queryFn: async () => (await api.get<Paged<AdminUser>>('/admin/users', { params: { q: q || undefined, status: status || undefined, page, size: 20 } })).data, placeholderData: (p) => p });
  const act = useMutation({ mutationFn: (a: { id: string; kind: 'suspend' | 'reactivate'; reason: string }) => api.post(`/admin/users/${a.id}/${a.kind}`, { reason: a.reason }), onSuccess: () => { setError(null); qc.invalidateQueries({ queryKey: ['admin-users'] }); qc.invalidateQueries({ queryKey: ['admin-dashboard'] }); }, onError: (e) => setError(toApiError(e).message) });
  const ask = (u: AdminUser, kind: 'suspend' | 'reactivate') => { const r = window.prompt(`Reason to ${kind} ${u.email} (recorded in the audit log, min 5 characters):`); if (r && r.trim().length >= 5) act.mutate({ id: u.id, kind, reason: r.trim() }); };
  const pages = list.data ? Math.max(1, Math.ceil(list.data.total / list.data.size)) : 1;
  return (
    <div className="space-y-6"><div><h1 className="text-2xl font-bold">Users</h1><p className="text-sm text-muted">Accounts, status and sign-in activity. Passwords, tokens and financial records are never shown here.</p></div>
      {error && <Alert>{error}</Alert>}
      <div className="grid gap-3 rounded-2xl border border-line bg-surface p-4 sm:grid-cols-3"><TextField label="Search name or email" value={q} onChange={(e) => { setQ(e.target.value); setPage(0); }} />
        <SelectField label="Status" value={status} onChange={(e) => { setStatus(e.target.value); setPage(0); }}><option value="">All</option>{['PENDING_VERIFICATION', 'ACTIVE', 'SUSPENDED', 'DELETED'].map((s) => <option key={s} value={s}>{s.toLowerCase().replace('_', ' ')}</option>)}</SelectField></div>
      <div className="overflow-x-auto rounded-2xl border border-line bg-surface"><table className="w-full text-left text-sm"><caption className="sr-only">Users</caption>
        <thead className="bg-surface-2 text-xs uppercase text-muted"><tr>{['User', 'Status', 'Joined', 'Last sign-in', 'Accounts', 'Open tickets', ''].map((h) => <th key={h} scope="col" className="px-3 py-2">{h}</th>)}</tr></thead>
        <tbody className="divide-y divide-line">{list.data?.items.map((u) => <tr key={u.id}><td className="px-3 py-2"><p className="font-medium">{u.fullName}</p><p className="text-xs text-muted">{u.email}{u.emailVerified ? '' : ' (unverified)'}</p></td>
          <td className="px-3"><span className={`rounded-full px-2 py-0.5 text-xs ${u.status === 'ACTIVE' ? 'bg-success/15 text-success' : u.status === 'SUSPENDED' ? 'bg-danger/15 text-danger' : 'bg-surface-2 text-muted'}`}>{u.status.toLowerCase().replace('_', ' ')}</span></td>
          <td className="num px-3 text-xs">{new Date(u.createdAt).toLocaleDateString()}</td><td className="num px-3 text-xs">{u.lastLogin ? new Date(u.lastLogin).toLocaleString() : '—'}</td><td className="num px-3">{u.accounts}</td><td className="num px-3">{u.openTickets}</td>
          <td className="px-3 text-right">{u.status === 'ACTIVE' && <Button variant="ghost" onClick={() => ask(u, 'suspend')}>Suspend</Button>}{u.status === 'SUSPENDED' && <Button variant="secondary" onClick={() => ask(u, 'reactivate')}>Reactivate</Button>}</td></tr>)}</tbody></table></div>
      <div className="flex items-center justify-between text-sm text-muted"><Button variant="secondary" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>Previous</Button><span>Page {page + 1} of {pages} · {list.data?.total ?? 0} users</span><Button variant="secondary" disabled={page + 1 >= pages} onClick={() => setPage((p) => p + 1)}>Next</Button></div></div>
  );
}
