import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import { ME_KEY, useAuth } from '../../auth/AuthContext';
import { Alert } from '../../components/Alert';
import { AuthShell } from '../../components/AuthShell';
import { Button } from '../../components/Button';
import { SelectField } from '../../components/SelectField';
import { TextField } from '../../components/TextField';
import { api, toApiError } from '../../lib/api';
import { AMOUNT_RE } from '../../lib/money';
import type { AccountType, CategoryView } from '../../lib/types';

interface Draft { step: number; currency: string; timezone: string; goals: string[]; categoryIds: string[]; accounts: { name: string; type: AccountType; opening: string }[] }
const GOALS = ['Build an emergency fund', 'Track spending', 'Save for a purchase or trip', 'Pay down debt', 'Plan a budget', 'Understand subscriptions'];
const STEPS = ['Basics', 'Goals', 'Categories', 'Accounts', 'Review'];

export function OnboardingPage() {
  const { me } = useAuth();
  const qc = useQueryClient();
  const navigate = useNavigate();
  const zone = Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';
  const zones = typeof Intl.supportedValuesOf === 'function' ? Intl.supportedValuesOf('timeZone') : [zone];
  const [d, setD] = useState<Draft>({ step: 0, currency: 'USD', timezone: zone, goals: [], categoryIds: [], accounts: [{ name: 'Main account', type: 'BANK', opening: '' }] });
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [loaded, setLoaded] = useState(false);
  const state = useQuery({ queryKey: ['onboarding'], queryFn: async () => (await api.get<{ completed: boolean; draft: string | null }>('/onboarding')).data });
  const categories = useQuery({ queryKey: ['categories'], queryFn: async () => (await api.get<CategoryView[]>('/categories')).data });

  useEffect(() => {   // resume an unfinished wizard
    if (state.data && !loaded) {
      setLoaded(true);
      try { if (state.data.draft) setD((cur) => ({ ...cur, ...(JSON.parse(state.data!.draft!) as Partial<Draft>) })); } catch { /* ignore corrupt draft */ }
    }
  }, [state.data, loaded]);

  if (me?.onboardingCompleted || state.data?.completed) return <Navigate to="/app/dashboard" replace />;

  const go = (step: number) => { setD((x) => ({ ...x, step })); setError(null); void api.put('/onboarding/draft', { draft: JSON.stringify({ ...d, step }) }).catch(() => undefined); };
  const toggle = (list: string[], v: string) => list.includes(v) ? list.filter((x) => x !== v) : [...list, v];

  const validate = (): string | null => {
    if (d.step === 0 && !/^[A-Za-z]{3}$/.test(d.currency)) return 'Use a 3-letter currency code such as USD.';
    if (d.step === 3) {
      if (d.accounts.some((a) => !a.name.trim())) return 'Give every account a name.';
      if (new Set(d.accounts.map((a) => a.name.trim().toLowerCase())).size !== d.accounts.length) return 'Give each account a different name.';
      if (d.accounts.some((a) => a.opening !== '' && !AMOUNT_RE.test(a.opening))) return 'Opening balances must be valid amounts (up to 4 decimals).';
    }
    return null;
  };
  const next = () => { const e = validate(); if (e) return setError(e); go(d.step + 1); };

  const finish = async () => {
    setBusy(true); setError(null);
    try {
      await api.post('/onboarding/complete', {
        preferredCurrency: d.currency.toUpperCase(), timezone: d.timezone, goals: d.goals, preferredCategoryIds: d.categoryIds,
        accounts: d.accounts.map((a) => ({ name: a.name.trim(), type: a.type, openingBalance: a.opening === '' ? '0' : a.opening })),
      });
      await qc.invalidateQueries({ queryKey: ME_KEY });
      navigate('/app/dashboard', { replace: true });
    } catch (e) { setError(toApiError(e).message); } finally { setBusy(false); }
  };

  return (
    <AuthShell title="Let's set up FlowPilot" subtitle={`Step ${d.step + 1} of ${STEPS.length} — ${STEPS[d.step]}. You can leave and come back; your progress is saved.`}>
      <ol className="mb-6 flex gap-1" aria-label="Progress">{STEPS.map((s, i) => <li key={s} aria-current={i === d.step ? 'step' : undefined} className={`h-1.5 flex-1 rounded-full ${i <= d.step ? 'bg-accent' : 'bg-surface-2'}`}><span className="sr-only">{s}</span></li>)}</ol>
      {error && <div className="mb-4"><Alert>{error}</Alert></div>}

      {d.step === 0 && <div className="space-y-4"><TextField label="Preferred currency" maxLength={3} value={d.currency} onChange={(e) => setD({ ...d, currency: e.target.value.toUpperCase() })} hint="Used for your dashboard and new accounts." />
        <SelectField label="Timezone" value={d.timezone} onChange={(e) => setD({ ...d, timezone: e.target.value })}>{zones.map((z) => <option key={z}>{z}</option>)}</SelectField></div>}
      {d.step === 1 && <fieldset className="space-y-2"><legend className="mb-2 text-sm font-medium">What would you like FlowPilot to help with?</legend>{GOALS.map((g) => <label key={g} className="flex items-center gap-2 text-sm"><input type="checkbox" className="h-4 w-4 accent-[rgb(var(--fp-accent))]" checked={d.goals.includes(g)} onChange={() => setD({ ...d, goals: toggle(d.goals, g) })} />{g}</label>)}</fieldset>}
      {d.step === 2 && <fieldset className="space-y-2"><legend className="mb-2 text-sm font-medium">Categories you use most (optional)</legend>{categories.data?.map((c) => <label key={c.id} className="flex items-center gap-2 text-sm"><input type="checkbox" className="h-4 w-4 accent-[rgb(var(--fp-accent))]" checked={d.categoryIds.includes(c.id)} onChange={() => setD({ ...d, categoryIds: toggle(d.categoryIds, c.id) })} />{c.name} <span className="text-xs text-muted">({c.kind.toLowerCase()})</span></label>)}</fieldset>}
      {d.step === 3 && <div className="space-y-4"><p className="text-sm text-muted">Opening balances are recorded as real ledger entries, so your totals start correct.</p>
        {d.accounts.map((a, i) => (<div key={i} className="grid gap-3 rounded-xl border border-line p-3 sm:grid-cols-3">
          <TextField label="Name" value={a.name} onChange={(e) => setD({ ...d, accounts: d.accounts.map((x, j) => j === i ? { ...x, name: e.target.value } : x) })} />
          <SelectField label="Type" value={a.type} onChange={(e) => setD({ ...d, accounts: d.accounts.map((x, j) => j === i ? { ...x, type: e.target.value as AccountType } : x) })}><option value="BANK">Bank</option><option value="CASH">Cash</option><option value="SAVINGS">Savings</option><option value="WALLET">Virtual wallet</option></SelectField>
          <TextField label="Opening balance" inputMode="decimal" placeholder="0.00" value={a.opening} onChange={(e) => setD({ ...d, accounts: d.accounts.map((x, j) => j === i ? { ...x, opening: e.target.value } : x) })} />
          {d.accounts.length > 1 && <Button variant="ghost" onClick={() => setD({ ...d, accounts: d.accounts.filter((_, j) => j !== i) })}>Remove</Button>}</div>))}
        {d.accounts.length < 10 && <Button variant="secondary" onClick={() => setD({ ...d, accounts: [...d.accounts, { name: '', type: 'CASH', opening: '' }] })}>Add another account</Button>}</div>}
      {d.step === 4 && <dl className="space-y-2 text-sm"><div className="flex justify-between"><dt className="text-muted">Currency</dt><dd>{d.currency.toUpperCase()}</dd></div><div className="flex justify-between"><dt className="text-muted">Timezone</dt><dd>{d.timezone}</dd></div>
        <div className="flex justify-between"><dt className="text-muted">Goals</dt><dd>{d.goals.length || 'None'}</dd></div><div><dt className="text-muted">Accounts</dt><dd><ul className="mt-1 list-disc pl-5">{d.accounts.map((a, i) => <li key={i}>{a.name} ({a.type.toLowerCase()}) — opening {a.opening || '0'}</li>)}</ul></dd></div></dl>}

      <div className="mt-6 flex justify-between"><Button variant="ghost" disabled={d.step === 0} onClick={() => go(d.step - 1)}>Back</Button>{d.step < 4 ? <Button onClick={next}>Continue</Button> : <Button loading={busy} onClick={finish}>Finish setup</Button>}</div>
    </AuthShell>
  );
}
