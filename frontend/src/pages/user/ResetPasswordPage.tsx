import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { useEffect, useState as useStateOnce } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { Alert } from '../../components/Alert';
import { AuthShell } from '../../components/AuthShell';
import { Button, ButtonLink } from '../../components/Button';
import { TextField } from '../../components/TextField';
import { api, toApiError } from '../../lib/api';
import { resetSchema, type ResetForm } from '../../lib/validation';

export function ResetPasswordPage() {
  const [params] = useSearchParams();
  const [token] = useStateOnce(() => params.get('token'));   // read once, then remove it from the URL
  useEffect(() => { if (token) window.history.replaceState(null, '', window.location.pathname); }, [token]);
  const [done, setDone] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<ResetForm>({ resolver: zodResolver(resetSchema) });

  if (!token) return <AuthShell title="Reset link incomplete"><p className="mb-4 text-sm text-muted">This link is missing its token.</p><ButtonLink to="/user/forgot-password">Request a new link</ButtonLink></AuthShell>;
  if (done) return <AuthShell title="Password updated"><p className="mb-6 text-sm text-muted">All devices were signed out. Log in with your new password.</p><ButtonLink to="/user/login" className="w-full">Log in</ButtonLink></AuthShell>;

  const onSubmit = async (v: ResetForm) => {
    setError(null);
    try { await api.post('/auth/reset-password', { token, password: v.password }); setDone(true); }
    catch (e) { setError(toApiError(e).message); }
  };

  return (
    <AuthShell title="Choose a new password">
      <form noValidate onSubmit={handleSubmit(onSubmit)} className="space-y-4">
        {error && <Alert>{error} <Link to="/user/forgot-password" className="font-semibold underline">Request a new link</Link></Alert>}
        <TextField label="New password" type="password" autoComplete="new-password" hint="At least 12 characters, mixing three character types." error={errors.password?.message} {...register('password')} />
        <TextField label="Confirm password" type="password" autoComplete="new-password" error={errors.confirmPassword?.message} {...register('confirmPassword')} />
        <Button type="submit" loading={isSubmitting} className="w-full">Update password</Button>
      </form>
    </AuthShell>
  );
}