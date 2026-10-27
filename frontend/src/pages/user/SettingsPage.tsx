import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { useNavigate } from 'react-router-dom';
import { z } from 'zod';
import { useAuth } from '../../auth/AuthContext';
import { Alert } from '../../components/Alert';
import { AvatarUploader } from '../../components/AvatarUploader';
import { Button } from '../../components/Button';
import { SecurityPanel } from '../../components/SecurityPanel';
import { SelectField } from '../../components/SelectField';
import { TextField } from '../../components/TextField';
import { api, toApiError } from '../../lib/api';
import { downloadFile } from '../../lib/download';
import { AMOUNT_RE } from '../../lib/money';
import type { CategoryManageView, NotificationPrefs, ProfileView } from '../../lib/types';
import { useTheme } from '../../theme/ThemeProvider';

const TABS = ['Profile & preferences', 'Notifications', 'Categories', 'Privacy & data', 'Security & devices'] as const;
type Tab = (typeof TABS)[number];

export function SettingsPage() {
  const [tab, setTab] = useState<Tab>('Profile & preferences');
  return (
    <div className="space-y-6">
      <div><h1 className="text-2xl font-bold">Settings &amp; Profile</h1></div>
      <div role="tablist" aria-label="Settings sections" className="flex gap-1 overflow-x-auto rounded-xl border border-line bg-surface p-1">
        {TABS.map((t) => <button key={t} role="tab" id={`tab-${t}`} aria-selected={tab === t} aria-controls="panel" onClick={() => setTab(t)} className={`whitespace-nowrap rounded-lg px-3 py-1.5 text-sm font-medium ${tab === t ? 'bg-accent text-white' : 'text-muted hover:text-ink'}`}>{t}</button>)}
      </div>
      <div id="panel" role="tabpanel" aria-labelledby={`tab-${tab}`}>
        {tab === 'Profile & preferences' && <ProfileTab />}
        {tab === 'Notifications' && <NotificationsTab />}
        {tab === 'Categories' && <CategoriesTab />}
        {tab === 'Privacy & data' && <PrivacyTab />}
        {tab === 'Security & devices' && <SecurityPanel />}
      </div>
    </div>
  );
}

const profileSchema = z.object({
  fullName: z.string().trim().min(2, 'Enter your name.').max(120), preferredCurrency: z.string().trim().toUpperCase().regex(/^[A-Z]{3}$/, 'Use a 3-letter code.'),
  timezone: z.string().min(1), locale: z.string().regex(/^[a-z]{2}(-[A-Z]{2})?$/, 'Use a code like en-US.'),
  themePreference: z.enum(['light', 'dark', 'system']), numberFormat: z.enum(['auto', 'comma', 'dot', 'space']), dateFormat: z.enum(['auto', 'ymd', 'dmy', 'mdy']),
});
type ProfileForm = z.infer<typeof profileSchema>;

