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
import type { AccountView, GoalView } from '../../lib/types';
import { amountOnlySchema, goalSchema, type GoalFormValues } from '../../lib/validation';

function GoalCard({ g, accounts, onChanged, onError }: { g: GoalView; accounts: AccountView[]; onChanged: () => void; onError: (m: string) => void }) {
  const [mode, setMode] = useState<'none' | 'plan' | 'transfer'>('none');
  const [from, setFrom] = useState(''); const [to, setTo] = useState('');
  const [amount, setAmount] = useState(''); const [amountErr, setAmountErr] = useState<string | null>(null);
  const usable = accounts.filter((a) => a.currency === g.currency);
  const run = useMutation({
    mutationFn: () => mode === 'plan' ? api.post(`/goals/${g.id}/allocations`, { amount }) : api.post(`/goals/${g.id}/transfers`, { amount, fromAccountId: from, toAccountId: to }),
    onSuccess: () => { setMode('none'); setAmount(''); onChanged(); }, onError: (e) => onError(toApiError(e).message),
  });
  const archive = useMutation({ mutationFn: () => api.delete(`/goals/${g.id}`), onSuccess: onChanged, onError: (e) => onError(toApiError(e).message) });
  const submit = () => {
    const r = amountOnlySchema.safeParse({ amount });
    if (!r.success) return setAmountErr(r.error.issues[0].message);
    if (mode === 'transfer' && (!from || !to || from === to)) return setAmountErr('Choose two different accounts.');
    setAmountErr(null); run.mutate();
  };
  return (
    <article className="rounded-2xl border border-line bg-surface p-5">
      <div className="mb-3 flex items-start justify-between gap-3">
        <div><h3 className="font-semibold">{g.name}{g.status === 'COMPLETED' && <span className="ml-2 rounded-full bg-success/15 px-2 py-0.5 text-xs text-success">reached</span>}</h3>
          <p className="text-xs text-muted">{g.goalType.toLowerCase()}{g.targetDate ? ` · by ${g.targetDate}` : ''}{g.contributionAmount ? ` · plan ${formatMoney(g.contributionAmount, g.currency)} ${g.contributionFrequency?.toLowerCase()}` : ''}</p></div>
        <Button variant="ghost" aria-label={`Archive goal ${g.name}`} onClick={() => { if (window.confirm(`Archive “${g.name}”? Money already transferred stays in your accounts.`)) archive.mutate(); }}>Archive</Button>
      </div>
      <ProgressBar percent={g.percentSaved} tone={g.status === 'COMPLETED' ? 'success' : 'accent'} label={`${g.name} progress`} />
      <p className="num mt-3 text-sm"><strong>{formatMoney(g.saved, g.currency)}</strong> saved of {formatMoney(g.targetAmount, g.currency)} ({g.percentSaved.toFixed(0)}%)</p>
      <p className="num text-xs text-muted">Planned, not yet moved: {formatMoney(g.planned, g.currency)}{g.suggestedMonthly ? ` · to reach the date, about ${formatMoney(g.suggestedMonthly, g.currency)} / month` : ''}</p>
      {g.status === 'ACTIVE' && (<div className="mt-3">
        {mode === 'none' ? (<div className="flex gap-2"><Button variant="secondary" onClick={() => setMode('plan')}>Plan allocation</Button><Button onClick={() => { setMode('transfer'); setFrom(usable[0]?.id ?? ''); setTo(usable.find((a) => a.type === 'SAVINGS')?.id ?? usable[1]?.id ?? ''); }}>Transfer money</Button></div>) : (
          <div className="grid gap-3 rounded-xl border border-line bg-surface-2/50 p-3">
            <p className="text-xs text-muted">{mode === 'plan' ? 'A planned allocation is a note of intent. No money moves and your balances do not change.' : 'This is a real internal transfer between your accounts, recorded in the ledger.'}</p>
            <TextField label="Amount" inputMode="decimal" value={amount} onChange={(e) => setAmount(e.target.value)} error={amountErr ?? undefined} />
            {mode === 'transfer' && <div className="grid grid-cols-2 gap-3"><SelectField label="From" value={from} onChange={(e) => setFrom(e.target.value)}>{usable.map((a) => <option key={a.id} value={a.id}>{a.name}</option>)}</SelectField><SelectField label="To (savings)" value={to} onChange={(e) => setTo(e.target.value)}>{usable.map((a) => <option key={a.id} value={a.id}>{a.name}</option>)}</SelectField></div>}
            <div className="flex gap-2"><Button loading={run.isPending} onClick={submit}>{mode === 'plan' ? 'Save plan' : 'Transfer'}</Button><Button variant="ghost" onClick={() => setMode('none')}>Cancel</Button></div>
          </div>)}
      </div>)}
    </article>
  );
}

