import type { ReactNode } from 'react';
import { formatPct } from '../lib/money';

/** `goodWhenUp` flips colour logic for expenses (a rise is not "good"). */
export function StatCard({ label, value, change, goodWhenUp = true, icon, highlight = false }:
  { label: string; value: string; change?: number | null; goodWhenUp?: boolean; icon: ReactNode; highlight?: boolean }) {
  const pct = change === undefined ? null : formatPct(change);
  const good = change === null || change === undefined ? null : (change >= 0) === goodWhenUp;
  return (
    <div className={`rounded-2xl border p-4 ${highlight ? 'border-accent/50 bg-accent/10 shadow-glow' : 'border-line bg-surface'}`}>
      <div className="mb-3 flex items-center gap-3 text-sm text-muted"><span className="flex h-9 w-9 items-center justify-center rounded-xl bg-surface-2 text-accent" aria-hidden="true">{icon}</span>{label}</div>
      <p className="num text-2xl font-bold tracking-tight">{value}</p>
      {pct && <p className={`mt-1 text-xs ${good ? 'text-success' : 'text-danger'}`}>{pct} <span className="text-muted">vs previous period</span></p>}
      {change === null && <p className="mt-1 text-xs text-muted">No previous data to compare</p>}
    </div>
  );
}
