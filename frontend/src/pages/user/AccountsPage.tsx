import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Alert } from '../../components/Alert';
import { Button } from '../../components/Button';
import { SelectField } from '../../components/SelectField';
import { TextField } from '../../components/TextField';
import { api, toApiError } from '../../lib/api';
import { formatMoney } from '../../lib/money';
import type { AccountView } from '../../lib/types';
import { accountSchema, type AccountForm } from '../../lib/validation';

export function AccountsPage() {
  const qc = useQueryClient();
  const [error, setError] = useState<string | null>(null);
  const accounts = useQuery({ queryKey: ['accounts'], queryFn: async () => (await api.get<AccountView[]>('/accounts')).data });
  const form = useForm<AccountForm>({ resolver: zodResolver(accountSchema), defaultValues: { type: 'BANK', currency: 'USD', openingBalance: '', allowNegative: false } });

  const refresh = () => { qc.invalidateQueries({ queryKey: ['accounts'] }); qc.invalidateQueries({ queryKey: ['dashboard'] }); qc.invalidateQueries({ queryKey: ['transactions'] }); };
  const create = useMutation({
    mutationFn: (v: AccountForm) => api.post('/accounts', { ...v, openingBalance: v.openingBalance === '' ? '0' : v.openingBalance }),
    onSuccess: () => { form.reset(); setError(null); refresh(); },
    onError: (e) => setError(toApiError(e).message),
  });
  const archive = useMutation({
    mutationFn: (id: string) => api.delete(`/accounts/${id}`),
    onSuccess: () => { setError(null); refresh(); },
    onError: (e) => setError(toApiError(e).message),
  });

  return (
    <div className="space-y-6">
      <div><h1 className="text-2xl font-bold">Accounts &amp; Wallets</h1><p className="text-sm text-muted">Every balance is the sum of its ledger entries, so totals always reconcile.</p></div>
      {error && <Alert>{error}</Alert>}
      <div className="grid gap-6 lg:grid-cols-[1.3fr_1fr]">
        <section aria-labelledby="al" className="rounded-2xl border border-line bg-surface p-5">
          <h2 id="al" className="mb-3 text-lg font-semibold">Your accounts</h2>
          {accounts.isLoading && <p role="status" className="text-sm text-muted">Loading…</p>}
          {accounts.data?.length === 0 && <p className="text-sm text-muted">No accounts yet. Add one to start tracking.</p>}
          <ul className="space-y-3">
            {accounts.data?.map((a) => (
              <li key={a.id} className="flex items-center justify-between gap-3 rounded-xl border border-line bg-surface-2/50 p-4">
                <div><p className="font-medium">{a.name}</p><p className="text-xs text-muted">{a.type.toLowerCase()} · {a.currency}{a.allowNegative ? ' · overdraft allowed' : ''}</p></div>
                <div className="flex items-center gap-3">
                  <p className="num text-lg font-semibold">{formatMoney(a.currentBalance, a.currency)}</p>
                  <Button variant="ghost" loading={archive.isPending && archive.variables === a.id} aria-label={`Archive ${a.name}`}
                    onClick={() => { if (window.confirm(`Archive “${a.name}”? Its history is kept.`)) archive.mutate(a.id); }}>Archive</Button>
                </div>
              </li>
            ))}
          </ul>
        </section>
        <section aria-labelledby="an" className="rounded-2xl border border-line bg-surface p-5">
          <h2 id="an" className="mb-3 text-lg font-semibold">Add account</h2>
          <form noValidate onSubmit={form.handleSubmit((v) => create.mutate(v))} className="space-y-4">
            <TextField label="Name" error={form.formState.errors.name?.message} {...form.register('name')} />
            <div className="grid grid-cols-2 gap-3">
              <SelectField label="Type" {...form.register('type')}><option value="BANK">Bank</option><option value="CASH">Cash</option><option value="SAVINGS">Savings</option><option value="WALLET">Wallet</option></SelectField>
              <TextField label="Currency" maxLength={3} error={form.formState.errors.currency?.message} {...form.register('currency')} />
            </div>
            <TextField label="Opening balance" inputMode="decimal" placeholder="0.00" error={form.formState.errors.openingBalance?.message} {...form.register('openingBalance')} />
            <label className="flex items-center gap-2 text-sm text-muted"><input type="checkbox" className="h-4 w-4 accent-[rgb(var(--fp-accent))]" {...form.register('allowNegative')} />Allow overdraft (balance may go below zero)</label>
            <Button type="submit" loading={create.isPending} className="w-full">Add account</Button>
          </form>
        </section>
      </div>
    </div>
  );
}
