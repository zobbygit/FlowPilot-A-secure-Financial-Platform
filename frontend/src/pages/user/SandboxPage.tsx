import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Alert } from '../../components/Alert';
import { Button } from '../../components/Button';
import { SelectField } from '../../components/SelectField';
import { TextField } from '../../components/TextField';
import { api, toApiError } from '../../lib/api';
import { AMOUNT_RE, formatMoney, isoDate } from '../../lib/money';
import type { BillsOverview, SandboxEvent, Scenario, Simulation } from '../../lib/types';

interface Draft { kind: SandboxEvent['kind']; label: string; date: string; amount: string; direction: 'out' | 'in'; billId: string }
const COLORS = ['#f472b6', '#22d3ee', '#34d399'];

function toEvent(d: Draft): SandboxEvent {
  if (d.kind === 'CANCEL_BILL') return { kind: d.kind, label: d.label || 'Cancel bill', date: null, amount: null, billId: d.billId };
  return { kind: d.kind, label: d.label, date: d.date, amount: `${d.direction === 'out' ? '-' : ''}${d.amount}`, billId: null };
}

export function SandboxPage() {
  const qc = useQueryClient();
  const today = isoDate(new Date());
  const [currency, setCurrency] = useState('USD'); const [horizon, setHorizon] = useState(60);
  const [drafts, setDrafts] = useState<Draft[]>([{ kind: 'ONE_OFF', label: 'Laptop', date: isoDate(new Date(Date.now() + 14 * 864e5)), amount: '', direction: 'out', billId: '' }]);
  const [name, setName] = useState(''); const [error, setError] = useState<string | null>(null);
  const [runs, setRuns] = useState<{ label: string; sim: Simulation }[]>([]);
  const [compare, setCompare] = useState<string[]>([]);
  const bills = useQuery({ queryKey: ['bills'], queryFn: async () => (await api.get<BillsOverview>('/bills')).data.bills });
  const saved = useQuery({ queryKey: ['sandbox', 'scenarios'], queryFn: async () => (await api.get<Scenario[]>('/sandbox/scenarios')).data });

  const valid = () => drafts.every((d) => d.kind === 'CANCEL_BILL' ? !!d.billId : d.label.trim() && AMOUNT_RE.test(d.amount) && Number(d.amount) > 0 && d.date >= today);
  const simulate = useMutation({
    mutationFn: async () => {
      const mine = (await api.post<Simulation>('/sandbox/simulate', { currency, horizonDays: horizon, events: drafts.map(toEvent) })).data;
      const others = await Promise.all((saved.data ?? []).filter((s) => compare.includes(s.id)).map(async (s) => ({ label: s.name, sim: (await api.post<Simulation>('/sandbox/simulate', { currency: s.currency, horizonDays: horizon, events: s.events.filter((e) => e.kind === 'CANCEL_BILL' || (e.date && e.date >= today)) })).data })));
      return [{ label: 'This scenario', sim: mine }, ...others];
    },
    onSuccess: (r) => { setRuns(r); setError(null); }, onError: (e) => setError(toApiError(e).message),
  });
  const save = useMutation({ mutationFn: () => api.post('/sandbox/scenarios', { name, currency, horizonDays: horizon, events: drafts.map(toEvent) }), onSuccess: () => { setName(''); setError(null); qc.invalidateQueries({ queryKey: ['sandbox'] }); }, onError: (e) => setError(toApiError(e).message) });
  const del = useMutation({ mutationFn: (id: string) => api.delete(`/sandbox/scenarios/${id}`), onSuccess: () => qc.invalidateQueries({ queryKey: ['sandbox'] }) });
  const set = (i: number, p: Partial<Draft>) => setDrafts(drafts.map((d, j) => (j === i ? { ...d, ...p } : d)));
  const first = runs[0]?.sim;

  return (
    <div className="space-y-6">
      <div className="rounded-2xl border border-warning/50 bg-warning/10 px-4 py-3 text-sm" role="note"><strong>Planning sandbox — hypothetical.</strong> Scenarios never change your balances and are never written to your transaction ledger. Results are projections from the assumptions shown, not predictions.</div>
      <div><h1 className="text-2xl font-bold">Planning Sandbox</h1><p className="text-sm text-muted">Try a purchase, an unexpected expense or an income change and compare it with your baseline for the next 30–90 days.</p></div>
      {error && <Alert>{error}</Alert>}
      <div className="grid gap-6 lg:grid-cols-[1fr_1.3fr]">
        <section className="space-y-4 rounded-2xl border border-line bg-surface p-5" aria-label="Scenario builder">
          <div className="grid grid-cols-2 gap-3"><TextField label="Currency" maxLength={3} value={currency} onChange={(e) => setCurrency(e.target.value.toUpperCase())} /><SelectField label="Horizon" value={horizon} onChange={(e) => setHorizon(Number(e.target.value))}><option value={30}>30 days</option><option value={60}>60 days</option><option value={90}>90 days</option></SelectField></div>
          <ol className="space-y-3">{drafts.map((d, i) => (<li key={i} className="rounded-xl border border-line p-3"><div className="grid grid-cols-2 gap-2">
            <SelectField label="Event" value={d.kind} onChange={(e) => set(i, { kind: e.target.value as Draft['kind'] })}><option value="ONE_OFF">One-off purchase / income</option><option value="MONTHLY_CHANGE">Monthly change (income or cost)</option><option value="CANCEL_BILL">Cancel a bill</option></SelectField>
            {d.kind === 'CANCEL_BILL' ? <SelectField label="Bill" value={d.billId} onChange={(e) => set(i, { billId: e.target.value, label: bills.data?.find((b) => b.id === e.target.value)?.name ?? '' })}><option value="">Choose…</option>{bills.data?.map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}</SelectField> : <TextField label="Label" value={d.label} onChange={(e) => set(i, { label: e.target.value })} />}
            {d.kind !== 'CANCEL_BILL' && <><SelectField label="Direction" value={d.direction} onChange={(e) => set(i, { direction: e.target.value as Draft['direction'] })}><option value="out">Money out</option><option value="in">Money in</option></SelectField><TextField label="Amount" inputMode="decimal" value={d.amount} onChange={(e) => set(i, { amount: e.target.value })} /><TextField className="col-span-2" label={d.kind === 'MONTHLY_CHANGE' ? 'Starting on' : 'On'} type="date" min={today} value={d.date} onChange={(e) => set(i, { date: e.target.value })} /></>}</div>
            <Button variant="ghost" onClick={() => setDrafts(drafts.filter((_, j) => j !== i))}>Remove</Button></li>))}</ol>
          <Button variant="secondary" disabled={drafts.length >= 12} onClick={() => setDrafts([...drafts, { kind: 'ONE_OFF', label: '', date: today, amount: '', direction: 'out', billId: '' }])}>Add event</Button>
          {saved.data && saved.data.length > 0 && <fieldset><legend className="mb-1 text-sm font-medium">Compare with saved scenarios (up to 2)</legend>{saved.data.map((s) => <label key={s.id} className="flex items-center gap-2 text-sm"><input type="checkbox" className="h-4 w-4 accent-[rgb(var(--fp-accent))]" checked={compare.includes(s.id)} disabled={!compare.includes(s.id) && compare.length >= 2} onChange={() => setCompare(compare.includes(s.id) ? compare.filter((x) => x !== s.id) : [...compare, s.id])} />{s.name} <span className="text-xs text-muted">({s.horizonDays}d)</span></label>)}</fieldset>}
          <Button loading={simulate.isPending} disabled={!valid()} onClick={() => simulate.mutate()} className="w-full">Run simulation</Button>
          <div className="flex gap-2"><TextField className="flex-1" label="Save as" value={name} onChange={(e) => setName(e.target.value)} /><Button className="self-end" variant="secondary" loading={save.isPending} disabled={!name.trim() || !valid()} onClick={() => save.mutate()}>Save</Button></div>
          {saved.data?.map((s) => <p key={s.id} className="flex items-center justify-between text-xs text-muted"><span>{s.name} · {s.events.length} events</span><span><Button variant="ghost" onClick={() => setDrafts(s.events.map((e) => ({ kind: e.kind, label: e.label, date: e.date ?? today, amount: (e.amount ?? '').replace('-', ''), direction: e.amount?.startsWith('-') ? 'out' : 'in', billId: e.billId ?? '' })))}>Load</Button><Button variant="ghost" onClick={() => del.mutate(s.id)}>Delete</Button></span></p>)}
        </section>
        <section aria-labelledby="res" className="space-y-4 rounded-2xl border border-line bg-surface p-5"><h2 id="res" className="text-lg font-semibold">Projected balance</h2>
          {!first && <p className="text-sm text-muted">Build a scenario and run it to see the projection.</p>}
          {first && <><LineChart runs={runs} currency={first.currency} />
            <dl className="grid gap-3 text-sm sm:grid-cols-2"><div><dt className="text-muted">Baseline end</dt><dd className="num font-semibold">{formatMoney(first.result.endBaseline, first.currency)}</dd></div><div><dt className="text-muted">Scenario end</dt><dd className="num font-semibold">{formatMoney(first.result.endScenario, first.currency)}</dd></div>
              <div><dt className="text-muted">Lowest point (scenario)</dt><dd className="num">{formatMoney(first.result.lowestScenario, first.currency)} on {first.result.lowestScenarioDate}</dd></div><div><dt className="text-muted">First goes negative</dt><dd>{first.result.firstNegativeScenario ?? 'Not within this horizon'}</dd></div></dl>
            {first.result.firstNegativeScenario && <Alert>In this scenario your balance would drop below zero on {first.result.firstNegativeScenario}.</Alert>}
            <div className="rounded-xl bg-surface-2/60 p-3 text-xs text-muted"><p className="mb-1 font-semibold text-ink">Assumptions</p><ul className="list-disc space-y-0.5 pl-4">{first.assumptions.notes.map((n) => <li key={n}>{n}</li>)}<li>Starting balance {first.assumptions.startingBalance}; average daily net {first.assumptions.averageDailyNet}; {first.assumptions.scheduledBills} scheduled bill payments in range.</li></ul></div></>}
        </section>
      </div>
    </div>
  );
}

