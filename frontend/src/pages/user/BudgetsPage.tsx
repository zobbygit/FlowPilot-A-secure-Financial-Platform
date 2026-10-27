import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Alert } from '../../components/Alert';
import { Button } from '../../components/Button';
import { ProgressBar } from '../../components/ProgressBar';
import { SelectField } from '../../components/SelectField';
import { TextField } from '../../components/TextField';
import { api, toApiError } from '../../lib/api';
import { formatMoney } from '../../lib/money';
import type { BudgetView, CategoryView } from '../../lib/types';
import { budgetSchema, type BudgetFormValues } from '../../lib/validation';

export function BudgetsPage() {
  const qc = useQueryClient();
  const [error, setError] = useState<string | null>(null);
  const budgets = useQuery({ queryKey: ['budgets'], queryFn: async () => (await api.get<BudgetView[]>('/budgets')).data });
  const categories = useQuery({ queryKey: ['categories'], queryFn: async () => (await api.get<CategoryView[]>('/categories')).data });
  const form = useForm<BudgetFormValues>({ resolver: zodResolver(budgetSchema), defaultValues: { periodType: 'MONTHLY', currency: 'USD', rollover: false, thresholds: '80,100' } });
  const type = form.watch('periodType');

  const create = useMutation({
    mutationFn: (v: BudgetFormValues) => api.post('/budgets', {
      ...v, categoryId: v.categoryId || null, startDate: v.periodType === 'CUSTOM' ? v.startDate : null, endDate: v.periodType === 'CUSTOM' ? v.endDate : null,
      thresholds: v.thresholds === '' ? [] : v.thresholds.split(',').map((t) => Number(t.trim())),
    }),
    onSuccess: () => { form.reset(); setError(null); qc.invalidateQueries({ queryKey: ['budgets'] }); },
    onError: (e) => setError(toApiError(e).message),
  });
  const archive = useMutation({ mutationFn: (id: string) => api.delete(`/budgets/${id}`), onSuccess: () => qc.invalidateQueries({ queryKey: ['budgets'] }), onError: (e) => setError(toApiError(e).message) });

  return (
    <div className="space-y-6">
      <div><h1 className="text-2xl font-bold">Budgets</h1><p className="text-sm text-muted">Spending is calculated live from your ledger — actual versus planned, always up to date.</p></div>
      {error && <Alert>{error}</Alert>}
      <div className="grid gap-6 lg:grid-cols-[1.3fr_1fr]">
        <section aria-labelledby="bl" className="space-y-3">
          <h2 id="bl" className="sr-only">Your budgets</h2>
          {budgets.isLoading && <p role="status" className="text-sm text-muted">Loading…</p>}
          {budgets.data?.length === 0 && <p className="rounded-2xl border border-dashed border-line p-6 text-sm text-muted">No budgets yet. Create one to track spending against a limit.</p>}
          {budgets.data?.map((b) => (
            <article key={b.id} className="rounded-2xl border border-line bg-surface p-5">
              <div className="mb-3 flex items-start justify-between gap-3">
                <div><h3 className="font-semibold">{b.name}</h3><p className="text-xs text-muted">{b.categoryName ?? 'All expenses'} · {b.periodStart} → {b.periodEnd}{b.rollover ? ' · rollover on' : ''}</p></div>
                <Button variant="ghost" loading={archive.isPending && archive.variables === b.id} aria-label={`Archive budget ${b.name}`} onClick={() => { if (window.confirm(`Archive “${b.name}”?`)) archive.mutate(b.id); }}>Archive</Button>
              </div>
              <ProgressBar percent={b.percentUsed} tone={b.state === 'EXCEEDED' ? 'danger' : b.state === 'WARNING' ? 'warning' : 'success'} label={`${b.name} spending`} />
              <div className="num mt-3 flex flex-wrap justify-between gap-2 text-sm">
                <span>Spent <strong>{formatMoney(b.spent, b.currency)}</strong> of {formatMoney(b.limit, b.currency)}{Number(b.carriedOver) > 0 && <span className="text-muted"> (+{formatMoney(b.carriedOver, b.currency)} carried over)</span>}</span>
                <span className={Number(b.remaining) < 0 ? 'font-semibold text-danger' : 'text-muted'}>{Number(b.remaining) < 0 ? `${formatMoney(Math.abs(Number(b.remaining)), b.currency)} over` : `${formatMoney(b.remaining, b.currency)} left`}</span>
              </div>
              {b.crossedThresholds.length > 0 && <p role="status" className={`mt-2 text-xs ${b.state === 'EXCEEDED' ? 'text-danger' : 'text-warning'}`}>{b.state === 'EXCEEDED' ? 'Budget exceeded.' : `Passed your ${b.crossedThresholds.join('% and ')}% alert.`} ({b.percentUsed.toFixed(0)}% used)</p>}
            </article>
          ))}
        </section>
        <section aria-labelledby="bn" className="rounded-2xl border border-line bg-surface p-5">
          <h2 id="bn" className="mb-3 text-lg font-semibold">New budget</h2>
          <form noValidate onSubmit={form.handleSubmit((v) => create.mutate(v))} className="space-y-4">
            <TextField label="Name" error={form.formState.errors.name?.message} {...form.register('name')} />
            <div className="grid grid-cols-2 gap-3">
              <SelectField label="Period" {...form.register('periodType')}><option value="MONTHLY">Monthly</option><option value="CUSTOM">Custom dates</option></SelectField>
              <SelectField label="Category" {...form.register('categoryId')}><option value="">All expenses</option>{categories.data?.filter((c) => c.kind === 'EXPENSE').map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}</SelectField>
            </div>
            {type === 'CUSTOM' && <div className="grid grid-cols-2 gap-3"><TextField label="Start" type="date" error={form.formState.errors.startDate?.message} {...form.register('startDate')} /><TextField label="End" type="date" error={form.formState.errors.endDate?.message} {...form.register('endDate')} /></div>}
            <div className="grid grid-cols-2 gap-3"><TextField label="Limit" inputMode="decimal" error={form.formState.errors.limitAmount?.message} {...form.register('limitAmount')} /><TextField label="Currency" maxLength={3} error={form.formState.errors.currency?.message} {...form.register('currency')} /></div>
            <TextField label="Alert thresholds (%)" hint="Comma-separated, e.g. 50,80,100" error={form.formState.errors.thresholds?.message} {...form.register('thresholds')} />
            {type === 'MONTHLY' && <label className="flex items-center gap-2 text-sm text-muted"><input type="checkbox" className="h-4 w-4 accent-[rgb(var(--fp-accent))]" {...form.register('rollover')} />Roll over unspent money from last month</label>}
            <Button type="submit" loading={create.isPending} className="w-full">Create budget</Button>
          </form>
        </section>
      </div>
    </div>
  );
}
