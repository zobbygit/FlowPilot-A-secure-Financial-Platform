import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Alert } from '../../components/Alert';
import { Button } from '../../components/Button';
import { TextField } from '../../components/TextField';
import { api, toApiError } from '../../lib/api';
import type { Setting } from '../../lib/types';

const PROTECTED = ['Session lifetimes and idle timeout', 'Cookie security flags (Secure, SameSite)', 'Allowed web origins (CORS)', 'Database, email (Brevo) and storage (Cloudinary) credentials', 'Administrator bootstrap'];

export function AdminConfigPage() {
  const qc = useQueryClient();
  const [draft, setDraft] = useState<Record<string, string>>({}); const [msg, setMsg] = useState<{ tone: 'success' | 'danger'; text: string } | null>(null);
  const q = useQuery({ queryKey: ['admin-settings'], queryFn: async () => (await api.get<Setting[]>('/admin/settings')).data });
  const save = useMutation({ mutationFn: (a: { key: string; value: string }) => api.put(`/admin/settings/${a.key}`, { value: a.value }), onSuccess: (_r, a) => { setDraft(({ [a.key]: _x, ...rest }) => rest); setMsg({ tone: 'success', text: `Saved ${a.key}. Changes apply within about 30 seconds.` }); qc.invalidateQueries({ queryKey: ['admin-settings'] }); }, onError: (e) => setMsg({ tone: 'danger', text: toApiError(e).message }) });
  return (
    <div className="space-y-6"><div><h1 className="text-2xl font-bold">System configuration</h1><p className="text-sm text-muted">Only whitelisted, validated settings can be changed here. Each change records the old and new value in the audit log.</p></div>
      {msg && <Alert tone={msg.tone}>{msg.text}</Alert>}
      <section className="space-y-4 rounded-2xl border border-line bg-surface p-5">{q.data?.map((s) => { const v = draft[s.key] ?? s.value, dirty = v !== s.value; return (
        <div key={s.key} className="grid items-end gap-3 sm:grid-cols-[1fr_auto]"><TextField label={s.key} hint={`${s.description} Last changed ${new Date(s.updatedAt).toLocaleString()}.`} value={v} onChange={(e) => setDraft({ ...draft, [s.key]: e.target.value })} /><Button disabled={!dirty} loading={save.isPending && save.variables?.key === s.key} onClick={() => save.mutate({ key: s.key, value: v })}>Save</Button></div>); })}</section>
      <section className="rounded-2xl border border-line bg-surface-2/50 p-5"><h2 className="mb-2 font-semibold">Protected by design</h2><p className="mb-2 text-sm text-muted">These are deliberately not editable from the UI. Change them through the deployment environment so a compromised or mistaken session can't weaken security.</p><ul className="list-disc pl-5 text-sm text-muted">{PROTECTED.map((p) => <li key={p}>{p}</li>)}</ul></section></div>
  );
}
