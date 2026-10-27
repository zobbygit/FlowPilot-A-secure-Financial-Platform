// import { zodResolver } from '@hookform/resolvers/zod';
// import { useState } from 'react';
// import { useForm } from 'react-hook-form';
// import { Link, useNavigate } from 'react-router-dom';
// import { Alert } from '../../components/Alert';
// import { AuthShell } from '../../components/AuthShell';
// import { Button } from '../../components/Button';
// import { TextField } from '../../components/TextField';
// import { api, toApiError } from '../../lib/api';
// import { registerSchema, type RegisterForm } from '../../lib/validation';

// export function RegisterPage() {
//   const navigate = useNavigate();
//   const [formError, setFormError] = useState<string | null>(null);
//   const { register, handleSubmit, setError, formState: { errors, isSubmitting } } = useForm<RegisterForm>({
//     resolver: zodResolver(registerSchema),
//     defaultValues: { acceptTerms: false },
//   });

//   const onSubmit = async ({ fullName, email, password, acceptTerms }: RegisterForm) => {
//     setFormError(null);
//     try {
//       await api.post('/auth/register', { fullName, email, password, acceptTerms });
//       navigate('/user/check-email', { state: { email } });
//     } catch (e) {
//       const err = toApiError(e);
//       if (err.fieldErrors) {
//         Object.entries(err.fieldErrors).forEach(([k, msg]) => setError(k as keyof RegisterForm, { message: msg }));
//       } else if (err.code === 'WEAK_PASSWORD') setError('password', { message: err.message });
//       else setFormError(err.message);
//     }
//   };

//   return (
//     <AuthShell title="Create your account" subtitle="Start your journey to smarter money management.">
//       <form noValidate onSubmit={handleSubmit(onSubmit)} className="space-y-4">
//         {formError && <Alert>{formError}</Alert>}
//         <TextField label="Full name" autoComplete="name" error={errors.fullName?.message} {...register('fullName')} />
//         <TextField label="Email address" type="email" autoComplete="email" error={errors.email?.message} {...register('email')} />
//         <TextField label="Password" type="password" autoComplete="new-password" hint="At least 12 characters, mixing three character types." error={errors.password?.message} {...register('password')} />
//         <TextField label="Confirm password" type="password" autoComplete="new-password" error={errors.confirmPassword?.message} {...register('confirmPassword')} />
//         <div>
//           <label className="flex items-start gap-2 text-sm text-muted">
//             <input type="checkbox" className="mt-1 h-4 w-4 accent-[rgb(var(--fp-accent))]" {...register('acceptTerms')} />
//             <span>I agree to the <Link to="/legal/terms" className="text-accent underline">Terms of Service</Link> and <Link to="/legal/privacy" className="text-accent underline">Privacy Policy</Link></span>
//           </label>
//           {errors.acceptTerms && <p role="alert" className="mt-1.5 text-xs text-danger">{errors.acceptTerms.message}</p>}
//         </div>
//         <Button type="submit" loading={isSubmitting} className="w-full">Create account</Button>
//       </form>
//       <p className="mt-6 text-center text-sm text-muted">Already have an account? <Link to="/user/login" className="font-medium text-accent underline">Log in</Link></p>
//     </AuthShell>
//   );
// }




import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link, useNavigate } from 'react-router-dom';
import { Check, Eye, EyeOff, X } from 'lucide-react';
import { Alert } from '../../components/Alert';
import { AuthShell } from '../../components/AuthShell';
import { Button } from '../../components/Button';
import { api, toApiError } from '../../lib/api';
import { registerSchema, type RegisterForm } from '../../lib/validation';

// Helper to calculate password strength (0 to 4)
const getPasswordStrength = (pwd: string) => {
  if (!pwd) return 0;
  let score = 0;
  if (pwd.length >= 8) score++;
  if (pwd.length >= 12) score++;
  if (/[A-Z]/.test(pwd) && /[a-z]/.test(pwd)) score++;
  if (/[0-9]/.test(pwd) && /[^A-Za-z0-9]/.test(pwd)) score++;
  return Math.min(score, 4);
};

