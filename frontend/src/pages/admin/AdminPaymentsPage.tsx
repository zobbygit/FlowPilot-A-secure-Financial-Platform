import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Alert } from '../../components/Alert';
import { Button } from '../../components/Button';
import { SelectField } from '../../components/SelectField';
import { TextField } from '../../components/TextField';
import { api, toApiError } from '../../lib/api';
import type { AdminPayment, Paged } from '../../lib/types';

interface Detail { payment: AdminPayment; events: { from: string; to: string; note: string; at: string; actor: string }[]; disputes: { status: string; reason: string; resolution: string; opened: string }[]; refund: { amount: string; reason: string; at: string } | null; relatedTickets: { ticket: string; status: string }[] }

export function AdminPaymentsPage() {
  const qc = useQueryClient();
  const [f, setF] = useState({ status: '', q: '', from: '', to: '', minAmount: '', suspicious: false });
  const [page, setPage] = useState(0); const [open, setOpen] = useState<string | null>(null); const [error, setError] = useState<string | null>(null);
  const list = useQuery({ queryKey: ['admin-payments', f, page], queryFn: async () => (await api.get<Paged<AdminPayment>>('/admin/payments', { params: { ...Object.fromEntries(Object.entries(f).filter(([, v]) => v !== '' && v !== false)), page, size: 20 } })).data, placeholderData: (p) => p });
  const detail = useQuery({ queryKey: ['admin-payment', open], enabled: !!open, queryFn: async () => (await api.get<Detail>(`/admin/payments/${open}`)).data });
  const refresh = () => { qc.invalidateQueries({ queryKey: ['admin-payments'] }); qc.invalidateQueries({ queryKey: ['admin-payment'] }); qc.invalidateQueries({ queryKey: ['admin-dashboard'] }); };
  const act = useMutation({ mutationFn: (a: { id: string; kind: 'refund' | 'dispute/reject'; reason: string }) => api.post(`/admin/payments/${a.id}/${a.kind}`, { reason: a.reason }), onSuccess: () => { setError(null); refresh(); }, onError: (e) => setError(toApiError(e).message) });
  const ask = (p: AdminPayment, kind: 'refund' | 'dispute/reject') => {
    const r = window.prompt(kind === 'refund' ? `Reason for refunding ${p.reference} in full (recorded in the audit log and shown to both parties):` : 'Explanation for the customer (shown to them):');
    if (r && r.trim().length >= 5 && (kind !== 'refund' || window.confirm(`Refund ${p.amount} ${p.currency} from ${p.payee} back to ${p.payer}? This moves simulated money in both ledgers.`))) act.mutate({ id: p.id, kind, reason: r.trim() });
  };
  const pages = list.data ? Math.max(1, Math.ceil(list.data.total / list.data.size)) : 1;
  return (
    <div className="space-y-6"><div><h1 className="text-2xl font-bold">Payment monitoring</h1><p className="text-sm text-muted">Simulated payments only. Every intervention requires a reason and is written to the audit log.</p></div>
      {error && <Alert>{error}</Alert>}
      <div className="grid gap-3 rounded-2xl border border-line bg-surface p-4 sm:grid-cols-3 lg:grid-cols-6">
        <SelectField label="Status" value={f.status} onChange={(e) => { setF({ ...f, status: e.target.value }); setPage(0); }}><option value="">All</option>{['PENDING', 'SUCCESSFUL', 'FAILED', 'CANCELLED', 'REFUNDED'].map((s) => <option key={s} value={s}>{s.toLowerCase()}</option>)}</SelectField>
        <TextField label="Reference" placeholder="FP-…" value={f.q} onChange={(e) => { setF({ ...f, q: e.target.value }); setPage(0); }} /><TextField label="From" type="date" value={f.from} onChange={(e) => setF({ ...f, from: e.target.value })} /><TextField label="To" type="date" value={f.to} onChange={(e) => setF({ ...f, to: e.target.value })} />
        <TextField label="Min amount" inputMode="decimal" value={f.minAmount} onChange={(e) => setF({ ...f, minAmount: e.target.value })} /><label className="flex items-end gap-2 pb-2 text-sm"><input type="checkbox" className="h-4 w-4 accent-[rgb(var(--fp-accent))]" checked={f.suspicious} onChange={(e) => { setF({ ...f, suspicious: e.target.checked }); setPage(0); }} />Flagged only</label></div>
      <ul className="space-y-3">{list.data?.items.map((p) => (
        <li key={p.id} className="rounded-2xl border border-line bg-surface p-4 text-sm"><div className="flex flex-wrap items-start justify-between gap-3"><div><p className="font-semibold">{p.payer} → {p.payee} <span className="num font-normal text-muted">· {p.reference}</span></p>
          <p className="text-xs text-muted">{new Date(p.createdAt).toLocaleString()} · {p.status.toLowerCase()}{p.failureReason ? ` (${p.failureReason})` : ''}{p.disputeStatus ? ` · dispute ${p.disputeStatus.toLowerCase().replace('_', ' ')}` : ''}</p>{p.flags.map((fl) => <span key={fl} className="mr-1 mt-1 inline-block rounded-full bg-warning/15 px-2 py-0.5 text-xs text-warning">⚑ {fl}</span>)}</div>
          <div className="text-right"><p className="num text-lg font-semibold">{p.amount} {p.currency}</p><div className="mt-1 flex justify-end gap-1">{p.status === 'SUCCESSFUL' && <Button variant="secondary" onClick={() => ask(p, 'refund')}>Refund</Button>}{p.disputeStatus === 'OPEN' && <Button variant="ghost" onClick={() => ask(p, 'dispute/reject')}>Reject dispute</Button>}<Button variant="ghost" aria-expanded={open === p.id} onClick={() => setOpen(open === p.id ? null : p.id)}>Details</Button></div></div></div>
          {open === p.id && detail.data && <div className="mt-3 grid gap-3 border-t border-line pt-3 text-xs text-muted sm:grid-cols-2"><div><p className="mb-1 font-semibold text-ink">History</p><ul className="space-y-0.5">{detail.data.events.map((e, i) => <li key={i}>{new Date(e.at).toLocaleString()} — {e.from !== 'null' ? `${e.from.toLowerCase()} → ` : ''}{e.to.toLowerCase()} by {e.actor}{e.note !== 'null' ? ` (${e.note})` : ''}</li>)}</ul></div>
            <div><p className="mb-1 font-semibold text-ink">Disputes, refund & tickets</p><ul className="space-y-0.5">{detail.data.disputes.map((d, i) => <li key={i}>Dispute {d.status.toLowerCase().replace('_', ' ')}: {d.reason}{d.resolution !== 'null' ? ` → ${d.resolution}` : ''}</li>)}{detail.data.refund && <li>Refunded {detail.data.refund.amount} on {new Date(detail.data.refund.at).toLocaleDateString()} — {detail.data.refund.reason}</li>}{detail.data.relatedTickets.map((t) => <li key={t.ticket}>Ticket {t.ticket} ({t.status.toLowerCase().replace('_', ' ')})</li>)}{detail.data.disputes.length === 0 && !detail.data.refund && detail.data.relatedTickets.length === 0 && <li>None</li>}</ul></div></div>}</li>))}</ul>
      <div className="flex items-center justify-between text-sm text-muted"><Button variant="secondary" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>Previous</Button><span>Page {page + 1} of {pages} · {list.data?.total ?? 0} payments</span><Button variant="secondary" disabled={page + 1 >= pages} onClick={() => setPage((p) => p + 1)}>Next</Button></div></div>
  );
}
