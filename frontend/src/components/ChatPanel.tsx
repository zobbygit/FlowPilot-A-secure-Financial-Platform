import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef, useState } from 'react';
import { api, toApiError } from '../lib/api';
import { downloadFile, getBlob } from '../lib/download';
import { checkFile, RECEIPT_MAX, RECEIPT_TYPES } from '../lib/files';
import type { TicketDetail } from '../lib/types';
import { Alert } from './Alert';
import { Button } from './Button';

/** Persistent two-way support chat. The server is the source of truth; WebSocket hints just trigger a re-read. */
export function ChatPanel({ ticketId, base }: { ticketId: string; base: '/support' | '/admin/support' }) {
  const qc = useQueryClient();
  const bottom = useRef<HTMLDivElement>(null);
  const fileRef = useRef<HTMLInputElement>(null);
  const [text, setText] = useState(''); const [error, setError] = useState<string | null>(null);
  const key = base === '/support' ? ['ticket', ticketId] : ['admin-ticket', ticketId];
  const q = useQuery({ queryKey: key, queryFn: async () => (await api.get<TicketDetail>(`${base}/tickets/${ticketId}`)).data, refetchInterval: 60_000 });
  const send = useMutation({
    mutationFn: () => { const fd = new FormData(); if (text.trim()) fd.append('body', text.trim()); const f = fileRef.current?.files?.[0]; if (f) fd.append('file', f); return api.post<TicketDetail>(`${base}/tickets/${ticketId}/messages`, fd); },
    onSuccess: (r) => { qc.setQueryData(key, r.data); setText(''); if (fileRef.current) fileRef.current.value = ''; setError(null); qc.invalidateQueries({ queryKey: [base === '/support' ? 'tickets' : 'admin-tickets'] }); },
    onError: (e) => setError(toApiError(e).message),
  });
  useEffect(() => { bottom.current?.scrollIntoView({ block: 'end' }); }, [q.data?.messages.length]);
  const submit = () => {
    const f = fileRef.current?.files?.[0];
    if (f) { const bad = checkFile(f, RECEIPT_TYPES, RECEIPT_MAX); if (bad) return setError(bad); }
    if (!text.trim() && !f) return setError('Write a message or attach a file.');
    setError(null); send.mutate();
  };
  if (q.isLoading) return <p role="status" className="text-sm text-muted">Loading conversation…</p>;
  if (q.isError || !q.data) return <Alert>{toApiError(q.error).message}</Alert>;
  const open = q.data.ticket.chatOpen;
  return (
    <div className="flex h-full min-h-[24rem] flex-col rounded-2xl border border-line bg-surface">
      <div className="flex-1 space-y-3 overflow-y-auto p-4" role="log" aria-live="polite" aria-label="Conversation">
        <div className="rounded-xl bg-surface-2/60 p-3 text-sm"><p className="mb-1 text-xs text-muted">Original request</p><p className="whitespace-pre-wrap">{q.data.ticket.description}</p></div>
        {q.data.messages.map((m) => (
          <div key={m.id} className={`flex ${m.mine ? 'justify-end' : 'justify-start'}`}><div className={`max-w-[85%] rounded-2xl px-3 py-2 text-sm ${m.mine ? 'bg-accent text-white' : 'bg-surface-2'}`}>
            <p className={`mb-0.5 text-xs ${m.mine ? 'text-white/80' : 'text-muted'}`}>{m.mine ? 'You' : m.senderRole === 'ADMIN' ? `${m.senderName} (support)` : m.senderName}</p>
            {m.body && <p className="whitespace-pre-wrap break-words">{m.body}</p>}
            {m.fileMessageId && <button type="button" className="mt-1 text-xs underline" onClick={() => downloadFile(() => getBlob(`${base}/messages/${m.fileMessageId}/file`, { download: 'true' }), m.fileName ?? 'attachment').catch((e) => setError(toApiError(e).message))}>📎 {m.fileName}</button>}
            <p className={`mt-1 text-right text-[10px] ${m.mine ? 'text-white/70' : 'text-muted'}`}>{new Date(m.createdAt).toLocaleString()}{m.mine ? (m.readAt ? ' · read' : ' · delivered') : ''}</p></div></div>))}
        <div ref={bottom} />
      </div>
      {error && <div className="px-4"><Alert>{error}</Alert></div>}
      {open ? (
        <form className="flex flex-col gap-2 border-t border-line p-3" onSubmit={(e) => { e.preventDefault(); submit(); }}>
          <label htmlFor={`msg-${ticketId}`} className="sr-only">Message</label>
          <textarea id={`msg-${ticketId}`} rows={2} maxLength={4000} value={text} onChange={(e) => setText(e.target.value)} placeholder="Type a message…" className="w-full rounded-xl border border-line bg-surface-2/60 px-3 py-2 text-sm" />
          <div className="flex items-center justify-between gap-2"><input ref={fileRef} type="file" aria-label="Attach a screenshot or document" accept={RECEIPT_TYPES.join(',')} className="max-w-[60%] text-xs" /><Button type="submit" loading={send.isPending}>Send</Button></div></form>
      ) : <p className="border-t border-line p-3 text-center text-sm text-muted">This ticket is closed.</p>}
    </div>
  );
}
