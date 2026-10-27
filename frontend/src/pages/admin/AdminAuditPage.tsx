import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Button } from '../../components/Button';
import { SelectField } from '../../components/SelectField';
import { TextField } from '../../components/TextField';
import { api } from '../../lib/api';
import type { AuditItem, Paged } from '../../lib/types';

function Rows({ items }: { items: AuditItem[] }) {
  return <ul className="divide-y divide-line text-sm">{items.map((a) => <li key={a.id} className="py-2"><div className="flex flex-wrap justify-between gap-2"><span><span className={`mr-2 rounded px-1.5 py-0.5 text-xs ${a.scope === 'ADMIN' ? 'bg-accent/15 text-accent' : 'bg-surface-2 text-muted'}`}>{a.scope.toLowerCase()}</span><span className={a.outcome === 'FAILURE' ? 'text-danger' : ''}>{a.type.toLowerCase().replaceAll('_', ' ')}</span>{a.actorEmail && <span className="text-muted"> · {a.actorEmail}</span>}{a.ip && <span className="text-xs text-muted"> · {a.ip}</span>}</span><span className="num text-xs text-muted">{new Date(a.at).toLocaleString()}</span></div>{a.details && <p className="text-xs text-muted">{a.details}</p>}</li>)}</ul>;
}

export function AdminAuditPage({ timeline = false }: { timeline?: boolean }) {
  const [f, setF] = useState({ scope: '', type: '', actor: '', outcome: '', from: '', to: '' }); const [page, setPage] = useState(0);
  const q = useQuery({ queryKey: [timeline ? 'admin-timeline' : 'admin-audit', f, page], queryFn: async () => (await api.get<Paged<AuditItem>>(timeline ? '/admin/timeline' : '/admin/audit', { params: { ...(timeline ? {} : Object.fromEntries(Object.entries(f).filter(([, v]) => v !== ''))), page, size: timeline ? 30 : 50 } })).data, placeholderData: (p) => p });
  const pages = q.data ? Math.max(1, Math.ceil(q.data.total / q.data.size)) : 1;
  return (
    <div className="space-y-6"><div><h1 className="text-2xl font-bold">{timeline ? 'Activity timeline' : 'Audit logs'}</h1><p className="text-sm text-muted">{timeline ? 'Important platform events, updated live. Read-only.' : 'Append-only record of security, payment and administrative events. Viewing this page is itself recorded. Nobody can edit or delete entries.'}</p></div>
      {!timeline && <div className="grid gap-3 rounded-2xl border border-line bg-surface p-4 sm:grid-cols-3 lg:grid-cols-6">
        <SelectField label="Scope" value={f.scope} onChange={(e) => { setF({ ...f, scope: e.target.value }); setPage(0); }}><option value="">All</option><option value="USER">User</option><option value="ADMIN">Admin</option></SelectField>
        <TextField label="Event type" placeholder="LOGIN_FAILED" value={f.type} onChange={(e) => { setF({ ...f, type: e.target.value.toUpperCase() }); setPage(0); }} />
        <TextField label="Actor email" value={f.actor} onChange={(e) => { setF({ ...f, actor: e.target.value }); setPage(0); }} />
        <SelectField label="Outcome" value={f.outcome} onChange={(e) => { setF({ ...f, outcome: e.target.value }); setPage(0); }}><option value="">All</option><option value="SUCCESS">Success</option><option value="FAILURE">Failure</option></SelectField>
        <TextField label="From" type="date" value={f.from} onChange={(e) => setF({ ...f, from: e.target.value })} /><TextField label="To" type="date" value={f.to} onChange={(e) => setF({ ...f, to: e.target.value })} /></div>}
      <section className="rounded-2xl border border-line bg-surface p-4" aria-live={timeline ? 'polite' : undefined}>{q.data && <Rows items={q.data.items} />}{q.data?.items.length === 0 && <p className="text-sm text-muted">No events match.</p>}</section>
      <div className="flex items-center justify-between text-sm text-muted"><Button variant="secondary" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>Previous</Button><span>Page {page + 1} of {pages} · {q.data?.total ?? 0} events</span><Button variant="secondary" disabled={page + 1 >= pages} onClick={() => setPage((p) => p + 1)}>Next</Button></div></div>
  );
}
