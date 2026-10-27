import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Alert } from '../../components/Alert';
import { Button } from '../../components/Button';
import { ChatPanel } from '../../components/ChatPanel';
import { SelectField } from '../../components/SelectField';
import { TextField } from '../../components/TextField';
import { api, toApiError } from '../../lib/api';
import type { Paged, TicketDetail, TicketView } from '../../lib/types';

const P: Record<string, string> = { URGENT: 'bg-danger/15 text-danger', HIGH: 'bg-warning/15 text-warning', MEDIUM: 'bg-accent/15 text-accent', LOW: 'bg-surface-2 text-muted' };
const label = (s: string) => s.toLowerCase().replace('_', ' ');

export function AdminSupportPage() {
  const qc = useQueryClient();
  const [f, setF] = useState({ status: '', priority: '', category: '', q: '', mine: false });
  const [page, setPage] = useState(0); const [sel, setSel] = useState<string | null>(null); const [error, setError] = useState<string | null>(null);
  const list = useQuery({ queryKey: ['admin-tickets', f, page], queryFn: async () => (await api.get<Paged<TicketView>>('/admin/support/tickets', { params: { ...Object.fromEntries(Object.entries(f).filter(([, v]) => v !== '' && v !== false)), page, size: 20 } })).data, placeholderData: (p) => p });
  const detail = useQuery({ queryKey: ['admin-ticket', sel], enabled: !!sel, queryFn: async () => (await api.get<TicketDetail>(`/admin/support/tickets/${sel}`)).data });
  const refresh = () => { ['admin-tickets', 'admin-ticket', 'admin-dashboard'].forEach((k) => qc.invalidateQueries({ queryKey: [k] })); };
  const fail = (e: unknown) => setError(toApiError(e).message);
  const status = useMutation({ mutationFn: (a: { id: string; status: string; note?: string }) => api.post(`/admin/support/tickets/${a.id}/status`, { status: a.status, note: a.note }), onSuccess: () => { setError(null); refresh(); }, onError: fail });
  const prio = useMutation({ mutationFn: (a: { id: string; priority: string }) => api.post(`/admin/support/tickets/${a.id}/priority`, { priority: a.priority }), onSuccess: refresh, onError: fail });
  const assign = useMutation({ mutationFn: (id: string) => api.post(`/admin/support/tickets/${id}/assign`), onSuccess: refresh, onError: fail });
  const t = detail.data?.ticket;
  const pages = list.data ? Math.max(1, Math.ceil(list.data.total / list.data.size)) : 1;
  return (
    <div className="space-y-6"><div><h1 className="text-2xl font-bold">Support queue</h1><p className="text-sm text-muted">Newest and most urgent first. Replies reach the user instantly and as a notification.</p></div>
      {error && <Alert>{error}</Alert>}
      <div className="grid gap-3 rounded-2xl border border-line bg-surface p-4 sm:grid-cols-3 lg:grid-cols-5">
        <SelectField label="Status" value={f.status} onChange={(e) => { setF({ ...f, status: e.target.value }); setPage(0); }}><option value="">All</option>{['OPEN', 'IN_PROGRESS', 'AWAITING_USER', 'RESOLVED', 'CLOSED'].map((s) => <option key={s} value={s}>{label(s)}</option>)}</SelectField>
        <SelectField label="Priority" value={f.priority} onChange={(e) => { setF({ ...f, priority: e.target.value }); setPage(0); }}><option value="">All</option>{['URGENT', 'HIGH', 'MEDIUM', 'LOW'].map((s) => <option key={s} value={s}>{label(s)}</option>)}</SelectField>
        <SelectField label="Category" value={f.category} onChange={(e) => { setF({ ...f, category: e.target.value }); setPage(0); }}><option value="">All</option>{['PAYMENT', 'RECORD', 'BUG', 'ACCOUNT', 'OTHER'].map((s) => <option key={s} value={s}>{label(s)}</option>)}</SelectField>
        <TextField label="Search title / FP-T-…" value={f.q} onChange={(e) => { setF({ ...f, q: e.target.value }); setPage(0); }} /><label className="flex items-end gap-2 pb-2 text-sm"><input type="checkbox" className="h-4 w-4 accent-[rgb(var(--fp-accent))]" checked={f.mine} onChange={(e) => setF({ ...f, mine: e.target.checked })} />Assigned to me</label></div>
      <div className="grid gap-6 lg:grid-cols-[24rem_1fr]">
        <section aria-label="Tickets" className="space-y-2">{list.data?.items.length === 0 && <p className="rounded-2xl border border-dashed border-line p-6 text-sm text-muted">Nothing in the queue.</p>}
          {list.data?.items.map((x) => <button key={x.id} type="button" onClick={() => setSel(x.id)} className={`w-full rounded-2xl border bg-surface p-3 text-left text-sm ${sel === x.id ? 'border-accent' : 'border-line'}`}>
            <span className="flex items-center justify-between gap-2"><span className="font-medium">{x.ticketNo}</span><span className={`rounded-full px-2 py-0.5 text-xs ${P[x.priority]}`}>{label(x.priority)}</span></span><span className="block truncate">{x.title}</span>
            <span className="text-xs text-muted">{x.requesterName} · {label(x.status)} · {label(x.category)}{x.unread > 0 && <span className="ml-2 rounded-full bg-danger px-1.5 text-white">{x.unread} new</span>}</span></button>)}
          <div className="flex items-center justify-between pt-2 text-xs text-muted"><Button variant="secondary" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>Prev</Button><span>{page + 1}/{pages}</span><Button variant="secondary" disabled={page + 1 >= pages} onClick={() => setPage((p) => p + 1)}>Next</Button></div></section>
        <section>{!sel && <p className="rounded-2xl border border-dashed border-line p-8 text-center text-sm text-muted">Select a ticket.</p>}
          {sel && t && <div className="space-y-3"><div className="rounded-2xl border border-line bg-surface p-4"><div className="flex flex-wrap items-start justify-between gap-3"><div><h2 className="text-lg font-semibold">{t.ticketNo} · {t.title}</h2><p className="text-xs text-muted">{t.requesterName} ({t.requesterEmail}) · {label(t.category)}{t.paymentReference ? ` · payment ${t.paymentReference}` : ''} · {t.assignedTo ? `handled by ${t.assignedTo}` : 'unassigned'}</p></div>
            <div className="flex flex-wrap items-end gap-2"><SelectField label="Status" className="w-40" value={t.status} onChange={(e) => { const n = window.prompt('Note for the customer (optional):') ?? undefined; status.mutate({ id: t.id, status: e.target.value, note: n || undefined }); }}>{['OPEN', 'IN_PROGRESS', 'AWAITING_USER', 'RESOLVED', 'CLOSED'].map((s) => <option key={s} value={s}>{label(s)}</option>)}</SelectField>
              <SelectField label="Priority" className="w-32" value={t.priority} onChange={(e) => prio.mutate({ id: t.id, priority: e.target.value })}>{['URGENT', 'HIGH', 'MEDIUM', 'LOW'].map((s) => <option key={s} value={s}>{label(s)}</option>)}</SelectField>
              <Button variant="secondary" onClick={() => assign.mutate(t.id)}>Assign to me</Button></div></div></div>
            <ChatPanel key={t.id} ticketId={t.id} base="/admin/support" />
            <details className="rounded-xl border border-line bg-surface p-3 text-xs text-muted"><summary className="cursor-pointer">Ticket history</summary><ul className="mt-2 space-y-1">{detail.data?.events.map((e, i) => <li key={i}>{new Date(e.createdAt).toLocaleString()} — {e.actorName} ({e.actorRole.toLowerCase()}): {e.type.toLowerCase()}{e.from || e.to ? ` ${e.from ?? ''} → ${e.to ?? ''}` : ''}{e.note ? ` (${e.note})` : ''}</li>)}</ul></details></div>}</section>
      </div></div>
  );
}
