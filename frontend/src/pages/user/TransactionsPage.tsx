import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Plus } from 'lucide-react';
import { useState } from 'react';
import { Alert } from '../../components/Alert';
import { Button, ButtonLink } from '../../components/Button';
import { SelectField } from '../../components/SelectField';
import { TextField } from '../../components/TextField';
import { api, toApiError } from '../../lib/api';
import type { AccountView, PageView, TransactionView } from '../../lib/types';
import { TransactionForm } from './TransactionForm';
import { TransactionTable } from './TransactionTable';

const SIZE = 15;

export function TransactionsPage() {
  const qc = useQueryClient();
  const [adding, setAdding] = useState(false);
  const [page, setPage] = useState(0);
  const [filters, setFilters] = useState({ q: '', type: '', accountId: '', from: '', to: '' });
  const [error, setError] = useState<string | null>(null);

  const accounts = useQuery({ queryKey: ['accounts'], queryFn: async () => (await api.get<AccountView[]>('/accounts')).data });
  const list = useQuery({
    queryKey: ['transactions', page, filters],
    queryFn: async () => (await api.get<PageView<TransactionView>>('/transactions', {
      params: { page, size: SIZE, ...Object.fromEntries(Object.entries(filters).filter(([, v]) => v !== '')) },
    })).data,
    placeholderData: (prev) => prev,
  });
  const reverse = useMutation({
    mutationFn: (id: string) => api.post(`/transactions/${id}/reverse`),
    onSuccess: () => { setError(null); ['transactions', 'dashboard', 'accounts'].forEach((k) => qc.invalidateQueries({ queryKey: [k] })); },
    onError: (e) => setError(toApiError(e).message),
  });
  const set = (k: keyof typeof filters, v: string) => { setPage(0); setFilters((f) => ({ ...f, [k]: v })); };
  const pages = list.data ? Math.max(1, Math.ceil(list.data.total / list.data.size)) : 1;

  if (accounts.data?.length === 0) {
    return (<div className="rounded-2xl border border-dashed border-line bg-surface p-8 text-center"><h1 className="text-lg font-semibold">Add an account first</h1><p className="mt-1 text-sm text-muted">Transactions are recorded against an account.</p><ButtonLink to="/app/accounts" className="mt-4">Add an account</ButtonLink></div>);
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div><h1 className="text-2xl font-bold">Transactions</h1><p className="text-sm text-muted">Posted transactions are never edited. Use Reverse to correct a mistake.</p></div>
        <Button onClick={() => setAdding((a) => !a)} aria-expanded={adding}><Plus className="h-4 w-4" aria-hidden="true" />Add transaction</Button>
      </div>
      {adding && accounts.data && <TransactionForm accounts={accounts.data} onDone={() => setAdding(false)} />}
      {error && <Alert>{error}</Alert>}

      <section aria-label="Filters" className="grid gap-3 rounded-2xl border border-line bg-surface p-4 sm:grid-cols-2 lg:grid-cols-5">
        <TextField label="Search" value={filters.q} onChange={(e) => set('q', e.target.value)} placeholder="Merchant or description" />
        <SelectField label="Type" value={filters.type} onChange={(e) => set('type', e.target.value)}><option value="">All</option><option value="EXPENSE">Expense</option><option value="INCOME">Income</option><option value="TRANSFER">Transfer</option></SelectField>
        <SelectField label="Account" value={filters.accountId} onChange={(e) => set('accountId', e.target.value)}><option value="">All</option>{accounts.data?.map((a) => <option key={a.id} value={a.id}>{a.name}</option>)}</SelectField>
        <TextField label="From" type="date" value={filters.from} onChange={(e) => set('from', e.target.value)} />
        <TextField label="To" type="date" value={filters.to} onChange={(e) => set('to', e.target.value)} />
      </section>

      <section className="rounded-2xl border border-line bg-surface p-5" aria-busy={list.isFetching}>
        {list.isLoading && <p role="status" className="text-sm text-muted">Loading…</p>}
        {list.isError && <Alert>{toApiError(list.error).message}</Alert>}
        {list.data && list.data.items.length === 0 && <p className="text-sm text-muted">No transactions match these filters.</p>}
        {list.data && list.data.items.length > 0 && (
          <TransactionTable items={list.data.items} busyId={reverse.isPending ? reverse.variables : null}
            onReverse={(t) => { if (window.confirm('Reverse this transaction? A mirror-image entry is added; the original stays on record.')) reverse.mutate(t.id); }} />
        )}
        <div className="mt-4 flex items-center justify-between text-sm text-muted">
          <span>{list.data ? `${list.data.total} transaction${list.data.total === 1 ? '' : 's'}` : ''}</span>
          <div className="flex items-center gap-2">
            <Button variant="secondary" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>Previous</Button>
            <span>Page {page + 1} of {pages}</span>
            <Button variant="secondary" disabled={page + 1 >= pages} onClick={() => setPage((p) => p + 1)}>Next</Button>
          </div>
        </div>
      </section>
    </div>
  );
}
