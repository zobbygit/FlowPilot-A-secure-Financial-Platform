import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowRight } from 'lucide-react';
import { useState } from 'react';
import { Alert } from '../../components/Alert';
import { Button } from '../../components/Button';
import { SelectField } from '../../components/SelectField';
import { TextField } from '../../components/TextField';
import { api, toApiError } from '../../lib/api';
import { AMOUNT_RE } from '../../lib/money';
import type { AccountView, ActionDef, AutomationPreview, BillView, BillsOverview, BudgetView, CategoryView, GoalView, Outcome, RuleRequest, RuleView, RunView } from '../../lib/types';

const OUTCOME_STYLE: Record<string, string> = { PLANNED: 'text-success', SKIPPED_INSUFFICIENT: 'text-danger', SKIPPED_LIMIT: 'text-warning', SKIPPED_INVALID: 'text-danger', SKIPPED_DONE: 'text-muted' };
const OUTCOME_LABEL: Record<string, string> = { PLANNED: 'planned', SKIPPED_INSUFFICIENT: 'skipped — not enough unallocated money', SKIPPED_LIMIT: 'skipped — per-run limit', SKIPPED_INVALID: 'skipped', SKIPPED_DONE: 'already planned' };
const empty = (accountId: string): RuleRequest => ({ name: '', triggerType: 'INCOME_RECEIVED', schedule: null, scheduleDay: null, minTriggerAmount: null, triggerCategoryId: null, sourceAccountId: accountId, priority: 100, perRunLimit: null, actions: [] });

