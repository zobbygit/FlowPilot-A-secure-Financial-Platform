/** Display-only formatting. Arithmetic on money never happens in the browser. */
export function formatMoney(amount: string | number, currency: string, opts: { signed?: boolean } = {}): string {
  const n = Number(amount);
  const f = new Intl.NumberFormat(undefined, { style: 'currency', currency, signDisplay: opts.signed ? 'exceptZero' : 'auto' });
  return f.format(Number.isFinite(n) ? n : 0);
}

export function formatPct(p: number | null): string | null {
  return p === null ? null : `${p > 0 ? '+' : ''}${p.toFixed(1)}%`;
}

/** yyyy-mm-dd from local calendar parts (avoids toISOString UTC shifts). */
export function isoDate(d: Date): string {
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${d.getFullYear()}-${m}-${day}`;
}

export const AMOUNT_RE = /^\d{1,15}(\.\d{1,4})?$/;
