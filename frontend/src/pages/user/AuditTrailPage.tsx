import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Button } from '../../components/Button';
import { SelectField } from '../../components/SelectField';
import { TextField } from '../../components/TextField';
import { api } from '../../lib/api';
import type { FinAuditItem, Paged } from '../../lib/types';

export function AuditTrailPage() {
  const [entityType, setEntityType] = useState(''); const [from, setFrom] = useState(''); const [to, setTo] = useState(''); const [page, setPage] = useState(0);
  const types = useQuery({ queryKey: ['fin-audit-types'], queryFn: async () => (await api.get<string[]>('/audit/financial/types')).data });
  const q = useQuery({ queryKey: ['fin-audit', entityType, from, to, page], queryFn: async () => (await api.get<Paged<FinAuditItem>>('/audit/financial', { params: { entityType: entityType || undefined, from: from || undefined, to: to || undefined, page, size: 30 } })).data, placeholderData: (p) => p });
  const pages = q.data ? Math.max(1, Math.ceil(q.data.total / q.data.size)) : 1;
  return (
    <div className="space-y-6"><div><h1 className="text-2xl font-bold">Financial audit trail</h1><p className="text-sm text-muted">A permanent, read-only record of important changes to your finances — who did what, when, and the before/after values. Entries can't be edited or deleted.</p></div>
      <div className="grid gap-3 rounded-2xl border border-line bg-surface p-4 sm:grid-cols-3"><SelectField label="Record type" value={entityType} onChange={(e) => { setEntityType(e.target.value); setPage(0); }}><option value="">All</option>{types.data?.map((t) => <option key={t} value={t}>{t.toLowerCase().replaceAll('_', ' ')}</option>)}</SelectField><TextField label="From" type="date" value={from} onChange={(e) => { setFrom(e.target.value); setPage(0); }} /><TextField label="To" type="date" value={to} onChange={(e) => { setTo(e.target.value); setPage(0); }} /></div>
      <section className="rounded-2xl border border-line bg-surface p-4"><ul className="divide-y divide-line text-sm">{q.data?.items.length === 0 && <li className="py-6 text-center text-muted">Nothing recorded yet.</li>}{q.data?.items.map((a) => <li key={a.id} className="py-2.5"><div className="flex flex-wrap justify-between gap-2"><span><span className="mr-2 rounded bg-surface-2 px-1.5 py-0.5 text-xs text-muted">{a.entityType.toLowerCase().replaceAll('_', ' ')}</span>{a.action.toLowerCase().replaceAll('_', ' ')}</span><span className="num text-xs text-muted">{new Date(a.at).toLocaleString()}</span></div>
        {a.summary && <p className="text-xs text-muted">{a.summary}</p>}{(a.previous || a.next) && <p className="num text-xs"><span className="text-muted">{a.previous ?? '—'}</span> → <span>{a.next ?? '—'}</span></p>}<p className="text-xs text-muted">by {a.actor}</p></li>)}</ul></section>
      <div className="flex items-center justify-between text-sm text-muted"><Button variant="secondary" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>Previous</Button><span>Page {page + 1} of {pages} · {q.data?.total ?? 0} entries</span><Button variant="secondary" disabled={page + 1 >= pages} onClick={() => setPage((p) => p + 1)}>Next</Button></div></div>
  );
}
