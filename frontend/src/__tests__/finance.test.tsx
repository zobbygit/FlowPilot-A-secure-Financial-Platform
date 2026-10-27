import { render, screen } from '@testing-library/react';
import { SpendingDonut } from '../components/Charts';
import { AMOUNT_RE, formatMoney, formatPct, isoDate } from '../lib/money';
import { accountSchema, transactionSchema } from '../lib/validation';

describe('money helpers', () => {
  it('formats currency and percentages', () => {
    expect(formatMoney('1234.5', 'USD')).toMatch(/1,234\.50/);
    expect(formatPct(12.34)).toBe('+12.3%');
    expect(formatPct(-5)).toBe('-5.0%');
    expect(formatPct(null)).toBeNull();
  });
  it('builds local ISO dates', () => expect(isoDate(new Date(2026, 0, 5))).toBe('2026-01-05'));
  it('accepts only plain decimals with at most 4 places', () => {
    ['10', '0.5', '1234.5678'].forEach((v) => expect(AMOUNT_RE.test(v)).toBe(true));
    ['', '-1', '1e3', '1.23456', '1,5', 'abc'].forEach((v) => expect(AMOUNT_RE.test(v)).toBe(false));
  });
});

describe('transactionSchema', () => {
  const base = { type: 'EXPENSE' as const, amount: '12.50', occurredOn: '2026-10-01', accountId: 'a1' };
  it('accepts a valid expense', () => expect(transactionSchema.safeParse(base).success).toBe(true));
  it('rejects zero and malformed amounts', () => {
    expect(transactionSchema.safeParse({ ...base, amount: '0' }).success).toBe(false);
    expect(transactionSchema.safeParse({ ...base, amount: '1.99999' }).success).toBe(false);
  });
  it('requires a different destination for transfers', () => {
    expect(transactionSchema.safeParse({ ...base, type: 'TRANSFER' }).success).toBe(false);
    expect(transactionSchema.safeParse({ ...base, type: 'TRANSFER', toAccountId: 'a1' }).success).toBe(false);
    expect(transactionSchema.safeParse({ ...base, type: 'TRANSFER', toAccountId: 'a2' }).success).toBe(true);
  });
});

describe('accountSchema', () => {
  it('normalises currency to upper case and validates opening balance', () => {
    const r = accountSchema.safeParse({ name: 'Main', type: 'BANK', currency: 'usd', openingBalance: '', allowNegative: false });
    expect(r.success && r.data.currency).toBe('USD');
    expect(accountSchema.safeParse({ name: 'Main', type: 'BANK', currency: 'USD', openingBalance: '-5', allowNegative: false }).success).toBe(false);
  });
});

describe('SpendingDonut', () => {
  it('renders an accessible legend with the same data as the chart', () => {
    render(<SpendingDonut total="100" currency="USD" slices={[
      { categoryId: '1', name: 'Food & Dining', color: '#8b5cf6', amount: '60', percent: 60 },
      { categoryId: '2', name: 'Transport', color: '#22d3ee', amount: '40', percent: 40 },
    ]} />);
    expect(screen.getByRole('img')).toHaveAttribute('aria-label', expect.stringContaining('$100.00'));
    expect(screen.getByText('Food & Dining')).toBeInTheDocument();
    expect(screen.getByText('60%')).toBeInTheDocument();
  });
});
