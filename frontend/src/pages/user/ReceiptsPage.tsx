import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useRef, useState } from 'react';
import { Alert } from '../../components/Alert';
import { Button } from '../../components/Button';
import { SelectField } from '../../components/SelectField';
import { TextField } from '../../components/TextField';
import { api, toApiError } from '../../lib/api';
import { downloadFile, getBlob } from '../../lib/download';
import { checkFile, prettySize, RECEIPT_MAX, RECEIPT_TYPES } from '../../lib/files';
import { AMOUNT_RE } from '../../lib/money';
import type { MemoView, PageView, ReceiptView, TransactionView } from '../../lib/types';

const STATUS: Record<ReceiptView['reviewStatus'], string> = { UNREVIEWED: 'bg-surface-2 text-muted', VERIFIED: 'bg-success/15 text-success', NEEDS_REVIEW: 'bg-warning/15 text-warning' };

export function ReceiptsPage() {
  const [tab, setTab] = useState<'receipts' | 'memos'>('receipts');
  return (
    <div className="space-y-6">
      <div><h1 className="text-2xl font-bold">Receipts &amp; Memos</h1><p className="text-sm text-muted">Personal records you keep for yourself — not official tax invoices or certified documents.</p></div>
      <div role="tablist" aria-label="Sections" className="flex w-fit gap-1 rounded-xl border border-line bg-surface p-1">
        {(['receipts', 'memos'] as const).map((t) => <button key={t} role="tab" aria-selected={tab === t} onClick={() => setTab(t)} className={`rounded-lg px-4 py-1.5 text-sm font-medium capitalize ${tab === t ? 'bg-accent text-white' : 'text-muted'}`}>{t}</button>)}
      </div>
      {tab === 'receipts' ? <Receipts /> : <Memos />}
    </div>
  );
}

