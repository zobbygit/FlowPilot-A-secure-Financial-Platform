// import { useQueryClient } from '@tanstack/react-query';
// import { useEffect } from 'react';
// import { env } from './env';

// /** Topic hints -> query keys to re-read from the REST API (the database stays the source of truth). */
// const TOPIC_KEYS: Record<string, string[]> = {
//   notifications: ['notifications', 'unread'],
//   payments: ['payments', 'accounts', 'transactions', 'dashboard'],
//   support: ['tickets', 'ticket', 'admin-tickets', 'admin-ticket', 'unread'],
//   timeline: ['admin-timeline', 'admin-dashboard'],
// };

// export function wsUrl(apiBase: string): string {
//   const u = new URL(apiBase);
//   u.protocol = u.protocol === 'https:' ? 'wss:' : 'ws:';
//   u.pathname = '/ws'; u.search = ''; u.hash = '';
//   return u.toString();
// }

// /** Opens one authenticated WebSocket with capped exponential backoff; after any reconnect it reloads server state. */
// export function useLiveUpdates(enabled: boolean): void {
//   const qc = useQueryClient();
//   useEffect(() => {
//     if (!enabled || typeof WebSocket === 'undefined') return;
//     let ws: WebSocket | null = null, stopped = false, attempt = 0, timer: ReturnType<typeof setTimeout> | undefined, everConnected = false;

//     const connect = () => {
//       ws = new WebSocket(wsUrl(env.apiBaseUrl));
//       ws.onopen = () => {
//         attempt = 0;
//         if (everConnected) qc.invalidateQueries();   // missed events while offline: reload everything from the server
//         everConnected = true;
//       };
//       ws.onmessage = (ev) => {
//         try {
//           const msg = JSON.parse(String(ev.data)) as { type?: string; topic?: string };
//           if (msg.type === 'refresh' && msg.topic && TOPIC_KEYS[msg.topic]) TOPIC_KEYS[msg.topic].forEach((k) => qc.invalidateQueries({ queryKey: [k] }));
//         } catch { /* ignore malformed hints */ }
//       };
//       ws.onclose = () => {
//         if (stopped) return;
//         attempt += 1;
//         timer = setTimeout(connect, Math.min(30_000, 1000 * 2 ** Math.min(attempt, 5)) + Math.random() * 500);
//       };
//     };
//     connect();
//     return () => { stopped = true; if (timer) clearTimeout(timer); ws?.close(); };
//   }, [enabled, qc]);
// }


import { useQueryClient } from '@tanstack/react-query';
import { useEffect } from 'react';
import { env } from './env';

/** Topic hints -> query keys to re-read from the REST API (the database stays the source of truth). */
const TOPIC_KEYS: Record<string, string[]> = {
  notifications: ['notifications', 'unread'],
  payments: ['payments', 'accounts', 'transactions', 'dashboard'],
  support: ['tickets', 'ticket', 'admin-tickets', 'admin-ticket', 'unread'],
  timeline: ['admin-timeline', 'admin-dashboard'],
};

export function wsUrl(apiBase: string): string {
  // If apiBase is relative (e.g., '/api'), use the current window origin.
  // Otherwise, use the absolute URL provided.
  const baseUrl = apiBase.startsWith('http') ? apiBase : window.location.origin;
  const u = new URL(baseUrl);
  
  u.protocol = u.protocol === 'https:' ? 'wss:' : 'ws:';
  u.pathname = '/ws'; 
  u.search = ''; 
  u.hash = '';
  
  return u.toString();
}

/** Opens one authenticated WebSocket with capped exponential backoff; after any reconnect it reloads server state. */
export function useLiveUpdates(enabled: boolean): void {
  const qc = useQueryClient();
  useEffect(() => {
    if (!enabled || typeof WebSocket === 'undefined') return;
    let ws: WebSocket | null = null, stopped = false, attempt = 0, timer: ReturnType<typeof setTimeout> | undefined, everConnected = false;

    const connect = () => {
      ws = new WebSocket(wsUrl(env.apiBaseUrl));
      ws.onopen = () => {
        attempt = 0;
        if (everConnected) qc.invalidateQueries();   // missed events while offline: reload everything from the server
        everConnected = true;
      };
      ws.onmessage = (ev) => {
        try {
          const msg = JSON.parse(String(ev.data)) as { type?: string; topic?: string };
          if (msg.type === 'refresh' && msg.topic && TOPIC_KEYS[msg.topic]) TOPIC_KEYS[msg.topic].forEach((k) => qc.invalidateQueries({ queryKey: [k] }));
        } catch { /* ignore malformed hints */ }
      };
      ws.onclose = () => {
        if (stopped) return;
        attempt += 1;
        timer = setTimeout(connect, Math.min(30_000, 1000 * 2 ** Math.min(attempt, 5)) + Math.random() * 500);
      };
    };
    connect();
    return () => { stopped = true; if (timer) clearTimeout(timer); ws?.close(); };
  }, [enabled, qc]);
}