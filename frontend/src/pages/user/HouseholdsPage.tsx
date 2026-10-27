import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Alert } from '../../components/Alert';
import { Button } from '../../components/Button';
import { ProgressBar } from '../../components/ProgressBar';
import { SelectField } from '../../components/SelectField';
import { TextField } from '../../components/TextField';
import { api, toApiError } from '../../lib/api';
import { AMOUNT_RE, formatMoney, isoDate } from '../../lib/money';
import type { CategoryView, HExpense, HInvitation, HouseholdDetail, HouseholdSummary, PaymentProfile, Paged } from '../../lib/types';

export function HouseholdsPage() {
  const qc = useQueryClient();
  const [sel, setSel] = useState<string | null>(null); const [error, setError] = useState<string | null>(null);
  const [name, setName] = useState(''); const [currency, setCurrency] = useState('USD');
  const list = useQuery({ queryKey: ['households'], queryFn: async () => (await api.get<HouseholdSummary[]>('/households')).data });
  const invites = useQuery({ queryKey: ['household-invites'], queryFn: async () => (await api.get<HInvitation[]>('/households/invitations')).data });
  const profile = useQuery({ queryKey: ['payments', 'profile'], queryFn: async () => (await api.get<PaymentProfile>('/payments/profile')).data });
  const refresh = () => ['households', 'household', 'household-invites', 'household-expenses'].forEach((k) => qc.invalidateQueries({ queryKey: [k] }));
  const fail = (e: unknown) => setError(toApiError(e).message);
  const create = useMutation({ mutationFn: () => api.post<HouseholdSummary>('/households', { name, currency: currency.toUpperCase() }), onSuccess: (r) => { setName(''); setSel(r.data.id); setError(null); refresh(); }, onError: fail });
  const respond = useMutation({ mutationFn: (a: { id: string; accept: boolean }) => api.post(`/households/invitations/${a.id}/${a.accept ? 'accept' : 'decline'}`), onSuccess: () => { setError(null); refresh(); }, onError: fail });
  return (
    <div className="space-y-6"><div><h1 className="text-2xl font-bold">Shared Households</h1><p className="text-sm text-muted">Track shared expenses, split them fairly and settle up. Only members see a household's data; your personal accounts stay private.</p>{profile.data && <p className="text-xs text-muted">Others invite you with your handle: <span className="num font-semibold text-ink">@{profile.data.handle}</span></p>}</div>
      {error && <Alert>{error}</Alert>}
      {invites.data && invites.data.length > 0 && <section aria-label="Invitations" className="rounded-2xl border border-accent/50 bg-accent/5 p-4"><h2 className="mb-2 font-semibold">Invitations</h2><ul className="space-y-2 text-sm">{invites.data.map((i) => <li key={i.id} className="flex flex-wrap items-center justify-between gap-2"><span><strong>{i.invitedBy}</strong> invited you to “{i.householdName}”</span><span className="flex gap-1"><Button onClick={() => respond.mutate({ id: i.id, accept: true })}>Accept</Button><Button variant="ghost" onClick={() => respond.mutate({ id: i.id, accept: false })}>Decline</Button></span></li>)}</ul></section>}
      <div className="grid gap-6 lg:grid-cols-[18rem_1fr]">
        <section className="space-y-3"><ul className="space-y-2">{list.data?.map((h) => <li key={h.id}><button type="button" onClick={() => setSel(h.id)} className={`w-full rounded-2xl border bg-surface p-3 text-left text-sm ${sel === h.id ? 'border-accent' : 'border-line'}`}><span className="font-medium">{h.name}</span><span className="block text-xs text-muted">{h.members} members · you {Number(h.myBalance) >= 0 ? 'are owed' : 'owe'} {formatMoney(Math.abs(Number(h.myBalance)), h.currency)}</span></button></li>)}</ul>
          <div className="space-y-3 rounded-2xl border border-line bg-surface p-4"><h2 className="font-semibold">New household</h2><TextField label="Name" value={name} onChange={(e) => setName(e.target.value)} /><TextField label="Currency" maxLength={3} value={currency} onChange={(e) => setCurrency(e.target.value.toUpperCase())} /><Button loading={create.isPending} disabled={!name.trim()} onClick={() => create.mutate()} className="w-full">Create</Button></div></section>
        <section>{sel ? <Household id={sel} onChanged={refresh} setError={setError} onGone={() => setSel(null)} /> : <p className="rounded-2xl border border-dashed border-line p-8 text-center text-sm text-muted">Select or create a household.</p>}</section>
      </div></div>
  );
}

