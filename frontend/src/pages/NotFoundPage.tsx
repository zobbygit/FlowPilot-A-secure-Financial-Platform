import { ButtonLink } from '../components/Button';
import { AuthShell } from '../components/AuthShell';

export function NotFoundPage() {
  return (
    <AuthShell title="Page not found" subtitle="The page you're looking for doesn't exist or has moved.">
      <ButtonLink to="/">Back to home</ButtonLink>
    </AuthShell>
  );
}
