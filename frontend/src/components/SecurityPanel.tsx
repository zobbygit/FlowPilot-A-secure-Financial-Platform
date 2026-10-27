import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { api, toApiError } from '../lib/api';
import type { SessionView } from '../lib/types';
import { changePasswordSchema, type ChangePasswordForm } from '../lib/validation';
import { Alert } from './Alert';
import { Button } from './Button';
import { TextField } from './TextField';

const fmt = (iso: string) => new Date(iso).toLocaleString();

/** Sessions, device history and password change. Shared by the User and Admin shells. */
export function SecurityPanel() {
  const qc = useQueryClient();
  const sessions = useQuery({ queryKey: ['sessions'], queryFn: async () => (await api.get<SessionView[]>('/users/me/sessions')).data });
  const revoke = useMutation({ mutationFn: (id: string) => api.delete(`/users/me/sessions/${id}`), onSuccess: () => qc.invalidateQueries({ queryKey: ['sessions'] }) });
  const revokeOthers = useMutation({ mutationFn: () => api.post('/users/me/sessions/revoke-others'), onSuccess: () => qc.invalidateQueries({ queryKey: ['sessions'] }) });

  const [notice, setNotice] = useState<{ tone: 'success' | 'danger'; text: string } | null>(null);
  const form = useForm<ChangePasswordForm>({ resolver: zodResolver(changePasswordSchema) });
  const change = useMutation({
    mutationFn: (v: ChangePasswordForm) => api.post('/users/me/password', v),
    onSuccess: () => { form.reset(); setNotice({ tone: 'success', text: 'Password changed. Other devices were signed out.' }); qc.invalidateQueries({ queryKey: ['sessions'] }); },
    onError: (e) => setNotice({ tone: 'danger', text: toApiError(e).message }),
  });

  return (
    <div className="grid gap-8 lg:grid-cols-2">
      <section aria-labelledby="sessions-h" className="rounded-2xl border border-line bg-surface p-5">
        <div className="mb-4 flex items-center justify-between gap-3">
          <h2 id="sessions-h" className="text-lg font-semibold">Devices &amp; sessions</h2>
          <Button variant="secondary" loading={revokeOthers.isPending} onClick={() => revokeOthers.mutate()}>Sign out other devices</Button>
        </div>
        <p className="mb-3 text-xs text-muted">Browser details are reported by your browser and are not proof of a specific physical device. History is kept for 30 days.</p>
        {sessions.isLoading && <p role="status" className="text-sm text-muted">Loading sessions…</p>}
        {sessions.isError && <Alert>{toApiError(sessions.error).message}</Alert>}
        <ul className="space-y-3">
          {sessions.data?.map((s) => (
            <li key={s.id} className="rounded-xl border border-line bg-surface-2/50 p-3 text-sm">
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <p className="truncate font-medium">{s.userAgent ?? 'Unknown browser'}{s.current && <span className="ml-2 rounded-full bg-live/15 px-2 py-0.5 text-xs text-live">This device</span>}</p>
                  <p className="text-xs text-muted">Signed in {fmt(s.createdAt)} · last active {fmt(s.lastActiveAt)} · {s.status.toLowerCase()}</p>
                </div>
                {s.status === 'ACTIVE' && !s.current && (
                  <Button variant="ghost" aria-label={`Revoke session from ${s.userAgent ?? 'unknown browser'}`} loading={revoke.isPending && revoke.variables === s.id} onClick={() => revoke.mutate(s.id)}>Revoke</Button>
                )}
              </div>
            </li>
          ))}
        </ul>
      </section>

      <section aria-labelledby="pw-h" className="rounded-2xl border border-line bg-surface p-5">
        <h2 id="pw-h" className="mb-4 text-lg font-semibold">Change password</h2>
        {notice && <div className="mb-4"><Alert tone={notice.tone}>{notice.text}</Alert></div>}
        <form noValidate onSubmit={form.handleSubmit((v) => change.mutate(v))} className="space-y-4">
          <TextField label="Current password" type="password" autoComplete="current-password" error={form.formState.errors.currentPassword?.message} {...form.register('currentPassword')} />
          <TextField label="New password" type="password" autoComplete="new-password" hint="At least 12 characters, mixing three character types." error={form.formState.errors.newPassword?.message} {...form.register('newPassword')} />
          <Button type="submit" loading={change.isPending}>Update password</Button>
        </form>
      </section>
    </div>
  );
}