function Receipts() {
  const qc = useQueryClient();
  const fileRef = useRef<HTMLInputElement>(null);
  const [error, setError] = useState<string | null>(null);
  const [f, setF] = useState({ receiptNumber: '', merchant: '', subtotal: '', tax: '', total: '', currency: 'USD', transactionId: '' });
  const list = useQuery({ queryKey: ['receipts'], queryFn: async () => (await api.get<ReceiptView[]>('/receipts')).data });
  const txs = useQuery({ queryKey: ['transactions', 'for-receipts'], queryFn: async () => (await api.get<PageView<TransactionView>>('/transactions', { params: { size: 50 } })).data.items });

  const upload = useMutation({
    mutationFn: (file: File) => {
      const fd = new FormData(); fd.append('file', file);
      (Object.entries(f) as [string, string][]).forEach(([k, v]) => { if (v !== '') fd.append(k, v); });
      return api.post('/receipts', fd);
    },
    onSuccess: () => { setError(null); if (fileRef.current) fileRef.current.value = ''; setF({ ...f, receiptNumber: '', merchant: '', subtotal: '', tax: '', total: '', transactionId: '' }); qc.invalidateQueries({ queryKey: ['receipts'] }); },
    onError: (e) => setError(toApiError(e).message),
  });
  const review = useMutation({ mutationFn: (a: { id: string; status: string }) => api.post(`/receipts/${a.id}/review`, { status: a.status }), onSuccess: () => qc.invalidateQueries({ queryKey: ['receipts'] }), onError: (e) => setError(toApiError(e).message) });
  const remove = useMutation({ mutationFn: (id: string) => api.delete(`/receipts/${id}`), onSuccess: () => qc.invalidateQueries({ queryKey: ['receipts'] }), onError: (e) => setError(toApiError(e).message) });

  const submit = () => {
    const file = fileRef.current?.files?.[0];
    if (!file) return setError('Choose a file first.');
    const bad = checkFile(file, RECEIPT_TYPES, RECEIPT_MAX);
    if (bad) return setError(bad);
    for (const k of ['subtotal', 'tax', 'total'] as const) if (f[k] !== '' && !AMOUNT_RE.test(f[k])) return setError(`Enter a valid ${k} amount.`);
    if (f.subtotal && f.tax && f.total && Math.abs(Number(f.subtotal) + Number(f.tax) - Number(f.total)) > 0.00005) return setError('Subtotal plus tax must equal the total.');
    setError(null); upload.mutate(file);
  };
  const open = (r: ReceiptView, dl: boolean) => downloadFile(() => getBlob(`/receipts/${r.id}/file`, { download: String(dl) }), r.fileName).catch((e) => setError(toApiError(e).message));

  return (
    <div className="grid gap-6 lg:grid-cols-[1.4fr_1fr]">
      <section aria-labelledby="rl" className="space-y-3"><h2 id="rl" className="sr-only">Your receipts</h2>{error && <Alert>{error}</Alert>}
        {list.data?.length === 0 && <p className="rounded-2xl border border-dashed border-line p-6 text-sm text-muted">No receipts yet.</p>}
        {list.data?.map((r) => (
          <article key={r.id} className="rounded-2xl border border-line bg-surface p-4">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div><h3 className="font-semibold">{r.merchant ?? r.fileName}<span className={`ml-2 rounded-full px-2 py-0.5 text-xs ${STATUS[r.reviewStatus]}`}>{r.reviewStatus.toLowerCase().replace('_', ' ')}</span></h3>
                <p className="text-xs text-muted">{r.receiptNumber ? `#${r.receiptNumber} · ` : ''}{r.fileName} · {prettySize(r.sizeBytes)}{r.total ? ` · total ${r.total} ${r.currency ?? ''}` : ''}</p></div>
              <div className="flex flex-wrap gap-1"><Button variant="secondary" onClick={() => open(r, false)}>View</Button><Button variant="ghost" onClick={() => open(r, true)}>Download</Button>
                {r.reviewStatus !== 'VERIFIED' && <Button variant="ghost" onClick={() => review.mutate({ id: r.id, status: 'VERIFIED' })}>Mark verified</Button>}
                {r.reviewStatus !== 'NEEDS_REVIEW' && <Button variant="ghost" onClick={() => review.mutate({ id: r.id, status: 'NEEDS_REVIEW' })}>Needs review</Button>}
                <Button variant="ghost" aria-label={`Delete receipt ${r.fileName}`} onClick={() => { if (window.confirm('Delete this receipt and its file?')) remove.mutate(r.id); }}>Delete</Button></div></div>
            <details className="mt-2 text-xs text-muted"><summary className="cursor-pointer">Status history</summary><ul className="mt-1 space-y-0.5">{r.history.map((h, i) => <li key={i}>{new Date(h.changedAt).toLocaleString()} — {h.fromStatus ? `${h.fromStatus.toLowerCase()} → ` : ''}{h.toStatus.toLowerCase()}{h.note ? ` (${h.note})` : ''}</li>)}</ul></details>
          </article>))}
      </section>
      <section aria-labelledby="ru" className="rounded-2xl border border-line bg-surface p-5"><h2 id="ru" className="mb-3 text-lg font-semibold">Upload receipt</h2>
        <div className="space-y-4">
          <div><label htmlFor="rf" className="mb-1.5 block text-sm font-medium">File (JPEG, PNG, WebP or PDF, up to 8 MB)</label><input id="rf" ref={fileRef} type="file" accept={RECEIPT_TYPES.join(',')} className="w-full text-sm" /></div>
          <TextField label="Merchant" value={f.merchant} onChange={(e) => setF({ ...f, merchant: e.target.value })} />
          <TextField label="Receipt number" value={f.receiptNumber} onChange={(e) => setF({ ...f, receiptNumber: e.target.value })} />
          <div className="grid grid-cols-3 gap-3"><TextField label="Subtotal" inputMode="decimal" value={f.subtotal} onChange={(e) => setF({ ...f, subtotal: e.target.value })} /><TextField label="Tax" inputMode="decimal" value={f.tax} onChange={(e) => setF({ ...f, tax: e.target.value })} /><TextField label="Total" inputMode="decimal" value={f.total} onChange={(e) => setF({ ...f, total: e.target.value })} /></div>
          <div className="grid grid-cols-2 gap-3"><TextField label="Currency" maxLength={3} value={f.currency} onChange={(e) => setF({ ...f, currency: e.target.value.toUpperCase() })} />
            <SelectField label="Attach to transaction" value={f.transactionId} onChange={(e) => setF({ ...f, transactionId: e.target.value })}><option value="">Not linked</option>{txs.data?.map((t) => <option key={t.id} value={t.id}>{t.occurredOn} · {t.merchant ?? t.description ?? t.type.toLowerCase()} · {t.amount}</option>)}</SelectField></div>
          <Button loading={upload.isPending} onClick={submit} className="w-full">Upload</Button></div></section>
    </div>
  );
}