export function ProfileTab() {
  const qc = useQueryClient();
  const { setPreference } = useTheme();
  const [notice, setNotice] = useState<{ tone: 'success' | 'danger'; text: string } | null>(null);
  const q = useQuery({ queryKey: ['profile'], queryFn: async () => (await api.get<ProfileView>('/users/me/profile')).data });
  const zones = typeof Intl.supportedValuesOf === 'function' ? Intl.supportedValuesOf('timeZone') : ['UTC'];
  const form = useForm<ProfileForm>({ resolver: zodResolver(profileSchema), values: q.data ? { fullName: q.data.fullName, preferredCurrency: q.data.preferredCurrency, timezone: q.data.timezone, locale: q.data.locale, themePreference: q.data.themePreference, numberFormat: q.data.numberFormat as ProfileForm['numberFormat'], dateFormat: q.data.dateFormat as ProfileForm['dateFormat'] } : undefined });
  const save = useMutation({
    mutationFn: (v: ProfileForm) => api.put<ProfileView>('/users/me/profile', v),
    onSuccess: (r, v) => { setPreference(v.themePreference); qc.setQueryData(['profile'], r.data); qc.invalidateQueries({ queryKey: ['me'] }); qc.invalidateQueries({ queryKey: ['dashboard'] }); setNotice({ tone: 'success', text: 'Settings saved.' }); },
    onError: (e) => setNotice({ tone: 'danger', text: toApiError(e).message }),
  });
  if (q.isLoading) return <p role="status" className="text-sm text-muted">Loading…</p>;
  const e = form.formState.errors;
  return (
    <form noValidate onSubmit={form.handleSubmit((v) => save.mutate(v))} className="grid gap-4 rounded-2xl border border-line bg-surface p-5 md:grid-cols-2">
      {notice && <div className="md:col-span-2"><Alert tone={notice.tone}>{notice.text}</Alert></div>}
      <div className="md:col-span-2"><AvatarUploader url={q.data?.avatarUrl ?? null} name={q.data?.fullName ?? ''} /></div>
      <TextField label="Full name" error={e.fullName?.message} {...form.register('fullName')} />
      <TextField label="Email" value={q.data?.email ?? ''} readOnly hint="Email changes aren't supported yet." onChange={() => undefined} />
      <TextField label="Preferred currency" maxLength={3} error={e.preferredCurrency?.message} {...form.register('preferredCurrency')} />
      <SelectField label="Timezone" {...form.register('timezone')}>{zones.map((z) => <option key={z}>{z}</option>)}</SelectField>
      <TextField label="Locale" hint="e.g. en-US, en-GB, de-DE" error={e.locale?.message} {...form.register('locale')} />
      <SelectField label="Theme" {...form.register('themePreference')}><option value="system">Match my device</option><option value="dark">Dark</option><option value="light">Light</option></SelectField>
      <SelectField label="Number format" {...form.register('numberFormat')}><option value="auto">Automatic</option><option value="comma">1,234.56</option><option value="dot">1.234,56</option><option value="space">1 234,56</option></SelectField>
      <SelectField label="Date format" {...form.register('dateFormat')}><option value="auto">Automatic</option><option value="ymd">2026-10-31</option><option value="dmy">31/10/2026</option><option value="mdy">10/31/2026</option></SelectField>
      <div className="md:col-span-2"><Button type="submit" loading={save.isPending}>Save changes</Button></div>
    </form>
  );
}

export function NotificationsTab() {
  const qc = useQueryClient();
  const [notice, setNotice] = useState<string | null>(null);
  const q = useQuery({ queryKey: ['notif-prefs'], queryFn: async () => (await api.get<NotificationPrefs>('/notifications/preferences')).data });
  const [draft, setDraft] = useState<NotificationPrefs | null>(null);
  const p = draft ?? q.data;
  const save = useMutation({ mutationFn: (v: NotificationPrefs) => api.put<NotificationPrefs>('/notifications/preferences', v), onSuccess: (r) => { qc.setQueryData(['notif-prefs'], r.data); setDraft(null); setNotice('Preferences saved.'); }, onError: (e) => setNotice(toApiError(e).message) });
  if (!p) return <p role="status" className="text-sm text-muted">Loading…</p>;
  const rows: [keyof NotificationPrefs, string][] = [['bills', 'Upcoming bills & subscription renewals'], ['budgets', 'Budget thresholds'], ['lowBalance', 'Low balances'], ['payments', 'Payment requests & status changes'], ['automation', 'Automation runs'], ['support', 'Support replies']];
  const thresholdBad = p.lowBalanceThreshold !== null && p.lowBalanceThreshold !== '' && !AMOUNT_RE.test(p.lowBalanceThreshold);
  return (
    <section className="space-y-4 rounded-2xl border border-line bg-surface p-5">
      {notice && <Alert tone="info">{notice}</Alert>}
      <fieldset className="space-y-3"><legend className="mb-1 text-sm font-medium">Tell me about</legend>
        {rows.map(([k, label]) => <label key={k} className="flex items-center gap-2 text-sm"><input type="checkbox" className="h-4 w-4 accent-[rgb(var(--fp-accent))]" checked={Boolean(p[k])} onChange={(e) => setDraft({ ...p, [k]: e.target.checked })} />{label}</label>)}
        <p className="text-xs text-muted">Security alerts (like sign-ins from new browsers) are always on.</p></fieldset>
      <TextField label="Low-balance threshold (in your preferred currency)" inputMode="decimal" value={p.lowBalanceThreshold ?? ''} onChange={(e) => setDraft({ ...p, lowBalanceThreshold: e.target.value })} error={thresholdBad ? 'Enter a valid amount.' : undefined} hint="Leave empty to turn low-balance alerts off." />
      <Button loading={save.isPending} disabled={thresholdBad} onClick={() => save.mutate(p)}>Save preferences</Button>
    </section>
  );
}