function Household({ id, onChanged, setError, onGone }: { id: string; onChanged: () => void; setError: (m: string | null) => void; onGone: () => void }) {
  const [tab, setTab] = useState<'expenses' | 'balances' | 'budgets' | 'members'>('expenses');
  const detail = useQuery({ queryKey: ['household', id], queryFn: async () => (await api.get<HouseholdDetail>(`/households/${id}`)).data, retry: false });
  const expenses = useQuery({ queryKey: ['household-expenses', id], queryFn: async () => (await api.get<Paged<HExpense>>(`/households/${id}/expenses`, { params: { size: 50 } })).data });
  const cats = useQuery({ queryKey: ['categories'], queryFn: async () => (await api.get<CategoryView[]>('/categories')).data });
  const [handle, setHandle] = useState('');
  const [f, setF] = useState({ description: '', amount: '', occurredOn: isoDate(new Date()), categoryId: '', payerId: '', splitType: 'EQUAL' as 'EQUAL' | 'EXACT' | 'PERCENT' });
  const [vals, setVals] = useState<Record<string, string>>({}); const [chosen, setChosen] = useState<Record<string, boolean>>({});
  const [bf, setBf] = useState({ name: '', limit: '', categoryId: '' });
  const run = useMutation({ mutationFn: (fn: () => Promise<unknown>) => fn(), onSuccess: () => { setError(null); onChanged(); }, onError: (e) => setError(toApiError(e).message) });
  if (detail.isLoading) return <p role="status" className="text-sm text-muted">Loading…</p>;
  if (detail.isError || !detail.data) return <Alert>{toApiError(detail.error).message}</Alert>;
  const d = detail.data, cur = d.currency, members = d.members;
  const parts = members.filter((m) => chosen[m.userId] ?? true);
  const addExpense = () => {
    if (!f.description.trim() || !AMOUNT_RE.test(f.amount)) return setError('Enter a description and a valid amount.');
    const payer = f.payerId || members[0].userId;
    const participants = parts.map((m) => ({ userId: m.userId, value: f.splitType === 'EQUAL' ? '0' : (vals[m.userId] || '0') }));
    if (participants.length === 0) return setError('Choose at least one person to split with.');
    run.mutate(() => api.post(`/households/${id}/expenses`, { description: f.description, amount: f.amount, occurredOn: f.occurredOn, categoryId: f.categoryId || null, payerId: payer, splitType: f.splitType, participants }).then(() => { setF({ ...f, description: '', amount: '' }); setVals({}); }));
  };
  return (
    <div className="space-y-4"><div className="flex flex-wrap items-center justify-between gap-2"><h2 className="text-xl font-semibold">{d.name}</h2>{d.owner && <Button variant="ghost" onClick={() => { if (window.confirm('Archive this household for everyone?')) run.mutate(() => api.post(`/households/${id}/archive`).then(onGone)); }}>Archive</Button>}</div>
      <div role="tablist" aria-label="Household" className="flex w-fit gap-1 rounded-xl border border-line bg-surface p-1">{(['expenses', 'balances', 'budgets', 'members'] as const).map((t) => <button key={t} role="tab" aria-selected={tab === t} onClick={() => setTab(t)} className={`rounded-lg px-3 py-1.5 text-sm font-medium capitalize ${tab === t ? 'bg-accent text-white' : 'text-muted'}`}>{t}</button>)}</div>

      {tab === 'expenses' && <div className="grid gap-4 xl:grid-cols-[1.3fr_1fr]"><ul className="space-y-2">{expenses.data?.items.length === 0 && <li className="rounded-2xl border border-dashed border-line p-6 text-sm text-muted">No shared expenses yet.</li>}
        {expenses.data?.items.map((e) => <li key={e.id} className="rounded-2xl border border-line bg-surface p-3 text-sm"><div className="flex items-start justify-between gap-2"><div><p className="font-medium">{e.description}</p><p className="text-xs text-muted">{e.occurredOn} · paid by {e.payer}{e.categoryName ? ` · ${e.categoryName}` : ''} · {e.splitType.toLowerCase()} split</p><p className="mt-1 text-xs text-muted">{e.shares.map((s) => `${s.name} ${formatMoney(s.share, cur)}`).join(' · ')}</p></div>
          <div className="text-right"><p className="num font-semibold">{formatMoney(e.amount, cur)}</p>{e.canEdit && <Button variant="ghost" aria-label={`Remove ${e.description}`} onClick={() => { if (window.confirm('Remove this expense? Balances recalculate; history is kept.')) run.mutate(() => api.delete(`/households/${id}/expenses/${e.id}`)); }}>Remove</Button>}</div></div></li>)}</ul>
        <div className="space-y-3 rounded-2xl border border-line bg-surface p-4"><h3 className="font-semibold">Add shared expense</h3>
          <TextField label="What for?" value={f.description} onChange={(e) => setF({ ...f, description: e.target.value })} /><div className="grid grid-cols-2 gap-2"><TextField label={`Amount (${cur})`} inputMode="decimal" value={f.amount} onChange={(e) => setF({ ...f, amount: e.target.value })} /><TextField label="Date" type="date" value={f.occurredOn} onChange={(e) => setF({ ...f, occurredOn: e.target.value })} /></div>
          <div className="grid grid-cols-2 gap-2"><SelectField label="Paid by" value={f.payerId || members[0].userId} onChange={(e) => setF({ ...f, payerId: e.target.value })}>{members.map((m) => <option key={m.userId} value={m.userId}>{m.name}</option>)}</SelectField><SelectField label="Category" value={f.categoryId} onChange={(e) => setF({ ...f, categoryId: e.target.value })}><option value="">None</option>{cats.data?.filter((c) => c.kind === 'EXPENSE').map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}</SelectField></div>
          <SelectField label="Split" value={f.splitType} onChange={(e) => setF({ ...f, splitType: e.target.value as typeof f.splitType })}><option value="EQUAL">Equally</option><option value="EXACT">Exact amounts</option><option value="PERCENT">By percentage</option></SelectField>
          <fieldset className="space-y-1"><legend className="text-sm font-medium">Between</legend>{members.map((m) => <div key={m.userId} className="flex items-center gap-2 text-sm"><input type="checkbox" aria-label={`Include ${m.name}`} className="h-4 w-4 accent-[rgb(var(--fp-accent))]" checked={chosen[m.userId] ?? true} onChange={(e) => setChosen({ ...chosen, [m.userId]: e.target.checked })} /><span className="flex-1">{m.name}</span>{f.splitType !== 'EQUAL' && (chosen[m.userId] ?? true) && <input aria-label={`${m.name} ${f.splitType === 'PERCENT' ? 'percent' : 'amount'}`} inputMode="decimal" className="num w-24 rounded-lg border border-line bg-surface-2/60 px-2 py-1 text-right" value={vals[m.userId] ?? ''} onChange={(e) => setVals({ ...vals, [m.userId]: e.target.value })} />}</div>)}</fieldset>
          <Button className="w-full" loading={run.isPending} onClick={addExpense}>Add expense</Button></div></div>}

      {tab === 'balances' && <div className="grid gap-4 lg:grid-cols-2"><section className="rounded-2xl border border-line bg-surface p-4"><h3 className="mb-2 font-semibold">Who owes whom</h3><ul className="space-y-1 text-sm">{members.map((m) => <li key={m.userId} className="flex justify-between"><span>{m.name}</span><span className={`num ${Number(m.balance) < 0 ? 'text-danger' : Number(m.balance) > 0 ? 'text-success' : 'text-muted'}`}>{Number(m.balance) > 0 ? 'is owed ' : Number(m.balance) < 0 ? 'owes ' : 'settled '}{Number(m.balance) !== 0 && formatMoney(Math.abs(Number(m.balance)), cur)}</span></li>)}</ul></section>
        <section className="rounded-2xl border border-line bg-surface p-4"><h3 className="mb-2 font-semibold">Suggested settle-up</h3>{d.settleUp.length === 0 && <p className="text-sm text-muted">Everyone is settled.</p>}<ul className="space-y-2 text-sm">{d.settleUp.map((s, i) => <li key={i} className="flex flex-wrap items-center justify-between gap-2"><span>{s.from} pays {s.to} <strong className="num">{formatMoney(s.amount, cur)}</strong></span>
          <Button variant="secondary" onClick={() => { if (window.confirm(`Record that ${s.from} paid ${s.to} ${s.amount} ${cur}? (This records a settlement; it does not move money.)`)) run.mutate(() => api.post(`/households/${id}/settlements`, { fromUser: s.fromUser, toUser: s.toUser, amount: s.amount, note: null })); }}>Record as paid</Button></li>)}</ul><p className="mt-2 text-xs text-muted">You can only record settlements you're part of. FlowPilot tracks the balance; the payment itself happens outside, or via the simulator.</p></section></div>}

      {tab === 'budgets' && <div className="grid gap-4 lg:grid-cols-[1.3fr_1fr]"><ul className="space-y-3">{d.budgets.length === 0 && <li className="rounded-2xl border border-dashed border-line p-6 text-sm text-muted">No shared budgets.</li>}{d.budgets.map((b) => <li key={b.id} className="rounded-2xl border border-line bg-surface p-4 text-sm"><div className="mb-2 flex justify-between"><span className="font-medium">{b.name}{b.categoryName ? ` · ${b.categoryName}` : ''}</span>{d.owner && <Button variant="ghost" onClick={() => run.mutate(() => api.delete(`/households/${id}/budgets/${b.id}`))}>Archive</Button>}</div><ProgressBar percent={b.percentUsed} tone={b.percentUsed >= 100 ? 'danger' : b.percentUsed >= 80 ? 'warning' : 'success'} label={b.name} /><p className="num mt-1 text-xs text-muted">{formatMoney(b.spent, cur)} of {formatMoney(b.limit, cur)} this month</p></li>)}</ul>
        {d.owner ? <div className="space-y-3 rounded-2xl border border-line bg-surface p-4"><h3 className="font-semibold">New shared budget</h3><TextField label="Name" value={bf.name} onChange={(e) => setBf({ ...bf, name: e.target.value })} /><TextField label="Monthly limit" inputMode="decimal" value={bf.limit} onChange={(e) => setBf({ ...bf, limit: e.target.value })} /><SelectField label="Category" value={bf.categoryId} onChange={(e) => setBf({ ...bf, categoryId: e.target.value })}><option value="">All shared expenses</option>{cats.data?.filter((c) => c.kind === 'EXPENSE').map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}</SelectField>
          <Button className="w-full" disabled={!bf.name.trim() || !AMOUNT_RE.test(bf.limit)} onClick={() => run.mutate(() => api.post(`/households/${id}/budgets`, { name: bf.name, limitAmount: bf.limit, categoryId: bf.categoryId || null }).then(() => setBf({ name: '', limit: '', categoryId: '' })))}>Create</Button></div> : <p className="text-sm text-muted">Only the owner creates shared budgets.</p>}</div>}

      {tab === 'members' && <div className="grid gap-4 lg:grid-cols-2"><section className="rounded-2xl border border-line bg-surface p-4"><h3 className="mb-2 font-semibold">Members</h3><ul className="divide-y divide-line text-sm">{members.map((m) => <li key={m.userId} className="flex items-center justify-between py-2"><span>{m.name} <span className="text-xs text-muted">{m.role.toLowerCase()}</span></span>{m.role !== 'OWNER' && (d.owner || true) && <Button variant="ghost" onClick={() => { if (window.confirm(d.owner ? `Remove ${m.name}?` : 'Leave this household?')) run.mutate(() => api.delete(`/households/${id}/members/${m.userId}`)); }}>{d.owner ? 'Remove' : 'Leave'}</Button>}</li>)}</ul><p className="mt-2 text-xs text-muted">Members with an unsettled balance can't leave or be removed.</p></section>
        {d.owner && <section className="rounded-2xl border border-line bg-surface p-4"><h3 className="mb-2 font-semibold">Invite someone</h3><TextField label="Their payment handle" placeholder="alex-4f2k" value={handle} onChange={(e) => setHandle(e.target.value.toLowerCase())} /><Button className="mt-2" disabled={handle.length < 3} onClick={() => run.mutate(() => api.post(`/households/${id}/invitations`, { handle }).then(() => setHandle('')))}>Send invitation</Button>
          {d.pendingInvitations.length > 0 && <ul className="mt-3 space-y-1 text-xs text-muted">{d.pendingInvitations.map((i) => <li key={i.id} className="flex justify-between"><span>Pending: {i.invitee}</span><Button variant="ghost" onClick={() => run.mutate(() => api.delete(`/households/${id}/invitations/${i.id}`))}>Cancel</Button></li>)}</ul>}</section>}</div>}
    </div>
  );
}