function LineChart({ runs, currency }: { runs: { label: string; sim: Simulation }[]; currency: string }) {
  const W = 620, H = 220, P = { l: 8, r: 8, t: 10, b: 22 };
  const base = runs[0].sim.result.points;
  const all = runs.flatMap((r) => r.sim.result.points.flatMap((p) => [Number(p.baseline), Number(p.scenario)]));
  const lo = Math.min(0, ...all), hi = Math.max(1, ...all);
  const x = (i: number) => P.l + (W - P.l - P.r) * (i / Math.max(1, base.length - 1));
  const y = (v: number) => P.t + (H - P.t - P.b) * (1 - (v - lo) / (hi - lo));
  const line = (vals: number[]) => vals.map((v, i) => `${i === 0 ? 'M' : 'L'}${x(i).toFixed(1)},${y(v).toFixed(1)}`).join(' ');
  return (
    <div><svg viewBox={`0 0 ${W} ${H}`} className="h-56 w-full" role="img" aria-label={`Projected balance in ${currency}: baseline versus ${runs.map((r) => r.label).join(', ')}`}>
      <line x1={P.l} x2={W - P.r} y1={y(0)} y2={y(0)} stroke="rgb(var(--fp-line))" strokeDasharray="4 4" />
      <path d={line(base.map((p) => Number(p.baseline)))} fill="none" stroke="rgb(var(--fp-muted))" strokeWidth="2" strokeDasharray="6 4" />
      {runs.map((r, k) => <path key={r.label} d={line(r.sim.result.points.map((p) => Number(p.scenario)))} fill="none" stroke={COLORS[k % COLORS.length]} strokeWidth="2.5" strokeLinejoin="round" />)}
      <text x={P.l} y={H - 6} fontSize="10" className="fill-muted">{base[0].date}</text><text x={W - P.r} y={H - 6} textAnchor="end" fontSize="10" className="fill-muted">{base[base.length - 1].date}</text></svg>
      <ul className="flex flex-wrap gap-4 text-xs text-muted"><li className="flex items-center gap-1.5"><span className="h-0.5 w-5 bg-muted" aria-hidden="true" />Baseline</li>{runs.map((r, k) => <li key={r.label} className="flex items-center gap-1.5"><span className="h-0.5 w-5" style={{ background: COLORS[k % COLORS.length] }} aria-hidden="true" />{r.label}</li>)}</ul></div>
  );
}
