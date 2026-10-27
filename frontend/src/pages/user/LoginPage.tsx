// import { Link } from 'react-router-dom';
// import { AuthShell } from '../../components/AuthShell';
// import { LoginForm } from './LoginForm';

// export function LoginPage() {
//   return (
//     <AuthShell title="Welcome back" subtitle="Log in to your FlowPilot account.">
//       <LoginForm role="USER" />
//       <p className="mt-6 text-center text-sm text-muted">New here? <Link to="/user/register" className="font-medium text-accent underline">Create an account</Link></p>
//     </AuthShell>
//   );
// }


import { Link } from 'react-router-dom';
import { AuthShell } from '../../components/AuthShell';
import { LoginForm } from './LoginForm';

export function LoginPage() {
  return (
    <AuthShell title="Welcome back" subtitle="Log in to your FlowPilot account.">
      
      {/* Container for the premium glass card */}
      <div className="relative w-full max-w-[420px] mx-auto mt-6">
        
        {/* Ambient background glows */}
        <div className="pointer-events-none absolute -top-12 -left-12 h-48 w-48 rounded-full bg-accent/20 blur-[80px]" />
        <div className="pointer-events-none absolute -bottom-12 -right-12 h-48 w-48 rounded-full bg-accent/10 blur-[80px]" />

        {/* Glassmorphism Card */}
        <div className="relative overflow-hidden rounded-3xl border border-line bg-surface/70 p-8 shadow-2xl backdrop-blur-xl sm:p-10">
          
          {/* Note: I removed the custom <h1> and <p> here because AuthShell renders them automatically */}

          <LoginForm role="USER" />

          <p className="mt-8 text-center text-sm text-muted">
            New here?{' '}
            <Link 
              to="/user/register" 
              className="font-semibold text-accent underline decoration-accent/30 underline-offset-4 transition-all hover:text-accent/80 hover:decoration-accent"
            >
              Create an account
            </Link>
          </p>
        </div>
      </div>
      
    </AuthShell>
  );
}