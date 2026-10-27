import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Alert } from '../../components/Alert';
import { Button } from '../../components/Button';
import { SelectField } from '../../components/SelectField';
import { TextField } from '../../components/TextField';
import { api, toApiError } from '../../lib/api';
import { downloadFile, getBlob } from '../../lib/download';
import { AMOUNT_RE, formatMoney } from '../../lib/money';
import type { AccountView, PageView, PaymentDetail, PaymentProfile, PaymentRequestView, PaymentView } from '../../lib/types';

const STATUS: Record<PaymentView['status'], string> = { PENDING: 'bg-warning/15 text-warning', SUCCESSFUL: 'bg-success/15 text-success', FAILED: 'bg-danger/15 text-danger', CANCELLED: 'bg-surface-2 text-muted', REFUNDED: 'bg-live/15 text-live' };
type Tab = 'send' | 'requests' | 'history';

export function PaymentsPage() {
  const [params] = useSearchParams();
  const [tab, setTab] = useState<Tab>(params.get('request') ? 'send' : 'send');
  const profile = useQuery({ queryKey: ['payments', 'profile'], queryFn: async () => (await api.get<PaymentProfile>('/payments/profile')).data });
  return (
    <div className="space-y-6">
      <div className="rounded-2xl border border-warning/50 bg-warning/10 px-4 py-3 text-sm" role="note"><strong>Simulated payments.</strong> No real money moves and no bank, card or UPI network is involved. Balances change only inside FlowPilot.</div>
      <div className="flex flex-wrap items-end justify-between gap-3"><div><h1 className="text-2xl font-bold">Payment Simulator</h1>{profile.data && <p className="text-sm text-muted">Your handle: <span className="num font-semibold text-ink">@{profile.data.handle}</span> — share it so others can pay you.</p>}</div>
        <div role="tablist" aria-label="Payments" className="flex gap-1 rounded-xl border border-line bg-surface p-1">{(['send', 'requests', 'history'] as Tab[]).map((t) => <button key={t} role="tab" aria-selected={tab === t} onClick={() => setTab(t)} className={`rounded-lg px-4 py-1.5 text-sm font-medium capitalize ${tab === t ? 'bg-accent text-white' : 'text-muted'}`}>{t}</button>)}</div></div>
      {tab === 'send' && <Send initialRef={params.get('request')} onDone={() => setTab('history')} />}
      {tab === 'requests' && <Requests />}
      {tab === 'history' && <History />}
    </div>
  );
}

function useRefresh() { const qc = useQueryClient(); return () => ['payments', 'accounts', 'transactions', 'dashboard', 'notifications', 'unread'].forEach((k) => qc.invalidateQueries({ queryKey: [k] })); }

