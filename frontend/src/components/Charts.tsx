import { formatMoney } from '../lib/money';
import type { CategorySlice, MonthFlow } from '../lib/types';

const PALETTE = ['#8b5cf6', '#a78bfa', '#22d3ee', '#f472b6', '#c084fc', '#34d399', '#6366f1'];

/** Dependency-free SVG donut. The legend and aria-label carry the same data for non-visual users. */
export function SpendingDonut({ slices, currency, total }: { slices: CategorySlice[]; currency: string; total: string }) {
  const R = 54, C = 2 * Math.PI * R;
  let offset = 0;
  return (
    <div className="flex flex-col items-center gap-6 sm:flex-row">
      <svg viewBox="0 0 140 140" className="h-44 w-44 shrink-0" role="img"
        aria-label={`Spending by category, total ${formatMoney(total, currency)}`}>
        <circle cx="70" cy="70" r={R} fill="none" stroke="rgb(var(--fp-surface-2))" strokeWidth="18" />
        {slices.map((s, i) => {
          const len = (s.percent / 100) * C;
          const el = (
            <circle key={s.categoryId ?? 'none'} cx="70" cy="70" r={R} fill="none" stroke={s.color ?? PALETTE[i % PALETTE.length]}
              strokeWidth="18" strokeDasharray={`${len} ${C - len}`} strokeDashoffset={-offset} transform="rotate(-90 70 70)" />
          );
          offset += len;
          return el;
        })}
        <text x="70" y="66" textAnchor="middle" className="fill-muted" fontSize="9">Total spent</text>
        <text x="70" y="82" textAnchor="middle" className="num fill-ink" fontSize="13" fontWeight="700">{formatMoney(total, currency)}</text>
      </svg>
      <ul className="w-full space-y-2 text-sm">
        {slices.map((s, i) => (
          <li key={s.categoryId ?? 'none'} className="flex items-center justify-between gap-3">
            <span className="flex items-center gap-2"><span className="h-2.5 w-2.5 rounded-full" style={{ background: s.color ?? PALETTE[i % PALETTE.length] }} aria-hidden="true" />{s.name}</span>
            <span className="num text-muted">{s.percent.toFixed(0)}%</span>
          </li>
        ))}
      </ul>
    </div>
  );
}

/** Grouped income/expense bars, one pair per month. */
export function CashFlowChart({ data, currency }: { data: MonthFlow[]; currency: string }) {
  const W = 640, H = 220, PAD = { l: 8, r: 8, t: 12, b: 26 };
  const max = Math.max(1, ...data.flatMap((d) => [Number(d.income), Number(d.expenses)]));
  const slot = (W - PAD.l - PAD.r) / Math.max(1, data.length);
  const bw = Math.min(14, slot / 3);
  const y = (v: number) => PAD.t + (H - PAD.t - PAD.b) * (1 - v / max);
  const base = y(0);
  const label = (m: string) => new Date(`${m}-01T00:00:00`).toLocaleString(undefined, { month: 'short' });
  return (
    <div>
      <svg viewBox={`0 0 ${W} ${H}`} className="h-56 w-full" role="img" aria-label="Monthly income and expenses for the last 12 months">
        <line x1={PAD.l} x2={W - PAD.r} y1={base} y2={base} stroke="rgb(var(--fp-line))" />
        {data.map((d, i) => {
          const x = PAD.l + slot * i + slot / 2;
          const inc = Number(d.income), exp = Number(d.expenses);
          return (
            <g key={d.month}>
              <rect x={x - bw - 1} y={y(inc)} width={bw} height={Math.max(0, base - y(inc))} rx="3" fill="rgb(var(--fp-accent))"><title>{`${label(d.month)} income ${formatMoney(d.income, currency)}`}</title></rect>
              <rect x={x + 1} y={y(exp)} width={bw} height={Math.max(0, base - y(exp))} rx="3" fill="#f472b6"><title>{`${label(d.month)} expenses ${formatMoney(d.expenses, currency)}`}</title></rect>
              <text x={x} y={H - 8} textAnchor="middle" fontSize="10" className="fill-muted">{label(d.month)}</text>
            </g>
          );
        })}
      </svg>
      <div className="mt-2 flex gap-4 text-xs text-muted">
        <span className="flex items-center gap-1.5"><span className="h-2 w-2 rounded-full bg-accent" aria-hidden="true" />Income</span>
        <span className="flex items-center gap-1.5"><span className="h-2 w-2 rounded-full" style={{ background: '#f472b6' }} aria-hidden="true" />Expenses</span>
      </div>
    </div>
  );
}
