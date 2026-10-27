import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useRef, useState } from 'react';
import { useForm } from 'react-hook-form';
import { Alert } from '../../components/Alert';
import { Button } from '../../components/Button';
import { SelectField } from '../../components/SelectField';
import { TextField } from '../../components/TextField';
import { api, toApiError } from '../../lib/api';
import { isoDate } from '../../lib/money';
import type { AccountView, CategoryView } from '../../lib/types';
import { transactionSchema, type TransactionFormValues } from '../../lib/validation';

export function TransactionForm({ accounts, onDone }: { accounts: AccountView[]; onDone: () => void }) {
  const qc = useQueryClient();
  const [error, setError] = useState<string | null>(null);
  // One key per logical submission: a retry after a network blip can never double-post.
  const idemKey = useRef(crypto.randomUUID());
  const categories = useQuery({ queryKey: ['categories'], queryFn: async () => (await api.get<CategoryView[]>('/categories')).data });
  const { register, handleSubmit, watch, formState: { errors } } = useForm<TransactionFormValues>({
    resolver: zodResolver(transactionSchema),
    defaultValues: { type: 'EXPENSE', occurredOn: isoDate(new Date()), accountId: accounts[0]?.id ?? '' },
  });
  const type = watch('type');
  const kind = type === 'INCOME' ? 'INCOME' : 'EXPENSE';

  const post = useMutation({
    mutationFn: (v: TransactionFormValues) => api.post('/transactions', {
      ...v, categoryId: v.categoryId || null, toAccountId: type === 'TRANSFER' ? v.toAccountId : null,
    }, { headers: { 'Idempotency-Key': idemKey.current } }),
    onSuccess: () => {
      idemKey.current = crypto.randomUUID();
      ['transactions', 'dashboard', 'accounts'].forEach((k) => qc.invalidateQueries({ queryKey: [k] }));
      onDone();
    },
    onError: (e) => setError(toApiError(e).message),
  });

  return (
    <form noValidate onSubmit={handleSubmit((v) => { setError(null); post.mutate(v); })} className="grid gap-4 rounded-2xl border border-line bg-surface p-5 md:grid-cols-2">
      {error && <div className="md:col-span-2"><Alert>{error}</Alert></div>}
      <SelectField label="Type" {...register('type')}><option value="EXPENSE">Expense</option><option value="INCOME">Income</option><option value="TRANSFER">Transfer</option></SelectField>
      <TextField label="Amount" inputMode="decimal" placeholder="0.00" error={errors.amount?.message} {...register('amount')} />
      <SelectField label={type === 'TRANSFER' ? 'From account' : 'Account'} error={errors.accountId?.message} {...register('accountId')}>
        {accounts.map((a) => <option key={a.id} value={a.id}>{a.name} ({a.currency})</option>)}
      </SelectField>
      {type === 'TRANSFER' ? (
        <SelectField label="To account" error={errors.toAccountId?.message} {...register('toAccountId')}>
          <option value="">Choose…</option>{accounts.map((a) => <option key={a.id} value={a.id}>{a.name} ({a.currency})</option>)}
        </SelectField>
      ) : (
        <SelectField label="Category" {...register('categoryId')}>
          <option value="">Uncategorised</option>
          {categories.data?.filter((c) => c.kind === kind).map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
        </SelectField>
      )}
      <TextField label="Date" type="date" error={errors.occurredOn?.message} {...register('occurredOn')} />
      <TextField label="Merchant / payee (optional)" error={errors.merchant?.message} {...register('merchant')} />
      <TextField className="md:col-span-2" label="Description (optional)" error={errors.description?.message} {...register('description')} />
      <div className="flex gap-3 md:col-span-2"><Button type="submit" loading={post.isPending}>Record transaction</Button><Button type="button" variant="ghost" onClick={onDone}>Cancel</Button></div>
    </form>
  );
}