function Send({ initialRef, onDone }: { initialRef: string | null; onDone: () => void }) {
  const refresh = useRefresh();
  const accounts = useQuery({ queryKey: ['accounts'], queryFn: async () => (await api.get<AccountView[]>('/accounts')).data });
  const [handle, setHandle] = useState(''); const [amount, setAmount] = useState(''); const [note, setNote] = useState(''); const [from, setFrom] = useState('');
  const [reqRef, setReqRef] = useState(initialRef ?? '');
  const [recipient, setRecipient] = useState<string | null>(null);
  const [pending, setPending] = useState<PaymentView | null>(null);
  const [error, setError] = useState<string | null>(null);
  const idem = useRef(crypto.randomUUID());
  const request = useQuery({ queryKey: ['payments', 'request', reqRef], enabled: /^FP-[A-Z0-9]{10}$/.test(reqRef), retry: false, queryFn: async () => (await api.get<PaymentRequestView>(`/payments/requests/by-reference/${reqRef}`)).data });
  const usable = accounts.data?.filter((a) => !request.data || a.currency === request.data.currency) ?? [];
  useEffect(() => { if (!from && usable[0]) setFrom(usable[0].id); }, [usable, from]);

  const lookup = async () => { setError(null); setRecipient(null); try { const r = await api.get<{ displayName: string }>(`/payments/lookup/${handle.trim().toLowerCase()}`); setRecipient(r.data.displayName); } catch (e) { setError(toApiError(e).message); } };
  const initiate = useMutation({
    mutationFn: () => api.post<PaymentView>('/payments', request.data ? { requestReference: reqRef, fromAccountId: from, note: note || null } : { toHandle: handle.trim().toLowerCase(), fromAccountId: from, amount, note: note || null }, { headers: { 'Idempotency-Key': idem.current } }),
    onSuccess: (r) => { setPending(r.data); setError(null); }, onError: (e) => setError(toApiError(e).message),
  });
  const confirm = useMutation({ mutationFn: (id: string) => api.post<PaymentView>(`/payments/${id}/confirm`), onSuccess: (r) => { refresh(); idem.current = crypto.randomUUID(); if (r.data.status === 'SUCCESSFUL') onDone(); else setError(`Payment ${r.data.status.toLowerCase()}.`); setPending(null); }, onError: (e) => { refresh(); setError(toApiError(e).message); setPending(null); } });
  const cancel = useMutation({ mutationFn: (id: string) => api.post(`/payments/${id}/cancel`), onSuccess: () => { setPending(null); refresh(); idem.current = crypto.randomUUID(); } });

  const start = () => {
    if (!request.data && !AMOUNT_RE.test(amount)) return setError('Enter a valid amount.');
    if (!request.data && !recipient) return setError('Look up the recipient first.');
    setError(null); initiate.mutate();
  };

  if (pending) {
    return (
      <section aria-labelledby="rv" className="mx-auto max-w-lg rounded-2xl border border-line bg-surface p-6"><h2 id="rv" className="mb-4 text-lg font-semibold">Review &amp; confirm</h2>
        {error && <Alert>{error}</Alert>}
        <dl className="space-y-2 text-sm"><div className="flex justify-between"><dt className="text-muted">To</dt><dd>{pending.counterpartyName}{pending.counterpartyHandle ? ` (@${pending.counterpartyHandle})` : ''}</dd></div><div className="flex justify-between"><dt className="text-muted">Amount</dt><dd className="num text-lg font-bold">{formatMoney(pending.amount, pending.currency)}</dd></div>
          <div className="flex justify-between"><dt className="text-muted">Reference</dt><dd className="num">{pending.reference}</dd></div>{pending.note && <div className="flex justify-between"><dt className="text-muted">Note</dt><dd>{pending.note}</dd></div>}</dl>
        <p className="mt-4 text-xs text-muted">Nothing has moved yet. This simulated payment expires in 15 minutes if not confirmed.</p>
        <div className="mt-4 flex gap-2"><Button loading={confirm.isPending} onClick={() => confirm.mutate(pending.id)}>Confirm simulated payment</Button><Button variant="ghost" loading={cancel.isPending} onClick={() => cancel.mutate(pending.id)}>Cancel</Button></div></section>
    );
  }
  return (
    <section aria-labelledby="sd" className="mx-auto max-w-lg rounded-2xl border border-line bg-surface p-6"><h2 id="sd" className="mb-4 text-lg font-semibold">{request.data ? 'Pay a request' : 'Send simulated money'}</h2>
      {error && <div className="mb-3"><Alert>{error}</Alert></div>}{request.isError && <div className="mb-3"><Alert>{toApiError(request.error).message}</Alert></div>}
      <div className="space-y-4">
        {request.data ? (<div className="rounded-xl border border-accent/40 bg-accent/10 p-3 text-sm"><p><strong>{request.data.counterpartyName}</strong> requests <strong className="num">{formatMoney(request.data.amount, request.data.currency)}</strong></p>{request.data.note && <p className="text-muted">{request.data.note}</p>}<p className="text-xs text-muted">Status: {request.data.status.toLowerCase()} · ref {request.data.reference}</p>
          <Button variant="ghost" onClick={() => setReqRef('')}>Send to a handle instead</Button></div>) : (<>
          <div className="flex items-end gap-2"><TextField className="flex-1" label="Recipient handle" placeholder="alex-4f2k" value={handle} onChange={(e) => { setHandle(e.target.value); setRecipient(null); }} /><Button variant="secondary" disabled={handle.trim().length < 3} onClick={lookup}>Find</Button></div>
          {recipient && <p role="status" className="text-sm text-success">Paying {recipient}</p>}
          <TextField label="Amount" inputMode="decimal" value={amount} onChange={(e) => setAmount(e.target.value)} />
          <TextField label="Or pay a request reference" placeholder="FP-XXXXXXXXXX" value={reqRef} onChange={(e) => setReqRef(e.target.value.toUpperCase())} /></>)}
        <SelectField label="Pay from" value={from} onChange={(e) => setFrom(e.target.value)}>{usable.map((a) => <option key={a.id} value={a.id}>{a.name} — {formatMoney(a.currentBalance, a.currency)}</option>)}</SelectField>
        <TextField label="Note (optional)" value={note} onChange={(e) => setNote(e.target.value)} />
        <Button loading={initiate.isPending} disabled={!from || (!!request.data && request.data.status !== 'OPEN')} onClick={start} className="w-full">Review payment</Button></div></section>
  );
}

