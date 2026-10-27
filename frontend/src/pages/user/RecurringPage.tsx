import { useMutation } from '@tanstack/react-query';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Alert } from '../../components/Alert';
import { Button } from '../../components/Button';
import { CsvMapper, mappingToForm } from '../../components/CsvMapper';
import { api, toApiError } from '../../lib/api';
import type { RecurringCandidate } from '../../lib/types';

export function RecurringPage() {
  const navigate = useNavigate();
  const [candidates, setCandidates] = useState<RecurringCandidate[] | null>(null);
  const [dismissed, setDismissed] = useState<Set<string>>(new Set());
  const [error, setError] = useState<string | null>(null);
  const detect = useMutation({ mutationFn: ({ file, m }: { file: File; m: Parameters<typeof mappingToForm>[1] }) => api.post<RecurringCandidate[]>('/recurring/detect', mappingToForm(file, m)),
    onSuccess: (r) => { setCandidates(r.data); setDismissed(new Set()); setError(null); }, onError: (e) => setError(toApiError(e).message) });
  const freq = (f: string) => (f === 'BIWEEKLY' ? 'WEEKLY' : f);
  return (
    <div className="space-y-6">
      <div><h1 className="text-2xl font-bold">Find recurring charges</h1><p className="text-sm text-muted">Upload a transaction CSV and FlowPilot suggests charges that <em>might</em> repeat. It creates nothing by itself — you review every suggestion.</p></div>
      {error && <Alert>{error}</Alert>}
      <div className="grid gap-6 lg:grid-cols-[1fr_1.3fr]">
        <section className="rounded-2xl border border-line bg-surface p-5">{detect.isPending ? <p role="status" className="text-sm text-muted">Analysing…</p> : <CsvMapper withReference={false} actionLabel="Look for recurring charges" onReady={(file, m) => detect.mutate({ file, m })} />}<p className="mt-3 text-xs text-muted">The file is analysed and discarded; it is not stored and no transactions are imported.</p></section>
        <section aria-labelledby="cand" className="space-y-3"><h2 id="cand" className="text-lg font-semibold">Candidates</h2>
          {candidates === null && <p className="text-sm text-muted">Results appear here.</p>}
          {candidates?.length === 0 && <p className="rounded-2xl border border-dashed border-line p-6 text-sm text-muted">No clear recurring patterns found. That's normal for short or varied statements — a repeat purchase isn't necessarily a subscription.</p>}
          {candidates?.filter((c) => !dismissed.has(c.merchant)).map((c) => (
            <article key={c.merchant} className="rounded-2xl border border-line bg-surface p-4"><div className="flex items-start justify-between gap-3"><div><h3 className="font-semibold">{c.merchant}</h3><p className="num text-sm">{c.typicalAmount} · {c.frequency.toLowerCase()} · last {c.lastDate} · next about {c.nextExpected}</p></div>
              <span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${c.confidence >= 80 ? 'bg-success/15 text-success' : c.confidence >= 60 ? 'bg-warning/15 text-warning' : 'bg-surface-2 text-muted'}`}>{c.confidence}% confidence</span></div>
              <ul className="mt-2 list-disc space-y-0.5 pl-5 text-xs text-muted">{c.reasons.map((r) => <li key={r}>{r}</li>)}</ul>{c.priceChangeNote && <p className="mt-1 text-xs text-warning">{c.priceChangeNote}</p>}
              <div className="mt-3 flex gap-2"><Button onClick={() => navigate('/app/bills', { state: { prefill: { name: c.merchant, amount: c.typicalAmount, frequency: freq(c.frequency), nextDueDate: c.nextExpected } } })}>Review as a bill / subscription</Button><Button variant="ghost" onClick={() => setDismissed(new Set([...dismissed, c.merchant]))}>Not recurring</Button></div></article>))}</section>
      </div>
    </div>
  );
}
