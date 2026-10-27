import { useParams } from 'react-router-dom';
import { AuthShell } from '../../components/AuthShell';
import { ButtonLink } from '../../components/Button';
import { NotFoundPage } from '../NotFoundPage';

const DOCS: Record<string, { title: string; body: string[] }> = {
  terms: { title: 'Terms of Service', body: ['FlowPilot is a personal recordkeeping and payment-simulation tool. Payments inside FlowPilot are simulated and do not move real money.', 'Templates and documents you create are personal records, not official tax invoices or legally certified receipts.'] },
  privacy: { title: 'Privacy Policy', body: ['We collect only what is needed to run your account: your name, email, and the financial records you enter.', 'Session records (browser details and IP address) are kept for 30 days so you can review and revoke devices.', 'You can export or delete your data from Settings once those features ship.'] },
  cookies: { title: 'Cookie Policy', body: ['Essential cookies keep you signed in and protect against cross-site request forgery. They cannot be switched off.', 'Optional cookies are not used today. If added later, they load only after you choose Accept.'] },
};

export function LegalPage() {
  const { doc = '' } = useParams();
  const page = DOCS[doc];
  if (!page) return <NotFoundPage />;
  return (
    <AuthShell title={page.title} subtitle="Draft text — requires legal review before public launch.">
      <div className="space-y-4 text-sm text-muted">{page.body.map((p) => <p key={p}>{p}</p>)}</div>
      <div className="mt-6"><ButtonLink to="/" variant="secondary">Back to home</ButtonLink></div>
    </AuthShell>
  );
}
