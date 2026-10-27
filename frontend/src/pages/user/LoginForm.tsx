// import { zodResolver } from '@hookform/resolvers/zod';
// import { useState } from 'react';
// import { useForm } from 'react-hook-form';
// import { Link, useLocation, useNavigate } from 'react-router-dom';
// import { useAuth } from '../../auth/AuthContext';
// import { Alert } from '../../components/Alert';
// import { Button } from '../../components/Button';
// import { TextField } from '../../components/TextField';
// import { api, resetCsrf, toApiError } from '../../lib/api';
// import type { Me, Role } from '../../lib/types';
// import { loginSchema, type LoginForm as LoginValues } from '../../lib/validation';

// export function LoginForm({ role }: { role: Role }) {
//   const { setMe } = useAuth();
//   const navigate = useNavigate();
//   const location = useLocation();
//   const [error, setError] = useState<{ code: string; message: string } | null>(null);
//   const { register, handleSubmit, getValues, formState: { errors, isSubmitting } } = useForm<LoginValues>({ resolver: zodResolver(loginSchema) });

//   const onSubmit = async (v: LoginValues) => {
//     setError(null);
//     try {
//       const { data } = await api.post<Me>(role === 'ADMIN' ? '/auth/admin/login' : '/auth/login', v);
//       resetCsrf();
//       setMe(data);
//       const from = (location.state as { from?: string } | null)?.from;
//       navigate(from && from.startsWith(role === 'ADMIN' ? '/admin' : '/app') ? from : role === 'ADMIN' ? '/admin' : '/app', { replace: true });
//     } catch (e) {
//       const err = toApiError(e);
//       setError({ code: err.code, message: err.code === 'RATE_LIMITED' && err.retryAfter ? `${err.message} Try again in ${Math.ceil(err.retryAfter / 60)} min.` : err.message });
//     }
//   };

//   return (
//     <form noValidate onSubmit={handleSubmit(onSubmit)} className="space-y-4">
//       {error && (
//         <Alert>
//           {error.message}{' '}
//           {error.code === 'EMAIL_NOT_VERIFIED' && <Link to="/user/check-email" state={{ email: getValues('email') }} className="font-semibold underline">Resend verification email</Link>}
//         </Alert>
//       )}
//       <TextField label="Email address" type="email" autoComplete="email" error={errors.email?.message} {...register('email')} />
//       <TextField label="Password" type="password" autoComplete="current-password" error={errors.password?.message} {...register('password')} />
//       {role === 'USER' && <div className="text-right text-sm"><Link to="/user/forgot-password" className="text-accent underline">Forgot password?</Link></div>}
//       <Button type="submit" loading={isSubmitting} className="w-full">Log in</Button>
//     </form>
//   );
// }



import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { Eye, EyeOff } from 'lucide-react';
import { useAuth } from '../../auth/AuthContext';
import { Alert } from '../../components/Alert';
import { Button } from '../../components/Button';
import { api, resetCsrf, toApiError } from '../../lib/api';
import type { Me, Role } from '../../lib/types';
import { loginSchema, type LoginForm as LoginValues } from '../../lib/validation';

export function LoginForm({ role }: { role: Role }) {
  const { setMe } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [error, setError] = useState<{ code: string; message: string } | null>(null);
  const [showPassword, setShowPassword] = useState(false);
  
  const { register, handleSubmit, getValues, formState: { errors, isSubmitting } } = useForm<LoginValues>({ resolver: zodResolver(loginSchema) });

  const onSubmit = async (v: LoginValues) => {
    setError(null);
    try {
      const { data } = await api.post<Me>(role === 'ADMIN' ? '/auth/admin/login' : '/auth/login', v);
      resetCsrf();
      setMe(data);
      const from = (location.state as { from?: string } | null)?.from;
      navigate(from && from.startsWith(role === 'ADMIN' ? '/admin' : '/app') ? from : role === 'ADMIN' ? '/admin' : '/app', { replace: true });
    } catch (e) {
      const err = toApiError(e);
      setError({ code: err.code, message: err.code === 'RATE_LIMITED' && err.retryAfter ? `${err.message} Try again in ${Math.ceil(err.retryAfter / 60)} min.` : err.message });
    }
  };

  return (
    <div className="w-full">
       {error && (
        <div className="mb-6">
          <Alert tone="danger">
            {error.message}{' '}
            {error.code === 'EMAIL_NOT_VERIFIED' && (
              <Link to="/user/check-email" state={{ email: getValues('email') }} className="font-semibold underline">
                Resend verification email
              </Link>
            )}
          </Alert>
        </div>
      )}

      <form noValidate onSubmit={handleSubmit(onSubmit)} className="space-y-6">
        
        {/* Floating Label Email Input */}
        <div className="relative">
          <input
            id="email"
            type="email"
            autoComplete="email"
            {...register('email')}
            placeholder=" "
            className={`peer w-full rounded-xl border bg-surface-2/40 px-4 pt-6 pb-2 text-sm text-ink outline-none transition-all focus:border-accent focus:bg-surface focus:ring-1 focus:ring-accent ${
              errors.email ? 'border-danger' : 'border-line'
            }`}
          />
          <label
            htmlFor="email"
            className="pointer-events-none absolute left-4 top-2 text-xs text-muted transition-all peer-placeholder-shown:top-3.5 peer-placeholder-shown:text-sm peer-focus:top-2 peer-focus:text-xs peer-focus:text-accent"
          >
            Email address
          </label>
          {errors.email && <p className="mt-1.5 text-xs text-danger">{errors.email.message}</p>}
        </div>

        {/* Floating Label Password Input */}
        <div className="relative">
          <input
            id="password"
            type={showPassword ? 'text' : 'password'}
            autoComplete="current-password"
            {...register('password')}
            placeholder=" "
            className={`peer w-full rounded-xl border bg-surface-2/40 px-4 pt-6 pb-2 pr-12 text-sm text-ink outline-none transition-all focus:border-accent focus:bg-surface focus:ring-1 focus:ring-accent ${
              errors.password ? 'border-danger' : 'border-line'
            }`}
          />
          <label
            htmlFor="password"
            className="pointer-events-none absolute left-4 top-2 text-xs text-muted transition-all peer-placeholder-shown:top-3.5 peer-placeholder-shown:text-sm peer-focus:top-2 peer-focus:text-xs peer-focus:text-accent"
          >
            Password
          </label>
          <button
            type="button"
            onClick={() => setShowPassword(!showPassword)}
            className="absolute right-3 top-1/2 -translate-y-1/2 text-muted transition-colors hover:text-ink focus:outline-none"
            tabIndex={-1}
          >
            {showPassword ? <EyeOff className="h-5 w-5" /> : <Eye className="h-5 w-5" />}
          </button>
          {errors.password && <p className="mt-1.5 text-xs text-danger">{errors.password.message}</p>}
        </div>

        {role === 'USER' && (
          <div className="flex justify-end">
            <Link to="/user/forgot-password" className="text-sm font-medium text-accent transition-colors hover:text-accent/80">
              Forgot password?
            </Link>
          </div>
        )}

        {/* Premium Gradient CTA Button */}
        <Button 
          type="submit" 
          loading={isSubmitting} 
          className="w-full bg-gradient-to-r from-accent to-accent/80 py-3 text-sm font-semibold text-white shadow-lg shadow-accent/20 transition-all hover:from-accent hover:to-accent hover:shadow-accent/30 active:scale-[0.98]"
        >
          Sign in
        </Button>
      </form>
    </div>
  );
}