// The ONLY file that touches import.meta.env (Jest replaces it with src/test/envShim.ts).
// VITE_* values are public. Never read secrets from here.
export const env = {
  apiBaseUrl: (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? 'http://localhost:8080/api',
};
