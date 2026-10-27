import { useQuery } from '@tanstack/react-query';
import { useRef, useState } from 'react';
import { api } from '../lib/api';
import { parseOptions, validateField, defaultFor } from '../lib/templates';
import type { AccountView, CategoryView, FieldDef, PageView, TransactionView } from '../lib/types';
import { Button } from './Button';
import { SelectField } from './SelectField';
import { TextField } from './TextField';

interface Props { fields: FieldDef[]; submitLabel: string; onSubmit?: (values: Record<string, string>, file: File | null) => void; busy?: boolean; preview?: boolean }

/** Renders any template definition. Used by "use template" and by the builder's live preview (preview = no real data, no submit). */
export function TemplateForm({ fields, submitLabel, onSubmit, busy, preview = false }: Props) {
  const [values, setValues] = useState<Record<string, string>>(() => Object.fromEntries(fields.map((f) => [f.key, defaultFor(f)])));
  const [errors, setErrors] = useState<Record<string, string>>({});
  const fileRef = useRef<HTMLInputElement>(null);
  const need = (t: string) => fields.some((f) => f.type === t) && !preview;
  const accounts = useQuery({ queryKey: ['accounts'], queryFn: async () => (await api.get<AccountView[]>('/accounts')).data, enabled: need('ACCOUNT') });
  const categories = useQuery({ queryKey: ['categories'], queryFn: async () => (await api.get<CategoryView[]>('/categories')).data, enabled: need('CATEGORY') });
  const txs = useQuery({ queryKey: ['transactions', 'for-template'], queryFn: async () => (await api.get<PageView<TransactionView>>('/transactions', { params: { size: 50 } })).data.items, enabled: need('TRANSACTION') });
  const set = (k: string, v: string) => setValues((x) => ({ ...x, [k]: v }));

  const submit = () => {
    const errs: Record<string, string> = {};
    fields.forEach((f) => { const e = validateField(f, values[f.key]); if (e) errs[f.key] = e; });
    setErrors(errs);
    if (Object.keys(errs).length === 0 && onSubmit) onSubmit(values, fileRef.current?.files?.[0] ?? null);
  };

  return (
    <form noValidate onSubmit={(e) => { e.preventDefault(); submit(); }} className="space-y-4">
      {fields.map((f) => {
        const label = f.required ? `${f.label} *` : f.label, err = errors[f.key];
        switch (f.type) {
          case 'DROPDOWN': return <SelectField key={f.key} label={label} value={values[f.key]} error={err} onChange={(e) => set(f.key, e.target.value)}><option value="">Choose…</option>{parseOptions(f.options).map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}</SelectField>;
          case 'CATEGORY': return <SelectField key={f.key} label={label} value={values[f.key]} error={err} onChange={(e) => set(f.key, e.target.value)}><option value="">{preview ? 'Your categories appear here' : 'Uncategorised'}</option>{categories.data?.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}</SelectField>;
          case 'ACCOUNT': return <SelectField key={f.key} label={label} value={values[f.key]} error={err} onChange={(e) => set(f.key, e.target.value)}><option value="">{preview ? 'Your accounts appear here' : 'Choose…'}</option>{accounts.data?.map((a) => <option key={a.id} value={a.id}>{a.name} ({a.currency})</option>)}</SelectField>;
          case 'TRANSACTION': return <SelectField key={f.key} label={label} value={values[f.key]} error={err} onChange={(e) => set(f.key, e.target.value)}><option value="">{preview ? 'Your transactions appear here' : 'None'}</option>{txs.data?.map((t) => <option key={t.id} value={t.id}>{t.occurredOn} · {t.merchant ?? t.description ?? t.type.toLowerCase()} · {t.amount}</option>)}</SelectField>;
          case 'CHECKBOX': return <label key={f.key} className="flex items-center gap-2 text-sm"><input type="checkbox" className="h-4 w-4 accent-[rgb(var(--fp-accent))]" checked={values[f.key] === 'true'} onChange={(e) => set(f.key, String(e.target.checked))} />{f.label}</label>;
          case 'FILE': return <div key={f.key}><label htmlFor={`f-${f.key}`} className="mb-1.5 block text-sm font-medium">{label}</label><input id={`f-${f.key}`} ref={fileRef} type="file" accept="image/jpeg,image/png,image/webp,application/pdf" className="w-full text-sm" disabled={preview} /></div>;
          case 'DATE': return <TextField key={f.key} label={label} type="date" value={values[f.key]} error={err} onChange={(e) => set(f.key, e.target.value)} />;
          case 'TAGS': return <TextField key={f.key} label={label} hint="Comma-separated, up to 8" value={values[f.key]} error={err} onChange={(e) => set(f.key, e.target.value)} />;
          case 'AMOUNT': case 'NUMBER': return <TextField key={f.key} label={label} inputMode={f.type === 'AMOUNT' ? 'decimal' : 'numeric'} value={values[f.key]} error={err} onChange={(e) => set(f.key, e.target.value)} />;
          default: return <TextField key={f.key} label={label} value={values[f.key]} error={err} onChange={(e) => set(f.key, e.target.value)} />;
        }
      })}
      <Button type="submit" loading={busy} className="w-full">{preview ? `${submitLabel} (preview — validates only)` : submitLabel}</Button>
    </form>
  );
}
