import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowDownLeft, ArrowUpRight, PiggyBank, Wallet } from 'lucide-react';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../../auth/AuthContext';
import { Alert } from '../../components/Alert';
import { Button, ButtonLink } from '../../components/Button';
import { CashFlowChart, SpendingDonut } from '../../components/Charts';
import { FinancialPipeline } from '../../components/FinancialPipeline';
import { ProgressBar } from '../../components/ProgressBar';
import { StatCard } from '../../components/StatCard';
import { api, toApiError } from '../../lib/api';
import { formatMoney, isoDate } from '../../lib/money';
import type { BillView, DashboardView, GoalView, SafeToSpend } from '../../lib/types';
import { TransactionTable } from './TransactionTable';

const RANGES = { '7D': 7, '30D': 30, '3M': 90, '1Y': 365 } as const;
type RangeKey = keyof typeof RANGES;
const WIDGETS: [string, string][] = [['stats', 'Key figures'], ['safe', 'Safe to spend'], ['pipeline', 'Activity pipeline'], ['cashflow', 'Monthly cash flow'], ['spending', 'Spending breakdown'], ['bills', 'Upcoming bills'], ['goals', 'Savings goals'], ['recent', 'Recent transactions']];
const WIDE = new Set(['stats', 'safe', 'pipeline', 'recent']);