function Memos() {
  const qc = useQueryClient();
  const [q, setQ] = useState(''); const [tag, setTag] = useState(''); const [pinnedOnly, setPinnedOnly] = useState(false);
  const [editing, setEditing] = useState<MemoView | null>(null);
  const [form, setForm] = useState({ title: '', body: '', tags: '', followUpDate: '' });
  const [error, setError] = useState<string | null>(null);
  const list = useQuery({ queryKey: ['memos', q, tag, pinnedOnly], queryFn: async () => (await api.get<MemoView[]>('/memos', { params: { q: q || undefined, tag: tag || undefined, pinned: pinnedOnly } })).data });
  const refresh = () => qc.invalidateQueries({ queryKey: ['memos'] });
  const save = useMutation({
    mutationFn: () => { const body = { ...form, followUpDate: form.followUpDate || null, pinned: editing?.pinned ?? false, transactionId: editing?.transactionId ?? null, goalId: editing?.goalId ?? null }; return editing ? api.put(`/memos/${editing.id}`, body) : api.post('/memos', body); },
    onSuccess: () => { setEditing(null); setForm({ title: '', body: '', tags: '', followUpDate: '' }); setError(null); refresh(); }, onError: (e) => setError(toApiError(e).message),
  });
  const pin = useMutation({ mutationFn: (m: MemoView) => api.put(`/memos/${m.id}`, { title: m.title, body: m.body, tags: m.tags.join(','), pinned: !m.pinned, followUpDate: m.followUpDate, transactionId: m.transactionId, goalId: m.goalId }), onSuccess: refresh });
  const remove = useMutation({ mutationFn: (id: string) => api.delete(`/memos/${id}`), onSuccess: refresh });
  const edit = (m: MemoView) => { setEditing(m); setForm({ title: m.title, body: m.body ?? '', tags: m.tags.join(', '), followUpDate: m.followUpDate ?? '' }); };
  return (
    <div className="grid gap-6 lg:grid-cols-[1.4fr_1fr]">
      <section className="space-y-3" aria-label="Memos">
        <div className="grid gap-3 rounded-2xl border border-line bg-surface p-3 sm:grid-cols-3"><TextField label="Search" value={q} onChange={(e) => setQ(e.target.value)} /><TextField label="Tag" value={tag} onChange={(e) => setTag(e.target.value)} /><label className="flex items-end gap-2 pb-2 text-sm"><input type="checkbox" className="h-4 w-4 accent-[rgb(var(--fp-accent))]" checked={pinnedOnly} onChange={(e) => setPinnedOnly(e.target.checked)} />Pinned only</label></div>
        {list.data?.length === 0 && <p className="rounded-2xl border border-dashed border-line p-6 text-sm text-muted">No memos match.</p>}
        {list.data?.map((m) => (<article key={m.id} className="rounded-2xl border border-line bg-surface p-4"><div className="flex items-start justify-between gap-3"><div><h3 className="font-semibold">{m.pinned && '📌 '}{m.title}</h3>{m.body && <p className="mt-1 whitespace-pre-wrap text-sm text-muted">{m.body}</p>}
          <p className="mt-2 text-xs text-muted">{m.tags.map((t) => `#${t}`).join(' ')}{m.followUpDate ? ` · follow up ${m.followUpDate}` : ''}</p></div>
          <div className="flex shrink-0 gap-1"><Button variant="ghost" onClick={() => pin.mutate(m)}>{m.pinned ? 'Unpin' : 'Pin'}</Button><Button variant="ghost" onClick={() => edit(m)}>Edit</Button><Button variant="ghost" aria-label={`Delete memo ${m.title}`} onClick={() => { if (window.confirm('Delete this memo?')) remove.mutate(m.id); }}>Delete</Button></div></div></article>))}
      </section>
      <section className="rounded-2xl border border-line bg-surface p-5"><h2 className="mb-3 text-lg font-semibold">{editing ? 'Edit memo' : 'New memo'}</h2>{error && <div className="mb-3"><Alert>{error}</Alert></div>}
        <div className="space-y-4"><TextField label="Title" value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} />
          <div><label htmlFor="mb" className="mb-1.5 block text-sm font-medium">Note</label><textarea id="mb" rows={5} maxLength={4000} value={form.body} onChange={(e) => setForm({ ...form, body: e.target.value })} className="w-full rounded-xl border border-line bg-surface-2/60 px-3 py-2 text-sm" /></div>
          <TextField label="Tags" hint="Comma-separated, up to 8" value={form.tags} onChange={(e) => setForm({ ...form, tags: e.target.value })} /><TextField label="Follow-up date" type="date" value={form.followUpDate} onChange={(e) => setForm({ ...form, followUpDate: e.target.value })} />
          <div className="flex gap-2"><Button loading={save.isPending} disabled={!form.title.trim()} onClick={() => save.mutate()}>{editing ? 'Save' : 'Add memo'}</Button>{editing && <Button variant="ghost" onClick={() => { setEditing(null); setForm({ title: '', body: '', tags: '', followUpDate: '' }); }}>Cancel</Button>}</div></div></section>
    </div>
  );
}