export function AutomationPage() {
  const qc = useQueryClient();
  const [editing, setEditing] = useState<(RuleRequest & { id?: string }) | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [preview, setPreview] = useState<{ rule: RuleView; data: AutomationPreview } | null>(null);
  const rules = useQuery({ queryKey: ['automation', 'rules'], queryFn: async () => (await api.get<RuleView[]>('/automation/rules')).data });
  const runs = useQuery({ queryKey: ['automation', 'runs'], queryFn: async () => (await api.get<RunView[]>('/automation/runs')).data });
  const accounts = useQuery({ queryKey: ['accounts'], queryFn: async () => (await api.get<AccountView[]>('/accounts')).data });
  const refresh = () => qc.invalidateQueries({ queryKey: ['automation'] });
  const fail = (e: unknown) => setError(toApiError(e).message);

  const save = useMutation({ mutationFn: (r: RuleRequest & { id?: string }) => r.id ? api.put(`/automation/rules/${r.id}`, r) : api.post('/automation/rules', r), onSuccess: () => { setEditing(null); setError(null); refresh(); }, onError: fail });
  const status = useMutation({ mutationFn: (a: { id: string; to: 'pause' | 'resume' | 'archive' }) => api.post(`/automation/rules/${a.id}/${a.to}`), onSuccess: refresh, onError: fail });
  const retry = useMutation({ mutationFn: (id: string) => api.post(`/automation/runs/${id}/retry`), onSuccess: () => { setError(null); refresh(); }, onError: fail });
  const runNow = useMutation({ mutationFn: (a: { id: string; income: string | null }) => api.post(`/automation/rules/${a.id}/run`, { incomeAmount: a.income }), onSuccess: () => { setError(null); refresh(); }, onError: fail });
  const doPreview = useMutation({ mutationFn: (a: { rule: RuleView; income: string | null }) => api.post<AutomationPreview>(`/automation/rules/${a.rule.id}/preview`, { incomeAmount: a.income }).then((r) => ({ rule: a.rule, data: r.data })), onSuccess: (r) => { setPreview(r); setError(null); }, onError: fail });

  if (editing) return <Builder initial={editing} accounts={accounts.data ?? []} busy={save.isPending} error={error} onCancel={() => { setEditing(null); setError(null); }} onSave={(r) => save.mutate(r)} />;
  const needsIncome = (r: RuleView) => r.actions.some((a) => a.amountType === 'PERCENT');
  const askIncome = (r: RuleView): string | null | undefined => { if (!needsIncome(r)) return null; const v = window.prompt('Example income amount to apply the percentages to:'); return v && AMOUNT_RE.test(v) ? v : undefined; };

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3"><div><h1 className="text-2xl font-bold">Money Automation</h1><p className="text-sm text-muted">Rules <strong>plan</strong> allocations toward goals, bills and budgets. They never move real money, never overspend, and always explain what they skipped.</p></div>
        <Button disabled={!accounts.data?.length} onClick={() => setEditing(empty(accounts.data![0].id))}>New rule</Button></div>
      {error && <Alert>{error}</Alert>}
      <div className="grid gap-6 xl:grid-cols-[1.2fr_1fr]">
        <section aria-label="Rules" className="space-y-3">
          {rules.data?.length === 0 && <p className="rounded-2xl border border-dashed border-line p-6 text-sm text-muted">No rules yet. Example: “When income arrives, plan 10% to my emergency fund.”</p>}
          {rules.data?.map((r) => (
            <article key={r.id} className={`rounded-2xl border border-line bg-surface p-4 ${r.status === 'PAUSED' ? 'opacity-70' : ''}`}>
              <div className="flex items-start justify-between gap-3"><div><h2 className="font-semibold">{r.name} <span className={`ml-1 rounded-full px-2 py-0.5 text-xs ${r.status === 'ACTIVE' ? 'bg-success/15 text-success' : 'bg-warning/15 text-warning'}`}>{r.status.toLowerCase()}</span></h2>
                <p className="mt-1 flex flex-wrap items-center gap-1 text-xs text-muted"><span className="rounded bg-surface-2 px-1.5 py-0.5">When: {r.triggerType === 'INCOME_RECEIVED' ? `income${r.minTriggerAmount ? ` ≥ ${r.minTriggerAmount}` : ''}` : r.triggerType === 'SCHEDULE' ? `${r.schedule?.toLowerCase()} on day ${r.scheduleDay}` : 'run manually'}</span><ArrowRight className="h-3 w-3" aria-hidden="true" /><span className="rounded bg-surface-2 px-1.5 py-0.5">Then: {r.actions.length} planned allocation{r.actions.length === 1 ? '' : 's'}{r.perRunLimit ? `, max ${r.perRunLimit}` : ''}</span>
                  <span>· priority {r.priority}</span>{r.lastRunAt && <span>· last run {new Date(r.lastRunAt).toLocaleDateString()} ({r.lastRunStatus?.toLowerCase()})</span>}</p></div>
                <div className="flex shrink-0 flex-wrap justify-end gap-1">
                  <Button variant="secondary" loading={doPreview.isPending} onClick={() => { const inc = askIncome(r); if (inc !== undefined) doPreview.mutate({ rule: r, income: inc }); }}>Preview</Button>
                  {r.status === 'ACTIVE' && <Button variant="ghost" onClick={() => { const inc = askIncome(r); if (inc !== undefined && window.confirm('Run this rule now? It will record planned allocations (no money moves).')) runNow.mutate({ id: r.id, income: inc }); }}>Run now</Button>}
                  <Button variant="ghost" onClick={() => setEditing({ ...r, minTriggerAmount: r.minTriggerAmount, perRunLimit: r.perRunLimit })}>Edit</Button>
                  <Button variant="ghost" onClick={() => status.mutate({ id: r.id, to: r.status === 'ACTIVE' ? 'pause' : 'resume' })}>{r.status === 'ACTIVE' ? 'Pause' : 'Resume'}</Button>
                  <Button variant="ghost" onClick={() => { if (window.confirm(`Archive “${r.name}”? History is kept.`)) status.mutate({ id: r.id, to: 'archive' }); }}>Archive</Button></div></div>
            </article>))}
          {preview && <section aria-live="polite" className="rounded-2xl border border-accent/50 bg-accent/5 p-4"><h2 className="font-semibold">Preview: {preview.rule.name}</h2><p className="text-xs text-muted">{preview.data.note} Unallocated in source account: {preview.data.available}.</p><OutcomeList outcomes={preview.data.outcomes} /><p className="mt-2 text-sm font-semibold">Would plan {preview.data.totalAllocated} in total.</p><Button variant="ghost" onClick={() => setPreview(null)}>Close preview</Button></section>}
        </section>
        <section aria-labelledby="hist" className="space-y-3"><h2 id="hist" className="text-lg font-semibold">Execution history</h2>
          {runs.data?.length === 0 && <p className="text-sm text-muted">No runs yet.</p>}
          {runs.data?.map((r) => (<article key={r.id} className="rounded-2xl border border-line bg-surface p-4 text-sm"><div className="flex items-start justify-between gap-2"><div><p className="font-medium">{r.ruleName} <span className={`rounded-full px-2 py-0.5 text-xs ${r.status === 'SUCCESS' ? 'bg-success/15 text-success' : r.status === 'PARTIAL' ? 'bg-warning/15 text-warning' : 'bg-danger/15 text-danger'}`}>{r.status.toLowerCase()}</span>{r.attempt > 1 && <span className="ml-1 text-xs text-muted">attempt {r.attempt}</span>}</p><p className="text-xs text-muted">{new Date(r.createdAt).toLocaleString()} · planned {r.totalAllocated}{r.triggerAmount ? ` of income ${r.triggerAmount}` : ''}</p></div>{r.retryable && <Button variant="secondary" loading={retry.isPending && retry.variables === r.id} onClick={() => retry.mutate(r.id)}>Retry</Button>}</div>
            {r.message && <p className="mt-1 text-xs text-danger">{r.message}</p>}<OutcomeList outcomes={r.outcomes} /></article>))}
        </section>
      </div>
    </div>
  );
}

