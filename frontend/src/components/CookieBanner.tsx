import { useState } from 'react';
import { Link } from 'react-router-dom';
import { readConsent, saveConsent, type ConsentChoice } from '../lib/consent';
import { Button } from './Button';

/** Compact consent banner. FlowPilot loads no optional tracking; the saved choice gates any future ones. */
export function CookieBanner() {
  const [visible, setVisible] = useState(() => readConsent() === null);
  if (!visible) return null;

  const choose = (c: ConsentChoice) => { saveConsent(c); setVisible(false); };

  return (
    <div role="dialog" aria-label="Cookie preferences" aria-live="polite"
      className="fixed inset-x-3 bottom-3 z-50 mx-auto flex max-w-xl flex-col gap-3 rounded-2xl border border-line bg-surface p-4 shadow-glow sm:flex-row sm:items-center">
      <p className="text-sm text-muted">
        We use essential cookies to keep you signed in. Optional cookies stay off unless you accept.{' '}
        <Link to="/legal/cookies" className="font-medium text-accent underline">Cookie policy</Link>
      </p>
      <div className="flex shrink-0 gap-2">
        <Button variant="secondary" onClick={() => choose('rejected')}>Reject</Button>
        <Button onClick={() => choose('accepted')}>Accept</Button>
      </div>
    </div>
  );
}
