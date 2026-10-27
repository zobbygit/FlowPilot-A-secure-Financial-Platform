import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Alert } from '../../components/Alert';
import { Button } from '../../components/Button';
import { SelectField } from '../../components/SelectField';
import { TextField } from '../../components/TextField';
import { api, toApiError } from '../../lib/api';
import type { AdminUser, DefaultCategory, InvestigationSummary, Paged } from '../../lib/types';

export function AdminOversightPage() {
  const qc = useQueryClient();
  const [error, setError] = useState<string | null>(null);
  const [name, setName] = useState(''); const [kind, setKind] = useState<'EXPENSE' | 'INCOME'>('EXPENSE'); const [color, setColor] = useState('#8b5cf6');
  const cats = useQuery({ queryKey: ['admin-cats'], queryFn: async () => (await api.get<DefaultCategory[]>('/admin/oversight/categories')).data });
  const fail = (e: unknown) => setError(toApiError(e).message);
  const refresh = () => qc.invalidateQueries({ queryKey: ['admin-cats'] });
  const create = useMutation({ mutationFn: () => api.post('/admin/oversight/categories', { name, kind, color }), onSuccess: () => { setName(''); setError(null); refresh(); }, onError: fail });
  const rename = useMutation({ mutationFn: (c: DefaultCategory) => { const n = window.prompt('New name:', c.name); if (!n || !n.trim()) return Promise.resolve(null); return api.put(`/admin/oversight/categories/${c.id}`, { name: n.trim(), kind: c.kind, color: c.color }); }, onSuccess: refresh, onError: fail });
  const arch = useMutation({ mutationFn: (c: DefaultCategory) => api.post(`/admin/oversight/categories/${c.id}/${c.archived ? 'restore' : 'archive'}`), onSuccess: refresh, onError: fail });

  const [email, setEmail] = useState(''); const [reason, setReason] = useState(''); const [ticket, setTicket] = useState('');
  const [summary, setSummary] = useState<InvestigationSummary | null>(null);
  const open = useMutation({
    mutationFn: async () => {
      const u = (await api.get<Paged<AdminUser>>('/admin/users', { params: { q: email.trim(), size: 5 } })).data.items.find((x) => x.email === email.trim().toLowerCase());
      if (!u) throw new Error('No user with that exact email.');
      const id = (await api.post<{ id: string }>('/admin/oversight/investigations', { userId: u.id, reason, ticketId: ticket || null })).data.id;
      return (await api.get<InvestigationSummary>(`/admin/oversight/investigations/${id}`)).data;
    },
    onSuccess: (s) => { setSummary(s); setError(null); }, onError: (e) => setError(e instanceof Error && !('code' in e) ? e.message : toApiError(e).message),
  });
  return (
    <div className="space-y-8"><div><h1 className="text-2xl font-bold">Oversight</h1><p className="text-sm text-muted">Platform defaults and audited investigations. Admins have no default access to anyone's private financial detail.</p></div>
      {error && <Alert>{error}</Alert>}
      <section className="grid gap-6 lg:grid-cols-[1.4fr_1fr]" aria-labelledby="dc"><div className="rounded-2xl border border-line bg-surface p-5"><h2 id="dc" className="mb-3 text-lg font-semibold">Default categories</h2>
        <ul className="divide-y divide-line text-sm">{cats.data?.map((c) => <li key={c.id} className={`flex items-center justify-between gap-2 py-2 ${c.archived ? 'opacity-60' : ''}`}><span className="flex items-center gap-2"><span className="h-3 w-3 rounded-full" style={{ background: c.color ?? '#6b7280' }} aria-hidden="true" />{c.name}<span className="text-xs text-muted">{c.kind.toLowerCase()} · used {c.usage}×{c.archived ? ' · archived' : ''}</span></span>
          <span><Button variant="ghost" onClick={() => rename.mutate(c)}>Rename</Button><Button variant="ghost" onClick={() => arch.mutate(c)}>{c.archived ? 'Restore' : 'Archive'}</Button></span></li>)}</ul></div>
        <div className="rounded-2xl border border-line bg-surface p-5"><h3 className="mb-3 font-semibold">Add default category</h3><div className="space-y-3"><TextField label="Name" value={name} onChange={(e) => setName(e.target.value)} /><div className="grid grid-cols-2 gap-3"><SelectField label="Kind" value={kind} onChange={(e) => setKind(e.target.value as typeof kind)}><option value="EXPENSE">Expense</option><option value="INCOME">Income</option></SelectField><TextField label="Colour" type="color" value={color} onChange={(e) => setColor(e.target.value)} /></div><Button loading={create.isPending} disabled={!name.trim()} onClick={() => create.mutate()} className="w-full">Add</Button></div></div></section>
      <section className="rounded-2xl border border-warning/50 bg-warning/5 p-5" aria-labelledby="inv"><h2 id="inv" className="mb-1 text-lg font-semibold">Investigate a user</h2><p className="mb-3 text-sm text-muted">Requires a written reason (min 20 characters). Access lasts 24 hours, shows counts and balances only, and every step is audited.</p>
        <div className="grid gap-3 md:grid-cols-3"><TextField label="User email (exact)" value={email} onChange={(e) => setEmail(e.target.value)} /><TextField label="Related ticket id (optional)" value={ticket} onChange={(e) => setTicket(e.target.value)} /><TextField label="Reason" value={reason} onChange={(e) => setReason(e.target.value)} /></div>
        <Button className="mt-3" loading={open.isPending} disabled={!email.trim() || reason.trim().length < 20} onClick={() => open.mutate()}>Open investigation</Button>
        {summary && <div className="mt-4 rounded-xl bg-surface p-4 text-sm" aria-live="polite"><p className="font-semibold">{summary.userName} <span className="font-normal text-muted">({summary.userEmail}, {summary.status.toLowerCase()})</span></p>
          <dl className="mt-2 grid grid-cols-2 gap-2 sm:grid-cols-5"><div><dt className="text-xs text-muted">Accounts</dt><dd className="num">{summary.accounts}</dd></div><div><dt className="text-xs text-muted">Transactions 90d</dt><dd className="num">{summary.transactions90d}</dd></div><div><dt className="text-xs text-muted">Payments 90d</dt><dd className="num">{summary.payments90d}</dd></div><div><dt className="text-xs text-muted">Active bills</dt><dd className="num">{summary.openBills}</dd></div><div><dt className="text-xs text-muted">Budgets</dt><dd className="num">{summary.budgets}</dd></div></dl>
          <p className="mt-2 text-xs text-muted">Balances: {Object.entries(summary.balancesByCurrency).map(([c, v]) => `${v} ${c}`).join(' · ') || '—'}</p>
          <ul className="mt-2 space-y-0.5 text-xs">{summary.recentPayments.map((p) => <li key={p.reference} className="num">{p.reference} · {p.status.toLowerCase()} · {p.amount}</li>)}</ul>
          <ul className="mt-2 list-disc pl-5 text-xs text-muted">{summary.notes.map((n) => <li key={n}>{n}</li>)}</ul></div>}</section></div>
  );
}
