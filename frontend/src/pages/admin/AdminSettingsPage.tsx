import { useState } from 'react';
import { SecurityPanel } from '../../components/SecurityPanel';
import { NotificationsTab, ProfileTab } from '../user/SettingsPage';

export function AdminSettingsPage() {
  const [tab, setTab] = useState<'profile' | 'notifications' | 'security'>('profile');
  return (
    <div className="space-y-6"><h1 className="text-2xl font-bold">Admin profile &amp; settings</h1>
      <div role="tablist" aria-label="Settings" className="flex w-fit gap-1 rounded-xl border border-line bg-surface p-1">{([['profile', 'Profile & theme'], ['notifications', 'Notifications'], ['security', 'Security & sessions']] as const).map(([k, l]) => <button key={k} role="tab" aria-selected={tab === k} onClick={() => setTab(k)} className={`rounded-lg px-4 py-1.5 text-sm font-medium ${tab === k ? 'bg-accent text-white' : 'text-muted'}`}>{l}</button>)}</div>
      {tab === 'profile' && <ProfileTab />}{tab === 'notifications' && <NotificationsTab />}{tab === 'security' && <SecurityPanel />}</div>
  );
}
