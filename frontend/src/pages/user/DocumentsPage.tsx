import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useRef, useState } from 'react';
import { Alert } from '../../components/Alert';
import { Button } from '../../components/Button';
import { SelectField } from '../../components/SelectField';
import { TextField } from '../../components/TextField';
import { api, toApiError } from '../../lib/api';
import { downloadFile } from '../../lib/download';
import type { DocPreview, DocTemplate, PageView, PaymentView, TransactionView } from '../../lib/types';

const SOURCES: [string, string][] = [['MANUAL', 'Typed when generating'], ['USER_NAME', 'My name'], ['TODAY', 'Today’s date'], ['TX_DATE', 'Transaction date'], ['TX_AMOUNT', 'Transaction amount'], ['TX_MERCHANT', 'Transaction merchant'], ['TX_DESCRIPTION', 'Transaction description'], ['TX_CATEGORY', 'Transaction category'], ['TX_ACCOUNT', 'Transaction account'], ['PAY_REFERENCE', 'Payment reference'], ['PAY_AMOUNT', 'Payment amount'], ['PAY_PARTIES', 'Payment parties'], ['PAY_STATUS', 'Payment status']];
const FONT: Record<string, string> = { HELVETICA: 'ui-sans-serif, Helvetica, Arial, sans-serif', TIMES: 'Georgia, "Times New Roman", serif', COURIER: '"Courier New", monospace' };
const blank = (): DocTemplate => ({ id: '', name: '', kind: 'CUSTOM', title: '', brandName: null, headerText: null, footerText: null, accentColor: '#6d28d9', fontFamily: 'HELVETICA', fontSize: 11, orientation: 'PORTRAIT', hasLogo: false, builtIn: false, archived: false, fields: [{ label: 'Prepared by', source: 'USER_NAME' }] });

