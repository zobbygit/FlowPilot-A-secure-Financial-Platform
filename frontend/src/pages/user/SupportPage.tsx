import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Alert } from '../../components/Alert';
import { Button } from '../../components/Button';
import { ChatPanel } from '../../components/ChatPanel';
import { SelectField } from '../../components/SelectField';
import { TextField } from '../../components/TextField';
import { api, toApiError } from '../../lib/api';
import type { Paged, PaymentView, TicketDetail, TicketView } from '../../lib/types';

const STATUS: Record<string, string> = {
  OPEN: 'bg-accent/15 text-accent',
  IN_PROGRESS: 'bg-live/15 text-live',
  AWAITING_USER: 'bg-warning/15 text-warning',
  RESOLVED: 'bg-success/15 text-success',
  CLOSED: 'bg-surface-2 text-muted',
};

const label = (s: string) => s.toLowerCase().replace('_', ' ');

export function SupportPage() {
  const qc = useQueryClient();
  const [selected, setSelected] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);
  const [f, setF] = useState({ title: '', category: 'PAYMENT', priority: 'MEDIUM', description: '', paymentId: '' });
  const [error, setError] = useState<string | null>(null);

  const list = useQuery({
    queryKey: ['tickets'],
    queryFn: async () => (await api.get<Paged<TicketView>>('/support/tickets', { params: { size: 50 } })).data,
  });

  const pays = useQuery({
    queryKey: ['payments', 'list', 0, ''],
    enabled: creating && f.category === 'PAYMENT',
    queryFn: async () => (await api.get<Paged<PaymentView>>('/payments', { params: { size: 20 } })).data,
  });

  const detail = useQuery({
    queryKey: ['ticket', selected],
    enabled: !!selected,
    queryFn: async () => (await api.get<TicketDetail>(`/support/tickets/${selected}`)).data,
  });

  const refresh = () => {
    qc.invalidateQueries({ queryKey: ['tickets'] });
    qc.invalidateQueries({ queryKey: ['ticket'] });
    qc.invalidateQueries({ queryKey: ['unread'] });
  };

  const create = useMutation({
    mutationFn: () =>
      api.post<TicketView>('/support/tickets', {
        ...f,
        paymentId: f.category === 'PAYMENT' && f.paymentId ? f.paymentId : null,
      }),
    onSuccess: (r) => {
      setCreating(false);
      setF({ title: '', category: 'PAYMENT', priority: 'MEDIUM', description: '', paymentId: '' });
      setSelected(r.data.id);
      setError(null);
      refresh();
    },
    onError: (e) => setError(toApiError(e).message),
  });

  const act = useMutation({
    mutationFn: (a: { id: string; kind: 'reopen' | 'close' }) => api.post(`/support/tickets/${a.id}/${a.kind}`),
    onSuccess: () => {
      setError(null);
      refresh();
    },
    onError: (e) => setError(toApiError(e).message),
  });

  const t = detail.data?.ticket;

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold">Support</h1>
          <p className="text-sm text-muted">
            Report a payment issue, a wrong record, a bug or an account problem. Replies appear here and as notifications.
          </p>
        </div>
        <Button
          onClick={() => {
            setCreating(true);
            setSelected(null);
          }}
        >
          New ticket
        </Button>
      </div>

      {error && <Alert>{error}</Alert>}

      <div className="grid gap-6 lg:grid-cols-[22rem_1fr]">
        <section aria-label="Your tickets" className="space-y-2">
          {/* FIX: Added optional chaining to .items?.length */}
          {list.data?.items?.length === 0 && (
            <p className="rounded-2xl border border-dashed border-line p-6 text-sm text-muted">No tickets yet.</p>
          )}

          {/* FIX: Added optional chaining to .items?.map */}
          {list.data?.items?.map((x) => (
            <button
              key={x.id}
              type="button"
              onClick={() => {
                setSelected(x.id);
                setCreating(false);
              }}
              className={`w-full rounded-2xl border bg-surface p-3 text-left text-sm ${
                selected === x.id ? 'border-accent' : 'border-line'
              }`}
            >
              <span className="flex items-center justify-between gap-2">
                <span className="font-medium">{x.ticketNo}</span>
                <span className={`rounded-full px-2 py-0.5 text-xs ${STATUS[x.status] || 'bg-surface-2'}`}>
                  {label(x.status)}
                </span>
              </span>
              <span className="block truncate">{x.title}</span>
              <span className="text-xs text-muted">
                {x.category.toLowerCase()} · {x.priority.toLowerCase()} ·{' '}
                {new Date(x.updatedAt).toLocaleDateString()}
                {x.unread > 0 && <span className="ml-2 rounded-full bg-danger px-1.5 text-white">{x.unread} new</span>}
              </span>
            </button>
          ))}
        </section>

        <section>
          {creating && (
            <div className="space-y-4 rounded-2xl border border-line bg-surface p-5">
              <h2 className="text-lg font-semibold">New ticket</h2>
              <TextField label="Title" value={f.title} onChange={(e) => setF({ ...f, title: e.target.value })} />
              
              <div className="grid gap-3 sm:grid-cols-2">
                <SelectField label="Category" value={f.category} onChange={(e) => setF({ ...f, category: e.target.value })}>
                  <option value="PAYMENT">Payment issue</option>
                  <option value="RECORD">Incorrect record</option>
                  <option value="BUG">Bug</option>
                  <option value="ACCOUNT">Account problem</option>
                  <option value="OTHER">Other</option>
                </SelectField>
                <SelectField label="Priority" value={f.priority} onChange={(e) => setF({ ...f, priority: e.target.value })}>
                  <option value="LOW">Low</option>
                  <option value="MEDIUM">Medium</option>
                  <option value="HIGH">High</option>
                  <option value="URGENT">Urgent</option>
                </SelectField>
              </div>

              {f.category === 'PAYMENT' && (
                <SelectField
                  label="Related payment (optional)"
                  value={f.paymentId}
                  onChange={(e) => setF({ ...f, paymentId: e.target.value })}
                >
                  <option value="">None</option>
                  {/* FIX: Added optional chaining to .items?.map */}
                  {pays.data?.items?.map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.reference} · {p.amount} {p.currency} · {p.status.toLowerCase()}
                    </option>
                  ))}
                </SelectField>
              )}

              <div>
                <label htmlFor="desc" className="mb-1.5 block text-sm font-medium">
                  What happened?
                </label>
                <textarea
                  id="desc"
                  rows={5}
                  maxLength={4000}
                  value={f.description}
                  onChange={(e) => setF({ ...f, description: e.target.value })}
                  className="w-full rounded-xl border border-line bg-surface-2/60 px-3 py-2 text-sm"
                />
                <p className="mt-1 text-xs text-muted">
                  You can attach screenshots or documents in the conversation after creating the ticket.
                </p>
              </div>

              <div className="flex gap-2">
                <Button
                  loading={create.isPending}
                  disabled={!f.title.trim() || !f.description.trim()}
                  onClick={() => create.mutate()}
                >
                  Submit
                </Button>
                <Button variant="ghost" onClick={() => setCreating(false)}>
                  Cancel
                </Button>
              </div>
            </div>
          )}

          {!creating && !selected && (
            <p className="rounded-2xl border border-dashed border-line p-8 text-center text-sm text-muted">
              Select a ticket or create a new one.
            </p>
          )}

          {!creating && selected && t && (
            <div className="space-y-3">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div>
                  <h2 className="text-lg font-semibold">
                    {t.ticketNo} · {t.title}
                  </h2>
                  <p className="text-xs text-muted">
                    {label(t.status)} · {t.category.toLowerCase()} · {t.priority.toLowerCase()}
                    {t.paymentReference ? ` · payment ${t.paymentReference}` : ''}
                    {t.assignedTo ? ` · handled by ${t.assignedTo}` : ''}
                  </p>
                </div>

                <div className="flex gap-1">
                  {t.canReopen && (
                    <Button variant="secondary" onClick={() => act.mutate({ id: t.id, kind: 'reopen' })}>
                      Reopen
                    </Button>
                  )}
                  {t.canClose && (
                    <Button
                      variant="ghost"
                      onClick={() => {
                        if (window.confirm('Close this ticket?')) act.mutate({ id: t.id, kind: 'close' });
                      }}
                    >
                      Close ticket
                    </Button>
                  )}
                </div>
              </div>

              <ChatPanel key={t.id} ticketId={t.id} base="/support" />

              <details className="rounded-xl border border-line bg-surface p-3 text-xs text-muted">
                <summary className="cursor-pointer">Ticket history</summary>
                <ul className="mt-2 space-y-1">
                  {/* FIX: Added optional chaining to .events?.map */}
                  {detail.data?.events?.map((e, i) => (
                    <li key={i}>
                      {new Date(e.createdAt).toLocaleString()} — {e.actorName} ({e.actorRole.toLowerCase()}):{' '}
                      {e.type.toLowerCase()}
                      {e.from || e.to ? ` ${e.from ?? ''} → ${e.to ?? ''}` : ''}
                      {e.note ? ` (${e.note})` : ''}
                    </li>
                  ))}
                </ul>
              </details>
            </div>
          )}
        </section>
      </div>
    </div>
  );
}