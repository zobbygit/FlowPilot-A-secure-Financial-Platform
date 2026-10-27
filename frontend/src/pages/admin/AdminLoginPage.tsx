import { AuthShell } from '../../components/AuthShell';
import { LoginForm } from '../user/LoginForm';

export function AdminLoginPage() {
  return (
    <AuthShell title="Admin sign in" subtitle="Authorised administrators only. All sign-in attempts are audited.">
      <LoginForm role="ADMIN" />
    </AuthShell>
  );
}
