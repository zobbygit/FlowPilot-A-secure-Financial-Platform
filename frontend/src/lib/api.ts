import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios';
import { env } from './env';

export class ApiError extends Error {
  constructor(
    message: string,
    public readonly status: number,
    public readonly code: string,
    public readonly fieldErrors?: Record<string, string>,
    public readonly retryAfter?: number,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

/** Normalises any thrown value into an ApiError with a safe, user-facing message. */
export function toApiError(e: unknown): ApiError {
  if (e instanceof ApiError) return e;
  if (axios.isAxiosError(e)) {
    const err = e as AxiosError<{ code?: string; message?: string; fieldErrors?: Record<string, string> }>;
    if (!err.response) return new ApiError('Cannot reach the server. Check your connection and try again.', 0, 'NETWORK');
    const d = err.response.data;
    const retry = Number(err.response.headers?.['retry-after']);
    return new ApiError(
      d?.message ?? 'Something went wrong. Please try again.',
      err.response.status,
      d?.code ?? 'UNKNOWN',
      d?.fieldErrors,
      Number.isFinite(retry) ? retry : undefined,
    );
  }
  return new ApiError('Something went wrong. Please try again.', 0, 'UNKNOWN');
}

/** Credentials (the HttpOnly session cookie) ride along; the token below is the CSRF token, not a login secret. */
export const api = axios.create({ baseURL: env.apiBaseUrl, withCredentials: true, timeout: 15000 });
const bare = axios.create({ baseURL: env.apiBaseUrl, withCredentials: true, timeout: 15000 });

let csrfToken: string | null = null;
let csrfInFlight: Promise<string> | null = null;
const SAFE = new Set(['get', 'head', 'options']);

async function ensureCsrf(): Promise<string> {
  if (csrfToken) return csrfToken;
  if (!csrfInFlight) {
    csrfInFlight = bare
      .get<{ token: string }>('/auth/csrf')
      .then((r) => {
        csrfToken = r.data.token;
        return r.data.token;
      })
      .finally(() => {
        csrfInFlight = null;
      });
  }
  return csrfInFlight;
}

api.interceptors.request.use(async (cfg: InternalAxiosRequestConfig) => {
  // Every request (even a GET) waits for the ONE token fetch, so parallel requests at page load can't each mint a different token.
  const token = await ensureCsrf();
  if (!SAFE.has((cfg.method ?? 'get').toLowerCase())) {
    cfg.headers.set('X-XSRF-TOKEN', token);
    // File uploads (multipart) also carry the token as a form field, so they can't fail on a dropped/altered header.
    if (typeof FormData !== 'undefined' && cfg.data instanceof FormData) cfg.data.set('_csrf', token);
  }
  return cfg;
});

type Retriable = InternalAxiosRequestConfig & { _csrfRetried?: boolean };

api.interceptors.response.use(
  (r) => r,
  async (error: AxiosError<{ code?: string }>) => {
    const cfg = error.config as Retriable | undefined;
    if (error.response?.status === 403 && error.response.data?.code === 'CSRF_INVALID' && cfg && !cfg._csrfRetried) {
      cfg._csrfRetried = true;
      csrfToken = null;
      return api(cfg);
    }
    const isAuthCall = cfg?.url?.startsWith('/auth/');
    if (error.response?.status === 401 && !isAuthCall) window.dispatchEvent(new Event('fp:session-expired'));
    return Promise.reject(toApiError(error));
  },
);

export function resetCsrf(): void {
  csrfToken = null;
}