function OutcomeList({ outcomes }: { outcomes: Outcome[] }) {
  return <ul className="mt-2 space-y-1 text-xs">{outcomes.map((o) => <li key={o.position} className="flex justify-between gap-2"><span>{o.position}. {o.targetType.toLowerCase()} “{o.targetName}”</span><span className={`num ${OUTCOME_STYLE[o.status] ?? ''}`}>{o.status === 'PLANNED' ? o.allocated : OUTCOME_LABEL[o.status] ?? o.status}{o.message && o.status !== 'PLANNED' ? ` — ${o.message}` : ''}</span></li>)}</ul>;
}

function Builder({ initial, accounts, onSave, onCancel, busy, error }: { initial: RuleRequest & { id?: string }; accounts: AccountView[]; onSave: (r: RuleRequest & { id?: string }) => void; onCancel: () => void; busy: boolean; error: string | null }) {
  const [r, setR] = useState(initial);
  const [problem, setProblem] = useState<string | null>(null);
  const goals = useQuery({ queryKey: ['goals'], queryFn: async () => (await api.get<GoalView[]>('/goals')).data });
  const bills = useQuery({ queryKey: ['bills'], queryFn: async () => (await api.get<BillsOverview>('/bills')).data.bills });
  const budgets = useQuery({ queryKey: ['budgets'], queryFn: async () => (await api.get<BudgetView[]>('/budgets')).data });
  const cats = useQuery({ queryKey: ['categories'], queryFn: async () => (await api.get<CategoryView[]>('/categories')).data });
  const targets = (type: ActionDef['targetType']): { id: string; name: string }[] => type === 'GOAL' ? (goals.data ?? []).map((g) => ({ id: g.id, name: g.name })) : type === 'BILL' ? (bills.data as BillView[] | undefined ?? []).map((b) => ({ id: b.id, name: b.name })) : (budgets.data ?? []).map((b) => ({ id: b.id, name: b.name }));
  const setAction = (i: number, p: Partial<ActionDef>) => setR({ ...r, actions: r.actions.map((a, j) => (j === i ? { ...a, ...p } : a)) });
  const move = (i: number, d: -1 | 1) => { const j = i + d; if (j < 0 || j >= r.actions.length) return; const a = [...r.actions]; [a[i], a[j]] = [a[j], a[i]]; setR({ ...r, actions: a }); };
  const check = () => {
    if (!r.name.trim()) return setProblem('Name this rule.');
    if (r.actions.length === 0) return setProblem('Add at least one action.');
    if (r.actions.some((a) => !a.targetId || !AMOUNT_RE.test(String(a.amountValue)) || Number(a.amountValue) <= 0)) return setProblem('Every action needs a target and a valid amount.');
    const pct = r.actions.filter((a) => a.amountType === 'PERCENT').reduce((s, a) => s + Number(a.amountValue), 0);
    if (pct > 100) return setProblem('Percentages can’t add up to more than 100.');
    if (r.triggerType === 'SCHEDULE' && (!r.schedule || !r.scheduleDay)) return setProblem('Choose a schedule and day.');
    setProblem(null); onSave(r);
  };
  const first = (t: ActionDef['targetType']) => targets(t)[0]?.id ?? '';
  return (
    <div className="space-y-6"><div className="flex items-center justify-between"><h1 className="text-2xl font-bold">{r.id ? 'Edit rule' : 'New rule'}</h1><Button variant="ghost" onClick={onCancel}>Cancel</Button></div>{(problem || error) && <Alert>{problem ?? error}</Alert>}
      <div className="grid gap-4 lg:grid-cols-3">
        <section className="rounded-2xl border border-line bg-surface p-4" aria-labelledby="when"><h2 id="when" className="mb-3 font-semibold">1 · When</h2><div className="space-y-3">
          <TextField label="Rule name" value={r.name} onChange={(e) => setR({ ...r, name: e.target.value })} />
          <SelectField label="Trigger" value={r.triggerType} onChange={(e) => setR({ ...r, triggerType: e.target.value as RuleRequest['triggerType'], schedule: null, scheduleDay: null, minTriggerAmount: null, triggerCategoryId: null, actions: e.target.value === 'SCHEDULE' ? r.actions.map((a) => ({ ...a, amountType: 'FIXED' })) : r.actions })}><option value="INCOME_RECEIVED">Income is received</option><option value="SCHEDULE">On a schedule</option><option value="MANUAL">I run it myself</option></SelectField>
          {r.triggerType === 'SCHEDULE' && <div className="grid grid-cols-2 gap-3"><SelectField label="Repeats" value={r.schedule ?? ''} onChange={(e) => setR({ ...r, schedule: (e.target.value || null) as RuleRequest['schedule'], scheduleDay: 1 })}><option value="">Choose…</option><option value="WEEKLY">Weekly</option><option value="MONTHLY">Monthly</option></SelectField><TextField label={r.schedule === 'WEEKLY' ? 'Weekday (1=Mon)' : 'Day of month'} inputMode="numeric" value={r.scheduleDay ?? ''} onChange={(e) => setR({ ...r, scheduleDay: e.target.value ? Number(e.target.value) : null })} /></div>}</div></section>
        <section className="rounded-2xl border border-line bg-surface p-4" aria-labelledby="if"><h2 id="if" className="mb-3 font-semibold">2 · If</h2><div className="space-y-3">
          <SelectField label="Plan from account" value={r.sourceAccountId} onChange={(e) => setR({ ...r, sourceAccountId: e.target.value })}>{accounts.map((a) => <option key={a.id} value={a.id}>{a.name} ({a.currency})</option>)}</SelectField>
          {r.triggerType === 'INCOME_RECEIVED' && <><TextField label="Only if income is at least (optional)" inputMode="decimal" value={r.minTriggerAmount ?? ''} onChange={(e) => setR({ ...r, minTriggerAmount: e.target.value || null })} />
            <SelectField label="Only for income category (optional)" value={r.triggerCategoryId ?? ''} onChange={(e) => setR({ ...r, triggerCategoryId: e.target.value || null })}><option value="">Any</option>{cats.data?.filter((c) => c.kind === 'INCOME').map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}</SelectField></>}
          <TextField label="Max planned per run (optional)" inputMode="decimal" value={r.perRunLimit ?? ''} onChange={(e) => setR({ ...r, perRunLimit: e.target.value || null })} /><TextField label="Priority (lower runs first)" inputMode="numeric" value={r.priority} onChange={(e) => setR({ ...r, priority: Number(e.target.value) || 100 })} /></div></section>
        <section className="rounded-2xl border border-line bg-surface p-4" aria-labelledby="then"><h2 id="then" className="mb-3 font-semibold">3 · Then plan</h2>
          <ol className="space-y-3">{r.actions.map((a, i) => (<li key={i} className="rounded-xl border border-line p-3"><div className="grid grid-cols-2 gap-2">
            <SelectField label="Toward" value={a.targetType} onChange={(e) => setAction(i, { targetType: e.target.value as ActionDef['targetType'], targetId: first(e.target.value as ActionDef['targetType']) })}><option value="GOAL">Savings goal</option><option value="BILL">Bill</option><option value="BUDGET">Budget</option></SelectField>
            <SelectField label="Which" value={a.targetId} onChange={(e) => setAction(i, { targetId: e.target.value })}><option value="">Choose…</option>{targets(a.targetType).map((t) => <option key={t.id} value={t.id}>{t.name}</option>)}</SelectField>
            <SelectField label="Amount type" value={a.amountType} onChange={(e) => setAction(i, { amountType: e.target.value as ActionDef['amountType'] })}><option value="FIXED">Fixed amount</option>{r.triggerType !== 'SCHEDULE' && <option value="PERCENT">% of income</option>}</SelectField>
            <TextField label={a.amountType === 'PERCENT' ? 'Percent' : 'Amount'} inputMode="decimal" value={a.amountValue} onChange={(e) => setAction(i, { amountValue: e.target.value })} /></div>
            <div className="mt-1 flex gap-1"><Button variant="ghost" aria-label={`Move action ${i + 1} up`} onClick={() => move(i, -1)}>↑</Button><Button variant="ghost" aria-label={`Move action ${i + 1} down`} onClick={() => move(i, 1)}>↓</Button><Button variant="ghost" onClick={() => setR({ ...r, actions: r.actions.filter((_, j) => j !== i) })}>Remove</Button></div></li>))}</ol>
          <Button className="mt-3" variant="secondary" disabled={r.actions.length >= 10} onClick={() => setR({ ...r, actions: [...r.actions, { targetType: 'GOAL', targetId: first('GOAL'), amountType: 'FIXED', amountValue: '' }] })}>Add action</Button>
          <p className="mt-3 text-xs text-muted">Actions run top to bottom. If money runs out, later actions are skipped and reported — nothing is overspent.</p></section>
      </div><Button loading={busy} onClick={check}>Save rule</Button><p className="text-xs text-muted">Tip: after saving, use Preview to see exactly what the rule would do before anything runs.</p>
    </div>
  );
}