export function DashboardPage() {
  const { me } = useAuth();
  const qc = useQueryClient();
  const [range, setRange] = useState<RangeKey>('30D');
  const [customizing, setCustomizing] = useState(false);
  const [draft, setDraft] = useState<string[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const to = new Date(); const from = new Date(); from.setDate(to.getDate() - (RANGES[range] - 1));

  const q = useQuery({ queryKey: ['dashboard', range, isoDate(to)], queryFn: async () => (await api.get<DashboardView>('/dashboard', { params: { from: isoDate(from), to: isoDate(to) } })).data });
  const bills = useQuery({ queryKey: ['bills', 'upcoming'], queryFn: async () => (await api.get<BillView[]>('/bills/upcoming', { params: { limit: 4 } })).data });
  const goals = useQuery({ queryKey: ['goals'], queryFn: async () => (await api.get<GoalView[]>('/goals')).data });
  const safe = useQuery({ queryKey: ['safe-to-spend'], queryFn: async () => (await api.get<SafeToSpend>('/safe-to-spend')).data });
  const layout = useQuery({ queryKey: ['dashboard-layout'], queryFn: async () => (await api.get<{ widgets: string[] }>('/dashboard-layout')).data.widgets });
  const saveLayout = useMutation({ mutationFn: (w: string[]) => api.put('/dashboard-layout', { widgets: w }), onSuccess: () => { qc.invalidateQueries({ queryKey: ['dashboard-layout'] }); setCustomizing(false); setDraft(null); setError(null); }, onError: (e) => setError(toApiError(e).message) });
  const resetLayout = useMutation({ mutationFn: () => api.delete('/dashboard-layout'), onSuccess: () => { qc.invalidateQueries({ queryKey: ['dashboard-layout'] }); setCustomizing(false); setDraft(null); } });
  const demo = useMutation({ mutationFn: () => api.post('/users/me/demo-data'), onSuccess: () => { qc.invalidateQueries(); setError(null); }, onError: (e) => setError(toApiError(e).message) });

  const d = q.data; const cur = d?.currency ?? 'USD';
  const order = layout.data ?? WIDGETS.map(([k]) => k);
  const editing = draft ?? order;
  const toggle = (id: string) => setDraft(editing.includes(id) ? editing.filter((x) => x !== id) : [...editing, id]);
  const move = (id: string, dir: -1 | 1) => { const a = [...editing], i = a.indexOf(id), j = i + dir; if (i < 0 || j < 0 || j >= a.length) return; [a[i], a[j]] = [a[j], a[i]]; setDraft(a); };

  const card = (id: string, title: string, body: React.ReactNode) => <section key={id} aria-labelledby={`w-${id}`} className={`rounded-2xl border border-line bg-surface p-4 sm:p-5 ${WIDE.has(id) ? 'xl:col-span-2' : ''}`}><h2 id={`w-${id}`} className="mb-3 text-lg font-semibold">{title}</h2>{body}</section>;

  const render = (id: string): React.ReactNode => {
    if (!d) return null;
    switch (id) {
      case 'stats': return (<section key={id} aria-label="Key figures" className="grid gap-4 sm:grid-cols-2 xl:col-span-2 xl:grid-cols-4">
        <StatCard highlight label="Total balance" value={formatMoney(d.totalBalance, cur)} icon={<Wallet className="h-4 w-4" />} /><StatCard label="Income" value={formatMoney(d.totals.income, cur)} change={d.totals.incomeChangePct} icon={<ArrowDownLeft className="h-4 w-4" />} />
        <StatCard label="Expenses" value={formatMoney(d.totals.expenses, cur)} change={d.totals.expenseChangePct} goodWhenUp={false} icon={<ArrowUpRight className="h-4 w-4" />} /><StatCard label="Net saved" value={formatMoney(d.totals.net, cur, { signed: true })} change={d.totals.netChangePct} icon={<PiggyBank className="h-4 w-4" />} /></section>);
      case 'safe': return safe.data ? card(id, 'Safe to spend', (
        <div className="grid gap-4 lg:grid-cols-[1fr_1.4fr]"><div><p className={`num text-2xl sm:text-3xl font-bold ${Number(safe.data.safeToSpend) < 0 ? 'text-danger' : 'text-success'}`}>{formatMoney(safe.data.safeToSpend, safe.data.currency)}</p><p className="text-sm text-muted">{Number(safe.data.safeToSpend) > 0 ? `≈ ${formatMoney(safe.data.perDayRemainingMonth, safe.data.currency)} per day for the remaining ${safe.data.daysLeft} days this month` : 'Nothing is free to spend right now'}</p></div>
          <dl className="grid grid-cols-1 gap-2 text-xs min-[400px]:grid-cols-3"><div className="rounded-lg bg-surface-2/60 p-2"><dt className="text-muted">Spendable</dt><dd className="num font-semibold">{formatMoney(safe.data.liquid, safe.data.currency)}</dd></div><div className="rounded-lg bg-surface-2/60 p-2"><dt className="text-muted">− Bills due this month</dt><dd className="num font-semibold">{formatMoney(safe.data.upcomingBills, safe.data.currency)}</dd></div><div className="rounded-lg bg-surface-2/60 p-2"><dt className="text-muted">− Planned by rules</dt><dd className="num font-semibold">{formatMoney(safe.data.reservedAllocations, safe.data.currency)}</dd></div></dl>
          <details className="text-xs text-muted lg:col-span-2"><summary className="cursor-pointer">How this is calculated</summary><ul className="mt-1 list-disc pl-5">{safe.data.notes.map((n) => <li key={n}>{n}</li>)}</ul></details></div>)) : null;
      case 'pipeline': return <div key={id} className="overflow-hidden xl:col-span-2"><FinancialPipeline from={d.from} to={d.to} /></div>;
      case 'cashflow': return card(id, 'Monthly cash flow', <CashFlowChart data={d.cashFlow} currency={cur} />);
      case 'spending': return card(id, 'Spending breakdown', d.spending.length === 0 ? <p className="text-sm text-muted">No expenses recorded in this period.</p> : <SpendingDonut slices={d.spending} currency={cur} total={d.totals.expenses} />);
      case 'bills': return card(id, 'Upcoming bills & subscriptions', (<>{bills.data?.length === 0 && <p className="text-sm text-muted">Nothing due. <Link to="/app/bills" className="text-accent underline">Add bills</Link></p>}
        <ul className="divide-y divide-line">{bills.data?.map((b) => <li key={b.id} className="flex items-center justify-between gap-3 py-3 text-sm"><div><p className="font-medium">{b.name}</p><p className="text-xs text-muted">{b.nextDueDate}{b.overdue ? ' · overdue' : b.reminderDue ? ` · in ${b.daysUntilDue} days` : ''}</p></div><p className="num font-semibold">{formatMoney(b.amount, b.currency)}</p></li>)}</ul></>));
      case 'goals': return card(id, 'Savings goals', (<>{goals.data?.length === 0 && <p className="text-sm text-muted">No goals yet. <Link to="/app/savings" className="text-accent underline">Create one</Link></p>}
        <ul className="space-y-4">{goals.data?.slice(0, 3).map((g) => <li key={g.id}><div className="mb-1 flex justify-between text-sm"><span className="font-medium">{g.name}</span><span className="num text-muted">{formatMoney(g.saved, g.currency)} / {formatMoney(g.targetAmount, g.currency)}</span></div><ProgressBar percent={g.percentSaved} label={`${g.name} progress`} /></li>)}</ul></>));
     case 'recent': return card(id, 'Recent transactions', d.recent.length === 0 ? <p className="text-sm text-muted">Nothing yet. Record your first transaction.</p> : <><div className="overflow-x-auto"><TransactionTable items={d.recent} /></div><Link to="/app/transactions" className="mt-2 inline-block text-sm font-medium text-accent underline">View all</Link></>);
      default: return null;
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div><h1 className="text-xl font-bold sm:text-2xl">Welcome back, {me?.fullName.split(' ')[0]}</h1><p className="text-sm text-muted">Your financial overview{d ? ` · ${d.from} → ${d.to}` : ''}</p></div>
        <div className="flex w-full flex-col gap-2 min-[400px]:flex-row sm:w-auto sm:items-center">
          <div role="group" aria-label="Date range" className="flex w-full justify-between rounded-xl border border-line bg-surface p-1 min-[400px]:w-auto sm:justify-start">{(Object.keys(RANGES) as RangeKey[]).map((k) => <button key={k} type="button" aria-pressed={range === k} onClick={() => setRange(k)} className={`flex-1 rounded-lg px-3 py-1.5 text-sm font-medium sm:flex-none ${range === k ? 'bg-accent text-white' : 'text-muted hover:text-ink'}`}>{k}</button>)}</div>
          <Button variant="secondary" aria-expanded={customizing} onClick={() => { setCustomizing((c) => !c); setDraft(null); }} className="w-full sm:w-auto">Customize</Button></div>
      </div>

      {customizing && (
        <section aria-label="Customize dashboard" className="rounded-2xl border border-accent/50 bg-accent/5 p-4"><p className="mb-3 text-sm text-muted">Show, hide and reorder your widgets. This only changes how your dashboard looks.</p>
          <ul className="grid gap-2 sm:grid-cols-2">{[...editing, ...WIDGETS.map(([k]) => k).filter((k) => !editing.includes(k))].map((id) => { const on = editing.includes(id), label = WIDGETS.find(([k]) => k === id)![1]; return (
            <li key={id} className="flex items-center justify-between rounded-xl border border-line bg-surface px-3 py-2 text-sm"><label className="flex items-center gap-2"><input type="checkbox" className="h-4 w-4 accent-[rgb(var(--fp-accent))]" checked={on} onChange={() => toggle(id)} />{label}</label>
              {on && <span className="flex gap-1"><Button variant="ghost" aria-label={`Move ${label} up`} onClick={() => move(id, -1)}>↑</Button><Button variant="ghost" aria-label={`Move ${label} down`} onClick={() => move(id, 1)}>↓</Button></span>}</li>); })}</ul>
          <div className="mt-3 flex flex-wrap gap-2"><Button loading={saveLayout.isPending} disabled={editing.length === 0} onClick={() => saveLayout.mutate(editing)}>Save layout</Button><Button variant="ghost" onClick={() => resetLayout.mutate()}>Reset to default</Button><Button variant="ghost" onClick={() => { setCustomizing(false); setDraft(null); }}>Cancel</Button></div></section>)}

      {error && <Alert>{error}</Alert>}
      {q.isLoading && <p role="status" className="text-sm text-muted">Loading your overview…</p>}
      {q.isError && <Alert>{toApiError(q.error).message}</Alert>}

      {d && d.accounts.length === 0 && (
        <div className="rounded-2xl border border-dashed border-line bg-surface p-6 text-center sm:p-8"><h2 className="text-lg font-semibold">Add your first account</h2><p className="mx-auto mt-1 max-w-md text-sm text-muted">Accounts hold your balances. Add one and record a transaction, and this dashboard fills in from your real data.</p>
          <div className="mt-4 flex flex-col justify-center gap-2 min-[400px]:flex-row"><ButtonLink to="/app/accounts">Add an account</ButtonLink><Button variant="secondary" loading={demo.isPending} onClick={() => demo.mutate()}>Explore with demo data</Button></div><p className="mt-2 text-xs text-muted">Demo data adds sample “(demo)” accounts, transactions, bills, a budget and a goal. Archive the demo accounts any time.</p></div>)}

      {d && d.accounts.length > 0 && <div className="grid gap-6 xl:grid-cols-2">{order.map((id) => render(id))}</div>}
    </div>
  );
}