export function RegisterPage() {
  const navigate = useNavigate();
  const [formError, setFormError] = useState<string | null>(null);
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);

  const { register, handleSubmit, setError, watch, formState: { errors, isSubmitting } } = useForm<RegisterForm>({
    resolver: zodResolver(registerSchema),
    defaultValues: { acceptTerms: false },
  });

  const passwordValue = watch('password', '');
  const strength = getPasswordStrength(passwordValue);

  // Strength colors and labels
  const strengthMap = [
    { label: 'Too weak', color: 'bg-danger', text: 'text-danger' },
    { label: 'Weak', color: 'bg-danger', text: 'text-danger' },
    { label: 'Fair', color: 'bg-warning', text: 'text-warning' },
    { label: 'Good', color: 'bg-blue-500', text: 'text-blue-500' },
    { label: 'Strong', color: 'bg-success', text: 'text-success' },
  ];
  const currentStrength = strengthMap[strength];

  const onSubmit = async ({ fullName, email, password, acceptTerms }: RegisterForm) => {
    setFormError(null);
    try {
      await api.post('/auth/register', { fullName, email, password, acceptTerms });
      navigate('/user/check-email', { state: { email } });
    } catch (e) {
      const err = toApiError(e);
      if (err.fieldErrors) {
        Object.entries(err.fieldErrors).forEach(([k, msg]) => setError(k as keyof RegisterForm, { message: msg }));
      } else if (err.code === 'WEAK_PASSWORD') setError('password', { message: err.message });
      else setFormError(err.message);
    }
  };

  return (
    <AuthShell title="Create your account" subtitle="Start your journey to smarter money management.">
      
      {/* Container for the premium glass card */}
      <div className="relative w-full max-w-[420px] mx-auto mt-6">
        
        {/* Ambient background glows */}
        <div className="pointer-events-none absolute -top-12 -left-12 h-48 w-48 rounded-full bg-accent/20 blur-[80px]" />
        <div className="pointer-events-none absolute -bottom-12 -right-12 h-48 w-48 rounded-full bg-accent/10 blur-[80px]" />

        {/* Glassmorphism Card */}
        <div className="relative overflow-hidden rounded-3xl border border-line bg-surface/70 p-8 shadow-2xl backdrop-blur-xl sm:p-10">
          
          <form noValidate onSubmit={handleSubmit(onSubmit)} className="space-y-5">
            {formError && (
              <div className="mb-2">
                <Alert tone="danger">{formError}</Alert>
              </div>
            )}

            {/* Full Name */}
            <div className="relative">
              <input
                id="fullName"
                type="text"
                autoComplete="name"
                {...register('fullName')}
                placeholder=" "
                className={`peer w-full rounded-xl border bg-surface-2/40 px-4 pt-6 pb-2 text-sm text-ink outline-none transition-all focus:border-accent focus:bg-surface focus:ring-1 focus:ring-accent ${errors.fullName ? 'border-danger' : 'border-line'}`}
              />
              <label htmlFor="fullName" className="pointer-events-none absolute left-4 top-2 text-xs text-muted transition-all peer-placeholder-shown:top-3.5 peer-placeholder-shown:text-sm peer-focus:top-2 peer-focus:text-xs peer-focus:text-accent">
                Full name
              </label>
              {errors.fullName && <p className="mt-1.5 text-xs text-danger">{errors.fullName.message}</p>}
            </div>

            {/* Email */}
            <div className="relative">
              <input
                id="email"
                type="email"
                autoComplete="email"
                {...register('email')}
                placeholder=" "
                className={`peer w-full rounded-xl border bg-surface-2/40 px-4 pt-6 pb-2 text-sm text-ink outline-none transition-all focus:border-accent focus:bg-surface focus:ring-1 focus:ring-accent ${errors.email ? 'border-danger' : 'border-line'}`}
              />
              <label htmlFor="email" className="pointer-events-none absolute left-4 top-2 text-xs text-muted transition-all peer-placeholder-shown:top-3.5 peer-placeholder-shown:text-sm peer-focus:top-2 peer-focus:text-xs peer-focus:text-accent">
                Email address
              </label>
              {errors.email && <p className="mt-1.5 text-xs text-danger">{errors.email.message}</p>}
            </div>

            {/* Password with Strength Meter */}
            <div className="space-y-2">
              <div className="relative">
                <input
                  id="password"
                  type={showPassword ? 'text' : 'password'}
                  autoComplete="new-password"
                  {...register('password')}
                  placeholder=" "
                  className={`peer w-full rounded-xl border bg-surface-2/40 px-4 pt-6 pb-2 pr-12 text-sm text-ink outline-none transition-all focus:border-accent focus:bg-surface focus:ring-1 focus:ring-accent ${errors.password ? 'border-danger' : 'border-line'}`}
                />
                <label htmlFor="password" className="pointer-events-none absolute left-4 top-2 text-xs text-muted transition-all peer-placeholder-shown:top-3.5 peer-placeholder-shown:text-sm peer-focus:top-2 peer-focus:text-xs peer-focus:text-accent">
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
              </div>

              {/* Password Strength Meter */}
              {passwordValue.length > 0 && (
                <div className="space-y-2">
                  <div className="flex gap-1 h-1.5 w-full overflow-hidden rounded-full bg-surface-2">
                    {[1, 2, 3, 4].map((level) => (
                      <div
                        key={level}
                        className={`flex-1 transition-all duration-300 ${
                          strength >= level ? currentStrength.color : 'bg-transparent'
                        }`}
                      />
                    ))}
                  </div>
                  
                  <div className="flex items-center justify-between text-xs">
                    <span className={`font-medium ${currentStrength.text}`}>
                      {currentStrength.label}
                    </span>
                  </div>

                  {/* Live Checklist */}
                  <ul className="mt-2 space-y-1 text-xs text-muted">
                    <li className="flex items-center gap-1.5">
                      {passwordValue.length >= 12 ? <Check className="h-3.5 w-3.5 text-success" /> : <X className="h-3.5 w-3.5 text-muted/50" />}
                      At least 12 characters
                    </li>
                    <li className="flex items-center gap-1.5">
                      {/[A-Z]/.test(passwordValue) && /[a-z]/.test(passwordValue) ? <Check className="h-3.5 w-3.5 text-success" /> : <X className="h-3.5 w-3.5 text-muted/50" />}
                      Uppercase & lowercase letters
                    </li>
                    <li className="flex items-center gap-1.5">
                      {/[0-9]/.test(passwordValue) && /[^A-Za-z0-9]/.test(passwordValue) ? <Check className="h-3.5 w-3.5 text-success" /> : <X className="h-3.5 w-3.5 text-muted/50" />}
                      Number & special character
                    </li>
                  </ul>
                </div>
              )}
              {errors.password && <p className="mt-1.5 text-xs text-danger">{errors.password.message}</p>}
            </div>

            {/* Confirm Password */}
            <div className="relative">
              <input
                id="confirmPassword"
                type={showConfirm ? 'text' : 'password'}
                autoComplete="new-password"
                {...register('confirmPassword')}
                placeholder=" "
                className={`peer w-full rounded-xl border bg-surface-2/40 px-4 pt-6 pb-2 pr-12 text-sm text-ink outline-none transition-all focus:border-accent focus:bg-surface focus:ring-1 focus:ring-accent ${errors.confirmPassword ? 'border-danger' : 'border-line'}`}
              />
              <label htmlFor="confirmPassword" className="pointer-events-none absolute left-4 top-2 text-xs text-muted transition-all peer-placeholder-shown:top-3.5 peer-placeholder-shown:text-sm peer-focus:top-2 peer-focus:text-xs peer-focus:text-accent">
                Confirm password
              </label>
              <button
                type="button"
                onClick={() => setShowConfirm(!showConfirm)}
                className="absolute right-3 top-1/2 -translate-y-1/2 text-muted transition-colors hover:text-ink focus:outline-none"
                tabIndex={-1}
              >
                {showConfirm ? <EyeOff className="h-5 w-5" /> : <Eye className="h-5 w-5" />}
              </button>
              {errors.confirmPassword && <p className="mt-1.5 text-xs text-danger">{errors.confirmPassword.message}</p>}
            </div>

            {/* Terms Checkbox */}
            <div className="pt-2">
              <label className="flex items-start gap-3 text-sm text-muted">
                <input 
                  type="checkbox" 
                  className="mt-0.5 h-4 w-4 rounded border-line bg-surface-2 text-accent focus:ring-accent focus:ring-offset-0 accent-accent" 
                  {...register('acceptTerms')} 
                />
                <span className="leading-tight">
                  I agree to the <Link to="/legal/terms" className="text-accent underline decoration-accent/30 hover:decoration-accent">Terms of Service</Link> and <Link to="/legal/privacy" className="text-accent underline decoration-accent/30 hover:decoration-accent">Privacy Policy</Link>
                </span>
              </label>
              {errors.acceptTerms && <p role="alert" className="mt-1.5 text-xs text-danger">{errors.acceptTerms.message}</p>}
            </div>

            <Button 
              type="submit" 
              loading={isSubmitting} 
              className="w-full bg-gradient-to-r from-accent to-accent/80 py-3 text-sm font-semibold text-white shadow-lg shadow-accent/20 transition-all hover:from-accent hover:to-accent hover:shadow-accent/30 active:scale-[0.98]"
            >
              Create account
            </Button>
          </form>

          <p className="mt-8 text-center text-sm text-muted">
            Already have an account?{' '}
            <Link to="/user/login" className="font-semibold text-accent underline decoration-accent/30 underline-offset-4 transition-all hover:text-accent/80 hover:decoration-accent">
              Log in
            </Link>
          </p>
        </div>
      </div>
    </AuthShell>
  );
}