import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link } from 'react-router-dom';
import { Alert } from '../../components/Alert';
import { AuthShell } from '../../components/AuthShell';
import { Button } from '../../components/Button';
import { TextField } from '../../components/TextField';
import { api, toApiError } from '../../lib/api';
import { forgotSchema, type ForgotForm } from '../../lib/validation';

export function ForgotPasswordPage() {
  const [notice, setNotice] = useState<{ tone: 'success' | 'danger'; text: string } | null>(null);
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<ForgotForm>({ resolver: zodResolver(forgotSchema) });

  const onSubmit = async (v: ForgotForm) => {
    setNotice(null);
    try {
      await api.post('/auth/forgot-password', v);
      setNotice({ tone: 'success', text: 'If an account exists for that address, a password-reset link is on its way. It expires in 30 minutes.' });
    } catch (e) { setNotice({ tone: 'danger', text: toApiError(e).message }); }
  };

  return (
    <AuthShell title="Forgot your password?" subtitle="Enter your email and we'll send a reset link.">
      <form noValidate onSubmit={handleSubmit(onSubmit)} className="space-y-4">
        {notice && <Alert tone={notice.tone}>{notice.text}</Alert>}
        <TextField label="Email address" type="email" autoComplete="email" error={errors.email?.message} {...register('email')} />
        <Button type="submit" loading={isSubmitting} className="w-full">Send reset link</Button>
      </form>
      <p className="mt-6 text-center text-sm text-muted"><Link to="/user/login" className="font-medium text-accent underline">Back to log in</Link></p>
    </AuthShell>
  );
}
