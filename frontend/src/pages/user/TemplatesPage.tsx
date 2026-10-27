import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Alert } from '../../components/Alert';
import { Button } from '../../components/Button';
import { SelectField } from '../../components/SelectField';
import { TemplateForm } from '../../components/TemplateForm';
import { TextField } from '../../components/TextField';
import { api, toApiError } from '../../lib/api';
import { BINDINGS, REQUIRED } from '../../lib/templates';
import type { FieldDef, FieldType, TemplateRequest, TemplateView } from '../../lib/types';

const TYPES: FieldType[] = ['TEXT', 'AMOUNT', 'NUMBER', 'DATE', 'DROPDOWN', 'CHECKBOX', 'TAGS', 'FILE', 'CATEGORY', 'ACCOUNT', 'TRANSACTION'];
const TARGET_LABEL = { TRANSACTION: 'Transaction', MEMO: 'Memo', BILL: 'Recurring payment' } as const;

export function TemplatesPage() {
  const qc = useQueryClient();
  const [showArchived, setShowArchived] = useState(false);
  const [using, setUsing] = useState<TemplateView | null>(null);
  const [editing, setEditing] = useState<TemplateRequest & { id?: string } | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [created, setCreated] = useState<string | null>(null);
  const list = useQuery({ queryKey: ['templates', showArchived], queryFn: async () => (await api.get<TemplateView[]>('/templates', { params: { archived: showArchived } })).data });
  const refresh = () => qc.invalidateQueries({ queryKey: ['templates'] });
  const fail = (e: unknown) => setError(toApiError(e).message);

  const duplicate = useMutation({ mutationFn: (id: string) => api.post(`/templates/${id}/duplicate`), onSuccess: () => { setError(null); refresh(); }, onError: fail });
  const archive = useMutation({ mutationFn: (a: { id: string; archived: boolean }) => api.post(`/templates/${a.id}/${a.archived ? 'restore' : 'archive'}`), onSuccess: refresh, onError: fail });
  const save = useMutation({ mutationFn: (t: TemplateRequest & { id?: string }) => t.id ? api.put(`/templates/${t.id}`, t) : api.post('/templates', t), onSuccess: () => { setEditing(null); setError(null); refresh(); }, onError: fail });
  const run = useMutation({
    mutationFn: (a: { id: string; values: Record<string, string>; file: File | null }) => {
      const fd = new FormData(); Object.entries(a.values).forEach(([k, v]) => { if (v !== '') fd.append(k, v); }); if (a.file) fd.append('file', a.file);
      return api.post(`/templates/${a.id}/records`, fd);
    },
    onSuccess: (_r, a) => { setCreated(`Created from “${using?.name}”.`); setUsing(null); setError(null); ['transactions', 'dashboard', 'accounts', 'memos', 'bills', 'receipts'].forEach((k) => qc.invalidateQueries({ queryKey: [k] })); void a; },
    onError: fail,
  });

  if (editing) return <Builder initial={editing} busy={save.isPending} error={error} onCancel={() => { setEditing(null); setError(null); }} onSave={(t) => save.mutate(t)} />;

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3"><div><h1 className="text-2xl font-bold">My Finance Templates</h1><p className="text-sm text-muted">Reusable recording forms, private to you. Templates are not official tax invoices or certified receipts.</p></div>
        <div className="flex gap-2"><Button variant="secondary" aria-pressed={showArchived} onClick={() => setShowArchived((v) => !v)}>{showArchived ? 'Hide archived' : 'Show archived'}</Button>
          <Button onClick={() => setEditing({ name: '', description: '', targetType: 'TRANSACTION', fixedType: 'EXPENSE', fields: [{ key: 'amount', label: 'Amount', type: 'AMOUNT', binding: 'amount', required: true, options: null, defaultValue: null, min: 0.01, max: null }, { key: 'accountId', label: 'Account', type: 'ACCOUNT', binding: 'accountId', required: true, options: null, defaultValue: null, min: null, max: null }] })}>New template</Button></div></div>
      {error && !editing && <Alert>{error}</Alert>}{created && <Alert tone="success">{created}</Alert>}
      <div className="grid gap-6 lg:grid-cols-[1.3fr_1fr]">
        <section aria-label="Templates" className="grid gap-3 sm:grid-cols-2">
          {list.data?.map((t) => (
            <article key={t.id} className={`rounded-2xl border bg-surface p-4 ${using?.id === t.id ? 'border-accent shadow-glow' : 'border-line'} ${t.archived ? 'opacity-60' : ''}`}>
              <h2 className="font-semibold">{t.name}{t.builtIn && <span className="ml-2 rounded-full bg-accent/15 px-2 py-0.5 text-xs text-accent">built-in</span>}</h2>
              <p className="mt-1 text-xs text-muted">{TARGET_LABEL[t.targetType]} · {t.fields.length} fields</p>{t.description && <p className="mt-1 text-sm text-muted">{t.description}</p>}
              <div className="mt-3 flex flex-wrap gap-1">{!t.archived && <Button onClick={() => { setUsing(t); setCreated(null); setError(null); }}>Use</Button>}
                <Button variant="secondary" onClick={() => duplicate.mutate(t.id)}>Duplicate</Button>
                {!t.builtIn && <Button variant="ghost" onClick={() => setEditing({ ...t })}>Edit</Button>}{!t.builtIn && <Button variant="ghost" onClick={() => archive.mutate({ id: t.id, archived: t.archived })}>{t.archived ? 'Restore' : 'Archive'}</Button>}</div>
            </article>))}
        </section>
        <section aria-labelledby="use" className="rounded-2xl border border-line bg-surface p-5"><h2 id="use" className="mb-3 text-lg font-semibold">{using ? using.name : 'Create a record'}</h2>
          {using ? <TemplateForm key={using.id} fields={using.fields} submitLabel={`Create ${TARGET_LABEL[using.targetType].toLowerCase()}`} busy={run.isPending} onSubmit={(values, file) => run.mutate({ id: using.id, values, file })} /> : <p className="text-sm text-muted">Pick “Use” on a template to fill it in and create a real record in seconds.</p>}
          {using && error && <div className="mt-3"><Alert>{error}</Alert></div>}</section>
      </div>
    </div>
  );
}