export function GoalsPage() {
  const qc = useQueryClient();
  const [error, setError] = useState<string | null>(null);
  const goals = useQuery({ queryKey: ['goals'], queryFn: async () => (await api.get<GoalView[]>('/goals')).data });
  const accounts = useQuery({ queryKey: ['accounts'], queryFn: async () => (await api.get<AccountView[]>('/accounts')).data });
  const form = useForm<GoalFormValues>({ resolver: zodResolver(goalSchema), defaultValues: { goalType: 'EMERGENCY', currency: 'USD', contributionAmount: '', contributionFrequency: '' } });
  const refresh = () => ['goals', 'accounts', 'dashboard', 'transactions'].forEach((k) => qc.invalidateQueries({ queryKey: [k] }));
  const create = useMutation({
    mutationFn: (v: GoalFormValues) => api.post('/goals', { ...v, targetDate: v.targetDate || null, contributionAmount: v.contributionAmount || null, contributionFrequency: v.contributionFrequency || null }),
    onSuccess: () => { form.reset(); setError(null); refresh(); }, onError: (e) => setError(toApiError(e).message),
  });
  return (
    <div className="space-y-6">
      <div><h1 className="text-2xl font-bold">Savings &amp; Planning</h1><p className="text-sm text-muted">Progress counts real transfers only. Planned allocations are kept separate so nothing looks saved before it is.</p></div>
      {error && <Alert>{error}</Alert>}
      <div className="grid gap-6 lg:grid-cols-[1.4fr_1fr]">
        <section aria-labelledby="gl" className="space-y-3"><h2 id="gl" className="sr-only">Your goals</h2>
          {goals.data?.length === 0 && <p className="rounded-2xl border border-dashed border-line p-6 text-sm text-muted">No goals yet — start with an emergency fund.</p>}
          {goals.data?.map((g) => <GoalCard key={g.id} g={g} accounts={accounts.data ?? []} onChanged={() => { setError(null); refresh(); }} onError={setError} />)}
        </section>
        <section aria-labelledby="gn" className="rounded-2xl border border-line bg-surface p-5"><h2 id="gn" className="mb-3 text-lg font-semibold">New goal</h2>
          <form noValidate onSubmit={form.handleSubmit((v) => create.mutate(v))} className="space-y-4">
            <TextField label="Name" error={form.formState.errors.name?.message} {...form.register('name')} />
            <div className="grid grid-cols-2 gap-3"><SelectField label="Type" {...form.register('goalType')}><option value="EMERGENCY">Emergency fund</option><option value="TRAVEL">Travel</option><option value="GADGET">Gadget</option><option value="OTHER">Other</option></SelectField><TextField label="Target date (optional)" type="date" {...form.register('targetDate')} /></div>
            <div className="grid grid-cols-2 gap-3"><TextField label="Target amount" inputMode="decimal" error={form.formState.errors.targetAmount?.message} {...form.register('targetAmount')} /><TextField label="Currency" maxLength={3} error={form.formState.errors.currency?.message} {...form.register('currency')} /></div>
            <div className="grid grid-cols-2 gap-3"><TextField label="Planned contribution" inputMode="decimal" error={form.formState.errors.contributionAmount?.message} {...form.register('contributionAmount')} /><SelectField label="Every" error={form.formState.errors.contributionFrequency?.message} {...form.register('contributionFrequency')}><option value="">—</option><option value="WEEKLY">Week</option><option value="MONTHLY">Month</option></SelectField></div>
            <Button type="submit" loading={create.isPending} className="w-full">Create goal</Button>
          </form></section>
      </div>
    </div>
  );
}
