import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Alert } from '../../components/Alert';
import { Button } from '../../components/Button';
import { CsvMapper, mappingToForm } from '../../components/CsvMapper';
import { SelectField } from '../../components/SelectField';
import { api, toApiError } from '../../lib/api';
import { formatMoney } from '../../lib/money';
import type { AccountView, ImportSummary, RowPage, StatementRow } from '../../lib/types';

const STATUS: Record<string, string> = { SUGGESTED: 'bg-live/15 text-live', CONFIRMED: 'bg-success/15 text-success', UNMATCHED: 'bg-warning/15 text-warning', INVALID: 'bg-danger/15 text-danger', REJECTED: 'bg-surface-2 text-muted', POSSIBLE_DUPLICATE: 'bg-warning/15 text-warning' };

export function ReconcilePage() {
  const qc = useQueryClient();
  const [account, setAccount] = useState('');
  const [open, setOpen] = useState<string | null>(null);
  const [filter, setFilter] = useState(''); const [page, setPage] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const accounts = useQuery({ queryKey: ['accounts'], queryFn: async () => (await api.get<AccountView[]>('/accounts')).data });
  const imports = useQuery({ queryKey: ['recon', 'imports'], queryFn: async () => (await api.get<ImportSummary[]>('/reconciliation/imports')).data });
  const rows = useQuery({ queryKey: ['recon', 'rows', open, filter, page], enabled: !!open, queryFn: async () => (await api.get<RowPage>(`/reconciliation/imports/${open}`, { params: { status: filter || undefined, page, size: 25 } })).data, placeholderData: (p) => p });
  const refresh = () => { qc.invalidateQueries({ queryKey: ['recon'] }); ['transactions', 'accounts', 'dashboard'].forEach((k) => qc.invalidateQueries({ queryKey: [k] })); };
  const fail = (e: unknown) => setError(toApiError(e).message);

  const upload = useMutation({ mutationFn: ({ file, m }: { file: File; m: Parameters<typeof mappingToForm>[1] }) => api.post<ImportSummary>('/reconciliation/imports', mappingToForm(file, m, { accountId: account })),
    onSuccess: (r) => { setError(null); setOpen(r.data.id); setFilter(''); setPage(0); refresh(); }, onError: fail });
  const act = useMutation({ mutationFn: (a: { id: string; kind: 'confirm' | 'reject' | 'undo' | 'record' }) => api.post(`/reconciliation/rows/${a.id}/${a.kind}`), onSuccess: () => { setError(null); refresh(); }, onError: fail });
  const discard = useMutation({ mutationFn: (id: string) => api.delete(`/reconciliation/imports/${id}`), onSuccess: () => { setOpen(null); refresh(); }, onError: fail });
  const acct = accounts.data?.find((a) => a.id === (account || accounts.data?.[0]?.id));
  if (!account && accounts.data?.[0]) setAccount(accounts.data[0].id);

  return (
    <div className="space-y-6">
      <div><h1 className="text-2xl font-bold">Reconcile a statement</h1><p className="text-sm text-muted">Import a bank-statement CSV and match it against what you recorded. Existing transactions are never changed — you only confirm or reject matches.</p></div>
      {error && <Alert>{error}</Alert>}
      <div className="grid gap-6 xl:grid-cols-[1fr_1.4fr]">
        <section className="space-y-4 rounded-2xl border border-line bg-surface p-5" aria-labelledby="imp"><h2 id="imp" className="text-lg font-semibold">New import</h2>
          <SelectField label="Account the statement belongs to" value={account} onChange={(e) => setAccount(e.target.value)}>{accounts.data?.map((a) => <option key={a.id} value={a.id}>{a.name} ({a.currency})</option>)}</SelectField>
          {upload.isPending ? <p role="status" className="text-sm text-muted">Importing and matching…</p> : <CsvMapper withReference actionLabel={`Import into ${acct?.name ?? 'account'}`} onReady={(file, m) => upload.mutate({ file, m })} />}
          <h3 className="pt-2 font-semibold">History</h3>{imports.data?.length === 0 && <p className="text-sm text-muted">No imports yet.</p>}
          <ul className="space-y-2">{imports.data?.map((i) => <li key={i.id}><button type="button" onClick={() => { setOpen(i.id); setPage(0); setFilter(''); }} className={`w-full rounded-xl border p-3 text-left text-sm ${open === i.id ? 'border-accent' : 'border-line'} ${i.status === 'DISCARDED' ? 'opacity-50' : ''}`}>
            <span className="font-medium">{i.fileName ?? 'Statement'} → {i.accountName}</span><span className="block text-xs text-muted">{new Date(i.createdAt).toLocaleString()} · {i.rowCount} lines · {i.confirmed} confirmed · {i.suggested} suggested · {i.unmatched} unmatched{i.status === 'DISCARDED' ? ' · discarded' : ''}</span></button></li>)}</ul></section>

        <section className="space-y-4 rounded-2xl border border-line bg-surface p-5" aria-labelledby="rev"><h2 id="rev" className="text-lg font-semibold">Review</h2>
          {!open && <p className="text-sm text-muted">Pick an import to review its lines.</p>}
          {rows.data && (<>
            <dl className="grid grid-cols-3 gap-2 text-center text-xs sm:grid-cols-6">{[['Suggested', rows.data.summary.suggested], ['Confirmed', rows.data.summary.confirmed], ['Unmatched', rows.data.summary.unmatched], ['Duplicates?', rows.data.summary.possibleDuplicates], ['Invalid', rows.data.summary.invalid], ['Rejected', rows.data.summary.rejected]].map(([l, n]) => <div key={String(l)} className="rounded-lg bg-surface-2 p-2"><dt className="text-muted">{l}</dt><dd className="num text-lg font-bold">{n}</dd></div>)}</dl>
            <div className="flex flex-wrap items-end justify-between gap-2"><SelectField label="Show" className="w-48" value={filter} onChange={(e) => { setFilter(e.target.value); setPage(0); }}><option value="">All lines</option>{Object.keys(STATUS).map((s) => <option key={s} value={s}>{s.toLowerCase().replace('_', ' ')}</option>)}</SelectField>
              {rows.data.summary.status === 'ACTIVE' && <Button variant="ghost" onClick={() => { if (window.confirm('Discard this import? Your transactions are not changed; match decisions are cleared.')) discard.mutate(rows.data!.summary.id); }}>Discard import</Button>}</div>
            <ul className="space-y-2">{rows.data.rows.map((r: StatementRow) => (
              <li key={r.id} className="rounded-xl border border-line p-3 text-sm"><div className="flex flex-wrap items-start justify-between gap-2"><div className="min-w-0"><p className="truncate font-medium">{r.description || '(no description)'} <span className={`ml-1 rounded-full px-2 py-0.5 text-xs ${STATUS[r.status]}`}>{r.status.toLowerCase().replace('_', ' ')}</span></p>
                <p className="text-xs text-muted">Line {r.rowNo} · {r.date ?? '—'}{r.reference ? ` · ref ${r.reference}` : ''}</p>{r.validationError && <p className="text-xs text-danger">{r.validationError}</p>}{r.note && !r.validationError && <p className="text-xs text-muted">{r.note}{r.suggestedLabel ? ` — “${r.suggestedLabel}”` : ''}{r.score ? ` (confidence ${r.score}%)` : ''}</p>}</div>
                <p className={`num font-semibold ${r.amount && Number(r.amount) < 0 ? 'text-danger' : 'text-success'}`}>{r.amount ? formatMoney(r.amount, acct?.currency ?? 'USD', { signed: true }) : '—'}</p></div>
                {rows.data!.summary.status === 'ACTIVE' && <div className="mt-2 flex flex-wrap gap-1">
                  {r.status === 'SUGGESTED' && <Button onClick={() => act.mutate({ id: r.id, kind: 'confirm' })}>Confirm match</Button>}{['SUGGESTED', 'UNMATCHED', 'POSSIBLE_DUPLICATE'].includes(r.status) && <Button variant="ghost" onClick={() => act.mutate({ id: r.id, kind: 'reject' })}>Reject</Button>}
                  {['UNMATCHED', 'REJECTED'].includes(r.status) && <Button variant="secondary" onClick={() => { if (window.confirm('Record this statement line as a new transaction in FlowPilot?')) act.mutate({ id: r.id, kind: 'record' }); }}>Record as new transaction</Button>}
                  {['CONFIRMED', 'REJECTED'].includes(r.status) && <Button variant="ghost" onClick={() => act.mutate({ id: r.id, kind: 'undo' })}>Undo</Button>}</div>}</li>))}</ul>
            <div className="flex items-center justify-between text-sm text-muted"><Button variant="secondary" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>Previous</Button><span>Page {page + 1} of {Math.max(1, Math.ceil(rows.data.total / rows.data.size))}</span><Button variant="secondary" disabled={(page + 1) * rows.data.size >= rows.data.total} onClick={() => setPage((p) => p + 1)}>Next</Button></div>
            {rows.data.missingInStatement.length > 0 && <div className="rounded-xl border border-warning/50 bg-warning/5 p-3"><h3 className="text-sm font-semibold">Recorded in FlowPilot but not on this statement</h3><p className="text-xs text-muted">These fall inside the statement period and no line accounts for them. They may be pending, from a different account, or mistakes.</p>
              <ul className="mt-2 space-y-1 text-xs">{rows.data.missingInStatement.map((t) => <li key={t.id} className="flex justify-between"><span>{t.date} · {t.label}</span><span className="num">{t.amount}</span></li>)}</ul></div>}</>)}
        </section>
      </div>
    </div>
  );
}
