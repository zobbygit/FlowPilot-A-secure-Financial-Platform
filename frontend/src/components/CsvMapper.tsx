import { useRef, useState } from 'react';
import { api, toApiError } from '../lib/api';
import type { CsvPreview } from '../lib/types';
import { Alert } from './Alert';
import { Button } from './Button';
import { SelectField } from './SelectField';

export interface Mapping { hasHeader: boolean; dateCol: number; descCol: number; amountMode: 'single' | 'split'; amountCol: number | null; debitCol: number | null; creditCol: number | null; refCol: number | null; dateFormat: string; decimal: '.' | ',' }
export const DATE_FORMATS: [string, string][] = [['YMD_DASH', '2026-01-31'], ['DMY_SLASH', '31/01/2026'], ['MDY_SLASH', '01/31/2026'], ['DMY_DASH', '31-01-2026'], ['DMY_DOT', '31.01.2026']];

export function mappingToForm(file: File, m: Mapping, extra: Record<string, string> = {}): FormData {
  const fd = new FormData();
  fd.append('file', file); fd.append('hasHeader', String(m.hasHeader)); fd.append('dateCol', String(m.dateCol)); fd.append('descCol', String(m.descCol)); fd.append('dateFormat', m.dateFormat); fd.append('decimal', m.decimal);
  if (m.amountMode === 'single' && m.amountCol !== null) fd.append('amountCol', String(m.amountCol));
  if (m.amountMode === 'split') { if (m.debitCol !== null) fd.append('debitCol', String(m.debitCol)); if (m.creditCol !== null) fd.append('creditCol', String(m.creditCol)); }
  if (m.refCol !== null) fd.append('refCol', String(m.refCol));
  Object.entries(extra).forEach(([k, v]) => fd.append(k, v));
  return fd;
}

/** Upload a CSV, show a preview, let the user confirm or fix the column mapping. Nothing is saved at this stage. */
export function CsvMapper({ withReference, onReady, actionLabel }: { withReference: boolean; onReady: (file: File, m: Mapping) => void; actionLabel: string }) {
  const ref = useRef<HTMLInputElement>(null);
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState<CsvPreview | null>(null);
  const [m, setM] = useState<Mapping | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const choose = async (f: File) => {
    setError(null); setPreview(null); setM(null); setFile(f);
    if (f.size > 2 * 1024 * 1024) return setError('CSV files can be at most 2 MB.');
    setBusy(true);
    try {
      const fd = new FormData(); fd.append('file', f);
      const p = (await api.post<CsvPreview>('/csv/preview', fd)).data;
      setPreview(p);
      const g = p.guess, split = g.amountCol === null && (g.debitCol !== null || g.creditCol !== null);
      setM({ hasHeader: p.looksLikeHeader, dateCol: g.dateCol ?? 0, descCol: g.descCol ?? 1, amountMode: split ? 'split' : 'single', amountCol: g.amountCol ?? (split ? null : 2), debitCol: g.debitCol, creditCol: g.creditCol, refCol: withReference ? g.refCol : null, dateFormat: g.dateFormat, decimal: g.decimal as '.' | ',' });
    } catch (e) { setError(toApiError(e).message); } finally { setBusy(false); }
  };
  const cols = preview ? Math.max(preview.headers?.length ?? 0, ...preview.sample.map((r) => r.length)) : 0;
  const opts = (allowNone = false) => <>{allowNone && <option value="">— none —</option>}{Array.from({ length: cols }, (_, i) => <option key={i} value={i}>{`Column ${i + 1}${preview?.headers ? ` — ${preview.headers[i] ?? ''}` : ''}`}</option>)}</>;
  const num = (v: string) => (v === '' ? null : Number(v));

  return (
    <div className="space-y-4">
      <div><label htmlFor="csvf" className="mb-1.5 block text-sm font-medium">CSV file (UTF-8, up to 2 MB and 5,000 rows)</label><input id="csvf" ref={ref} type="file" accept=".csv,text/csv" className="w-full text-sm" onChange={(e) => e.target.files?.[0] && choose(e.target.files[0])} /></div>
      {busy && <p role="status" className="text-sm text-muted">Reading file…</p>}{error && <Alert>{error}</Alert>}
      {preview && m && (<>
        <div className="overflow-x-auto rounded-xl border border-line"><table className="w-full text-left text-xs"><caption className="sr-only">First rows of the file</caption>
          {preview.headers && <thead className="bg-surface-2"><tr>{preview.headers.map((h, i) => <th key={i} scope="col" className="px-2 py-1.5">{h || `Column ${i + 1}`}</th>)}</tr></thead>}
          <tbody>{preview.sample.map((r, i) => <tr key={i} className="border-t border-line">{r.map((c, j) => <td key={j} className="px-2 py-1">{c}</td>)}</tr>)}</tbody></table></div>
        <p className="text-xs text-muted">{preview.rowCount} data rows found. Check the mapping below — only the columns you choose are read.</p>
        <label className="flex items-center gap-2 text-sm"><input type="checkbox" className="h-4 w-4 accent-[rgb(var(--fp-accent))]" checked={m.hasHeader} onChange={(e) => setM({ ...m, hasHeader: e.target.checked })} />The first row is a header</label>
        <div className="grid gap-3 sm:grid-cols-2">
          <SelectField label="Date column" value={m.dateCol} onChange={(e) => setM({ ...m, dateCol: Number(e.target.value) })}>{opts()}</SelectField>
          <SelectField label="Date format" value={m.dateFormat} onChange={(e) => setM({ ...m, dateFormat: e.target.value })}>{DATE_FORMATS.map(([k, ex]) => <option key={k} value={k}>{ex}</option>)}</SelectField>
          <SelectField label="Description column" value={m.descCol} onChange={(e) => setM({ ...m, descCol: Number(e.target.value) })}>{opts()}</SelectField>
          <SelectField label="Amounts are in…" value={m.amountMode} onChange={(e) => setM({ ...m, amountMode: e.target.value as Mapping['amountMode'] })}><option value="single">One signed column (− = money out)</option><option value="split">Separate debit and credit columns</option></SelectField>
          {m.amountMode === 'single' ? <SelectField label="Amount column" value={m.amountCol ?? ''} onChange={(e) => setM({ ...m, amountCol: num(e.target.value) })}><option value="">Choose…</option>{opts()}</SelectField>
            : <><SelectField label="Debit (money out)" value={m.debitCol ?? ''} onChange={(e) => setM({ ...m, debitCol: num(e.target.value) })}>{opts(true)}</SelectField><SelectField label="Credit (money in)" value={m.creditCol ?? ''} onChange={(e) => setM({ ...m, creditCol: num(e.target.value) })}>{opts(true)}</SelectField></>}
          <SelectField label="Decimal separator" value={m.decimal} onChange={(e) => setM({ ...m, decimal: e.target.value as '.' | ',' })}><option value=".">Dot (1,234.50)</option><option value=",">Comma (1.234,50)</option></SelectField>
          {withReference && <SelectField label="Reference column (optional)" value={m.refCol ?? ''} onChange={(e) => setM({ ...m, refCol: num(e.target.value) })}>{opts(true)}</SelectField>}</div>
        <Button disabled={!file || (m.amountMode === 'single' ? m.amountCol === null : m.debitCol === null && m.creditCol === null)} onClick={() => file && onReady(file, m)}>{actionLabel}</Button></>)}
    </div>
  );
}