function Builder({ initial, onSave, onCancel, busy, error }: { initial: TemplateRequest & { id?: string }; onSave: (t: TemplateRequest & { id?: string }) => void; onCancel: () => void; busy: boolean; error: string | null }) {
  const [t, setT] = useState(initial);
  const [problem, setProblem] = useState<string | null>(null);
  const bindings = BINDINGS[t.targetType];
  const setField = (i: number, patch: Partial<FieldDef>) => setT({ ...t, fields: t.fields.map((f, j) => (j === i ? { ...f, ...patch } : f)) });
  const move = (i: number, d: -1 | 1) => { const j = i + d; if (j < 0 || j >= t.fields.length) return; const fs = [...t.fields]; [fs[i], fs[j]] = [fs[j], fs[i]]; setT({ ...t, fields: fs }); };
  const unusedBinding = () => bindings.find((b) => !t.fields.some((f) => f.binding === b.value));
  const add = () => { const b = unusedBinding(); if (!b) return; const n = t.fields.length + 1; setT({ ...t, fields: [...t.fields, { key: `field${n}`, label: b.label, type: b.types[0], binding: b.value, required: REQUIRED[t.targetType].includes(b.value), options: null, defaultValue: null, min: null, max: null }] }); };
  const check = () => {
    if (!t.name.trim()) return setProblem('Name your template.');
    const missing = REQUIRED[t.targetType].find((r) => !t.fields.some((f) => f.binding === r));
    if (missing) return setProblem(`Add a field for “${missing}”.`);
    setProblem(null); onSave({ ...t, fixedType: t.targetType === 'TRANSACTION' ? t.fixedType : null });
  };
  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between"><h1 className="text-2xl font-bold">{t.id ? 'Edit template' : 'New template'}</h1><Button variant="ghost" onClick={onCancel}>Cancel</Button></div>
      {(problem || error) && <Alert>{problem ?? error}</Alert>}
      <div className="grid gap-6 lg:grid-cols-[1.4fr_1fr]">
        <section className="space-y-4 rounded-2xl border border-line bg-surface p-5" aria-label="Template builder">
          <div className="grid gap-3 sm:grid-cols-2"><TextField label="Name" value={t.name} onChange={(e) => setT({ ...t, name: e.target.value })} /><TextField label="Description" value={t.description ?? ''} onChange={(e) => setT({ ...t, description: e.target.value })} />
            <SelectField label="Creates a…" value={t.targetType} onChange={(e) => { const tt = e.target.value as TemplateView['targetType']; setT({ ...t, targetType: tt, fixedType: tt === 'TRANSACTION' ? 'EXPENSE' : null, fields: [] }); }}><option value="TRANSACTION">Transaction</option><option value="MEMO">Memo</option><option value="BILL">Recurring payment</option></SelectField>
            {t.targetType === 'TRANSACTION' && <SelectField label="Type" value={t.fixedType ?? ''} onChange={(e) => setT({ ...t, fixedType: (e.target.value || null) as TemplateRequest['fixedType'] })}><option value="EXPENSE">Always an expense</option><option value="INCOME">Always income</option><option value="">Let the user choose (needs a choice field)</option></SelectField>}</div>
          <ol className="space-y-3">{t.fields.map((f, i) => (
            <li key={i} className="rounded-xl border border-line p-3"><div className="grid gap-3 sm:grid-cols-4">
              <TextField label="Label" value={f.label} onChange={(e) => setField(i, { label: e.target.value })} />
              <SelectField label="Fills" value={f.binding} onChange={(e) => { const b = bindings.find((x) => x.value === e.target.value)!; setField(i, { binding: b.value, type: b.types.includes(f.type) ? f.type : b.types[0], required: REQUIRED[t.targetType].includes(b.value) || f.required }); }}>{bindings.map((b) => <option key={b.value} value={b.value}>{b.label}</option>)}</SelectField>
              <SelectField label="Input" value={f.type} onChange={(e) => setField(i, { type: e.target.value as FieldType })}>{TYPES.filter((x) => bindings.find((b) => b.value === f.binding)?.types.includes(x)).map((x) => <option key={x}>{x}</option>)}</SelectField>
              <label className="flex items-end gap-2 pb-2 text-sm"><input type="checkbox" className="h-4 w-4 accent-[rgb(var(--fp-accent))]" checked={f.required} disabled={REQUIRED[t.targetType].includes(f.binding)} onChange={(e) => setField(i, { required: e.target.checked })} />Required</label></div>
              {f.type === 'DROPDOWN' && <TextField className="mt-3" label="Options" hint="value|Label, value|Label" value={f.options ?? ''} onChange={(e) => setField(i, { options: e.target.value })} />}
              {['TEXT', 'AMOUNT', 'NUMBER'].includes(f.type) && <div className="mt-3 grid grid-cols-3 gap-3"><TextField label={f.type === 'TEXT' ? 'Min length' : 'Minimum'} inputMode="decimal" value={f.min ?? ''} onChange={(e) => setField(i, { min: e.target.value === '' ? null : e.target.value })} /><TextField label={f.type === 'TEXT' ? 'Max length' : 'Maximum'} inputMode="decimal" value={f.max ?? ''} onChange={(e) => setField(i, { max: e.target.value === '' ? null : e.target.value })} /><TextField label="Default" value={f.defaultValue ?? ''} onChange={(e) => setField(i, { defaultValue: e.target.value || null })} /></div>}
              <div className="mt-2 flex gap-1"><Button variant="ghost" aria-label={`Move ${f.label} up`} onClick={() => move(i, -1)}>↑</Button><Button variant="ghost" aria-label={`Move ${f.label} down`} onClick={() => move(i, 1)}>↓</Button><Button variant="ghost" onClick={() => setT({ ...t, fields: t.fields.filter((_, j) => j !== i) })}>Remove</Button></div></li>))}</ol>
          <div className="flex gap-2"><Button variant="secondary" disabled={!unusedBinding() || t.fields.length >= 20} onClick={add}>Add field</Button><Button loading={busy} onClick={check}>Save template</Button></div>
        </section>
        <section aria-labelledby="pv" className="rounded-2xl border border-line bg-surface p-5"><h2 id="pv" className="mb-3 text-lg font-semibold">Live preview</h2>
          {t.fields.length === 0 ? <p className="text-sm text-muted">Add a field to see the form.</p> : <TemplateForm key={JSON.stringify(t.fields)} fields={t.fields.map((f) => ({ ...f, key: f.key || 'x' }))} submitLabel="Create" preview />}</section>
      </div>
    </div>
  );
}
