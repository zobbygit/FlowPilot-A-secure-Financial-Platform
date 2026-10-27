import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Alert } from '../../components/Alert';
import { Button } from '../../components/Button';
import { SelectField } from '../../components/SelectField';
import { TextField } from '../../components/TextField';
import { api, toApiError } from '../../lib/api';
import type { AnnouncementView } from '../../lib/types';

export function AdminAnnouncementsPage() {
  const qc = useQueryClient();
  const [f, setF] = useState({ title: '', body: '', audience: 'ALL', targetEmails: '' }); const [editing, setEditing] = useState<string | null>(null); const [error, setError] = useState<string | null>(null);
  const list = useQuery({ queryKey: ['admin-announcements'], queryFn: async () => (await api.get<AnnouncementView[]>('/admin/announcements')).data });
  const refresh = () => qc.invalidateQueries({ queryKey: ['admin-announcements'] }); const fail = (e: unknown) => setError(toApiError(e).message);
  const save = useMutation({ mutationFn: () => editing ? api.put(`/admin/announcements/${editing}`, f) : api.post('/admin/announcements', f), onSuccess: () => { setF({ title: '', body: '', audience: 'ALL', targetEmails: '' }); setEditing(null); setError(null); refresh(); }, onError: fail });
  const publish = useMutation({ mutationFn: (id: string) => api.post(`/admin/announcements/${id}/publish`), onSuccess: () => { setError(null); refresh(); }, onError: fail });
  const del = useMutation({ mutationFn: (id: string) => api.delete(`/admin/announcements/${id}`), onSuccess: refresh, onError: fail });
  return (
    <div className="space-y-6"><div><h1 className="text-2xl font-bold">Announcements</h1><p className="text-sm text-muted">Delivered as in-app notifications. Publishing is final and can't be sent twice; drafts can be edited or deleted.</p></div>
      {error && <Alert>{error}</Alert>}
      <div className="grid gap-6 lg:grid-cols-[1.3fr_1fr]">
        <section aria-label="History" className="space-y-3">{list.data?.length === 0 && <p className="rounded-2xl border border-dashed border-line p-6 text-sm text-muted">No announcements yet.</p>}
          {list.data?.map((a) => <article key={a.id} className="rounded-2xl border border-line bg-surface p-4 text-sm"><div className="flex items-start justify-between gap-3"><div><h2 className="font-semibold">{a.title} <span className={`ml-1 rounded-full px-2 py-0.5 text-xs ${a.status === 'PUBLISHED' ? 'bg-success/15 text-success' : 'bg-warning/15 text-warning'}`}>{a.status.toLowerCase()}</span></h2><p className="mt-1 whitespace-pre-wrap text-muted">{a.body}</p>
            <p className="mt-1 text-xs text-muted">Audience: {a.audience.toLowerCase().replaceAll('_', ' ')}{a.audience === 'SPECIFIC' ? ` (${a.targetCount} addresses)` : ''}{a.publishedAt ? ` · published ${new Date(a.publishedAt).toLocaleString()} · delivered to ${a.delivered} · read by ${a.read}` : ''}</p></div>
            {a.status === 'DRAFT' && <div className="flex shrink-0 flex-col gap-1"><Button onClick={() => { if (window.confirm('Publish now? This cannot be undone.')) publish.mutate(a.id); }}>Publish</Button><Button variant="ghost" onClick={() => { setEditing(a.id); setF({ title: a.title, body: a.body, audience: a.audience, targetEmails: '' }); }}>Edit</Button><Button variant="ghost" onClick={() => { if (window.confirm('Delete this draft?')) del.mutate(a.id); }}>Delete</Button></div>}</div></article>)}</section>
        <section className="rounded-2xl border border-line bg-surface p-5"><h2 className="mb-3 text-lg font-semibold">{editing ? 'Edit draft' : 'New announcement'}</h2><div className="space-y-4">
          <TextField label="Title" value={f.title} onChange={(e) => setF({ ...f, title: e.target.value })} /><div><label htmlFor="ab" className="mb-1.5 block text-sm font-medium">Message</label><textarea id="ab" rows={5} maxLength={1000} value={f.body} onChange={(e) => setF({ ...f, body: e.target.value })} className="w-full rounded-xl border border-line bg-surface-2/60 px-3 py-2 text-sm" /></div>
          <SelectField label="Audience" value={f.audience} onChange={(e) => setF({ ...f, audience: e.target.value })}><option value="ALL">All active users</option><option value="NEW_USERS_30D">Joined in the last 30 days</option><option value="SPECIFIC">Specific users (by email)</option></SelectField>
          {f.audience === 'SPECIFIC' && <div><label htmlFor="ae" className="mb-1.5 block text-sm font-medium">Emails (comma, space or newline separated)</label><textarea id="ae" rows={3} value={f.targetEmails} onChange={(e) => setF({ ...f, targetEmails: e.target.value })} className="w-full rounded-xl border border-line bg-surface-2/60 px-3 py-2 text-sm" /></div>}
          <div className="flex gap-2"><Button loading={save.isPending} disabled={!f.title.trim() || !f.body.trim()} onClick={() => save.mutate()}>{editing ? 'Save draft' : 'Save as draft'}</Button>{editing && <Button variant="ghost" onClick={() => { setEditing(null); setF({ title: '', body: '', audience: 'ALL', targetEmails: '' }); }}>Cancel</Button>}</div></div></section></div></div>
  );
}
