import { useQuery } from '@tanstack/react-query';
import { Download } from 'lucide-react';
import { useState } from 'react';
import { Alert } from '../../components/Alert';
import { Button } from '../../components/Button';
import { SelectField } from '../../components/SelectField';
import { TextField } from '../../components/TextField';
import { api, toApiError } from '../../lib/api';
import { downloadFile, getBlob } from '../../lib/download';
import { isoDate } from '../../lib/money';
import type { ReportTable } from '../../lib/types';

const REPORTS = [
  ['income-expense', 'Income vs expenses'], ['spending-by-category', 'Spending by category'], ['spending-by-merchant', 'Spending by merchant'],
  ['accounts', 'Account summaries'], ['subscription-cost', 'Subscription & bill costs'], ['budget-variance', 'Budget variance'], ['cash-flow-forecast', 'Cash-flow forecast'],
] as const;

export function ReportsPage() {
  const now = new Date();
  const [type, setType] = useState<string>('income-expense');
  const [from, setFrom] = useState(isoDate(new Date(now.getFullYear(), 0, 1)));
  const [to, setTo] = useState(isoDate(now));
  const [horizon, setHorizon] = useState('30');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const params = { from, to, horizon };
  const q = useQuery({ queryKey: ['report', type, from, to, horizon], queryFn: async () => (await api.get<ReportTable>(`/reports/${type}`, { params })).data });

  const exportAs = async (format: 'csv' | 'pdf') => {
    setBusy(format); setError(null);
    try { await downloadFile(() => getBlob(`/reports/${type}/export`, { ...params, format }), `flowpilot-${type}.${format}`); }
    catch (e) { setError(toApiError(e).message); } finally { setBusy(null); }
  };
  const forecast = type === 'cash-flow-forecast', dated = !['subscription-cost', 'budget-variance', 'cash-flow-forecast'].includes(type);

  return (
    <div className="space-y-6">
      <div><h1 className="text-2xl font-bold">Reports &amp; Exports</h1><p className="text-sm text-muted">Generated on the server from your ledger. Personal summaries — not official tax documents.</p></div>
      <section aria-label="Report options" className="grid gap-3 rounded-2xl border border-line bg-surface p-4 sm:grid-cols-2 lg:grid-cols-5">
        <SelectField label="Report" value={type} onChange={(e) => setType(e.target.value)}>{REPORTS.map(([k, l]) => <option key={k} value={k}>{l}</option>)}</SelectField>
        {dated && <><TextField label="From" type="date" value={from} onChange={(e) => setFrom(e.target.value)} /><TextField label="To" type="date" value={to} onChange={(e) => setTo(e.target.value)} /></>}
        {forecast && <SelectField label="Horizon" value={horizon} onChange={(e) => setHorizon(e.target.value)}><option value="30">30 days</option><option value="60">60 days</option><option value="90">90 days</option></SelectField>}
        <div className="flex items-end gap-2"><Button variant="secondary" loading={busy === 'csv'} onClick={() => exportAs('csv')}><Download className="h-4 w-4" aria-hidden="true" />CSV</Button><Button variant="secondary" loading={busy === 'pdf'} onClick={() => exportAs('pdf')}><Download className="h-4 w-4" aria-hidden="true" />PDF</Button></div>
      </section>
      {error && <Alert>{error}</Alert>}
      {q.isLoading && <p role="status" className="text-sm text-muted">Building report…</p>}
      {q.isError && <Alert>{toApiError(q.error).message}</Alert>}
      {q.data && (
        <section aria-labelledby="rt" className="rounded-2xl border border-line bg-surface p-5">
          <h2 id="rt" className="mb-1 text-lg font-semibold">{q.data.title}</h2>
          <p className="mb-3 text-xs text-muted">{q.data.currency}{q.data.from ? ` · ${q.data.from} → ${q.data.to}` : ''}</p>
          {q.data.rows.length === 0 ? <p className="text-sm text-muted">No data for this selection.</p> : (
            <div className="overflow-x-auto"><table className="w-full text-left text-sm"><caption className="sr-only">{q.data.title}</caption>
              <thead className="text-xs uppercase tracking-wide text-muted"><tr>{q.data.columns.map((c, i) => <th key={c} scope="col" className={`py-2 pr-3 ${i > 0 ? 'text-right' : ''}`}>{c}</th>)}</tr></thead>
              <tbody className="divide-y divide-line">{q.data.rows.map((r, i) => <tr key={i} className={r[0] === 'Total' ? 'font-semibold' : ''}>{r.map((c, j) => <td key={j} className={`py-2 pr-3 ${j > 0 ? 'num text-right' : ''}`}>{c}</td>)}</tr>)}</tbody></table></div>)}
          {q.data.notes.length > 0 && <ul className="mt-4 list-disc space-y-1 pl-5 text-xs text-muted">{q.data.notes.map((n) => <li key={n}>{n}</li>)}</ul>}
        </section>)}
    </div>
  );
}