function CategoriesTab() {
  const qc = useQueryClient();
  const [error, setError] = useState<string | null>(null);
  const [name, setName] = useState(''); const [kind, setKind] = useState<'EXPENSE' | 'INCOME'>('EXPENSE'); const [color, setColor] = useState('#8b5cf6');
  const q = useQuery({ queryKey: ['categories-manage'], queryFn: async () => (await api.get<CategoryManageView[]>('/categories/manage')).data });
  const refresh = () => { qc.invalidateQueries({ queryKey: ['categories-manage'] }); qc.invalidateQueries({ queryKey: ['categories'] }); };
  const fail = (e: unknown) => setError(toApiError(e).message);
  const create = useMutation({ mutationFn: () => api.post('/categories', { name, kind, color }), onSuccess: () => { setName(''); setError(null); refresh(); }, onError: fail });
  const archive = useMutation({ mutationFn: (a: { id: string; archived: boolean }) => api.post(`/categories/${a.id}/${a.archived ? 'restore' : 'archive'}`), onSuccess: () => { setError(null); refresh(); }, onError: fail });
  const move = useMutation({ mutationFn: (ids: string[]) => api.put('/categories/reorder', { ids }), onSuccess: refresh, onError: fail });
  const reorder = (c: CategoryManageView, dir: -1 | 1) => {
    const mine = (q.data ?? []).filter((x) => !x.builtIn && x.kind === c.kind);
    const i = mine.findIndex((x) => x.id === c.id), j = i + dir;
    if (j < 0 || j >= mine.length) return;
    const ids = mine.map((x) => x.id); [ids[i], ids[j]] = [ids[j], ids[i]]; move.mutate(ids);
  };
  return (
    <div className="grid gap-6 lg:grid-cols-[1.4fr_1fr]">
      <section className="rounded-2xl border border-line bg-surface p-5"><h2 className="mb-3 text-lg font-semibold">Categories</h2>{error && <Alert>{error}</Alert>}
        <ul className="divide-y divide-line">{q.data?.map((c) => (
          <li key={c.id} className={`flex items-center justify-between gap-3 py-2.5 text-sm ${c.archived ? 'opacity-60' : ''}`}>
            <span className="flex items-center gap-2"><span className="h-3 w-3 rounded-full" style={{ background: c.color ?? '#6b7280' }} aria-hidden="true" />{c.name}<span className="text-xs text-muted">{c.kind.toLowerCase()} · {c.usage} transactions{c.builtIn ? ' · built-in' : ''}{c.archived ? ' · archived' : ''}</span></span>
            {!c.builtIn && <span className="flex gap-1"><Button variant="ghost" aria-label={`Move ${c.name} up`} onClick={() => reorder(c, -1)}>↑</Button><Button variant="ghost" aria-label={`Move ${c.name} down`} onClick={() => reorder(c, 1)}>↓</Button>
              <Button variant="ghost" onClick={() => archive.mutate({ id: c.id, archived: c.archived })}>{c.archived ? 'Restore' : 'Archive'}</Button></span>}</li>))}</ul>
        <p className="mt-3 text-xs text-muted">Archiving hides a category from pickers; past transactions keep it.</p></section>
      <section className="rounded-2xl border border-line bg-surface p-5"><h2 className="mb-3 text-lg font-semibold">New category</h2>
        <div className="space-y-4"><TextField label="Name" value={name} onChange={(e) => setName(e.target.value)} /><div className="grid grid-cols-2 gap-3"><SelectField label="Kind" value={kind} onChange={(e) => setKind(e.target.value as typeof kind)}><option value="EXPENSE">Expense</option><option value="INCOME">Income</option></SelectField><TextField label="Colour" type="color" value={color} onChange={(e) => setColor(e.target.value)} /></div>
          <Button loading={create.isPending} disabled={!name.trim()} onClick={() => create.mutate()} className="w-full">Add category</Button></div></section>
    </div>
  );
}

