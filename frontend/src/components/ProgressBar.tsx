export function ProgressBar({ percent, tone = 'accent', label }: { percent: number; tone?: 'accent' | 'warning' | 'danger' | 'success'; label: string }) {
  const color = { accent: 'bg-accent', warning: 'bg-warning', danger: 'bg-danger', success: 'bg-success' }[tone];
  const v = Math.max(0, Math.min(100, percent));
  return (
    <div role="progressbar" aria-label={label} aria-valuenow={Math.round(v)} aria-valuemin={0} aria-valuemax={100} className="h-2.5 w-full overflow-hidden rounded-full bg-surface-2">
      <div className={`h-full rounded-full ${color} transition-[width] duration-500`} style={{ width: `${v}%` }} />
    </div>
  );
}
