import { ReactNode } from 'react';

/** Small, clearly-labelled product previews built from the real design system. Figures are SAMPLE data, never presented as live. */
export function Frame({ title, children }: { title: string; children: ReactNode }) {
  return (
    <figure className="rounded-2xl border border-line bg-surface/80 p-4 shadow-glow backdrop-blur">
      <figcaption className="mb-3 flex items-center justify-between text-xs text-muted"><span>{title}</span><span className="rounded-full bg-surface-2 px-2 py-0.5">Sample data</span></figcaption>{children}</figure>
  );
}

export function DashboardPreview() {
  const bars = [30, 52, 41, 66, 48, 72, 58, 80];
  return (
    <Frame title="Dashboard"><div className="grid grid-cols-2 gap-2 text-xs"><div className="rounded-xl border border-accent/40 bg-accent/10 p-2"><p className="text-muted">Total balance</p><p className="num text-base font-bold">$24,580.00</p></div><div className="rounded-xl border border-line p-2"><p className="text-muted">Income</p><p className="num text-base font-bold">$4,200.00</p></div></div>
      <svg viewBox="0 0 160 60" className="mt-3 w-full" aria-hidden="true">{bars.map((h, i) => <rect key={i} x={6 + i * 19} y={58 - h * 0.7} width="10" height={h * 0.7} rx="2" className="fill-accent" opacity={0.5 + i * 0.06} />)}</svg></Frame>
  );
}

export function RecordsPreview() {
  return (
    <Frame title="Transactions, receipts, memos"><ul className="space-y-1.5 text-xs">{[['Groceries', '−$54.20', 'receipt'], ['Salary', '+$2,800.00', ''], ['Train pass', '−$36.00', 'memo']].map(([n, a, tag]) => <li key={n} className="flex items-center justify-between rounded-lg bg-surface-2/60 px-2 py-1.5"><span>{n}{tag && <span className="ml-2 rounded-full bg-accent/15 px-1.5 text-accent">{tag}</span>}</span><span className="num">{a}</span></li>)}</ul></Frame>
  );
}

export function PlanPreview() {
  return (
    <Frame title="Budgets & goals"><div className="space-y-3 text-xs">{[['Food & dining', 72, 'bg-warning'], ['Emergency fund', 40, 'bg-accent']].map(([n, p, c]) => <div key={String(n)}><div className="mb-1 flex justify-between"><span>{n}</span><span className="num text-muted">{p}%</span></div><div className="h-2 rounded-full bg-surface-2"><div className={`h-full rounded-full ${c}`} style={{ width: `${p}%` }} /></div></div>)}</div></Frame>
  );
}

export function RulePreview() {
  return (
    <Frame title="A rule"><ol className="space-y-1.5 text-xs">{['When income of $1,000 or more arrives', 'If the source account has unallocated money', 'Then plan 10% to “Emergency fund”'].map((s, i) => <li key={s} className="flex gap-2 rounded-lg bg-surface-2/60 px-2 py-1.5"><span className="num font-bold text-accent">{i + 1}</span>{s}</li>)}</ol><p className="mt-2 text-xs text-muted">Rules plan allocations. They never move money.</p></Frame>
  );
}

export function HouseholdPreview() {
  return (
    <Frame title="Household balances"><ul className="space-y-1.5 text-xs">{[['Ana', 'is owed $42.00', 'text-success'], ['Sam', 'owes $30.00', 'text-danger'], ['Lee', 'owes $12.00', 'text-danger']].map(([n, t, c]) => <li key={n} className="flex justify-between rounded-lg bg-surface-2/60 px-2 py-1.5"><span>{n}</span><span className={`num ${c}`}>{t}</span></li>)}</ul></Frame>
  );
}

export function CalendarPreview() {
  const marks: Record<number, string> = { 3: 'bg-accent', 9: 'bg-accent', 14: 'bg-success', 21: 'bg-accent', 27: 'bg-warning' };
  return (
    <Frame title="Financial calendar"><div className="grid grid-cols-7 gap-1 text-center text-[10px]" aria-hidden="true">{Array.from({ length: 28 }, (_, i) => <div key={i} className="aspect-square rounded-md bg-surface-2/60 p-0.5"><span className="text-muted">{i + 1}</span>{marks[i + 1] && <span className={`mx-auto mt-0.5 block h-1.5 w-1.5 rounded-full ${marks[i + 1]}`} />}</div>)}</div></Frame>
  );
}

/** Illustrative pipeline: same visual language as the in-app pipeline, no figures. */
export function PipelineIllustration() {
  const stages = ['Income', 'Balances', 'Commitments', 'Budgets', 'Goals', 'Expenses', 'Available'];
  const pts = stages.map((_, i) => [40 + i * 120, 90 + Math.sin(i * 0.9) * 34]);
  const d = pts.map(([x, y], i) => `${i ? 'L' : 'M'}${x},${y}`).join(' ');
  return (
    <div className="overflow-x-auto"><svg viewBox="0 0 880 190" className="min-w-[640px] w-full" role="img" aria-label="Illustration of the pipeline: income, balances, commitments, budgets, goals, expenses and remaining funds, connected by a flowing line">
      <path d={d} fill="none" stroke="rgb(var(--fp-primary))" strokeOpacity=".3" strokeWidth="22" strokeLinecap="round" strokeLinejoin="round" transform="translate(0 10)" />
      <path d={d} fill="none" stroke="rgb(var(--fp-accent))" strokeWidth="10" strokeLinecap="round" strokeLinejoin="round" />
      <path d={d} fill="none" stroke="rgb(var(--fp-live))" strokeWidth="3" strokeLinecap="round" strokeDasharray="2 26" className="fp-flow" />
      {pts.map(([x, y], i) => <g key={stages[i]} transform={`translate(${x} ${y})`}><rect x="-38" y="-26" width="76" height="40" rx="12" fill="rgb(var(--fp-surface))" stroke="rgb(var(--fp-line))" transform="skewX(-8)" /><text y="-2" textAnchor="middle" fontSize="11" className="fill-ink">{stages[i]}</text><text y="46" textAnchor="middle" fontSize="10" className="fill-muted">{i + 1}</text></g>)}</svg></div>
  );
}
