import { zodResolver } from '@hookform/resolvers/zod';
import { MailCheck } from 'lucide-react';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link, useLocation } from 'react-router-dom';
import { Alert } from '../../components/Alert';
import { AuthShell } from '../../components/AuthShell';
import { Button } from '../../components/Button';
import { TextField } from '../../components/TextField';
import { api, toApiError } from '../../lib/api';
import { forgotSchema, type ForgotForm } from '../../lib/validation';

export function CheckEmailPage() {
  const initial = (useLocation().state as { email?: string } | null)?.email ?? '';
  const [notice, setNotice] = useState<{ tone: 'success' | 'danger'; text: string } | null>(null);
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<ForgotForm>({ resolver: zodResolver(forgotSchema), defaultValues: { email: initial } });

  const resend = async ({ email }: ForgotForm) => {
    setNotice(null);
    try {
      await api.post('/auth/resend-verification', { email });
      // Honest wording: the server accepted the request; delivery is not guaranteed.
      setNotice({ tone: 'success', text: 'Request received. If this address is awaiting verification, a new link is on its way.' });
    } catch (e) { setNotice({ tone: 'danger', text: toApiError(e).message }); }
  };

  return (
    <AuthShell title="Verify your email" subtitle="Open the verification link we sent to finish creating your account. It works once and expires in 24 hours.">
      <div className="mb-6 flex items-center gap-3 rounded-2xl border border-line bg-surface-2/60 p-4 text-sm text-muted">
        <MailCheck className="h-8 w-8 shrink-0 text-live" aria-hidden="true" />
        <p>Can't find it? Check your spam folder, or request a new link below.</p>
      </div>
      {notice && <div className="mb-4"><Alert tone={notice.tone}>{notice.text}</Alert></div>}
      <form noValidate onSubmit={handleSubmit(resend)} className="space-y-4">
        <TextField label="Email address" type="email" autoComplete="email" error={errors.email?.message} {...register('email')} />
        <Button type="submit" variant="secondary" loading={isSubmitting} className="w-full">Resend email</Button>
      </form>
      <p className="mt-6 text-center text-sm text-muted">Already verified? <Link to="/user/login" className="font-medium text-accent underline">Log in</Link></p>
    </AuthShell>
  );
}
