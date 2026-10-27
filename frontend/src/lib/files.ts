export const RECEIPT_TYPES = ['image/jpeg', 'image/png', 'image/webp', 'application/pdf'];
export const RECEIPT_MAX = 8 * 1024 * 1024;
export const AVATAR_TYPES = ['image/jpeg', 'image/png', 'image/webp'];
export const AVATAR_MAX = 2 * 1024 * 1024;

/** Early UX check only. The server re-validates the real file content and is the authority. */
export function checkFile(f: File, types: string[], max: number): string | null {
  if (f.size === 0) return 'That file is empty.';
  if (f.size > max) return `That file is too large (limit ${Math.round(max / 1024 / 1024)} MB).`;
  if (!types.includes(f.type)) return 'That file type is not allowed.';
  return null;
}

export const prettySize = (n: number) => (n < 1024 * 1024 ? `${Math.max(1, Math.round(n / 1024))} KB` : `${(n / 1024 / 1024).toFixed(1)} MB`);
