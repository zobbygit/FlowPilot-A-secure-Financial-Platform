import { useQuery } from '@tanstack/react-query';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { Alert } from '../../components/Alert';
import { Button } from '../../components/Button';
import { api, toApiError } from '../../lib/api';
import { formatMoney, isoDate } from '../../lib/money';
import type { CalendarEvent } from '../../lib/types';

const KINDS: Record<string, string> = { BILL: 'Bills', SUBSCRIPTION: 'Subscriptions', SAVINGS_PLAN: 'Savings plans', INCOME: 'Income', EXPENSE: 'Expenses', TRANSFER: 'Transfers' };

function startOfWeek(d: Date) { const x = new Date(d); x.setDate(x.getDate() - x.getDay()); x.setHours(0, 0, 0, 0); return x; }
const addDays = (d: Date, n: number) => { const x = new Date(d); x.setDate(x.getDate() + n); return x; };

export function CalendarPage() {
  const [view, setView] = useState<'month' | 'week'>('month');
  const [cursor, setCursor] = useState(() => new Date());
  const [hidden, setHidden] = useState<Set<string>>(new Set());
  const [state, setState] = useState<'ALL' | 'PLANNED' | 'COMPLETED'>('ALL');
  const [selected, setSelected] = useState<string>(isoDate(new Date()));

  const { from, to, days } = useMemo(() => {
    if (view === 'week') { const s = startOfWeek(cursor); return { from: s, to: addDays(s, 6), days: 7 }; }
    const first = new Date(cursor.getFullYear(), cursor.getMonth(), 1);
    const s = startOfWeek(first); return { from: s, to: addDays(s, 41), days: 42 };
  }, [view, cursor]);

  const q = useQuery({ queryKey: ['calendar', isoDate(from), isoDate(to)], queryFn: async () => (await api.get<CalendarEvent[]>('/calendar', { params: { from: isoDate(from), to: isoDate(to) } })).data });
  const events = (q.data ?? []).filter((e) => !hidden.has(e.kind) && (state === 'ALL' || e.state === state));
  const byDay = new Map<string, CalendarEvent[]>();
  events.forEach((e) => byDay.set(e.date, [...(byDay.get(e.date) ?? []), e]));
  const step = (dir: number) => setCursor((c) => view === 'week' ? addDays(c, 7 * dir) : new Date(c.getFullYear(), c.getMonth() + dir, 1));
  const title = view === 'week' ? `${from.toLocaleDateString()} – ${to.toLocaleDateString()}` : cursor.toLocaleString(undefined, { month: 'long', year: 'numeric' });
  const today = isoDate(new Date());

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div><h1 className="text-2xl font-bold">Financial calendar</h1><p className="text-sm text-muted"><span className="font-medium text-accent">Planned</span> events come from your schedules; <span className="font-medium text-success">completed</span> ones are real ledger entries.</p></div>
        <div className="flex items-center gap-2">
          <div role="group" aria-label="View" className="flex rounded-xl border border-line bg-surface p-1">{(['month', 'week'] as const).map((v) => <button key={v} type="button" aria-pressed={view === v} onClick={() => setView(v)} className={`rounded-lg px-3 py-1.5 text-sm font-medium capitalize ${view === v ? 'bg-accent text-white' : 'text-muted'}`}>{v}</button>)}</div>
          <Button variant="secondary" aria-label="Previous" onClick={() => step(-1)}><ChevronLeft className="h-4 w-4" aria-hidden="true" /></Button>
          <span className="min-w-40 text-center text-sm font-semibold">{title}</span>
          <Button variant="secondary" aria-label="Next" onClick={() => step(1)}><ChevronRight className="h-4 w-4" aria-hidden="true" /></Button>
        </div>
      </div>
      <div className="flex flex-wrap gap-2" role="group" aria-label="Filters">
        {Object.entries(KINDS).map(([k, label]) => <button key={k} type="button" aria-pressed={!hidden.has(k)} onClick={() => setHidden((h) => { const n = new Set(h); n.has(k) ? n.delete(k) : n.add(k); return n; })} className={`rounded-full border px-3 py-1 text-xs ${hidden.has(k) ? 'border-line text-muted line-through' : 'border-accent/60 bg-accent/10'}`}>{label}</button>)}
        <select aria-label="State filter" value={state} onChange={(e) => setState(e.target.value as typeof state)} className="rounded-full border border-line bg-surface px-3 py-1 text-xs"><option value="ALL">Planned + completed</option><option value="PLANNED">Planned only</option><option value="COMPLETED">Completed only</option></select>
      </div>
      {q.isError && <Alert>{toApiError(q.error).message}</Alert>}
      <div className={`grid gap-px overflow-hidden rounded-2xl border border-line bg-line ${view === 'week' ? 'grid-cols-1 sm:grid-cols-7' : 'grid-cols-7'}`}>
        {Array.from({ length: days }, (_, i) => addDays(from, i)).map((d) => {
          const key = isoDate(d), list = byDay.get(key) ?? [], outside = view === 'month' && d.getMonth() !== cursor.getMonth();
          return (
            <button key={key} type="button" onClick={() => setSelected(key)} aria-label={`${d.toDateString()}, ${list.length} events`} aria-pressed={selected === key}
              className={`min-h-20 bg-surface p-1.5 text-left align-top ${outside ? 'opacity-50' : ''} ${selected === key ? 'outline outline-2 outline-accent' : ''}`}>
              <span className={`num text-xs ${key === today ? 'rounded-full bg-accent px-1.5 py-0.5 text-white' : 'text-muted'}`}>{d.getDate()}</span>
              <span className="mt-1 flex flex-wrap gap-1">{list.slice(0, 4).map((e, i) => <span key={i} className={`h-1.5 w-1.5 rounded-full ${e.state === 'PLANNED' ? 'bg-accent' : 'bg-success'}`} aria-hidden="true" />)}{list.length > 4 && <span className="text-[10px] text-muted">+{list.length - 4}</span>}</span>
            </button>);
        })}
      </div>
      <section aria-labelledby="day" className="rounded-2xl border border-line bg-surface p-5">
        <h2 id="day" className="mb-3 text-lg font-semibold">{new Date(`${selected}T00:00:00`).toDateString()}</h2>
        {(byDay.get(selected) ?? []).length === 0 && <p className="text-sm text-muted">Nothing scheduled or recorded.</p>}
        <ul className="divide-y divide-line">{(byDay.get(selected) ?? []).map((e, i) => (
          <li key={i} className="flex items-center justify-between gap-3 py-3 text-sm">
            <div><p className="font-medium">{e.title}</p><p className="text-xs text-muted"><span className={e.state === 'PLANNED' ? 'text-accent' : 'text-success'}>{e.state.toLowerCase()}</span> · {KINDS[e.kind] ?? e.kind} · <Link to={e.link} className="underline">open</Link></p></div>
            <p className="num font-semibold">{formatMoney(e.amount, e.currency)}</p></li>))}</ul>
      </section>
    </div>
  );
}