function Requests() {
  const qc = useQueryClient();
  const [amount, setAmount] = useState(''); const [currency, setCurrency] = useState('USD'); const [handle, setHandle] = useState(''); const [note, setNote] = useState('');
  const [error, setError] = useState<string | null>(null); const [qr, setQr] = useState<{ ref: string; src: string } | null>(null);
  const list = useQuery({ queryKey: ['payments', 'requests'], queryFn: async () => (await api.get<PaymentRequestView[]>('/payments/requests')).data });
  const create = useMutation({ mutationFn: () => api.post<PaymentRequestView>('/payments/requests', { amount, currency: currency.toUpperCase(), fromHandle: handle.trim() ? handle.trim().toLowerCase() : null, note: note || null }),
    onSuccess: (r) => { setAmount(''); setNote(''); setHandle(''); setError(null); qc.invalidateQueries({ queryKey: ['payments', 'requests'] }); void showQr(r.data.reference); }, onError: (e) => setError(toApiError(e).message) });
  const cancel = useMutation({ mutationFn: (id: string) => api.delete(`/payments/requests/${id}`), onSuccess: () => qc.invalidateQueries({ queryKey: ['payments', 'requests'] }), onError: (e) => setError(toApiError(e).message) });
  const showQr = async (ref: string) => { try { const r = await api.get<string>(`/payments/requests/${ref}/qr`, { responseType: 'text' }); setQr({ ref, src: `data:image/svg+xml;charset=utf-8,${encodeURIComponent(r.data)}` }); } catch (e) { setError(toApiError(e).message); } };
  return (
    <div className="grid gap-6 lg:grid-cols-[1.3fr_1fr]">
      <section aria-label="Requests" className="space-y-3">{error && <Alert>{error}</Alert>}
        {list.data?.length === 0 && <p className="rounded-2xl border border-dashed border-line p-6 text-sm text-muted">No payment requests yet.</p>}
        {list.data?.map((r) => (<article key={r.id} className="rounded-2xl border border-line bg-surface p-4"><div className="flex items-start justify-between gap-3"><div><p className="font-semibold">{r.direction === 'CREATED' ? 'You asked' : 'Asked of you by'} {r.counterpartyName}</p><p className="num text-sm">{formatMoney(r.amount, r.currency)} · <span className="text-muted">{r.status.toLowerCase()} · ref {r.reference}</span></p>{r.note && <p className="text-sm text-muted">{r.note}</p>}</div>
          <div className="flex gap-1">{r.direction === 'CREATED' && r.status === 'OPEN' && <><Button variant="secondary" onClick={() => showQr(r.reference)}>QR</Button><Button variant="ghost" onClick={() => cancel.mutate(r.id)}>Cancel</Button></>}
            {r.direction === 'INCOMING' && r.status === 'OPEN' && <a className="rounded-xl bg-accent px-4 py-2 text-sm font-semibold text-white" href={`/app/payments?request=${r.reference}`}>Pay</a>}</div></div></article>))}</section>
      <section className="space-y-6"><div className="rounded-2xl border border-line bg-surface p-5"><h2 className="mb-3 text-lg font-semibold">Request a simulated payment</h2>
        <div className="space-y-4"><div className="grid grid-cols-2 gap-3"><TextField label="Amount" inputMode="decimal" value={amount} onChange={(e) => setAmount(e.target.value)} /><TextField label="Currency" maxLength={3} value={currency} onChange={(e) => setCurrency(e.target.value.toUpperCase())} /></div>
          <TextField label="From handle (optional)" hint="Leave empty for a shareable link / QR code" value={handle} onChange={(e) => setHandle(e.target.value)} /><TextField label="Note" value={note} onChange={(e) => setNote(e.target.value)} />
          <Button loading={create.isPending} disabled={!AMOUNT_RE.test(amount)} onClick={() => create.mutate()} className="w-full">Create request</Button></div></div>
        {qr && <div className="rounded-2xl border border-line bg-surface p-5 text-center"><h3 className="mb-2 font-semibold">Scan to pay (simulated)</h3><img src={qr.src} alt={`QR code for payment request ${qr.ref}`} className="mx-auto h-48 w-48 rounded-lg bg-white p-2" /><p className="num mt-2 text-sm">{qr.ref}</p></div>}</section>
    </div>
  );
}

