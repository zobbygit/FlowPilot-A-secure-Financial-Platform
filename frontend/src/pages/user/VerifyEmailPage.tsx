import { CheckCircle2, XCircle } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { AuthShell } from '../../components/AuthShell';
import { ButtonLink } from '../../components/Button';
import { api, toApiError } from '../../lib/api';

type State = { kind: 'loading' } | { kind: 'success' } | { kind: 'error'; code: string; message: string };

export function VerifyEmailPage() {
  const token = useSearchParams()[0].get('token');
  const [state, setState] = useState<State>(token ? { kind: 'loading' } : { kind: 'error', code: 'TOKEN_INVALID', message: 'This verification link is incomplete.' });
  const started = useRef(false);   // the token is single-use: never POST twice (StrictMode double effects)

  useEffect(() => {
    if (!token || started.current) return;
    started.current = true;
    window.history.replaceState(null, '', window.location.pathname);   // keep the one-time token out of history and referrers
    api.post('/auth/verify-email', { token })
      .then(() => setState({ kind: 'success' }))
      .catch((e) => { const err = toApiError(e); setState({ kind: 'error', code: err.code, message: err.message }); });
  }, [token]);

  if (state.kind === 'loading') return <AuthShell title="Verifying your email…"><p role="status" className="text-sm text-muted">One moment.</p></AuthShell>;

  if (state.kind === 'success') {
    return (
      <AuthShell title="Email Verified Successfully — Please Log In.">
        <CheckCircle2 className="mb-4 h-12 w-12 text-success" aria-hidden="true" />
        <p className="mb-6 text-sm text-muted">Your account is active. Log in to set up your finances.</p>
        <ButtonLink to="/user/login" className="w-full">Log in</ButtonLink>
      </AuthShell>
    );
  }

  const used = state.code === 'TOKEN_USED';
  return (
    <AuthShell title={used ? 'Link already used' : 'We couldn’t verify that link'}>
      <XCircle className="mb-4 h-12 w-12 text-danger" aria-hidden="true" />
      <p className="mb-6 text-sm text-muted">{state.message}{used ? ' If you already verified, you can log in.' : ' Request a fresh link and try again.'}</p>
      <div className="flex flex-wrap gap-3">
        <ButtonLink to="/user/login" variant={used ? 'primary' : 'secondary'}>Log in</ButtonLink>
        {!used && <ButtonLink to="/user/check-email">Get a new link</ButtonLink>}
      </div>
      <p className="mt-6 text-sm text-muted"><Link to="/" className="underline">Back to home</Link></p>
    </AuthShell>
  );
}