function PrivacyTab() {
  const navigate = useNavigate();
  const { signOut } = useAuth();
  const [pw, setPw] = useState(''); const [confirm, setConfirm] = useState(''); const [delPw, setDelPw] = useState('');
  const [msg, setMsg] = useState<{ tone: 'success' | 'danger'; text: string } | null>(null);
  const [busy, setBusy] = useState<'export' | 'delete' | null>(null);
  const doExport = async () => {
    setBusy('export'); setMsg(null);
    try { await downloadFile(() => api.post<Blob>('/users/me/export', { password: pw }, { responseType: 'blob' }), 'flowpilot-data-export.json'); setPw(''); setMsg({ tone: 'success', text: 'Your data export was downloaded.' }); }
    catch (e) { setMsg({ tone: 'danger', text: toApiError(e).message === 'Something went wrong. Please try again.' ? 'Export failed. Check your password and try again.' : toApiError(e).message }); } finally { setBusy(null); }
  };
  const doDelete = async () => {
    setBusy('delete'); setMsg(null);
    try { await api.post('/users/me/delete', { password: delPw, confirmation: confirm }); await signOut(); navigate('/', { replace: true }); }
    catch (e) { setMsg({ tone: 'danger', text: toApiError(e).message }); } finally { setBusy(null); }
  };
  return (
    <div className="space-y-6">
      {msg && <Alert tone={msg.tone}>{msg.text}</Alert>}
      <section className="rounded-2xl border border-line bg-surface p-5"><h2 className="mb-1 text-lg font-semibold">Download your data</h2>
        <p className="mb-3 text-sm text-muted">A JSON file of your profile, accounts, transactions, ledger, budgets, bills, goals and device history. Password hashes and tokens are never included.</p>
        <div className="flex flex-wrap items-end gap-3"><TextField label="Confirm your password" type="password" autoComplete="current-password" value={pw} onChange={(e) => setPw(e.target.value)} className="min-w-64" /><Button loading={busy === 'export'} disabled={!pw} onClick={doExport}>Download my data</Button></div></section>
      <section className="rounded-2xl border border-line bg-surface p-5"><h2 className="mb-1 text-lg font-semibold">What we keep, and for how long</h2>
        <ul className="list-disc space-y-1 pl-5 text-sm text-muted"><li>Financial records are kept while your account is active.</li><li>Sign-in and device history is kept for 30 days.</li><li>After account deletion, your identity is removed and sessions are ended; the immutable ledger is retained in de-identified form for financial-integrity and legal obligations.</li><li>Exports you download are your responsibility to store securely.</li></ul></section>
      <section className="rounded-2xl border border-danger/40 bg-danger/5 p-5"><h2 className="mb-1 text-lg font-semibold text-danger">Delete account</h2>
        <p className="mb-3 text-sm text-muted">This signs you out everywhere and cannot be undone. Download your data first if you want a copy.</p>
        <div className="grid gap-3 sm:grid-cols-2"><TextField label="Password" type="password" autoComplete="current-password" value={delPw} onChange={(e) => setDelPw(e.target.value)} /><TextField label="Type DELETE to confirm" value={confirm} onChange={(e) => setConfirm(e.target.value)} /></div>
        <Button variant="danger" className="mt-3" loading={busy === 'delete'} disabled={confirm !== 'DELETE' || !delPw} onClick={doDelete}>Permanently delete my account</Button></section>
    </div>
  );
}
