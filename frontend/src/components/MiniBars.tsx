/** Small grouped bar chart for admin trends. Values are also available as a table for assistive tech. */
export function MiniBars({ labels, series, height = 140 }: { labels: string[]; series: { name: string; values: number[]; className: string }[]; height?: number }) {
  const W = 600, H = height, P = 18;
  const max = Math.max(1, ...series.flatMap((s) => s.values));
  const slot = (W - P * 2) / labels.length, bw = Math.max(2, Math.min(12, slot / (series.length + 1)));
  return (
    <div>
      <svg viewBox={`0 0 ${W} ${H}`} className="w-full" style={{ height }} role="img" aria-label={series.map((s) => s.name).join(' and ') + ' per day'}>
        <line x1={P} x2={W - P} y1={H - 18} y2={H - 18} stroke="rgb(var(--fp-line))" />
        {labels.map((l, i) => {
          const x0 = P + slot * i + (slot - bw * series.length) / 2;
          return <g key={l}>{series.map((s, k) => { const h = ((H - 40) * s.values[i]) / max; return <rect key={s.name} x={x0 + k * bw} y={H - 18 - h} width={bw - 1} height={h} rx="2" className={s.className}><title>{`${l}: ${s.name} ${s.values[i]}`}</title></rect>; })}
            {i % 2 === 0 && <text x={P + slot * i + slot / 2} y={H - 4} textAnchor="middle" fontSize="9" className="fill-muted">{l.slice(5)}</text>}</g>;
        })}
      </svg>
      <ul className="mt-1 flex flex-wrap gap-4 text-xs text-muted">{series.map((s) => <li key={s.name} className="flex items-center gap-1.5"><span className={`h-2 w-2 rounded-sm ${s.className}`} aria-hidden="true" />{s.name}</li>)}</ul>
    </div>
  );
}