function History() {
  const refresh = useRefresh();
  const [page, setPage] = useState(0); const [status, setStatus] = useState(''); const [open, setOpen] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const list = useQuery({ queryKey: ['payments', 'list', page, status], queryFn: async () => (await api.get<PageView<PaymentView>>('/payments', { params: { page, size: 10, status: status || undefined } })).data, placeholderData: (p) => p });
  const detail = useQuery({ queryKey: ['payments', 'detail', open], enabled: !!open, queryFn: async () => (await api.get<PaymentDetail>(`/payments/${open}`)).data });
  const act = useMutation({ mutationFn: (a: { id: string; kind: 'confirm' | 'cancel' | 'refund' | 'dispute' | 'withdraw'; reason?: string }) => a.kind === 'withdraw' ? api.delete(`/payments/${a.id}/dispute`) : api.post(`/payments/${a.id}/${a.kind}`, a.kind === 'refund' || a.kind === 'dispute' ? { reason: a.reason } : undefined),
    onSuccess: () => { setError(null); refresh(); }, onError: (e) => { setError(toApiError(e).message); refresh(); } });
  const ask = (msg: string) => window.prompt(msg) ?? null;
  const pages = list.data ? Math.max(1, Math.ceil(list.data.total / list.data.size)) : 1;
  return (
    <div className="space-y-4">{error && <Alert>{error}</Alert>}
      <SelectField label="Status" className="max-w-xs" value={status} onChange={(e) => { setPage(0); setStatus(e.target.value); }}><option value="">All</option>{['PENDING', 'SUCCESSFUL', 'FAILED', 'CANCELLED', 'REFUNDED'].map((s) => <option key={s} value={s}>{s.toLowerCase()}</option>)}</SelectField>
      {list.data?.items.length === 0 && <p className="rounded-2xl border border-dashed border-line p-6 text-sm text-muted">No payments yet.</p>}
      <ul className="space-y-3">{list.data?.items.map((p) => (
        <li key={p.id} className="rounded-2xl border border-line bg-surface p-4"><div className="flex flex-wrap items-start justify-between gap-3">
          <div><p className="font-semibold">{p.direction === 'SENT' ? 'To' : 'From'} {p.counterpartyName}<span className={`ml-2 rounded-full px-2 py-0.5 text-xs ${STATUS[p.status]}`}>{p.status.toLowerCase()}</span>{p.disputeStatus === 'OPEN' && <span className="ml-2 rounded-full bg-warning/15 px-2 py-0.5 text-xs text-warning">disputed</span>}</p>
            <p className="num text-sm text-muted">{p.reference} · {new Date(p.createdAt).toLocaleString()}{p.failureReason ? ` · ${p.failureReason}` : ''}</p></div>
          <p className={`num text-lg font-semibold ${p.direction === 'SENT' ? 'text-danger' : 'text-success'}`}>{p.direction === 'SENT' ? '−' : '+'}{formatMoney(p.amount, p.currency)}</p></div>
          <div className="mt-3 flex flex-wrap gap-1">
            {p.canConfirm && <Button onClick={() => act.mutate({ id: p.id, kind: 'confirm' })}>Confirm</Button>}{p.canCancel && <Button variant="ghost" onClick={() => act.mutate({ id: p.id, kind: 'cancel' })}>Cancel</Button>}
            {p.canRefund && <Button variant="secondary" onClick={() => { const r = ask('Reason for the refund (optional):'); if (r !== null && window.confirm('Refund this payment in full? This creates new ledger entries on both sides.')) act.mutate({ id: p.id, kind: 'refund', reason: r }); }}>Refund</Button>}
            {p.canDispute && <Button variant="ghost" onClick={() => { const r = ask('What went wrong?'); if (r) act.mutate({ id: p.id, kind: 'dispute', reason: r }); }}>Dispute</Button>}
            {p.disputeStatus === 'OPEN' && p.direction === 'SENT' && <Button variant="ghost" onClick={() => act.mutate({ id: p.id, kind: 'withdraw' })}>Withdraw dispute</Button>}
            {(p.status === 'SUCCESSFUL' || p.status === 'REFUNDED') && <Button variant="ghost" onClick={() => downloadFile(() => getBlob(`/payments/${p.id}/receipt`), `flowpilot-simulated-${p.reference}.pdf`).catch((e) => setError(toApiError(e).message))}>Receipt (PDF)</Button>}
            <Button variant="ghost" aria-expanded={open === p.id} onClick={() => setOpen(open === p.id ? null : p.id)}>History</Button></div>
          {open === p.id && detail.data && <ul className="mt-3 space-y-1 text-xs text-muted">{detail.data.events.map((e, i) => <li key={i}>{new Date(e.createdAt).toLocaleString()} — {e.fromStatus ? `${e.fromStatus.toLowerCase()} → ` : ''}{e.toStatus.toLowerCase()}{e.note ? ` (${e.note})` : ''}</li>)}{detail.data.refund && <li>Refund of {detail.data.refund.amount} recorded {new Date(detail.data.refund.createdAt).toLocaleString()}{detail.data.refund.reason ? ` — ${detail.data.refund.reason}` : ''}</li>}</ul>}
        </li>))}</ul>
      <div className="flex items-center justify-between text-sm text-muted"><Button variant="secondary" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>Previous</Button><span>Page {page + 1} of {pages}</span><Button variant="secondary" disabled={page + 1 >= pages} onClick={() => setPage((p) => p + 1)}>Next</Button></div>
    </div>
  );
}
