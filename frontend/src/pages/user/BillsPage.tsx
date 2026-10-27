import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useLocation } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { Alert } from '../../components/Alert';
import { Button } from '../../components/Button';
import { SelectField } from '../../components/SelectField';
import { TextField } from '../../components/TextField';
import { api, toApiError } from '../../lib/api';
import { formatMoney, isoDate } from '../../lib/money';
import type { AccountView, BillStatus, BillsOverview, BillView, CategoryView } from '../../lib/types';
import { billSchema, type BillFormValues } from '../../lib/validation';

const STATUS_STYLE: Record<BillStatus, string> = { ACTIVE: 'bg-success/15 text-success', PAUSED: 'bg-warning/15 text-warning', CANCELLED: 'bg-surface-2 text-muted', NEEDS_REVIEW: 'bg-danger/15 text-danger' };
const label = (s: string) => s.toLowerCase().replace('_', ' ');

export function BillsPage() {
  const qc = useQueryClient();
  const [error, setError] = useState<string | null>(null);
  const [payFor, setPayFor] = useState<Record<string, string>>({});
  const overview = useQuery({ queryKey: ['bills'], queryFn: async () => (await api.get<BillsOverview>('/bills')).data });
  const accounts = useQuery({ queryKey: ['accounts'], queryFn: async () => (await api.get<AccountView[]>('/accounts')).data });
  const categories = useQuery({ queryKey: ['categories'], queryFn: async () => (await api.get<CategoryView[]>('/categories')).data });
  const prefill = (useLocation().state as { prefill?: { name: string; amount: string; frequency: BillFormValues['frequency']; nextDueDate: string } } | null)?.prefill;
  const form = useForm<BillFormValues>({ resolver: zodResolver(billSchema), defaultValues: { kind: 'SUBSCRIPTION', currency: 'USD', frequency: prefill?.frequency ?? 'MONTHLY', nextDueDate: prefill?.nextDueDate ?? isoDate(new Date()), reminderDaysBefore: '3', name: prefill?.name ?? '', amount: prefill?.amount ?? '' } });

  const done = () => ['bills', 'accounts', 'dashboard', 'transactions'].forEach((k) => qc.invalidateQueries({ queryKey: [k] }));
  const fail = (e: unknown) => setError(toApiError(e).message);
  const create = useMutation({
    mutationFn: (v: BillFormValues) => api.post('/bills', { ...v, categoryId: v.categoryId || null, accountId: v.accountId || null, reminderDaysBefore: Number(v.reminderDaysBefore) }),
    onSuccess: () => { form.reset(); setError(null); done(); }, onError: fail,
  });
  const status = useMutation({ mutationFn: (a: { id: string; status: BillStatus }) => api.patch(`/bills/${a.id}/status`, { status: a.status }), onSuccess: () => { setError(null); done(); }, onError: fail });
  const pay = useMutation({ mutationFn: (a: { id: string; accountId: string }) => api.post(`/bills/${a.id}/pay`, { accountId: a.accountId }), onSuccess: () => { setError(null); done(); }, onError: fail });
  const archive = useMutation({ mutationFn: (id: string) => api.delete(`/bills/${id}`), onSuccess: done, onError: fail });

  const accountFor = (b: BillView) => payFor[b.id] ?? b.accountId ?? accounts.data?.find((a) => a.currency === b.currency)?.id ?? '';

  return (
    <div className="space-y-6">
      <div><h1 className="text-2xl font-bold">Bills &amp; Subscriptions</h1><p className="text-sm text-muted">Paying records a real expense in your ledger and moves the due date forward.</p></div>
      {error && <Alert>{error}</Alert>}
     {overview.data?.estimates && overview.data.estimates.length > 0 && (
        <section aria-label="Estimated costs" className="grid gap-4 sm:grid-cols-2">
          {overview.data.estimates.map((e) => (
            <div key={e.currency} className="rounded-2xl border border-line bg-surface p-4"><p className="text-sm text-muted">Active items · {e.currency}</p>
              <p className="num text-xl font-bold">{formatMoney(e.monthly, e.currency)}<span className="text-sm font-normal text-muted"> / month</span></p>
              <p className="num text-sm text-muted">≈ {formatMoney(e.annual, e.currency)} / year</p></div>
          ))}
        </section>
      )}
      <div className="grid gap-6 lg:grid-cols-[1.4fr_1fr]">
        <section aria-labelledby="bl" className="space-y-3">
          <h2 id="bl" className="sr-only">Your bills and subscriptions</h2>
          {overview.data?.bills.length === 0 && <p className="rounded-2xl border border-dashed border-line p-6 text-sm text-muted">Nothing tracked yet.</p>}
          {overview.data?.bills.map((b) => (
            <article key={b.id} className="rounded-2xl border border-line bg-surface p-4">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <h3 className="font-semibold">{b.name} <span className={`ml-2 rounded-full px-2 py-0.5 text-xs ${STATUS_STYLE[b.status]}`}>{label(b.status)}</span></h3>
                  <p className="text-xs text-muted">{label(b.kind)} · {label(b.frequency)} · due {b.nextDueDate}{b.overdue ? ' (overdue)' : b.reminderDue ? ` (in ${b.daysUntilDue} days)` : ''}</p>
                  {b.previousAmount && <p className="text-xs text-warning">Price changed from {formatMoney(b.previousAmount, b.currency)} — please review.</p>}
                </div>
                <p className="num text-lg font-semibold">{formatMoney(b.amount, b.currency)}</p>
              </div>
              <div className="mt-3 flex flex-wrap items-end gap-2">
                {(b.status === 'ACTIVE' || b.status === 'NEEDS_REVIEW') && (<>
                  <SelectField label="Pay from" className="min-w-40" value={accountFor(b)} onChange={(e) => setPayFor((p) => ({ ...p, [b.id]: e.target.value }))}>
                    {accounts.data?.filter((a) => a.currency === b.currency).map((a) => <option key={a.id} value={a.id}>{a.name}</option>)}</SelectField>
                  <Button loading={pay.isPending && pay.variables?.id === b.id} disabled={!accountFor(b)} onClick={() => pay.mutate({ id: b.id, accountId: accountFor(b) })}>Mark paid</Button></>)}
                {b.status !== 'ACTIVE' && b.status !== 'CANCELLED' && <Button variant="secondary" onClick={() => status.mutate({ id: b.id, status: 'ACTIVE' })}>Confirm active</Button>}
                {b.status === 'ACTIVE' && <Button variant="secondary" onClick={() => status.mutate({ id: b.id, status: 'PAUSED' })}>Pause</Button>}
                {b.status !== 'CANCELLED' && <Button variant="ghost" onClick={() => status.mutate({ id: b.id, status: 'CANCELLED' })}>Cancel</Button>}
                <Button variant="ghost" aria-label={`Remove ${b.name}`} onClick={() => { if (window.confirm(`Remove “${b.name}” from your list? Past payments stay in your ledger.`)) archive.mutate(b.id); }}>Remove</Button>
              </div>
            </article>
          ))}
        </section>
        <section aria-labelledby="bn" className="rounded-2xl border border-line bg-surface p-5">
          <h2 id="bn" className="mb-3 text-lg font-semibold">Add bill or subscription</h2>
          <form noValidate onSubmit={form.handleSubmit((v) => create.mutate(v))} className="space-y-4">
            <TextField label="Name" error={form.formState.errors.name?.message} {...form.register('name')} />
            <div className="grid grid-cols-2 gap-3"><SelectField label="Kind" {...form.register('kind')}><option value="SUBSCRIPTION">Subscription</option><option value="BILL">Bill</option></SelectField>
              <SelectField label="Repeats" {...form.register('frequency')}><option value="WEEKLY">Weekly</option><option value="MONTHLY">Monthly</option><option value="QUARTERLY">Quarterly</option><option value="YEARLY">Yearly</option></SelectField></div>
            <div className="grid grid-cols-2 gap-3"><TextField label="Amount" inputMode="decimal" error={form.formState.errors.amount?.message} {...form.register('amount')} /><TextField label="Currency" maxLength={3} error={form.formState.errors.currency?.message} {...form.register('currency')} /></div>
            <div className="grid grid-cols-2 gap-3"><TextField label="Next due" type="date" error={form.formState.errors.nextDueDate?.message} {...form.register('nextDueDate')} /><TextField label="Remind (days before)" inputMode="numeric" error={form.formState.errors.reminderDaysBefore?.message} {...form.register('reminderDaysBefore')} /></div>
            <SelectField label="Category" {...form.register('categoryId')}><option value="">Uncategorised</option>{categories.data?.filter((c) => c.kind === 'EXPENSE').map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}</SelectField>
            <SelectField label="Default account (optional)" {...form.register('accountId')}><option value="">None</option>{accounts.data?.map((a) => <option key={a.id} value={a.id}>{a.name} ({a.currency})</option>)}</SelectField>
            <TextField label="Notes (optional)" error={form.formState.errors.notes?.message} {...form.register('notes')} />
            <Button type="submit" loading={create.isPending} className="w-full">Add</Button>
          </form>
        </section>
      </div>
    </div>
  );
}
