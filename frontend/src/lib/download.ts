import { api } from './api';

/** Fetches a protected file with the session cookie and hands it to the browser as a download. */
export async function downloadFile(request: () => Promise<{ data: Blob }>, filename: string): Promise<void> {
  const { data } = await request();
  const url = URL.createObjectURL(data);
  const a = document.createElement('a');
  a.href = url; a.download = filename; a.rel = 'noopener';
  document.body.appendChild(a); a.click(); a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

export const getBlob = (url: string, params?: Record<string, string>) => api.get<Blob>(url, { params, responseType: 'blob' });