export function DocumentsPage() {
  const qc = useQueryClient();
  const [sel, setSel] = useState<DocTemplate | null>(null); const [editing, setEditing] = useState<DocTemplate | null>(null);
  const [tx, setTx] = useState(''); const [pay, setPay] = useState(''); const [manual, setManual] = useState<Record<string, string>>({});
  const [prev, setPrev] = useState<DocPreview | null>(null); const [error, setError] = useState<string | null>(null);
  const logoRef = useRef<HTMLInputElement>(null);
  const list = useQuery({ queryKey: ['doc-templates'], queryFn: async () => (await api.get<DocTemplate[]>('/documents/templates')).data });
  const txs = useQuery({ queryKey: ['transactions', 'for-docs'], queryFn: async () => (await api.get<PageView<TransactionView>>('/transactions', { params: { size: 50 } })).data.items });
  const pays = useQuery({ queryKey: ['payments', 'list', 0, ''], queryFn: async () => (await api.get<PageView<PaymentView>>('/payments', { params: { size: 20 } })).data.items });
  const refresh = () => qc.invalidateQueries({ queryKey: ['doc-templates'] }); const fail = (e: unknown) => setError(toApiError(e).message);
  const body = () => ({ transactionId: tx || null, paymentId: pay || null, manual });
  const preview = useMutation({ mutationFn: () => api.post<DocPreview>(`/documents/templates/${sel!.id}/preview`, body()), onSuccess: (r) => { setPrev(r.data); setError(null); }, onError: fail });
  const save = useMutation({ mutationFn: (t: DocTemplate) => { const payload = { ...t, fields: t.fields }; return t.id ? api.put<DocTemplate>(`/documents/templates/${t.id}`, payload) : api.post<DocTemplate>('/documents/templates', payload); }, onSuccess: (r) => { setEditing(null); setSel(r.data); setError(null); refresh(); }, onError: fail });
  const dup = useMutation({ mutationFn: (id: string) => api.post<DocTemplate>(`/documents/templates/${id}/duplicate`), onSuccess: (r) => { setEditing(r.data); refresh(); }, onError: fail });
  const logo = useMutation({ mutationFn: (a: { id: string; file: File }) => { const fd = new FormData(); fd.append('file', a.file); return api.post<DocTemplate>(`/documents/templates/${a.id}/logo`, fd); }, onSuccess: (r) => { setEditing(r.data); refresh(); setError(null); }, onError: fail });
  const download = async () => { setError(null); try { await downloadFile(() => api.post<Blob>(`/documents/templates/${sel!.id}/pdf`, body(), { responseType: 'blob' }), 'flowpilot-document.pdf'); } catch (e) { setError(toApiError(e).message); } };

  if (editing) {
    const t = editing; const set = (p: Partial<DocTemplate>) => setEditing({ ...t, ...p });
    return (
      <div className="space-y-6"><div className="flex items-center justify-between"><h1 className="text-2xl font-bold">{t.id ? 'Edit layout' : 'New layout'}</h1><Button variant="ghost" onClick={() => setEditing(null)}>Cancel</Button></div>{error && <Alert>{error}</Alert>}
        <div className="grid gap-6 lg:grid-cols-[1.2fr_1fr]"><section className="space-y-4 rounded-2xl border border-line bg-surface p-5">
          <div className="grid gap-3 sm:grid-cols-2"><TextField label="Layout name" value={t.name} onChange={(e) => set({ name: e.target.value })} /><TextField label="Document title" value={t.title} onChange={(e) => set({ title: e.target.value })} /><TextField label="Brand / organisation (optional)" value={t.brandName ?? ''} onChange={(e) => set({ brandName: e.target.value })} /><SelectField label="Type" value={t.kind} onChange={(e) => set({ kind: e.target.value })}><option value="EXPENSE_VOUCHER">Expense voucher</option><option value="REIMBURSEMENT">Reimbursement form</option><option value="PAYMENT_CONFIRMATION">Payment confirmation</option><option value="CUSTOM">Custom</option></SelectField>
            <TextField label="Header line" value={t.headerText ?? ''} onChange={(e) => set({ headerText: e.target.value })} /><TextField label="Footer line" value={t.footerText ?? ''} onChange={(e) => set({ footerText: e.target.value })} />
            <TextField label="Accent colour" type="color" value={t.accentColor} onChange={(e) => set({ accentColor: e.target.value })} /><SelectField label="Font" value={t.fontFamily} onChange={(e) => set({ fontFamily: e.target.value as DocTemplate['fontFamily'] })}><option value="HELVETICA">Sans-serif</option><option value="TIMES">Serif</option><option value="COURIER">Monospace</option></SelectField>
            <TextField label="Font size (8–16)" inputMode="numeric" value={t.fontSize} onChange={(e) => set({ fontSize: Number(e.target.value) || 11 })} /><SelectField label="Orientation" value={t.orientation} onChange={(e) => set({ orientation: e.target.value as DocTemplate['orientation'] })}><option value="PORTRAIT">Portrait</option><option value="LANDSCAPE">Landscape</option></SelectField></div>
          {t.id && <div className="flex flex-wrap items-center gap-2 text-sm"><input ref={logoRef} type="file" className="sr-only" accept="image/png,image/jpeg" onChange={(e) => e.target.files?.[0] && logo.mutate({ id: t.id, file: e.target.files[0] })} /><Button variant="secondary" onClick={() => logoRef.current?.click()}>{t.hasLogo ? 'Replace logo' : 'Upload logo'}</Button>{t.hasLogo && <Button variant="ghost" onClick={() => api.delete<DocTemplate>(`/documents/templates/${t.id}/logo`).then((r) => { setEditing(r.data); refresh(); })}>Remove logo</Button>}<span className="text-xs text-muted">PNG or JPEG, up to 1 MB.</span></div>}
          <h2 className="font-semibold">Fields</h2><ol className="space-y-2">{t.fields.map((f, i) => <li key={i} className="grid items-end gap-2 sm:grid-cols-[1fr_1fr_auto]"><TextField label="Label" value={f.label} onChange={(e) => set({ fields: t.fields.map((x, j) => (j === i ? { ...x, label: e.target.value } : x)) })} /><SelectField label="Filled from" value={f.source} onChange={(e) => set({ fields: t.fields.map((x, j) => (j === i ? { ...x, source: e.target.value } : x)) })}>{SOURCES.map(([k, l]) => <option key={k} value={k}>{l}</option>)}</SelectField><span className="flex gap-1"><Button variant="ghost" aria-label="Move up" onClick={() => { if (i > 0) { const fs = [...t.fields]; [fs[i - 1], fs[i]] = [fs[i], fs[i - 1]]; set({ fields: fs }); } }}>↑</Button><Button variant="ghost" onClick={() => set({ fields: t.fields.filter((_, j) => j !== i) })}>Remove</Button></span></li>)}</ol>
          <Button variant="secondary" disabled={t.fields.length >= 20} onClick={() => set({ fields: [...t.fields, { label: 'New field', source: 'MANUAL' }] })}>Add field</Button>
          <div><Button loading={save.isPending} disabled={!t.name.trim() || !t.title.trim() || t.fields.length === 0} onClick={() => save.mutate(t)}>Save layout</Button></div></section>
          <section aria-labelledby="lp" className="rounded-2xl border border-line bg-surface p-5"><h2 id="lp" className="mb-3 text-lg font-semibold">Live layout preview</h2><Sheet t={t} fields={t.fields.map((f) => ({ label: f.label, value: f.source === 'MANUAL' ? '(typed when generating)' : `‹${f.source.toLowerCase().replace('_', ' ')}›`, source: f.source }))} /></section></div></div>
    );
  }
  return (
    <div className="space-y-6"><div className="flex flex-wrap items-center justify-between gap-3"><div><h1 className="text-2xl font-bold">Custom Documents</h1><p className="text-sm text-muted">Printable layouts for your own records. Every document carries a fixed notice that it is a personal record, not an official tax invoice or certified receipt.</p></div><Button onClick={() => setEditing(blank())}>New layout</Button></div>
      {error && <Alert>{error}</Alert>}
      <div className="grid gap-6 lg:grid-cols-[18rem_1fr]">
        <section className="space-y-2" aria-label="Layouts">{list.data?.map((t) => <div key={t.id} className={`rounded-2xl border bg-surface p-3 text-sm ${sel?.id === t.id ? 'border-accent' : 'border-line'}`}><button type="button" className="w-full text-left" onClick={() => { setSel(t); setPrev(null); setManual({}); }}><span className="font-medium">{t.name}</span>{t.builtIn && <span className="ml-2 rounded-full bg-accent/15 px-2 py-0.5 text-xs text-accent">built-in</span>}<span className="block text-xs text-muted">{t.fields.length} fields · {t.orientation.toLowerCase()}</span></button>
          <div className="mt-1 flex gap-1"><Button variant="ghost" onClick={() => dup.mutate(t.id)}>Duplicate</Button>{!t.builtIn && <><Button variant="ghost" onClick={() => setEditing(t)}>Edit</Button><Button variant="ghost" onClick={() => api.post(`/documents/templates/${t.id}/archive`).then(() => { if (sel?.id === t.id) setSel(null); refresh(); })}>Archive</Button></>}</div></div>)}</section>
        <section className="space-y-4">{!sel && <p className="rounded-2xl border border-dashed border-line p-8 text-center text-sm text-muted">Choose a layout to generate a document.</p>}
          {sel && <><div className="space-y-3 rounded-2xl border border-line bg-surface p-5"><h2 className="font-semibold">Generate “{sel.name}”</h2>
            {sel.fields.some((f) => f.source.startsWith('TX_')) && <SelectField label="Linked transaction" value={tx} onChange={(e) => setTx(e.target.value)}><option value="">Choose…</option>{txs.data?.map((t) => <option key={t.id} value={t.id}>{t.occurredOn} · {t.merchant ?? t.description ?? t.type.toLowerCase()} · {t.amount}</option>)}</SelectField>}
            {sel.fields.some((f) => f.source.startsWith('PAY_')) && <SelectField label="Linked payment" value={pay} onChange={(e) => setPay(e.target.value)}><option value="">Choose…</option>{pays.data?.map((p) => <option key={p.id} value={p.id}>{p.reference} · {p.amount} {p.currency} · {p.status.toLowerCase()}</option>)}</SelectField>}
            {sel.fields.map((f, i) => f.source === 'MANUAL' && <TextField key={i} label={f.label} value={manual[`m${i + 1}`] ?? ''} onChange={(e) => setManual({ ...manual, [`m${i + 1}`]: e.target.value })} />)}
            <div className="flex gap-2"><Button variant="secondary" loading={preview.isPending} onClick={() => preview.mutate()}>Preview</Button><Button onClick={download}>Download PDF</Button></div></div>
            {prev && <div className="rounded-2xl border border-line bg-surface p-5"><h3 className="mb-3 font-semibold">Preview</h3><Sheet t={prev.template} fields={prev.fields} /></div>}</>}</section></div></div>
  );
}

