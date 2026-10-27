import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { api, toApiError } from '../lib/api';
import { formatMoney } from '../lib/money';
import type { PipelineView } from '../lib/types';
import { Alert } from './Alert';

const W = 980, H = 300;

/**
 * "4D-style" art direction: layered depth (back grid, mid pipe, front nodes), perspective-skewed nodes, time as
 * flowing particles, and subtle cyan pulses where there was activity in the last 24h. The numbers are ALWAYS printed
 * in a plain list underneath, so the picture never replaces or hides the figures. Motion respects reduced-motion.
 */
export function FinancialPipeline({ from, to }: { from: string; to: string }) {
  const [selected, setSelected] = useState<string>('income');
  const q = useQuery({ queryKey: ['pipeline', from, to], queryFn: async () => (await api.get<PipelineView>('/pipeline', { params: { from, to } })).data });
  if (q.isLoading) return <p role="status" className="text-sm text-muted">Loading pipeline…</p>;
  if (q.isError) return <Alert>{toApiError(q.error).message}</Alert>;
  if (!q.data) return null;
  const { stages, currency } = q.data;
  const step = (W - 120) / (stages.length - 1);
  const pos = stages.map((_, i) => ({ x: 60 + i * step, y: 150 + Math.sin(i * 0.9) * 46 }));
  const path = pos.map((p, i) => `${i === 0 ? 'M' : 'L'}${p.x},${p.y}`).join(' ');
  const sel = stages.find((s) => s.id === selected) ?? stages[0];

  return (
    <section aria-labelledby="pl" className="rounded-2xl border border-line bg-surface p-5">
      <div className="mb-2 flex items-center justify-between"><h2 id="pl" className="text-lg font-semibold">Financial activity pipeline</h2><span className="rounded-full bg-live/15 px-2.5 py-1 text-xs text-live"><span className="fp-live-dot mr-1.5 inline-block h-1.5 w-1.5 rounded-full bg-live" aria-hidden="true" />cyan = activity in the last 24h</span></div>
      <div className="overflow-x-auto"><svg viewBox={`0 0 ${W} ${H}`} className="min-w-[720px] w-full" role="group" aria-label="Pipeline stages from income to remaining funds. The same figures are listed below.">
        <defs>
          <linearGradient id="pipe" x1="0" x2="1"><stop offset="0" stopColor="rgb(var(--fp-accent))" stopOpacity=".9" /><stop offset="1" stopColor="rgb(var(--fp-primary))" stopOpacity=".9" /></linearGradient>
          <linearGradient id="floor" x1="0" x2="0" y1="0" y2="1"><stop offset="0" stopColor="rgb(var(--fp-accent))" stopOpacity="0" /><stop offset="1" stopColor="rgb(var(--fp-accent))" stopOpacity=".16" /></linearGradient>
          <filter id="glow"><feGaussianBlur stdDeviation="4" result="b" /><feMerge><feMergeNode in="b" /><feMergeNode in="SourceGraphic" /></feMerge></filter>
        </defs>
        <g aria-hidden="true">{/* back layer: perspective floor grid */}
          <rect x="0" y="170" width={W} height="130" fill="url(#floor)" />
          {Array.from({ length: 12 }, (_, i) => <line key={`v${i}`} x1={W / 2 + (i - 5.5) * 20} y1="170" x2={W / 2 + (i - 5.5) * 120} y2={H} stroke="rgb(var(--fp-line))" strokeOpacity=".6" />)}
          {Array.from({ length: 5 }, (_, i) => <line key={`h${i}`} x1="0" x2={W} y1={176 + i * i * 7 + i * 6} y2={176 + i * i * 7 + i * 6} stroke="rgb(var(--fp-line))" strokeOpacity=".5" />)}
          {/* mid layer: the pipe, a soft shadow, and flowing "time" particles */}
          <path d={path} fill="none" stroke="rgb(var(--fp-primary))" strokeOpacity=".25" strokeWidth="26" strokeLinecap="round" strokeLinejoin="round" transform="translate(0 14)" />
          <path d={path} fill="none" stroke="url(#pipe)" strokeWidth="14" strokeLinecap="round" strokeLinejoin="round" />
          <path d={path} fill="none" stroke="rgb(var(--fp-live))" strokeWidth="3" strokeLinecap="round" strokeDasharray="2 26" className="fp-flow" filter="url(#glow)" />
        </g>
        {stages.map((s, i) => {   // front layer: dimensional nodes (each is a real button for keyboard/screen-reader use)
          const { x, y } = pos[i], active = s.id === selected;
          return (
            <g key={s.id} transform={`translate(${x} ${y})`} role="button" tabIndex={0} aria-pressed={active} aria-label={`${s.title}: ${formatMoney(s.amount, currency)}`} onClick={() => setSelected(s.id)}
              onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); setSelected(s.id); } }} className="cursor-pointer outline-none focus-visible:[&>rect]:stroke-[3]">
              {s.liveActivity && <circle r="34" fill="none" stroke="rgb(var(--fp-live))" strokeWidth="2" className="fp-pulse" />}
              <rect x="-44" y="-20" width="88" height="60" rx="14" fill="rgb(var(--fp-surface-2))" opacity=".9" transform="skewX(-8) translate(6 10)" />
              <rect x="-44" y="-30" width="88" height="60" rx="14" fill="rgb(var(--fp-surface))" stroke={active ? 'rgb(var(--fp-accent))' : 'rgb(var(--fp-line))'} strokeWidth={active ? 2.5 : 1.5} transform="skewX(-8)" />
              <text y="-8" textAnchor="middle" fontSize="9.5" className="fill-muted">{s.title.split(' ')[0]}</text>
              <text y="10" textAnchor="middle" fontSize="12" fontWeight="700" className="num fill-ink">{formatMoney(s.amount, currency)}</text>
              <text y="58" textAnchor="middle" fontSize="10" className="fill-muted">{i + 1}</text>
            </g>);
        })}
      </svg></div>

      <ol className="mt-4 grid gap-2 sm:grid-cols-2 lg:grid-cols-4" aria-label="Pipeline figures">{stages.map((s, i) => (
        <li key={s.id}><button type="button" aria-pressed={s.id === selected} onClick={() => setSelected(s.id)} className={`w-full rounded-xl border p-3 text-left text-sm ${s.id === selected ? 'border-accent bg-accent/10' : 'border-line'}`}>
          <span className="text-xs text-muted">{i + 1}. {s.title}{s.liveActivity && <span className="ml-1 text-live">● live</span>}</span><span className="num block text-base font-semibold">{formatMoney(s.amount, currency)}</span>{s.secondary && <span className="block text-xs text-muted">{s.secondary}</span>}</button></li>))}</ol>

      <div className="mt-4 rounded-xl border border-line bg-surface-2/50 p-4" aria-live="polite"><h3 className="font-semibold">{sel.title}</h3><p className="mt-1 text-sm text-muted">{sel.explanation}</p>
        {sel.related.length > 0 ? <ul className="mt-3 divide-y divide-line text-sm">{sel.related.map((r) => <li key={r.id} className="flex justify-between gap-3 py-1.5"><span>{r.title} <span className="text-xs text-muted">{r.date}</span></span><span className="num">{formatMoney(r.amount, currency)}</span></li>)}</ul> : <p className="mt-2 text-xs text-muted">No individual items for this stage.</p>}</div>
    </section>
  );
}
