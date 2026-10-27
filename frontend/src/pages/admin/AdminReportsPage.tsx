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

const TYPES: [string, string][] = [['user-growth', 'User growth'], ['platform-usage', 'Platform usage'], ['transaction-volumes', 'Transaction volumes'], ['payment-outcomes', 'Simulated payment outcomes'], ['support-workload', 'Support workload'], ['operational-health', 'Operational health']];

export function AdminReportsPage() {
  const [type, setType] = useState('user-growth'); const [from, setFrom] = useState(isoDate(new Date(Date.now() - 29 * 864e5))); const [to, setTo] = useState(isoDate(new Date()));
  const [error, setError] = useState<string | null>(null); const [busy, setBusy] = useState<string | null>(null);
  const q = useQuery({ queryKey: ['admin-report', type, from, to], queryFn: async () => (await api.get<ReportTable>(`/admin/reports/${type}`, { params: { from, to } })).data });
  const exportAs = async (format: 'csv' | 'pdf') => { setBusy(format); setError(null); try { await downloadFile(() => getBlob(`/admin/reports/${type}/export`, { from, to, format }), `flowpilot-admin-${type}.${format}`); } catch (e) { setError(toApiError(e).message); } finally { setBusy(null); } };
  return (
    <div className="space-y-6"><div><h1 className="text-2xl font-bold">Platform reports</h1><p className="text-sm text-muted">Aggregated and privacy-aware: no individual user records are included in any report.</p></div>
      <section className="grid gap-3 rounded-2xl border border-line bg-surface p-4 sm:grid-cols-2 lg:grid-cols-5"><SelectField label="Report" value={type} onChange={(e) => setType(e.target.value)}>{TYPES.map(([k, l]) => <option key={k} value={k}>{l}</option>)}</SelectField>
        {type !== 'operational-health' && <><TextField label="From" type="date" value={from} onChange={(e) => setFrom(e.target.value)} /><TextField label="To" type="date" value={to} onChange={(e) => setTo(e.target.value)} /></>}
        <div className="flex items-end gap-2"><Button variant="secondary" loading={busy === 'csv'} onClick={() => exportAs('csv')}><Download className="h-4 w-4" aria-hidden="true" />CSV</Button><Button variant="secondary" loading={busy === 'pdf'} onClick={() => exportAs('pdf')}><Download className="h-4 w-4" aria-hidden="true" />PDF</Button></div></section>
      {error && <Alert>{error}</Alert>}{q.isError && <Alert>{toApiError(q.error).message}</Alert>}
      {q.data && <section className="rounded-2xl border border-line bg-surface p-5"><h2 className="mb-3 text-lg font-semibold">{q.data.title}</h2><div className="overflow-x-auto"><table className="w-full text-left text-sm"><caption className="sr-only">{q.data.title}</caption>
        <thead className="text-xs uppercase text-muted"><tr>{q.data.columns.map((c, i) => <th key={c} scope="col" className={`py-2 pr-3 ${i > 0 ? 'text-right' : ''}`}>{c}</th>)}</tr></thead>
        <tbody className="divide-y divide-line">{q.data.rows.map((r, i) => <tr key={i}>{r.map((c, j) => <td key={j} className={`py-2 pr-3 ${j > 0 ? 'num text-right' : ''}`}>{c}</td>)}</tr>)}</tbody></table></div>
        <ul className="mt-3 list-disc pl-5 text-xs text-muted">{q.data.notes.map((n) => <li key={n}>{n}</li>)}</ul></section>}</div>
  );
}