function Sheet({ t, fields }: { t: DocTemplate; fields: { label: string; value: string }[] }) {
  return (
    <div className="mx-auto rounded-lg bg-white p-6 text-gray-900 shadow-inner" style={{ fontFamily: FONT[t.fontFamily], fontSize: t.fontSize + 2, maxWidth: t.orientation === 'LANDSCAPE' ? 680 : 480 }}>
      {t.brandName && <p className="font-bold">{t.brandName}</p>}<h4 className="mb-1 font-bold" style={{ color: t.accentColor, fontSize: t.fontSize + 9 }}>{t.title || 'Untitled'}</h4>{t.headerText && <p className="mb-3 text-xs text-gray-500">{t.headerText}</p>}
      <table className="w-full border-collapse"><tbody>{fields.map((f, i) => <tr key={i}><th scope="row" className="w-2/5 border border-gray-300 p-1.5 text-left font-bold">{f.label}</th><td className="border border-gray-300 p-1.5">{f.value}</td></tr>)}</tbody></table>
      {t.footerText && <p className="mt-3 text-xs text-gray-500">{t.footerText}</p>}<p className="mt-3 text-xs text-red-600">Personal record generated by FlowPilot. It is not an official tax invoice, a legally certified receipt, or a bank statement.</p></div>
  );
}
