import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { Alert } from '../../components/Alert';
import { MiniBars } from '../../components/MiniBars';
import { api, toApiError } from '../../lib/api';
import type { AdminDashboard } from '../../lib/types';

const Stat = ({ label, value, hint, tone }: { label: string; value: string | number; hint?: string; tone?: 'warn' | 'bad' }) => (
  <div className={`rounded-2xl border p-4 ${tone === 'bad' ? 'border-danger/50 bg-danger/5' : tone === 'warn' ? 'border-warning/50 bg-warning/5' : 'border-line bg-surface'}`}><p className="text-sm text-muted">{label}</p><p className="num text-2xl font-bold">{value}</p>{hint && <p className="text-xs text-muted">{hint}</p>}</div>
);

export function AdminHome() {
  const q = useQuery({ queryKey: ['admin-dashboard'], queryFn: async () => (await api.get<AdminDashboard>('/admin/dashboard')).data, refetchInterval: 60_000 });
  if (q.isLoading) return <p role="status" className="text-sm text-muted">Loading dashboard…</p>;
  if (q.isError || !q.data) return <Alert>{toApiError(q.error).message}</Alert>;
  const d = q.data, days = d.activity.map((a) => a.day);
  const users = Object.values(d.usersByStatus).reduce((a, b) => a + b, 0);
  return (
    <div className="space-y-6">
      <div><h1 className="text-2xl font-bold">Admin dashboard</h1><p className="text-sm text-muted">Platform-wide operational view. Aggregates only; private financial detail needs an audited investigation.</p></div>
      <section aria-label="Key figures" className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <Stat label="Users" value={users} hint={`${d.usersByStatus.ACTIVE ?? 0} active · ${d.usersByStatus.SUSPENDED ?? 0} suspended · +${d.newUsers7d} this week`} />
        <Stat label="Transactions (24h / 7d)" value={`${d.transactions24h} / ${d.transactions7d}`} />
        <Stat label="Simulated payments" value={Object.values(d.paymentsByStatus).reduce((a, b) => a + b, 0)} hint={`${d.failedPayments24h} failed in 24h · ${d.openDisputes} open disputes · ${d.refunds7d} refunds this week`} tone={d.failedPayments24h > 10 || d.openDisputes > 0 ? 'warn' : undefined} />
        <Stat label="Open support tickets" value={d.openTickets} hint={`${d.urgentOpenTickets} urgent · ${d.unassignedTickets} unassigned${d.avgFirstResponseHours !== null ? ` · avg first reply ${d.avgFirstResponseHours}h` : ''}`} tone={d.urgentOpenTickets > 0 ? 'bad' : d.unassignedTickets > 0 ? 'warn' : undefined} />
      </section>
      <div className="grid gap-6 xl:grid-cols-[1.4fr_1fr]">
        <section aria-labelledby="act" className="rounded-2xl border border-line bg-surface p-5"><h2 id="act" className="mb-2 text-lg font-semibold">Activity, last 14 days</h2>
          <MiniBars labels={days} series={[{ name: 'Transactions', values: d.activity.map((a) => a.transactions), className: 'fill-accent' }, { name: 'Payments', values: d.activity.map((a) => a.payments), className: 'fill-live' }, { name: 'New users', values: d.activity.map((a) => a.newUsers), className: 'fill-success' }]} />
          {d.paymentVolume7d.length > 0 && <p className="mt-3 text-xs text-muted">Successful simulated volume, 7 days: {d.paymentVolume7d.map((v) => `${v.amount} ${v.currency} (${v.count})`).join(' · ')}</p>}</section>
        <section aria-labelledby="hl" className="rounded-2xl border border-line bg-surface p-5"><h2 id="hl" className="mb-2 text-lg font-semibold">System health</h2>
          <ul className="space-y-1 text-sm">{d.health.checks.map((c) => <li key={c} className={c.startsWith('Database slow') || c.includes('not yet swept') ? 'text-warning' : 'text-success'}>● {c}</li>)}</ul>
          <dl className="mt-3 grid grid-cols-2 gap-2 text-xs"><div><dt className="text-muted">DB latency</dt><dd className="num">{d.health.dbLatencyMs} ms</dd></div><div><dt className="text-muted">Live connections</dt><dd className="num">{d.health.liveConnections}</dd></div>
            <div><dt className="text-muted">Heap</dt><dd className="num">{d.health.heapUsedMb} / {d.health.heapMaxMb} MB</dd></div><div><dt className="text-muted">Uptime</dt><dd className="num">{Math.floor(d.health.uptimeMinutes / 60)}h {d.health.uptimeMinutes % 60}m</dd></div></dl></section>
      </div>
      <section aria-labelledby="rec" className="rounded-2xl border border-line bg-surface p-5"><div className="mb-2 flex justify-between"><h2 id="rec" className="text-lg font-semibold">Recent audit activity</h2><Link to="/admin/audit" className="text-sm font-medium text-accent underline">Full audit log</Link></div>
        <ul className="divide-y divide-line text-sm">{d.recentAudit.map((a) => <li key={a.id} className="flex flex-wrap justify-between gap-2 py-2"><span><span className={`mr-2 rounded px-1.5 py-0.5 text-xs ${a.outcome === 'FAILURE' ? 'bg-danger/15 text-danger' : 'bg-surface-2 text-muted'}`}>{a.scope.toLowerCase()}</span>{a.type.toLowerCase().replaceAll('_', ' ')}{a.actorEmail && <span className="text-muted"> · {a.actorEmail}</span>}</span><span className="text-xs text-muted">{new Date(a.at).toLocaleString()}</span></li>)}</ul></section>
    </div>
  );
}
